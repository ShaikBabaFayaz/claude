package com.library.member;

import com.library.common.validation.TextNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A library member. Field access (lazy-proxy {@code getId()} stays uninitialised, RK-2).
 *
 * <p>{@code activeLoanCount} changes only through {@link #startLoan()} / {@link #endLoan()} (called from
 * {@code LoanService} and the seeder under the member's row lock, architecture 7.3). The counter methods are
 * deliberately unguarded: {@code ck_member_active_loans} and {@code ck_member_inactive_no_loans} are the backstop.
 * {@code emailNormalized} is the case-insensitive uniqueness key, lower-cased with {@code Locale.ROOT} (DR-15).
 */
@Entity
@Table(name = "member")
@DynamicUpdate
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** As entered (trimmed): the display value. */
    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "active_loan_count", nullable = false)
    private int activeLoanCount;

    /** Lost-update backstop (ADR-23); not exposed in the API. */
    @Version
    @Column(nullable = false)
    private long version;

    protected Member() {
        // for JPA
    }

    public Member(String name, String email) {
        this.name = name;
        this.email = email;
        this.emailNormalized = TextNormalizer.normalizeEmail(email);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public int getActiveLoanCount() {
        return activeLoanCount;
    }

    public long getVersion() {
        return version;
    }

    /** Replaces name and email (and the uniqueness key); never touches status or the loan counter. */
    public void edit(String name, String email) {
        this.name = name;
        this.email = email;
        this.emailNormalized = TextNormalizer.normalizeEmail(email);
    }

    /** Status change only; the "no active loans" guard lives in {@code MemberService} (AC-3.BE.6). */
    public void deactivate() {
        this.status = MemberStatus.INACTIVE;
    }

    public void activate() {
        this.status = MemberStatus.ACTIVE;
    }

    /** One more active loan. Counter mutator, see class comment. */
    public void startLoan() {
        activeLoanCount++;
    }

    /** One active loan fewer. Counter mutator, see class comment. */
    public void endLoan() {
        activeLoanCount--;
    }

    @PrePersist
    @PreUpdate
    void syncNormalizedEmail() {
        this.emailNormalized = TextNormalizer.normalizeEmail(email);
    }
}
