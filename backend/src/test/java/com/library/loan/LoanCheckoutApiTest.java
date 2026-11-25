package com.library.loan;

import static com.library.support.ApiRequests.get;
import static com.library.support.ApiRequests.post;
import static com.library.support.ApiRequests.postJson;
import static com.library.support.ErrorBodyAssert.assertError;
import static com.library.support.ErrorBodyAssert.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.book.Book;
import com.library.book.BookRepository;
import com.library.member.Member;
import com.library.member.MemberRepository;
import com.library.support.LoanFixtures;
import com.library.support.TestClockConfig;
import com.library.support.TestData;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * US-4 / AG-35 (T-17): checkout, return and loan-by-id through the full stack (MockMvc, real H2, fixed clock
 * 2026-11-25). Not transactional: every request commits, as in production; the tables are emptied before each test.
 * Every error response is checked against the AC-X.1 body shape. Includes the one concurrency test of the fast-track
 * scope (AC-4.BE.11).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class LoanCheckoutApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate TODAY = TestClockConfig.FIXED_DATE;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private BookRepository books;
    @Autowired
    private MemberRepository members;
    @Autowired
    private LoanRepository loans;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private LoanFixtures fixtures;

    @BeforeEach
    void cleanDatabase() {
        fixtures = new LoanFixtures(books, members, loans, jdbc, transactionManager);
        fixtures.deleteAll();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private MvcResult checkout(long memberId, long bookId) throws Exception {
        return mvc.perform(postJson("/api/loans", TestData.loanJson(memberId, bookId))).andReturn();
    }

    private MvcResult returnLoan(long loanId) throws Exception {
        return mvc.perform(post("/api/loans/" + loanId + "/return")).andReturn();
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private int bookCount(long bookId) {
        return jdbc.queryForObject("SELECT active_loan_count FROM book WHERE id = ?", Integer.class, bookId);
    }

    private int memberCount(long memberId) {
        return jdbc.queryForObject("SELECT active_loan_count FROM member WHERE id = ?", Integer.class, memberId);
    }

    private long loanRows() {
        return fixtures.countLoans();
    }

    private void deactivate(long memberId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            Member member = members.findById(memberId).orElseThrow();
            member.deactivate();
        });
    }

    private long newBook(int n, int copies) {
        return fixtures.book(TestData.isbn13(n), copies).getId();
    }

    private long newMember(String name) {
        return fixtures.member(name).getId();
    }

    // ---- AC-4.BE.1 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-4.BE.1 checkout creates a loan due in 14 days, counts change, Location resolves to GET /api/loans/{id}")
    void AC_4_BE_1_checkoutCreatesLoan() throws Exception {
        long memberId = newMember("Ada Lovelace");
        long bookId = newBook(1, 2);

        MvcResult result = checkout(memberId, bookId);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode loan = body(result);
        assertThat(loan.get("id").asLong()).isPositive();
        assertThat(loan.get("member").get("id").asLong()).isEqualTo(memberId);
        assertThat(loan.get("member").get("name").asText()).isEqualTo("Ada Lovelace");
        assertThat(loan.get("book").get("id").asLong()).isEqualTo(bookId);
        assertThat(loan.get("book").get("isbn").asText()).isEqualTo(TestData.isbn13(1));
        assertThat(loan.get("checkoutDate").asText()).isEqualTo(TODAY.toString());
        assertThat(loan.get("dueDate").asText()).isEqualTo(TODAY.plusDays(14).toString());
        assertThat(loan.get("returnDate").isNull()).isTrue();
        assertThat(loan.get("overdue").asBoolean()).isFalse();
        assertThat(loan.get("daysOverdue").asLong()).isZero();
        assertThat(bookCount(bookId)).isEqualTo(1);
        assertThat(memberCount(memberId)).isEqualTo(1);

        String location = result.getResponse().getHeader("Location");
        assertThat(location).isEqualTo("/api/loans/" + loan.get("id").asLong());
        MvcResult fetched = mvc.perform(get(location)).andReturn();
        assertThat(fetched.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(fetched)).isEqualTo(loan);
    }

    // ---- AC-4.BE.2 .. 6 ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-4.BE.2 a 4th active loan is rejected with LOAN_LIMIT_REACHED and nothing changes")
    void AC_4_BE_2_loanLimit() throws Exception {
        long memberId = newMember("Ada Lovelace");
        for (int i = 1; i <= 3; i++) {
            fixtures.activeLoan(memberId, newBook(i, 1), TODAY.minusDays(1), TODAY.plusDays(13));
        }
        long fourth = newBook(4, 2);

        assertError(checkout(memberId, fourth), 409, "LOAN_LIMIT_REACHED");

        assertThat(memberCount(memberId)).isEqualTo(3);
        assertThat(bookCount(fourth)).isZero();
        assertThat(loanRows()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-4.BE.3 a book without a free copy is rejected with NO_COPY_AVAILABLE and nothing changes")
    void AC_4_BE_3_noCopyAvailable() throws Exception {
        long holder = newMember("Grace Hopper");
        long other = newMember("Ada Lovelace");
        long bookId = newBook(1, 1);
        fixtures.activeLoan(holder, bookId, TODAY.minusDays(1), TODAY.plusDays(13));

        assertError(checkout(other, bookId), 409, "NO_COPY_AVAILABLE");

        assertThat(bookCount(bookId)).isEqualTo(1);
        assertThat(memberCount(other)).isZero();
        assertThat(loanRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-4.BE.4 an inactive member is rejected with MEMBER_INACTIVE")
    void AC_4_BE_4_memberInactive() throws Exception {
        long memberId = newMember("Ada Lovelace");
        long bookId = newBook(1, 1);
        deactivate(memberId);

        assertError(checkout(memberId, bookId), 409, "MEMBER_INACTIVE");

        assertThat(bookCount(bookId)).isZero();
        assertThat(loanRows()).isZero();
    }

    @Test
    @DisplayName("AC-4.BE.5 a second active loan of the same book for the same member is rejected with DUPLICATE_ACTIVE_LOAN")
    void AC_4_BE_5_duplicateActiveLoan() throws Exception {
        long memberId = newMember("Ada Lovelace");
        long bookId = newBook(1, 3);
        assertThat(checkout(memberId, bookId).getResponse().getStatus()).isEqualTo(201);

        assertError(checkout(memberId, bookId), 409, "DUPLICATE_ACTIVE_LOAN");

        assertThat(bookCount(bookId)).isEqualTo(1);
        assertThat(memberCount(memberId)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-4.BE.6 unknown member or book is 404 with the matching code; invalid body is 400 with field errors")
    void AC_4_BE_6_unknownIdsAndValidation() throws Exception {
        long memberId = newMember("Ada Lovelace");
        long bookId = newBook(1, 1);

        assertError(checkout(memberId + 1000, bookId), 404, "MEMBER_NOT_FOUND");
        assertError(checkout(memberId, bookId + 1000), 404, "BOOK_NOT_FOUND");
        // rule order: member 404 comes before book 404
        assertError(checkout(memberId + 1000, bookId + 1000), 404, "MEMBER_NOT_FOUND");

        MvcResult missing = mvc.perform(postJson("/api/loans", "{}")).andReturn();
        assertThat(fieldNames(assertError(missing, 400, "VALIDATION_FAILED"))).containsExactly("bookId", "memberId");
        MvcResult zero = mvc.perform(postJson("/api/loans", "{\"memberId\":0,\"bookId\":1.5}")).andReturn();
        assertThat(fieldNames(assertError(zero, 400, "VALIDATION_FAILED"))).contains("bookId");

        assertThat(loanRows()).isZero();
    }

    @Test
    @DisplayName("AC-4.BE.4/AC-4.BE.3 several failing rules report the first one in the fixed order")
    void AC_4_BE_4_ruleOrder() throws Exception {
        long holder = newMember("Grace Hopper");
        long memberId = newMember("Ada Lovelace");
        long bookId = newBook(1, 1);
        fixtures.activeLoan(holder, bookId, TODAY.minusDays(1), TODAY.plusDays(13));
        deactivate(memberId);

        // inactive member AND no copy left: MEMBER_INACTIVE wins
        assertError(checkout(memberId, bookId), 409, "MEMBER_INACTIVE");
    }

    // ---- AC-4.BE.7 / AC-4.BE.8 and GET by id ---------------------------------------------------------------------

    @Test
    @DisplayName("AC-4.BE.7 returning an overdue loan sets today, restores the copy and the member's slot, no fine")
    void AC_4_BE_7_returnOverdueLoan() throws Exception {
        long memberId = newMember("Ada Lovelace");
        long bookId = newBook(1, 1);
        long loanId = fixtures.activeLoan(memberId, bookId, TODAY.minusDays(20), TODAY.minusDays(6)).getId();

        MvcResult result = returnLoan(loanId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode loan = body(result);
        assertThat(loan.get("id").asLong()).isEqualTo(loanId);
        assertThat(loan.get("member").get("name").asText()).isEqualTo("Ada Lovelace");
        assertThat(loan.get("book").get("id").asLong()).isEqualTo(bookId);
        assertThat(loan.get("returnDate").asText()).isEqualTo(TODAY.toString());
        assertThat(loan.get("overdue").asBoolean()).isFalse();
        assertThat(loan.get("daysOverdue").asLong()).isZero();
        assertThat(bookCount(bookId)).isZero();
        assertThat(memberCount(memberId)).isZero();

        // the copy can be lent again, and GET by id shows the returned loan
        assertThat(checkout(memberId, bookId).getResponse().getStatus()).isEqualTo(201);
        MvcResult fetched = mvc.perform(get("/api/loans/" + loanId)).andReturn();
        assertThat(fetched.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(fetched).get("returnDate").asText()).isEqualTo(TODAY.toString());
    }

    @Test
    @DisplayName("AC-4.BE.8 a second return is 409 LOAN_ALREADY_RETURNED, an unknown loan 404 LOAN_NOT_FOUND, counts unchanged")
    void AC_4_BE_8_returnTwiceOrUnknown() throws Exception {
        long memberId = newMember("Ada Lovelace");
        long bookId = newBook(1, 2);
        long loanId = fixtures.activeLoan(memberId, bookId, TODAY.minusDays(2), TODAY.plusDays(12)).getId();
        assertThat(returnLoan(loanId).getResponse().getStatus()).isEqualTo(200);

        assertError(returnLoan(loanId), 409, "LOAN_ALREADY_RETURNED");
        assertError(returnLoan(loanId + 1000), 404, "LOAN_NOT_FOUND");

        assertThat(bookCount(bookId)).isZero();
        assertThat(memberCount(memberId)).isZero();
    }

    @Test
    @DisplayName("AC-4.BE.1 GET /api/loans/{id}: active loan shows overdue/daysOverdue, unknown id 404, non-numeric id 400")
    void AC_4_BE_1_getById() throws Exception {
        long memberId = newMember("Ada Lovelace");
        long bookId = newBook(1, 1);
        long loanId = fixtures.activeLoan(memberId, bookId, TODAY.minusDays(20), TODAY.minusDays(6)).getId();

        MvcResult result = mvc.perform(get("/api/loans/" + loanId)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode loan = body(result);
        assertThat(loan.get("overdue").asBoolean()).isTrue();
        assertThat(loan.get("daysOverdue").asLong()).isEqualTo(6);
        assertError(mvc.perform(get("/api/loans/" + (loanId + 1000))).andReturn(), 404, "LOAN_NOT_FOUND");
        assertError(mvc.perform(get("/api/loans/abc")).andReturn(), 400, "VALIDATION_FAILED");
    }

    // ---- AC-4.BE.11 (the one concurrency test) -------------------------------------------------------------------

    @Test
    @DisplayName("AC-4.BE.11 two parallel checkouts of the last copy / a member's last allowed loan: exactly one succeeds")
    void AC_4_BE_11_parallelCheckoutsOneWins() throws Exception {
        // Scenario A: two members race for the last copy of one book.
        long ada = newMember("Ada Lovelace");
        long grace = newMember("Grace Hopper");
        long lastCopy = newBook(1, 1);
        List<MvcResult> a = race(() -> checkout(ada, lastCopy), () -> checkout(grace, lastCopy));

        assertThat(a.stream().map(r -> r.getResponse().getStatus())).containsExactlyInAnyOrder(201, 409);
        MvcResult loser = a.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow();
        assertError(loser, 409, "NO_COPY_AVAILABLE");
        assertThat(bookCount(lastCopy)).isEqualTo(1);
        Book book = books.findById(lastCopy).orElseThrow();
        assertThat(book.getAvailableCopies()).isGreaterThanOrEqualTo(0);

        // Scenario B: a member with 2 loans races for two different books; only one may become the 3rd loan.
        long linus = newMember("Linus Torvalds");
        fixtures.activeLoan(linus, newBook(2, 2), TODAY.minusDays(1), TODAY.plusDays(13));
        fixtures.activeLoan(linus, newBook(3, 2), TODAY.minusDays(1), TODAY.plusDays(13));
        long bookX = newBook(4, 2);
        long bookY = newBook(5, 2);
        List<MvcResult> b = race(() -> checkout(linus, bookX), () -> checkout(linus, bookY));

        assertThat(b.stream().map(r -> r.getResponse().getStatus())).containsExactlyInAnyOrder(201, 409);
        MvcResult limited = b.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow();
        assertError(limited, 409, "LOAN_LIMIT_REACHED");
        assertThat(memberCount(linus)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM loan WHERE member_id = ? AND return_date IS NULL", Long.class, linus)).isEqualTo(3);
    }

    /** Runs the calls at the same moment (start latch) and returns their results. */
    @SafeVarargs
    private static List<MvcResult> race(Callable<MvcResult>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
