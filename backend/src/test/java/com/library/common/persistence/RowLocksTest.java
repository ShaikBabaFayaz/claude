package com.library.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.library.book.Book;
import com.library.book.BookRepository;
import com.library.common.error.ErrorCode;
import com.library.common.error.NotFoundException;
import com.library.loan.Loan;
import com.library.loan.LoanRepository;
import com.library.member.Member;
import com.library.member.MemberRepository;
import com.library.support.TestData;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RK-1: proves on H2 itself that {@link RowLocks} returns post-lock state. Not transactional (two real transactions on two
 * threads) and it cleans up the rows it creates; the context has its own unique database (DR-9).
 */
@SpringBootTest
class RowLocksTest {

    private static final long TIMEOUT_SECONDS = 20;

    @Autowired
    private RowLocks rowLocks;
    @Autowired
    private BookRepository books;
    @Autowired
    private MemberRepository members;
    @Autowired
    private LoanRepository loans;
    @Autowired
    private EntityManager em;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long bookId;
    private Long memberId;

    @BeforeEach
    void createRows() {
        tx = new TransactionTemplate(transactionManager);
        bookId = books.saveAndFlush(TestData.book("9780132350884", 5)).getId();
        memberId = members.saveAndFlush(TestData.member("Ada Lovelace")).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM loan");
        jdbc.update("DELETE FROM book");
        jdbc.update("DELETE FROM member");
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    @Test
    @DisplayName("AC-4.BE.11 / RK-1: a waiting transaction sees the state the holder committed")
    void AC_4_BE_11_waitingTransactionReadsCommittedState() throws Exception {
        CountDownLatch holderHasLock = new CountDownLatch(1);
        AtomicLong waitedMillis = new AtomicLong();

        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            Book book = rowLocks.lock(Book.class, bookId);
            holderHasLock.countDown();
            sleep(500);
            book.lendCopy();
        }));

        assertThat(holderHasLock.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Integer> waiter = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
            long start = System.nanoTime();
            Book book = rowLocks.lock(Book.class, bookId);
            waitedMillis.set((System.nanoTime() - start) / 1_000_000);
            return book.getActiveLoanCount();
        }));

        await(holder);
        assertThat(await(waiter)).as("count the waiter read after the lock was granted").isEqualTo(1);
        assertThat(waitedMillis.get()).as("the waiter really blocked on the holder's lock").isGreaterThanOrEqualTo(150);
    }

    @Test
    @DisplayName("AC-4.BE.11 / RK-1: an entity already loaded in the transaction is refreshed under the lock")
    void AC_4_BE_11_alreadyManagedEntityIsRefreshed() throws Exception {
        CountDownLatch waiterHasLoaded = new CountDownLatch(1);
        CountDownLatch holderCommitted = new CountDownLatch(1);

        CompletableFuture<Integer[]> waiter = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
            Book early = em.find(Book.class, bookId); // plain read: managed, count 0
            int before = early.getActiveLoanCount();
            waiterHasLoaded.countDown();
            await(holderCommitted);
            Book locked = rowLocks.lock(Book.class, bookId);
            assertThat(locked).as("same managed instance, refreshed").isSameAs(early);
            return new Integer[] {before, locked.getActiveLoanCount()};
        }));

        assertThat(waiterHasLoaded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        tx.executeWithoutResult(status -> rowLocks.lock(Book.class, bookId).lendCopy());
        holderCommitted.countDown();

        Integer[] counts = await(waiter);
        assertThat(counts[0]).as("stale value the waiter had cached").isZero();
        assertThat(counts[1]).as("value after lock + refresh").isEqualTo(1);
    }

    @Test
    @DisplayName("DR-2: locking a Loan's lazy member and book proxies loads the real rows under the lock")
    void DR_2_lazyProxiesAreLockedAndLoaded() {
        Long loanId = tx.execute(status -> {
            Book book = books.getReferenceById(bookId);
            Member member = members.getReferenceById(memberId);
            return loans.saveAndFlush(new Loan(member, book, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15))).getId();
        });

        tx.executeWithoutResult(status -> {
            Loan loan = rowLocks.lock(Loan.class, loanId);
            Long lazyMemberId = loan.getMember().getId();
            Long lazyBookId = loan.getBook().getId();
            Member member = rowLocks.lock(Member.class, lazyMemberId);
            Book book = rowLocks.lock(Book.class, lazyBookId);

            assertThat(member.getName()).isEqualTo("Ada Lovelace");
            assertThat(book.getTotalCopies()).isEqualTo(5);
            assertThat(member.getId()).isEqualTo(memberId);
            assertThat(book.getId()).isEqualTo(bookId);
        });
    }

    @Test
    @DisplayName("AC-1.BE.5 / AC-3.BE.4 / AC-4.BE.8: locking a missing id throws the type-specific NotFoundException")
    void notFoundPerType() {
        long missing = 987_654L;

        assertNotFound(Book.class, missing, ErrorCode.BOOK_NOT_FOUND);
        assertNotFound(Member.class, missing, ErrorCode.MEMBER_NOT_FOUND);
        assertNotFound(Loan.class, missing, ErrorCode.LOAN_NOT_FOUND);
        assertNotFound(Book.class, null, ErrorCode.BOOK_NOT_FOUND);
    }

    /** Each attempt gets its own transaction: the NotFoundException rolls the surrounding transaction back, as in a service. */
    private void assertNotFound(Class<?> type, Object id, ErrorCode expected) {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> rowLocks.lock(type, id)))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("a row deleted after it was loaded is reported as not found when it is locked again")
    void deletedAfterLoadIsNotFound() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            em.find(Book.class, bookId); // managed
            jdbc.update("DELETE FROM book WHERE id = ?", bookId); // gone behind the persistence context's back
            rowLocks.lock(Book.class, bookId);
        })).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BOOK_NOT_FOUND));
    }

    @Test
    @DisplayName("locking requires an active transaction (MANDATORY), it never silently runs without one")
    void requiresTransaction() {
        assertThatThrownBy(() -> rowLocks.lock(Book.class, bookId)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("NFR-5 / DR-3: a lock wait that times out surfaces as Spring's PessimisticLockingFailureException")
    void lockTimeoutIsTranslated() throws Exception {
        CountDownLatch holderHasLock = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            rowLocks.lock(Book.class, bookId);
            holderHasLock.countDown();
            await(releaseHolder);
        }));
        assertThat(holderHasLock.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        try {
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                em.createNativeQuery("SET LOCK_TIMEOUT 300").executeUpdate(); // this connection only, restored below
                try {
                    rowLocks.lock(Book.class, bookId);
                } finally {
                    em.createNativeQuery("SET LOCK_TIMEOUT 5000").executeUpdate();
                }
            })).isInstanceOf(PessimisticLockingFailureException.class);
        } finally {
            releaseHolder.countDown();
            await(holder);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
