package com.library.loan.dto;

import com.library.book.Book;
import com.library.loan.Loan;
import com.library.member.Member;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Response shape of every loan endpoint (architecture 6.1). {@code overdue}/{@code daysOverdue} are computed by the
 * server from its clock so the UI never does date arithmetic (NFR-12): only an active loan whose due date is strictly
 * before today is overdue; a returned loan is {@code false}/{@code 0}. The overdue list returns this same shape
 * ({@code OverdueLoanResponse} in the architecture) with {@code overdue = true}.
 */
public record LoanResponse(
        Long id,
        MemberRef member,
        BookRef book,
        LocalDate checkoutDate,
        LocalDate dueDate,
        LocalDate returnDate,
        boolean overdue,
        long daysOverdue) {

    public record MemberRef(Long id, String name) {
    }

    public record BookRef(Long id, String title, String isbn) {
    }

    /** Reads member and book through the loan: only for queries that fetch-join them (DR-8). */
    public static LoanResponse from(Loan loan, LocalDate today) {
        return of(loan, loan.getMember(), loan.getBook(), today);
    }

    /** Uses the given (already loaded or locked) member and book. */
    public static LoanResponse of(Loan loan, Member member, Book book, LocalDate today) {
        boolean overdue = loan.isActive() && loan.getDueDate().isBefore(today);
        long daysOverdue = overdue ? ChronoUnit.DAYS.between(loan.getDueDate(), today) : 0;
        return new LoanResponse(
                loan.getId(),
                new MemberRef(member.getId(), member.getName()),
                new BookRef(book.getId(), book.getTitle(), book.getIsbn()),
                loan.getCheckoutDate(),
                loan.getDueDate(),
                loan.getReturnDate(),
                overdue,
                daysOverdue);
    }
}
