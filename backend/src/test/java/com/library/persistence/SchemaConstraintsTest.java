package com.library.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every named constraint of schema.sql (architecture 5.2) fires. Raw SQL is used on purpose so that the schema is
 * tested independently of the entity mapping. Constraint names are compared case-insensitively, because H2 reports
 * unquoted names in upper case (architecture 6.2.1).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaConstraintsTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Environment environment;

    // ---- helpers -----------------------------------------------------------------------------------------------

    private long book(String isbn, int total, int active) {
        jdbc.update("INSERT INTO book (title, author, isbn, total_copies, active_loan_count) VALUES (?,?,?,?,?)",
                "T " + isbn, "A", isbn, total, active);
        return jdbc.queryForObject("SELECT id FROM book WHERE isbn = ?", Long.class, isbn);
    }

    private long member(String email, String status, int active) {
        jdbc.update("INSERT INTO member (name, email, email_normalized, status, active_loan_count) VALUES (?,?,?,?,?)",
                "M", email, email.toLowerCase(), status, active);
        return jdbc.queryForObject("SELECT id FROM member WHERE email_normalized = ?", Long.class, email.toLowerCase());
    }

    private void loan(long memberId, long bookId, String checkout, String due, String returned, Long activeBookId) {
        jdbc.update("INSERT INTO loan (member_id, book_id, checkout_date, due_date, return_date, active_book_id) "
                        + "VALUES (?,?,CAST(? AS DATE),CAST(? AS DATE),CAST(? AS DATE),?)",
                memberId, bookId, checkout, due, returned, activeBookId);
    }

    private static void assertViolation(Runnable statement, String constraintName) {
        assertThatThrownBy(statement::run)
                .as("constraint %s", constraintName)
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(fullMessage(e)).containsIgnoringCase(constraintName));
    }

    private static String fullMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }

    // ---- configuration -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("T-07 context starts with ddl-auto=validate against schema.sql")
    void contextValidatesAgainstSchema() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book", Integer.class)).isZero();
    }

    @Test
    @DisplayName("version columns default to 0 on book and member (ADR-23)")
    void versionDefaultsToZero() {
        long b = book("9780132350884", 3, 0);
        long m = member("a@example.org", "ACTIVE", 0);

        assertThat(jdbc.queryForObject("SELECT version FROM book WHERE id = ?", Long.class, b)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM member WHERE id = ?", Long.class, m)).isZero();
    }

    // ---- book --------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-1.BE.4 uk_book_isbn: duplicate ISBN")
    void AC_1_BE_4_duplicateIsbn() {
        book("9780132350884", 3, 0);

        assertViolation(() -> book("9780132350884", 1, 0), "uk_book_isbn");
    }

    @Test
    @DisplayName("Q-A1 an ISBN-10 and its ISBN-13 equivalent are both storable")
    void Q_A1_isbn10AndIsbn13BothStorable() {
        assertThatCode(() -> {
            book("0306406152", 1, 0);
            book("9780306406157", 1, 0);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ck_book_total_copies: total copies 0 and 1000 are rejected, 1 and 999 accepted")
    void totalCopiesRange() {
        assertViolation(() -> book("9780132350884", 0, 0), "ck_book_total_copies");
        assertViolation(() -> book("9780132350884", 1000, 0), "ck_book_total_copies");
        assertThatCode(() -> {
            book("9780132350884", 1, 0);
            book("9780306406157", 999, 0);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-4.BE.11 ck_book_active_loans: active_loan_count above total copies or negative")
    void AC_4_BE_11_bookCounterRange() {
        assertViolation(() -> book("9780132350884", 2, 3), "ck_book_active_loans");
        assertViolation(() -> book("9780132350884", 2, -1), "ck_book_active_loans");
        assertThatCode(() -> book("9780306406157", 2, 2)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-1.BE.6 ck_book_active_loans: total copies cannot drop below active loans on update")
    void AC_1_BE_6_totalCopiesBelowActiveLoansOnUpdate() {
        long b = book("9780132350884", 3, 2);

        assertViolation(() -> jdbc.update("UPDATE book SET total_copies = 1 WHERE id = ?", b), "ck_book_active_loans");
    }

    // ---- member ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-3.BE.3 uk_member_email: duplicate normalised email")
    void AC_3_BE_3_duplicateEmail() {
        member("ada@example.org", "ACTIVE", 0);

        assertViolation(() -> member("ada@example.org", "ACTIVE", 0), "uk_member_email");
    }

    @Test
    @DisplayName("ck_member_status: only ACTIVE and INACTIVE")
    void memberStatusValues() {
        assertViolation(() -> member("a@example.org", "DELETED", 0), "ck_member_status");
    }

    @Test
    @DisplayName("AC-4.BE.11 ck_member_active_loans: count 4 or negative")
    void AC_4_BE_11_memberCounterRange() {
        assertViolation(() -> member("a@example.org", "ACTIVE", 4), "ck_member_active_loans");
        assertViolation(() -> member("b@example.org", "ACTIVE", -1), "ck_member_active_loans");
        assertThatCode(() -> member("c@example.org", "ACTIVE", 3)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-3.BE.6 ck_member_inactive_no_loans: inactive member with an active loan")
    void AC_3_BE_6_inactiveMemberCannotHaveLoans() {
        assertViolation(() -> member("a@example.org", "INACTIVE", 1), "ck_member_inactive_no_loans");
        long m = member("b@example.org", "ACTIVE", 1);
        assertViolation(() -> jdbc.update("UPDATE member SET status = 'INACTIVE' WHERE id = ?", m),
                "ck_member_inactive_no_loans");
        assertThatCode(() -> member("c@example.org", "INACTIVE", 0)).doesNotThrowAnyException();
    }

    // ---- loan --------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-4.BE.5 uk_loan_member_active_book: second active loan of the same book by the same member")
    void AC_4_BE_5_duplicateActiveLoan() {
        long b = book("9780132350884", 3, 0);
        long m = member("a@example.org", "ACTIVE", 0);
        loan(m, b, "2026-11-01", "2026-11-15", null, b);

        assertViolation(() -> loan(m, b, "2026-11-02", "2026-11-16", null, b), "uk_loan_member_active_book");
    }

    @Test
    @DisplayName("AC-4.BE.5 several returned loans (NULL active_book_id) of the same pair are allowed")
    void AC_4_BE_5_secondNullActiveKeyAllowed() {
        long b = book("9780132350884", 3, 0);
        long m = member("a@example.org", "ACTIVE", 0);

        assertThatCode(() -> {
            loan(m, b, "2026-10-01", "2026-10-15", "2026-10-10", null);
            loan(m, b, "2026-10-20", "2026-11-03", "2026-10-25", null);
            loan(m, b, "2026-11-10", "2026-11-24", null, b);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ck_loan_active_key: active_book_id must be book_id while active and NULL once returned")
    void activeKeyConsistency() {
        long b = book("9780132350884", 3, 0);
        long other = book("9780306406157", 3, 0);
        long m = member("a@example.org", "ACTIVE", 0);

        assertViolation(() -> loan(m, b, "2026-11-01", "2026-11-15", null, null), "ck_loan_active_key");
        assertViolation(() -> loan(m, b, "2026-11-01", "2026-11-15", null, other), "ck_loan_active_key");
        assertViolation(() -> loan(m, b, "2026-11-01", "2026-11-15", "2026-11-05", b), "ck_loan_active_key");
    }

    @Test
    @DisplayName("ck_loan_due and ck_loan_return: dates must not precede the checkout date")
    void loanDateOrdering() {
        long b = book("9780132350884", 3, 0);
        long m = member("a@example.org", "ACTIVE", 0);

        assertViolation(() -> loan(m, b, "2026-11-10", "2026-11-09", null, b), "ck_loan_due");
        assertViolation(() -> loan(m, b, "2026-11-10", "2026-11-24", "2026-11-09", null), "ck_loan_return");
    }

    @Test
    @DisplayName("fk_loan_book: a book that has loans cannot be deleted without removing them first (AC-1.BE.7)")
    void AC_1_BE_7_bookWithLoansCannotBeDeleted() {
        long b = book("9780132350884", 3, 0);
        long m = member("a@example.org", "ACTIVE", 0);
        loan(m, b, "2026-10-01", "2026-10-15", "2026-10-10", null);

        assertViolation(() -> jdbc.update("DELETE FROM book WHERE id = ?", b), "fk_loan_book");

        jdbc.update("DELETE FROM loan WHERE book_id = ? AND return_date IS NOT NULL", b);
        assertThatCode(() -> jdbc.update("DELETE FROM book WHERE id = ?", b)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("fk_loan_member and fk_loan_book: loans must reference existing rows")
    void foreignKeysEnforced() {
        long b = book("9780132350884", 3, 0);
        long m = member("a@example.org", "ACTIVE", 0);

        assertViolation(() -> loan(m + 999, b, "2026-11-01", "2026-11-15", null, b), "fk_loan_member");
        assertViolation(() -> loan(m, b + 999, "2026-11-01", "2026-11-15", null, b + 999), "fk_loan_book");
    }
}
