package com.library.concurrency;

import static com.library.support.ApiRequests.delete;
import static com.library.support.ApiRequests.post;
import static com.library.support.ApiRequests.putJson;
import static com.library.support.ErrorBodyAssert.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import com.library.book.Book;
import com.library.book.BookRepository;
import com.library.common.persistence.RowLocks;
import com.library.loan.LoanRepository;
import com.library.member.Member;
import com.library.member.MemberRepository;
import com.library.member.MemberStatus;
import com.library.support.LoanFixtures;
import com.library.support.TestClockConfig;
import com.library.support.TestData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
 * Proves "lock first, then read" (architecture 7.3, DR-1, DR-2, DR-4) for the writers owned by T-11 and T-15, with two
 * real transactions: a holder thread takes a row lock and changes a counter exactly like {@code LoanService} will, while
 * the HTTP request under test has to wait for that lock. The request must then act on the <em>committed</em> counter
 * (reject, or leave it intact), never on a stale one. Not transactional; tables are emptied before each test.
 * The full checkout/return matrix is T-22.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class LockFirstWritersTest {

    private static final long WAIT_SECONDS = 20;
    private static final long HOLD_MILLIS = 500;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RowLocks rowLocks;
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
    private TransactionTemplate tx;

    @BeforeEach
    void cleanDatabase() {
        fixtures = new LoanFixtures(books, members, loans, jdbc, transactionManager);
        fixtures.deleteAll();
        tx = new TransactionTemplate(transactionManager);
    }

    /** Starts a transaction that locks the row, waits, applies {@code change} and commits; returns once the lock is held. */
    private <T> CompletableFuture<Void> holdLockThenChange(Class<T> type, Long id, Consumer<T> change) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            T entity = rowLocks.lock(type, id);
            locked.countDown();
            sleep(HOLD_MILLIS);
            change.accept(entity);
        }));
        assertThat(locked.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("holder obtained the lock").isTrue();
        return holder;
    }

    private static void finish(CompletableFuture<Void> holder) throws Exception {
        holder.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("DR-1 / AC-4.BE.11 a member edit waits for the lock and keeps the counter committed meanwhile")
    void memberEditDoesNotOverwriteCounter() throws Exception {
        Member member = fixtures.member("Ada Lovelace");
        CompletableFuture<Void> holder = holdLockThenChange(Member.class, member.getId(), Member::startLoan);

        long start = System.nanoTime();
        MvcResult result = mvc.perform(putJson("/api/members/" + member.getId(),
                TestData.memberJson("Ada King", "ada.king@example.org"))).andReturn();
        long waitedMillis = (System.nanoTime() - start) / 1_000_000;
        finish(holder);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(waitedMillis).as("the edit really waited for the lock").isGreaterThanOrEqualTo(HOLD_MILLIS / 3);
        Member stored = members.findById(member.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Ada King");
        assertThat(stored.getActiveLoanCount()).as("counter written by the lock holder survives the edit").isEqualTo(1);
        assertThat(stored.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("DR-1 / AC-3.BE.6 a deactivate that waited sees the loan started meanwhile and is rejected")
    void deactivateAfterConcurrentLoanIsRejected() throws Exception {
        Member member = fixtures.member("Ada Lovelace");
        CompletableFuture<Void> holder = holdLockThenChange(Member.class, member.getId(), Member::startLoan);

        MvcResult result = mvc.perform(post("/api/members/" + member.getId() + "/deactivate")).andReturn();
        finish(holder);

        assertError(result, 409, "MEMBER_HAS_ACTIVE_LOANS");
        Member stored = members.findById(member.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(stored.getActiveLoanCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("DR-4 a reactivate that waited for a status change applies on top of it")
    void reactivateWaitsForStatusChange() throws Exception {
        Member member = fixtures.member("Ada Lovelace");
        CompletableFuture<Void> holder = holdLockThenChange(Member.class, member.getId(), Member::deactivate);

        MvcResult result = mvc.perform(post("/api/members/" + member.getId() + "/reactivate")).andReturn();
        finish(holder);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(members.findById(member.getId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("DR-4 / AC-1.BE.6 a book update that waited sees the copies lent meanwhile and is rejected")
    void bookUpdateBelowConcurrentLoansIsRejected() throws Exception {
        Book book = fixtures.book("9780132350884", 3);
        CompletableFuture<Void> holder = holdLockThenChange(Book.class, book.getId(), b -> {
            b.lendCopy();
            b.lendCopy();
            b.lendCopy();
        });

        MvcResult result = mvc.perform(
                putJson("/api/books/" + book.getId(), TestData.bookJson("Clean Code", "RCM", "9780132350884", 2))).andReturn();
        finish(holder);

        assertError(result, 400, "TOTAL_COPIES_BELOW_ACTIVE_LOANS");
        Book stored = books.findById(book.getId()).orElseThrow();
        assertThat(stored.getTotalCopies()).isEqualTo(3);
        assertThat(stored.getActiveLoanCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("DR-4 a book update that waited keeps the counter committed meanwhile")
    void bookUpdateKeepsConcurrentCounter() throws Exception {
        Book book = fixtures.book("9780132350884", 3);
        CompletableFuture<Void> holder = holdLockThenChange(Book.class, book.getId(), Book::lendCopy);

        MvcResult result = mvc.perform(
                putJson("/api/books/" + book.getId(), TestData.bookJson("New title", "RCM", "9780132350884", 5))).andReturn();
        finish(holder);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Book stored = books.findById(book.getId()).orElseThrow();
        assertThat(stored.getTotalCopies()).isEqualTo(5);
        assertThat(stored.getActiveLoanCount()).as("counter written by the lock holder survives the update").isEqualTo(1);
    }

    @Test
    @DisplayName("DR-12 / AC-1.BE.7 a book delete that waited sees the copy lent meanwhile and is rejected")
    void bookDeleteAfterConcurrentLoanIsRejected() throws Exception {
        Book book = fixtures.book("9780132350884", 3);
        Member member = fixtures.member("Ada Lovelace");
        // the holder lends the copy for real (loan row + counters), as LoanService will
        CountDownLatch locked = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            Book lockedBook = rowLocks.lock(Book.class, book.getId());
            locked.countDown();
            sleep(HOLD_MILLIS);
            lockedBook.lendCopy();
            Member m = members.findById(member.getId()).orElseThrow();
            m.startLoan();
            loans.saveAndFlush(new com.library.loan.Loan(
                    m, lockedBook, TestClockConfig.FIXED_DATE, TestClockConfig.FIXED_DATE.plusDays(14)));
        }));
        assertThat(locked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        MvcResult result = mvc.perform(delete("/api/books/" + book.getId())).andReturn();
        finish(holder);

        assertError(result, 409, "BOOK_HAS_ACTIVE_LOANS");
        assertThat(books.existsById(book.getId())).isTrue();
        assertThat(fixtures.countLoans()).isEqualTo(1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
