package com.library.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Uniqueness lookups use {@code email_normalized} (trimmed, lower-cased with {@code Locale.ROOT}, DR-15). The list/search
 * specification is {@link MemberSpecifications}. Writing services call {@code saveAndFlush} on this repository so that a
 * constraint violation arrives as a translated {@code DataIntegrityViolationException} (DR-5).
 */
public interface MemberRepository extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {

    boolean existsByEmailNormalized(String emailNormalized);

    /** True if a member other than {@code id} already has this email (edit keeps its own email, AC-3.BE.3). */
    boolean existsByEmailNormalizedAndIdNot(String emailNormalized, Long id);
}
