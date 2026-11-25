package com.library.common.error;

import static com.library.common.error.ConstraintViolationTranslator.Operation.CHECKOUT;
import static com.library.common.error.ConstraintViolationTranslator.Operation.CREATE_BOOK;
import static com.library.common.error.ConstraintViolationTranslator.Operation.DEACTIVATE_MEMBER;
import static com.library.common.error.ConstraintViolationTranslator.Operation.DELETE_BOOK;
import static com.library.common.error.ConstraintViolationTranslator.Operation.EDIT_MEMBER;
import static com.library.common.error.ConstraintViolationTranslator.Operation.REACTIVATE_MEMBER;
import static com.library.common.error.ConstraintViolationTranslator.Operation.REGISTER_MEMBER;
import static com.library.common.error.ConstraintViolationTranslator.Operation.RETURN;
import static com.library.common.error.ConstraintViolationTranslator.Operation.UPDATE_BOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.library.book.Book;
import com.library.book.BookRepository;
import com.library.common.error.ConstraintViolationTranslator.Operation;
import com.library.loan.Loan;
import com.library.loan.LoanRepository;
import com.library.member.Member;
import com.library.member.MemberRepository;
import com.library.support.LogCapture;
import com.library.support.TestData;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Architecture 6.2.1, one row per (constraint, operation) pair, each provoked against the real H2 schema. Rows run in
 * two flavours: through Spring Data repositories (the exception then carries Hibernate's ConstraintViolationException)
 * and through raw JDBC (only the H2 message is available: the case-insensitive fallback).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ConstraintViolationTranslator.class)
class ConstraintViolationTranslatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Autowired
    private ConstraintViolationTranslator translator;
    @Autowired
    private BookRepository books;
    @Autowired
    private MemberRepository members;
    @Autowired
    private LoanRepository loans;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;

    /** What a row expects from the translator. */
    record Expected(int status, ErrorCode code, List<String> fields) {
    }

    record Row(String name, Operation operation, Consumer<ConstraintViolationTranslatorTest> provoke, Expected expected,
            boolean viaHibernate) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Expected conflict(ErrorCode code, String... fields) {
        return new Expected(409, code, List.of(fields));
    }

    private static Expected bad(ErrorCode code, String... fields) {
        return new Expected(400, code, List.of(fields));
    }

    private static final Expected INTERNAL = new Expected(500, ErrorCode.INTERNAL_ERROR, List.of());

    // ---- fixtures -----------------------------------------------------------------------------------------------

    private Book savedBook(String isbn, int total) {
        return books.saveAndFlush(TestData.book(isbn, total));
    }

    private Member savedMember(String email) {
        return members.saveAndFlush(TestData.member("Name", email));
    }

    private long jdbcBook(String isbn, int total, int active) {
        jdbc.update("INSERT INTO book (title, author, isbn, total_copies, active_loan_count) VALUES ('T','A',?,?,?)",
                isbn, total, active);
        return jdbc.queryForObject("SELECT id FROM book WHERE isbn = ?", Long.class, isbn);
    }

    private long jdbcMember(String email, String status, int active) {
        jdbc.update("INSERT INTO member (name, email, email_normalized, status, active_loan_count) "
                + "VALUES ('M',?,?,?,?)", email, email.toLowerCase(), status, active);
        return jdbc.queryForObject("SELECT id FROM member WHERE email_normalized = ?", Long.class, email.toLowerCase());
    }

    private void jdbcLoan(long memberId, long bookId, String checkout, String due, String returned, Long activeBookId) {
        jdbc.update("INSERT INTO loan (member_id, book_id, checkout_date, due_date, return_date, active_book_id) "
                + "VALUES (?,?,CAST(? AS DATE),CAST(? AS DATE),CAST(? AS DATE),?)",
                memberId, bookId, checkout, due, returned, activeBookId);
    }

    // ---- the table ----------------------------------------------------------------------------------------------

    static Stream<Row> hibernateRows() {
        return Stream.of(
                new Row("uk_book_isbn / CREATE_BOOK", CREATE_BOOK, t -> {
                    t.savedBook("9780132350884", 2);
                    t.savedBook("9780132350884", 1);
                }, conflict(ErrorCode.ISBN_ALREADY_EXISTS, "isbn"), true),
                new Row("uk_book_isbn / UPDATE_BOOK", UPDATE_BOOK, t -> {
                    Book first = t.savedBook("9780132350884", 2);
                    Book second = t.savedBook("9780306406157", 2);
                    second.update("T", "A", first.getIsbn(), 2);
                    t.books.flush();
                }, conflict(ErrorCode.ISBN_ALREADY_EXISTS, "isbn"), true),
                new Row("ck_book_total_copies / CREATE_BOOK (0)", CREATE_BOOK,
                        t -> t.savedBook("9780132350884", 0), bad(ErrorCode.VALIDATION_FAILED, "totalCopies"), true),
                new Row("ck_book_total_copies / CREATE_BOOK (1000)", CREATE_BOOK,
                        t -> t.savedBook("9780132350884", 1000), bad(ErrorCode.VALIDATION_FAILED, "totalCopies"), true),
                new Row("ck_book_total_copies / UPDATE_BOOK", UPDATE_BOOK, t -> {
                    Book book = t.savedBook("9780132350884", 2);
                    book.update("T", "A", book.getIsbn(), 1000);
                    t.books.flush();
                }, bad(ErrorCode.VALIDATION_FAILED, "totalCopies"), true),
                new Row("ck_book_active_loans / UPDATE_BOOK", UPDATE_BOOK, t -> {
                    Book book = t.savedBook("9780132350884", 3);
                    book.lendCopy();
                    book.lendCopy();
                    t.books.flush();
                    book.update("T", "A", book.getIsbn(), 1);
                    t.books.flush();
                }, bad(ErrorCode.TOTAL_COPIES_BELOW_ACTIVE_LOANS, "totalCopies"), true),
                new Row("ck_book_active_loans / CHECKOUT", CHECKOUT, t -> {
                    Book book = t.savedBook("9780132350884", 1);
                    book.lendCopy();
                    book.lendCopy();
                    t.books.flush();
                }, conflict(ErrorCode.NO_COPY_AVAILABLE), true),
                new Row("fk_loan_book / DELETE_BOOK", DELETE_BOOK, t -> {
                    Book book = t.savedBook("9780132350884", 2);
                    Member member = t.savedMember("a@example.org");
                    Loan loan = new Loan(member, book, TODAY.minusDays(20), TODAY.minusDays(6));
                    loan.markReturned(TODAY.minusDays(10));
                    t.loans.saveAndFlush(loan);
                    t.em.clear(); // as in a stale delete: no managed Loan references the removed Book any more
                    t.books.delete(t.books.findById(book.getId()).orElseThrow());
                    t.books.flush();
                }, conflict(ErrorCode.BOOK_HAS_ACTIVE_LOANS), true),
                new Row("uk_member_email / REGISTER_MEMBER (case-insensitive)", REGISTER_MEMBER, t -> {
                    t.savedMember("Ada@Example.org");
                    t.savedMember("ADA@example.ORG");
                }, conflict(ErrorCode.EMAIL_ALREADY_EXISTS, "email"), true),
                new Row("uk_member_email / EDIT_MEMBER", EDIT_MEMBER, t -> {
                    t.savedMember("ada@example.org");
                    Member other = t.savedMember("bob@example.org");
                    other.edit("Bob", "ADA@example.org");
                    t.members.flush();
                }, conflict(ErrorCode.EMAIL_ALREADY_EXISTS, "email"), true),
                new Row("ck_member_inactive_no_loans / DEACTIVATE_MEMBER", DEACTIVATE_MEMBER, t -> {
                    Member member = t.savedMember("ada@example.org");
                    member.startLoan();
                    t.members.flush();
                    member.deactivate();
                    t.members.flush();
                }, conflict(ErrorCode.MEMBER_HAS_ACTIVE_LOANS), true),
                new Row("ck_member_inactive_no_loans / CHECKOUT", CHECKOUT, t -> {
                    Member member = t.savedMember("ada@example.org");
                    member.deactivate();
                    t.members.flush();
                    member.startLoan();
                    t.members.flush();
                }, conflict(ErrorCode.MEMBER_INACTIVE), true),
                new Row("ck_member_active_loans / CHECKOUT", CHECKOUT, t -> {
                    Member member = t.savedMember("ada@example.org");
                    for (int i = 0; i < 4; i++) {
                        member.startLoan();
                    }
                    t.members.flush();
                }, conflict(ErrorCode.LOAN_LIMIT_REACHED), true),
                new Row("uk_loan_member_active_book / CHECKOUT", CHECKOUT, t -> {
                    Book book = t.savedBook("9780132350884", 3);
                    Member member = t.savedMember("ada@example.org");
                    t.loans.saveAndFlush(new Loan(member, book, TODAY, TODAY.plusDays(14)));
                    t.loans.saveAndFlush(new Loan(member, book, TODAY, TODAY.plusDays(14)));
                }, conflict(ErrorCode.DUPLICATE_ACTIVE_LOAN), true),

                // pairs that are NOT in the table -> broken invariant -> 500
                new Row("ck_book_active_loans / RETURN (counter below 0)", RETURN, t -> {
                    Book book = t.savedBook("9780132350884", 2);
                    book.returnCopy();
                    t.books.flush();
                }, INTERNAL, true),
                new Row("ck_member_active_loans / RETURN (counter below 0)", RETURN, t -> {
                    Member member = t.savedMember("ada@example.org");
                    member.endLoan();
                    t.members.flush();
                }, INTERNAL, true),
                new Row("ck_loan_due / CHECKOUT", CHECKOUT, t -> {
                    Book book = t.savedBook("9780132350884", 3);
                    Member member = t.savedMember("ada@example.org");
                    t.loans.saveAndFlush(new Loan(member, book, TODAY, TODAY.minusDays(1)));
                }, INTERNAL, true),
                new Row("uk_book_isbn / CHECKOUT (constraint does not belong to the operation)", CHECKOUT, t -> {
                    t.savedBook("9780132350884", 2);
                    t.savedBook("9780132350884", 1);
                }, INTERNAL, true),
                new Row("uk_member_email / REACTIVATE_MEMBER (constraint does not belong to the operation)",
                        REACTIVATE_MEMBER, t -> {
                            t.savedMember("ada@example.org");
                            t.savedMember("ada@example.org");
                        }, INTERNAL, true),
                new Row("ck_book_active_loans / DELETE_BOOK (constraint does not belong to the operation)", DELETE_BOOK,
                        t -> {
                            Book book = t.savedBook("9780132350884", 1);
                            book.lendCopy();
                            book.lendCopy();
                            t.books.flush();
                        }, INTERNAL, true));
    }

    static Stream<Row> jdbcRows() {
        return Stream.of(
                new Row("uk_book_isbn / CREATE_BOOK", CREATE_BOOK, t -> {
                    t.jdbcBook("9780132350884", 2, 0);
                    t.jdbcBook("9780132350884", 1, 0);
                }, conflict(ErrorCode.ISBN_ALREADY_EXISTS, "isbn"), false),
                new Row("ck_book_total_copies / UPDATE_BOOK", UPDATE_BOOK,
                        t -> t.jdbcBook("9780132350884", 1000, 0), bad(ErrorCode.VALIDATION_FAILED, "totalCopies"), false),
                new Row("ck_book_active_loans / UPDATE_BOOK", UPDATE_BOOK, t -> {
                    long id = t.jdbcBook("9780132350884", 3, 2);
                    t.jdbc.update("UPDATE book SET total_copies = 1 WHERE id = ?", id);
                }, bad(ErrorCode.TOTAL_COPIES_BELOW_ACTIVE_LOANS, "totalCopies"), false),
                new Row("ck_book_active_loans / CHECKOUT", CHECKOUT, t -> t.jdbcBook("9780132350884", 1, 2),
                        conflict(ErrorCode.NO_COPY_AVAILABLE), false),
                new Row("fk_loan_book / DELETE_BOOK", DELETE_BOOK, t -> {
                    long b = t.jdbcBook("9780132350884", 2, 0);
                    long m = t.jdbcMember("a@example.org", "ACTIVE", 0);
                    t.jdbcLoan(m, b, "2026-10-01", "2026-10-15", "2026-10-10", null);
                    t.jdbc.update("DELETE FROM book WHERE id = ?", b);
                }, conflict(ErrorCode.BOOK_HAS_ACTIVE_LOANS), false),
                new Row("uk_member_email / REGISTER_MEMBER", REGISTER_MEMBER, t -> {
                    t.jdbcMember("a@example.org", "ACTIVE", 0);
                    t.jdbcMember("a@example.org", "ACTIVE", 0);
                }, conflict(ErrorCode.EMAIL_ALREADY_EXISTS, "email"), false),
                new Row("ck_member_inactive_no_loans / DEACTIVATE_MEMBER", DEACTIVATE_MEMBER, t -> {
                    long id = t.jdbcMember("a@example.org", "ACTIVE", 1);
                    t.jdbc.update("UPDATE member SET status = 'INACTIVE' WHERE id = ?", id);
                }, conflict(ErrorCode.MEMBER_HAS_ACTIVE_LOANS), false),
                new Row("ck_member_inactive_no_loans / CHECKOUT", CHECKOUT,
                        t -> t.jdbcMember("a@example.org", "INACTIVE", 1), conflict(ErrorCode.MEMBER_INACTIVE), false),
                new Row("ck_member_active_loans / CHECKOUT", CHECKOUT, t -> t.jdbcMember("a@example.org", "ACTIVE", 4),
                        conflict(ErrorCode.LOAN_LIMIT_REACHED), false),
                new Row("uk_loan_member_active_book / CHECKOUT", CHECKOUT, t -> {
                    long b = t.jdbcBook("9780132350884", 3, 0);
                    long m = t.jdbcMember("a@example.org", "ACTIVE", 0);
                    t.jdbcLoan(m, b, "2026-11-01", "2026-11-15", null, b);
                    t.jdbcLoan(m, b, "2026-11-02", "2026-11-16", null, b);
                }, conflict(ErrorCode.DUPLICATE_ACTIVE_LOAN), false),
                new Row("ck_loan_active_key / CHECKOUT (500)", CHECKOUT, t -> {
                    long b = t.jdbcBook("9780132350884", 3, 0);
                    long m = t.jdbcMember("a@example.org", "ACTIVE", 0);
                    t.jdbcLoan(m, b, "2026-11-01", "2026-11-15", null, null);
                }, INTERNAL, false),
                new Row("ck_loan_return / RETURN (500)", RETURN, t -> {
                    long b = t.jdbcBook("9780132350884", 3, 0);
                    long m = t.jdbcMember("a@example.org", "ACTIVE", 0);
                    t.jdbcLoan(m, b, "2026-11-10", "2026-11-24", "2026-11-09", null);
                }, INTERNAL, false),
                new Row("ck_member_status / REACTIVATE_MEMBER (500)", REACTIVATE_MEMBER,
                        t -> t.jdbcMember("a@example.org", "DELETED", 0), INTERNAL, false));
    }

    // ---- tests --------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "6.2.1 via repository: {0}")
    @MethodSource("hibernateRows")
    void table_viaRepository(Row row) {
        assertRow(row);
    }

    @ParameterizedTest(name = "6.2.1 via JDBC message fallback: {0}")
    @MethodSource("jdbcRows")
    void table_viaJdbcMessageFallback(Row row) {
        assertRow(row);
    }

    private void assertRow(Row row) {
        DataIntegrityViolationException violation = catchViolation(row);

        boolean hasHibernateException = hasCause(violation, org.hibernate.exception.ConstraintViolationException.class);
        assertThat(hasHibernateException).as("exception flavour of '%s'", row.name()).isEqualTo(row.viaHibernate());

        ApiException translated = translator.translate(row.operation(), violation);

        assertThat(translated.getStatus()).as("HTTP status").isEqualTo(row.expected().status());
        assertThat(translated.getCode()).as("error code").isEqualTo(row.expected().code());
        assertThat(translated.getFieldErrors()).extracting(FieldErrorDto::field)
                .as("field errors").containsExactlyElementsOf(row.expected().fields());
        assertThat(translated.getFieldErrors()).extracting(FieldErrorDto::message).allMatch(m -> !m.isBlank());
        if (row.expected().status() != 500) {
            assertThat(translated.getMessage()).doesNotContainIgnoringCase("constraint").doesNotContain("uk_")
                    .doesNotContain("ck_").doesNotContain("fk_");
        }
        if (row.expected().status() == 500) {
            assertThat(translated).isInstanceOf(InternalErrorException.class);
            assertThat(translated.getCause()).as("row values must not travel with the exception").isNull();
        }
    }

    private DataIntegrityViolationException catchViolation(Row row) {
        Throwable thrown = null;
        try {
            row.provoke().accept(this);
        } catch (Throwable t) {
            thrown = t;
        }
        assertThat(thrown).as("'%s' must violate a constraint", row.name())
                .isInstanceOf(DataIntegrityViolationException.class);
        return (DataIntegrityViolationException) thrown;
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("constraint names are matched case-insensitively (H2 reports upper case)")
    void caseInsensitiveMatching() {
        for (String text : List.of(
                "Check constraint violation: \"CK_MEMBER_ACTIVE_LOANS: active_loan_count BETWEEN 0 AND 3\"",
                "check constraint violation: \"ck_member_active_loans\"",
                "Check constraint violation: \"Ck_Member_Active_Loans\"")) {
            ApiException translated = translator.translate(CHECKOUT, new DataIntegrityViolationException(text));

            assertThat(translated.getCode()).as(text).isEqualTo(ErrorCode.LOAN_LIMIT_REACHED);
        }
    }

    @Test
    @DisplayName("an unknown constraint or no constraint name at all is a 500, logged at ERROR with the name only")
    void unknownConstraintIsInternalError() {
        try (LogCapture logs = LogCapture.of(ConstraintViolationTranslator.class)) {
            ApiException known = translator.translate(CHECKOUT, new DataIntegrityViolationException(
                    "Check constraint violation: \"CK_LOAN_DUE: due_date >= checkout_date\"; VALUES ('secret@example.org')"));
            ApiException unknown = translator.translate(CHECKOUT, new DataIntegrityViolationException(
                    "NULL not allowed for column \"TITLE\"; VALUES ('secret@example.org')"));

            assertThat(known).isInstanceOf(InternalErrorException.class);
            assertThat(unknown).isInstanceOf(InternalErrorException.class);
            assertThat(logs.events(Level.ERROR)).hasSize(2);
            assertThat(logs.messages().get(0)).contains("operation=CHECKOUT").contains("constraint=ck_loan_due");
            assertThat(logs.messages().get(1)).contains("constraint=unknown");
            assertThat(logs.messages()).noneMatch(m -> m.contains("secret"));
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
        }
    }

    @Test
    @DisplayName("the translator knows exactly the constraints that schema.sql defines (no drift)")
    void knownConstraintsMatchSchemaSql() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/schema.sql"), StandardCharsets.UTF_8)
                .lines().filter(line -> !line.strip().startsWith("--"))
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
        TreeSet<String> inSchema = new TreeSet<>();
        Matcher matcher = Pattern.compile("CONSTRAINT\\s+(\\w+)", Pattern.CASE_INSENSITIVE).matcher(schema);
        while (matcher.find()) {
            inSchema.add(matcher.group(1).toLowerCase());
        }

        assertThat(new TreeSet<>(ConstraintViolationTranslator.KNOWN_CONSTRAINTS)).isEqualTo(inSchema);
    }

    @Test
    @DisplayName("a violation without any message or cause translates to a 500 instead of failing")
    void emptyViolation() {
        assertThat(translator.translate(UPDATE_BOOK, new DataIntegrityViolationException(null)))
                .isInstanceOf(InternalErrorException.class);
        assertThatThrownBy(() -> {
            throw translator.translate(UPDATE_BOOK, new DataIntegrityViolationException("x"));
        }).isInstanceOf(InternalErrorException.class);
    }
}
