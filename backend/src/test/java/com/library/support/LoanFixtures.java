package com.library.support;

import com.library.book.Book;
import com.library.book.BookRepository;
import com.library.loan.Loan;
import com.library.loan.LoanRepository;
import com.library.member.Member;
import com.library.member.MemberRepository;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test fixtures that put the database into states the API under test cannot (yet) create itself: loans with consistent
 * counters, written through the entity methods exactly like the production code does (architecture 7.3). Each call is
 * its own committed transaction, so tests using it must not be {@code @Transactional}.
 */
public final class LoanFixtures {

    private final BookRepository books;
    private final MemberRepository members;
    private final LoanRepository loans;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public LoanFixtures(BookRepository books, MemberRepository members, LoanRepository loans, JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.books = books;
        this.members = members;
        this.loans = loans;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Empties all three tables (children first). */
    public void deleteAll() {
        jdbc.update("DELETE FROM loan");
        jdbc.update("DELETE FROM book");
        jdbc.update("DELETE FROM member");
    }

    public Book book(String isbn, int totalCopies) {
        return books.saveAndFlush(TestData.book(isbn, totalCopies));
    }

    public Member member(String name) {
        return members.saveAndFlush(TestData.member(name));
    }

    /** An active loan: counters of the book and the member are incremented. */
    public Loan activeLoan(Long memberId, Long bookId, LocalDate checkout, LocalDate due) {
        return tx.execute(status -> {
            Member member = members.findById(memberId).orElseThrow();
            Book book = books.findById(bookId).orElseThrow();
            member.startLoan();
            book.lendCopy();
            return loans.saveAndFlush(new Loan(member, book, checkout, due));
        });
    }

    /** A returned loan (history): counters are untouched. */
    public Loan returnedLoan(Long memberId, Long bookId, LocalDate checkout, LocalDate due, LocalDate returned) {
        return tx.execute(status -> {
            Loan loan = new Loan(members.getReferenceById(memberId), books.getReferenceById(bookId), checkout, due);
            loan.markReturned(returned);
            return loans.saveAndFlush(loan);
        });
    }

    public long countBooks() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM book", Long.class);
    }

    public long countMembers() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member", Long.class);
    }

    public long countLoans() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM loan", Long.class);
    }
}
