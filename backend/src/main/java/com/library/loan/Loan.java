package com.library.loan;

import com.library.book.Book;
import com.library.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * One lending of a book to a member. {@code member} and {@code book} are explicitly LAZY (DR-2): the JPA default
 * (EAGER) would load them before their row locks are taken. Read their ids with {@code getMember().getId()}, which
 * does not initialise the proxy, and lock via {@code RowLocks} before reading any state.
 *
 * <p>No {@code @Version}: a loan is only modified under its own row lock and returned at most once
 * ({@code ck_loan_active_key}).
 */
@Entity
@Table(name = "loan")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "checkout_date", nullable = false)
    private LocalDate checkoutDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Null while the loan is active. */
    @Column(name = "return_date")
    private LocalDate returnDate;

    /** The book id while the loan is active, null once returned; backs {@code uk_loan_member_active_book}. */
    @Column(name = "active_book_id")
    private Long activeBookId;

    protected Loan() {
        // for JPA
    }

    /** Creates an active loan. The book must already have an id (it was persisted or loaded). */
    public Loan(Member member, Book book, LocalDate checkoutDate, LocalDate dueDate) {
        this.member = member;
        this.book = book;
        this.checkoutDate = checkoutDate;
        this.dueDate = dueDate;
        this.activeBookId = book.getId();
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public Book getBook() {
        return book;
    }

    public LocalDate getCheckoutDate() {
        return checkoutDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public Long getActiveBookId() {
        return activeBookId;
    }

    public boolean isActive() {
        return returnDate == null;
    }

    /** Marks the loan returned and releases the (member, book) active key. Counters are the service's job. */
    public void markReturned(LocalDate returnDate) {
        this.returnDate = returnDate;
        this.activeBookId = null;
    }
}
