package com.library.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.library.common.error.ConflictException;
import com.library.common.error.ConstraintViolationTranslator;
import com.library.common.error.ErrorCode;
import com.library.common.error.NotFoundException;
import com.library.common.persistence.RowLocks;
import com.library.member.dto.MemberRequest;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit test of the lock-first order (DR-1, architecture 7.3) and the constraint translation (DR-5) in
 * {@link MemberService}: collaborators are mocks so that the order of calls can be verified exactly.
 */
class MemberServiceTest {

    private static final long ID = 7L;

    private MemberRepository members;
    private RowLocks rowLocks;
    private MemberService service;

    @BeforeEach
    void setUp() {
        members = mock(MemberRepository.class);
        rowLocks = mock(RowLocks.class);
        service = new MemberService(members, rowLocks, new ConstraintViolationTranslator());
    }

    private Member lockedMember(boolean active, int activeLoans) {
        Member member = new Member("Ada Lovelace", "ada@example.org");
        for (int i = 0; i < activeLoans; i++) {
            member.startLoan();
        }
        if (!active) {
            member.deactivate();
        }
        when(rowLocks.lock(Member.class, ID)).thenReturn(member);
        return member;
    }

    private static MemberRequest request(String email) {
        return new MemberRequest("Ada King", email);
    }

    // ---- DR-1: lock first -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("DR-1 edit takes the member lock before the email lookup and before saving")
    void editLocksFirst() {
        lockedMember(true, 2);
        when(members.existsByEmailNormalizedAndIdNot("ada@example.org", ID)).thenReturn(false);

        service.update(ID, request("Ada@Example.org"));

        InOrder order = inOrder(rowLocks, members);
        order.verify(rowLocks).lock(Member.class, ID);
        order.verify(members).existsByEmailNormalizedAndIdNot("ada@example.org", ID);
        order.verify(members).saveAndFlush(any(Member.class));
    }

    @Test
    @DisplayName("DR-1 edit changes name and email only: status and the loan counter keep the locked values")
    void editKeepsStatusAndCounter() {
        Member member = lockedMember(true, 2);

        service.update(ID, request("ada.king@example.org"));

        assertThat(member.getName()).isEqualTo("Ada King");
        assertThat(member.getEmail()).isEqualTo("ada.king@example.org");
        assertThat(member.getActiveLoanCount()).isEqualTo(2);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("DR-1 deactivate and reactivate take the member lock first")
    void statusChangesLockFirst() {
        lockedMember(true, 0);
        service.deactivate(ID);
        InOrder order = inOrder(rowLocks, members);
        order.verify(rowLocks).lock(Member.class, ID);
        order.verify(members).saveAndFlush(any(Member.class));

        service.reactivate(ID);
        order.verify(rowLocks).lock(Member.class, ID);
        order.verify(members).saveAndFlush(any(Member.class));
    }

    @Test
    @DisplayName("AC-3.BE.4 edit, deactivate and reactivate of a missing member fail at the lock and do nothing else")
    void missingMemberStopsAtLock() {
        when(rowLocks.lock(Member.class, ID)).thenThrow(NotFoundException.member(ID));

        assertThatThrownBy(() -> service.update(ID, request("a@b.co"))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deactivate(ID)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.reactivate(ID)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(members);
    }

    // ---- business rules --------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-3.BE.6 deactivate with a locked active-loan count above 0 is 409 and nothing is saved")
    void deactivateWithActiveLoans() {
        Member member = lockedMember(true, 1);

        assertThatThrownBy(() -> service.deactivate(ID))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MEMBER_HAS_ACTIVE_LOANS));
        verify(members, never()).saveAndFlush(any());
        assertThat(member.isActive()).isTrue();
    }

    @Test
    @DisplayName("D9 deactivate of an inactive and reactivate of an active member are no-ops without a write")
    void idempotentActionsDoNotWrite() {
        lockedMember(false, 0);
        assertThat(service.deactivate(ID).status()).isEqualTo(MemberStatus.INACTIVE);
        verify(members, never()).saveAndFlush(any());

        lockedMember(true, 0);
        assertThat(service.reactivate(ID).status()).isEqualTo(MemberStatus.ACTIVE);
        verify(members, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("AC-3.BE.3 the duplicate check uses the lower-cased trimmed email, independent of the default locale")
    void duplicateCheckKeyIsLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            when(members.existsByEmailNormalized("info@example.org")).thenReturn(true);

            assertThatThrownBy(() -> service.register(new MemberRequest("Info", "  INFO@Example.org ")))
                    .isInstanceOfSatisfying(ConflictException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
                        assertThat(e.getFieldErrors()).extracting("field").containsExactly("email");
                    });
            verifyNoInteractions(rowLocks);
            verify(members).existsByEmailNormalized("info@example.org");
            verifyNoMoreInteractions(members);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("AC-3.BE.3 edit to another member's email is 409 and saves nothing")
    void editDuplicateEmail() {
        Member member = lockedMember(true, 0);
        when(members.existsByEmailNormalizedAndIdNot("grace@example.org", ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(ID, request("Grace@example.org")))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
        verify(members, never()).saveAndFlush(any());
        assertThat(member.getEmail()).as("entity not modified").isEqualTo("ada@example.org");
    }

    // ---- DR-5 translation ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DR-5 a uk_member_email violation on flush (register race) is translated to EMAIL_ALREADY_EXISTS")
    void registerTranslatesUniqueViolation() {
        when(members.existsByEmailNormalized("ada@example.org")).thenReturn(false);
        when(members.saveAndFlush(any(Member.class))).thenThrow(new DataIntegrityViolationException(
                "Unique index or primary key violation: \"PUBLIC.UK_MEMBER_EMAIL_INDEX_4 ON PUBLIC.MEMBER(EMAIL_NORMALIZED NULLS FIRST)\""));

        assertThatThrownBy(() -> service.register(request("ada@example.org")))
                .isInstanceOfSatisfying(ConflictException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
                    assertThat(e.getFieldErrors()).extracting("field").containsExactly("email");
                });
    }

    @Test
    @DisplayName("DR-5 a uk_member_email violation on flush during edit is translated to EMAIL_ALREADY_EXISTS")
    void editTranslatesUniqueViolation() {
        lockedMember(true, 0);
        when(members.saveAndFlush(any(Member.class))).thenThrow(new DataIntegrityViolationException(
                "Unique index or primary key violation: \"PUBLIC.UK_MEMBER_EMAIL_INDEX_4 ON PUBLIC.MEMBER(EMAIL_NORMALIZED NULLS FIRST)\""));

        assertThatThrownBy(() -> service.update(ID, request("ada@example.org")))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("DR-5 a ck_member_inactive_no_loans violation on flush during deactivate is MEMBER_HAS_ACTIVE_LOANS")
    void deactivateTranslatesCheckViolation() {
        lockedMember(true, 0);
        when(members.saveAndFlush(any(Member.class))).thenThrow(new DataIntegrityViolationException(
                "Check constraint violation: \"CK_MEMBER_INACTIVE_NO_LOANS: STATUS = 'ACTIVE' OR ACTIVE_LOAN_COUNT = 0\""));

        assertThatThrownBy(() -> service.deactivate(ID))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MEMBER_HAS_ACTIVE_LOANS));
    }

    @Test
    @DisplayName("AC-3.BE.4 get of a missing member is MEMBER_NOT_FOUND")
    void getMissing() {
        when(members.findById(ID)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.get(ID)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }
}
