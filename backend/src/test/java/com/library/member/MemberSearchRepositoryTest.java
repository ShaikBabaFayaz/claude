package com.library.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.library.member.dto.MemberSearchCriteria;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * The member list specification against the real H2 schema (T-15, AC-3.BE.8, D9): status, search with escaped LIKE
 * wildcards, order and paging. Rolled back after each test; own unique database (DR-9).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberSearchRepositoryTest {

    @Autowired
    private MemberRepository members;

    private Member save(String name, String email, boolean active) {
        Member member = new Member(name, email);
        if (!active) {
            member.deactivate();
        }
        return members.saveAndFlush(member);
    }

    private List<String> names(MemberSearchCriteria criteria) {
        return members.findAll(MemberSpecifications.matching(criteria), PageRequest.of(0, 100)).map(Member::getName).getContent();
    }

    @Test
    @DisplayName("AC-3.BE.8 order is lower(name) then id, regardless of letter case")
    void AC_3_BE_8_order() {
        Member bob = save("bob", "b1@example.org", true);
        Member bigBob = save("Bob", "b2@example.org", true);
        save("alice", "a@example.org", true);
        save("Zed", "z@example.org", true);

        Page<Member> page = members.findAll(MemberSpecifications.matching(MemberSearchCriteria.none()), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Member::getName).containsExactly("alice", "bob", "Bob", "Zed");
        assertThat(page.getContent().get(1).getId()).isEqualTo(bob.getId());
        assertThat(page.getContent().get(2).getId()).isEqualTo(bigBob.getId());
    }

    @Test
    @DisplayName("AC-3.BE.8 status filter")
    void AC_3_BE_8_status() {
        save("Ada", "a@example.org", true);
        save("Grace", "g@example.org", false);

        assertThat(names(new MemberSearchCriteria(MemberStatus.ACTIVE, null))).containsExactly("Ada");
        assertThat(names(new MemberSearchCriteria(MemberStatus.INACTIVE, null))).containsExactly("Grace");
        assertThat(names(MemberSearchCriteria.none())).containsExactly("Ada", "Grace");
    }

    @Test
    @DisplayName("D9 search is a case-insensitive contains on name or email")
    void D9_searchNameOrEmail() {
        save("Ada Lovelace", "countess@analytical.example", true);
        save("Grace Hopper", "GRACE@navy.example", true);

        assertThat(names(new MemberSearchCriteria(null, "LOVE"))).containsExactly("Ada Lovelace");
        assertThat(names(new MemberSearchCriteria(null, "analytical"))).containsExactly("Ada Lovelace");
        assertThat(names(new MemberSearchCriteria(null, "grace"))).containsExactly("Grace Hopper");
        assertThat(names(new MemberSearchCriteria(null, "EXAMPLE"))).containsExactly("Ada Lovelace", "Grace Hopper");
        assertThat(names(new MemberSearchCriteria(MemberStatus.INACTIVE, "example"))).isEmpty();
    }

    @Test
    @DisplayName("D9 LIKE wildcards in the search text are literal")
    void D9_wildcardsEscaped() {
        save("Ada 100% Lovelace", "ada@example.org", true);
        save("Grace Hopper", "grace_h@example.org", true);
        save("Back\\slash", "bs@example.org", true);

        assertThat(names(new MemberSearchCriteria(null, "%"))).containsExactly("Ada 100% Lovelace");
        assertThat(names(new MemberSearchCriteria(null, "_"))).containsExactly("Grace Hopper");
        assertThat(names(new MemberSearchCriteria(null, "\\"))).containsExactly("Back\\slash");
        assertThat(names(new MemberSearchCriteria(null, "%%"))).isEmpty();
    }

    @Test
    @DisplayName("AC-3.BE.8 paging reports the totals; a page beyond the end is empty with real totals")
    void AC_3_BE_8_paging() {
        for (int i = 0; i < 45; i++) {
            save(String.format("Member %02d", i), "m" + i + "@example.org", true);
        }
        MemberSearchCriteria none = MemberSearchCriteria.none();

        Page<Member> last = members.findAll(MemberSpecifications.matching(none), PageRequest.of(2, 20));
        Page<Member> beyond = members.findAll(MemberSpecifications.matching(none), PageRequest.of(9, 20));

        assertThat(last.getContent()).hasSize(5);
        assertThat(last.getTotalElements()).isEqualTo(45);
        assertThat(last.getTotalPages()).isEqualTo(3);
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(45);
    }

    @Test
    @DisplayName("AC-3.BE.3 the uniqueness lookups use the normalised email and exclude the member itself")
    void AC_3_BE_3_existenceLookups() {
        Member ada = save("Ada", "Ada@Example.org", true);

        assertThat(members.existsByEmailNormalized("ada@example.org")).isTrue();
        assertThat(members.existsByEmailNormalized("Ada@Example.org")).as("the key is the lower-case form").isFalse();
        assertThat(members.existsByEmailNormalizedAndIdNot("ada@example.org", ada.getId())).isFalse();
        assertThat(members.existsByEmailNormalizedAndIdNot("ada@example.org", ada.getId() + 1)).isTrue();
    }
}
