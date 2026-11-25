package com.library.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A catalogue entry. Field access (so {@code getId()} on a lazy proxy never initialises it, RK-2).
 *
 * <p>{@code activeLoanCount} is a denormalised counter. It changes only through {@link #lendCopy()} and
 * {@link #returnCopy()}, called from {@code LoanService} (and the seeder), always under the book's row lock
 * (architecture 7.3). The methods are deliberately unguarded: the database CHECK {@code ck_book_active_loans} is the
 * backstop, and the service reports the specific business error before it gets that far.
 */
@Entity
@Table(name = "book")
@DynamicUpdate
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String author;

    /** Normalised (hyphens/spaces removed, trailing X upper-case); unique. */
    @Column(nullable = false, length = 13)
    private String isbn;

    @Column(name = "total_copies", nullable = false)
    private int totalCopies;

    @Column(name = "active_loan_count", nullable = false)
    private int activeLoanCount;

    /** Lost-update backstop (ADR-23); not exposed in the API. */
    @Version
    @Column(nullable = false)
    private long version;

    protected Book() {
        // for JPA
    }

    public Book(String title, String author, String isbn, int totalCopies) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.totalCopies = totalCopies;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public int getActiveLoanCount() {
        return activeLoanCount;
    }

    public long getVersion() {
        return version;
    }

    /** Derived, never stored: copies that can still be lent. */
    public int getAvailableCopies() {
        return totalCopies - activeLoanCount;
    }

    /** Replaces the editable fields; never touches the loan counter. */
    public void update(String title, String author, String isbn, int totalCopies) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.totalCopies = totalCopies;
    }

    /** One more active loan. Counter mutator, see class comment. */
    public void lendCopy() {
        activeLoanCount++;
    }

    /** One active loan fewer. Counter mutator, see class comment. */
    public void returnCopy() {
        activeLoanCount--;
    }
}
