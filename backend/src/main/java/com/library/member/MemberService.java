package com.library.member;

import com.library.common.error.ConflictException;
import com.library.common.error.ConstraintViolationTranslator;
import com.library.common.error.ConstraintViolationTranslator.Operation;
import com.library.common.error.ErrorCode;
import com.library.common.error.FieldErrorDto;
import com.library.common.error.NotFoundException;
import com.library.common.persistence.RowLocks;
import com.library.common.validation.TextNormalizer;
import com.library.common.web.PageRequestParams;
import com.library.common.web.PageResponse;
import com.library.member.dto.MemberRequest;
import com.library.member.dto.MemberResponse;
import com.library.member.dto.MemberSearchCriteria;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Member use cases. One transaction per method (architecture 7.2). Edit, deactivate and reactivate take the member row
 * lock through {@link RowLocks} <em>first</em> and only then read the counter, the status or look up an email (7.3,
 * DR-1): without that, an edit could overwrite the loan counter or status that a concurrent checkout just changed.
 * Registering inserts a new row and needs no lock. Each writing method flushes through the repository and translates a
 * {@link DataIntegrityViolationException} per operation (DR-5). Email values are never logged (DR-14).
 */
@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    static final String EMAIL_EXISTS_MESSAGE = "A member with this email already exists.";

    private final MemberRepository members;
    private final RowLocks rowLocks;
    private final ConstraintViolationTranslator translator;

    public MemberService(MemberRepository members, RowLocks rowLocks, ConstraintViolationTranslator translator) {
        this.members = members;
        this.rowLocks = rowLocks;
        this.translator = translator;
    }

    @Transactional
    public MemberResponse register(MemberRequest request) {
        if (members.existsByEmailNormalized(TextNormalizer.normalizeEmail(request.email()))) {
            throw emailExists();
        }
        Member member = new Member(request.name(), request.email());
        try {
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw translator.translate(Operation.REGISTER_MEMBER, e);
        }
        log.info("Member registered: memberId={}", member.getId());
        return MemberResponse.from(member);
    }

    @Transactional(readOnly = true)
    public MemberResponse get(Long id) {
        return members.findById(id).map(MemberResponse::from).orElseThrow(() -> NotFoundException.member(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> list(MemberSearchCriteria criteria, PageRequestParams paging) {
        return PageResponse.from(
                members.findAll(MemberSpecifications.matching(criteria), paging.toPageable()), MemberResponse::from);
    }

    @Transactional
    public MemberResponse update(Long id, MemberRequest request) {
        Member member = rowLocks.lock(Member.class, id);
        // lookup after the lock and before the entity is modified (nothing dirty to auto-flush)
        if (members.existsByEmailNormalizedAndIdNot(TextNormalizer.normalizeEmail(request.email()), id)) {
            throw emailExists();
        }
        member.edit(request.name(), request.email());
        try {
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw translator.translate(Operation.EDIT_MEMBER, e);
        }
        return MemberResponse.from(member);
    }

    /** Idempotent: an already inactive member is returned unchanged (D9). */
    @Transactional
    public MemberResponse deactivate(Long id) {
        Member member = rowLocks.lock(Member.class, id);
        if (!member.isActive()) {
            return MemberResponse.from(member);
        }
        if (member.getActiveLoanCount() > 0) {
            log.info("Member deactivation rejected: code={} memberId={}", ErrorCode.MEMBER_HAS_ACTIVE_LOANS, id);
            throw new ConflictException(ErrorCode.MEMBER_HAS_ACTIVE_LOANS);
        }
        member.deactivate();
        try {
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw translator.translate(Operation.DEACTIVATE_MEMBER, e);
        }
        log.info("Member deactivated: memberId={}", id);
        return MemberResponse.from(member);
    }

    /** Idempotent: an already active member is returned unchanged (D9). */
    @Transactional
    public MemberResponse reactivate(Long id) {
        Member member = rowLocks.lock(Member.class, id);
        if (member.isActive()) {
            return MemberResponse.from(member);
        }
        member.activate();
        try {
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw translator.translate(Operation.REACTIVATE_MEMBER, e);
        }
        log.info("Member reactivated: memberId={}", id);
        return MemberResponse.from(member);
    }

    private static ConflictException emailExists() {
        return new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS, EMAIL_EXISTS_MESSAGE,
                List.of(new FieldErrorDto("email", EMAIL_EXISTS_MESSAGE)));
    }
}
