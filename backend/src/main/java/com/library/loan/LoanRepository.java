package com.library.loan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every loan read that builds a {@code LoanResponse} fetch-joins member and book, so building a list never issues
 * per-row queries (DR-8). The locking write paths do not use these: they lock through {@code RowLocks} first (DR-2).
 */
public interface LoanRepository extends JpaRepository<Loan, Long> {

    /**
     * Removes the returned-loan history of a book with one bulk statement (AC-1.BE.8, DR-12). Called by
     * {@code BookService.delete} after it holds the book lock and has seen no active loans. Bypasses the persistence
     * context on purpose; no loan of this book is loaded in that transaction.
     *
     * @return the number of deleted loans
     */
    @Modifying
    @Query("delete from Loan l where l.book.id = :bookId and l.returnDate is not null")
    int deleteReturnedByBookId(@Param("bookId") Long bookId);

    /** Does this member hold an active loan of this book? (AC-4.BE.5; call only after the member lock is held.) */
    @Query("select count(l) > 0 from Loan l where l.member.id = :memberId and l.activeBookId = :bookId")
    boolean existsActiveLoan(@Param("memberId") Long memberId, @Param("bookId") Long bookId);

    /** One loan, active or returned, with member and book (GET /api/loans/{id}). */
    @Query("select l from Loan l join fetch l.member join fetch l.book where l.id = :id")
    Optional<Loan> findDetailedById(@Param("id") Long id);

    /** Active loans, optionally filtered by member and/or book, most urgent first (AC-4.BE.10). */
    @Query(value = "select l from Loan l join fetch l.member join fetch l.book "
            + "where l.returnDate is null "
            + "and (:memberId is null or l.member.id = :memberId) "
            + "and (:bookId is null or l.book.id = :bookId) "
            + "order by l.dueDate asc, l.id asc",
            countQuery = "select count(l) from Loan l where l.returnDate is null "
                    + "and (:memberId is null or l.member.id = :memberId) "
                    + "and (:bookId is null or l.book.id = :bookId)")
    Page<Loan> findActive(@Param("memberId") Long memberId, @Param("bookId") Long bookId, Pageable pageable);

    /** Active loans due strictly before {@code today}, most overdue first (AC-4.BE.9). */
    @Query("select l from Loan l join fetch l.member join fetch l.book "
            + "where l.returnDate is null and l.dueDate < :today order by l.dueDate asc, l.id asc")
    List<Loan> findOverdue(@Param("today") LocalDate today);
}
