package com.library.loan;

import com.library.book.Book;
import com.library.common.error.ConflictException;
import com.library.common.error.ConstraintViolationTranslator;
import com.library.common.error.ConstraintViolationTranslator.Operation;
import com.library.common.error.ErrorCode;
import com.library.common.error.NotFoundException;
import com.library.common.persistence.RowLocks;
import com.library.common.web.PageRequestParams;
import com.library.common.web.PageResponse;
import com.library.loan.dto.CheckoutRequest;
import com.library.loan.dto.LoanResponse;
import com.library.member.Member;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loan use cases. One transaction per method (architecture 7.2). Global lock order is loan -> member -> book (7.3):
 * every lock is taken through {@link RowLocks} <em>before</em> the state it protects is read, so a request that had to
 * wait decides on the committed result of the other one (DR-2). Checkout locks member then book (the loan row is new);
 * return locks the loan, then reads the member and book ids from the lazy references without initialising them, then
 * locks member and book. Counters change only through the entity methods called here. Each writing method flushes
 * through the repository and translates a {@link DataIntegrityViolationException} per operation (DR-5). "Today" always
 * comes from the injected {@link Clock}.
 */
@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final LoanRepository loans;
    private final RowLocks rowLocks;
    private final ConstraintViolationTranslator translator;
    private final Clock clock;

    public LoanService(
            LoanRepository loans, RowLocks rowLocks, ConstraintViolationTranslator translator, Clock clock) {
        this.loans = loans;
        this.rowLocks = rowLocks;
        this.translator = translator;
        this.clock = clock;
    }

    /** Rule order is fixed so the error is deterministic when several rules fail (architecture 6.3 notes). */
    @Transactional
    public LoanResponse checkout(CheckoutRequest request) {
        Long memberId = request.memberId();
        Long bookId = request.bookId();
        Member member = rowLocks.lock(Member.class, memberId);
        Book book = rowLocks.lock(Book.class, bookId);

        if (!member.isActive()) {
            throw rejected(ErrorCode.MEMBER_INACTIVE, memberId, bookId);
        }
        if (loans.existsActiveLoan(memberId, bookId)) {
            throw rejected(ErrorCode.DUPLICATE_ACTIVE_LOAN, memberId, bookId);
        }
        if (member.getActiveLoanCount() >= LoanPolicy.MAX_ACTIVE_LOANS) {
            throw rejected(ErrorCode.LOAN_LIMIT_REACHED, memberId, bookId);
        }
        if (book.getAvailableCopies() < 1) {
            throw rejected(ErrorCode.NO_COPY_AVAILABLE, memberId, bookId);
        }

        LocalDate today = LocalDate.now(clock);
        Loan loan = new Loan(member, book, today, LoanPolicy.dueDate(today));
        member.startLoan();
        book.lendCopy();
        try {
            loans.saveAndFlush(loan);
        } catch (DataIntegrityViolationException e) {
            throw translator.translate(Operation.CHECKOUT, e);
        }
        log.info("Checkout done: loanId={} memberId={} bookId={}", loan.getId(), memberId, bookId);
        return LoanResponse.of(loan, member, book, today);
    }

    @Transactional
    public LoanResponse returnLoan(Long loanId) {
        Loan loan = rowLocks.lock(Loan.class, loanId);
        if (!loan.isActive()) {
            log.info("Return rejected: code={} loanId={}", ErrorCode.LOAN_ALREADY_RETURNED, loanId);
            throw new ConflictException(ErrorCode.LOAN_ALREADY_RETURNED);
        }
        // getId() on the lazy references does not initialise them: nothing of member/book is read before its lock
        Long memberId = loan.getMember().getId();
        Long bookId = loan.getBook().getId();
        Member member = rowLocks.lock(Member.class, memberId);
        Book book = rowLocks.lock(Book.class, bookId);

        LocalDate today = LocalDate.now(clock);
        loan.markReturned(today);
        member.endLoan();
        book.returnCopy();
        try {
            loans.saveAndFlush(loan);
        } catch (DataIntegrityViolationException e) {
            throw translator.translate(Operation.RETURN, e);
        }
        log.info("Return done: loanId={} memberId={} bookId={}", loanId, memberId, bookId);
        return LoanResponse.of(loan, member, book, today);
    }

    @Transactional(readOnly = true)
    public LoanResponse get(Long id) {
        Loan loan = loans.findDetailedById(id).orElseThrow(() -> NotFoundException.loan(id));
        return LoanResponse.from(loan, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanResponse> listActive(Long memberId, Long bookId, PageRequestParams paging) {
        LocalDate today = LocalDate.now(clock);
        return PageResponse.from(loans.findActive(memberId, bookId, paging.toPageable()), l -> LoanResponse.from(l, today));
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> listOverdue() {
        LocalDate today = LocalDate.now(clock);
        return loans.findOverdue(today).stream().map(l -> LoanResponse.from(l, today)).toList();
    }

    private static ConflictException rejected(ErrorCode code, Long memberId, Long bookId) {
        log.info("Checkout rejected: code={} memberId={} bookId={}", code, memberId, bookId);
        return new ConflictException(code);
    }
}
