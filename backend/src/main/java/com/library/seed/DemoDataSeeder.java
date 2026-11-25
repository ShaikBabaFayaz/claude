package com.library.seed;

import com.library.book.Book;
import com.library.book.BookRepository;
import com.library.common.validation.IsbnNormalizer;
import com.library.common.validation.IsbnValidator;
import com.library.loan.Loan;
import com.library.loan.LoanPolicy;
import com.library.loan.LoanRepository;
import com.library.member.Member;
import com.library.member.MemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the demo data set on every start when {@code library.demo-data.enabled=true} (FR-24, AC-X.4, architecture 8).
 * The database is in-memory, so the same data appears after every restart and earlier changes are gone (NFR-7).
 *
 * <p>Java rather than {@code data.sql} because loan dates are computed relative to today from the injected {@link Clock}
 * and the counters must match the inserted loans. Everything runs in one transaction. Loans are inserted directly (not
 * through {@code LoanService.checkout}) because they need back-dated checkout dates; the counters are changed through the
 * same entity methods as the service, and the database constraints still apply. Afterwards the counter invariants are
 * verified and start-up fails loudly if a fixture is inconsistent, so bad demo data can never ship silently.
 */
@Component
@ConditionalOnProperty(name = "library.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final BookRepository books;
    private final MemberRepository members;
    private final LoanRepository loans;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DemoDataSeeder(BookRepository books, MemberRepository members, LoanRepository loans,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.books = books;
        this.members = members;
        this.loans = loans;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(status -> seed());
    }

    private void seed() {
        LocalDate today = LocalDate.now(clock);
        List<Book> savedBooks = new ArrayList<>();
        for (DemoData.BookSpec spec : DemoData.BOOKS) {
            savedBooks.add(books.save(new Book(
                    spec.title(), spec.author(), IsbnNormalizer.normalize(spec.isbn()), spec.totalCopies())));
        }
        List<Member> savedMembers = new ArrayList<>();
        for (DemoData.MemberSpec spec : DemoData.MEMBERS) {
            savedMembers.add(members.save(new Member(spec.name(), spec.email())));
        }
        for (DemoData.LoanSpec spec : DemoData.LOANS) {
            Member member = savedMembers.get(spec.memberIndex());
            Book book = savedBooks.get(spec.bookIndex());
            member.startLoan();
            book.lendCopy();
            loans.save(new Loan(member, book, today.plusDays(spec.checkoutDaysFromToday()),
                    today.plusDays(spec.dueDaysFromToday())));
        }
        loans.flush();
        verifyInvariants();
        log.info("Demo data loaded: books={} members={} activeLoans={}",
                DemoData.BOOKS.size(), DemoData.MEMBERS.size(), DemoData.LOANS.size());
    }

    /** Counter = number of active loans per book and member, no member above the limit, all ISBNs valid. */
    private void verifyInvariants() {
        Map<Long, Integer> activePerBook = new HashMap<>();
        Map<Long, Integer> activePerMember = new HashMap<>();
        for (Loan loan : loans.findAll()) {
            if (loan.isActive()) {
                activePerBook.merge(loan.getBook().getId(), 1, Integer::sum);
                activePerMember.merge(loan.getMember().getId(), 1, Integer::sum);
            }
        }
        for (Book book : books.findAll()) {
            int expected = activePerBook.getOrDefault(book.getId(), 0);
            if (book.getActiveLoanCount() != expected || book.getAvailableCopies() < 0) {
                throw new IllegalStateException("Demo data inconsistent: book " + book.getId() + " counter "
                        + book.getActiveLoanCount() + " but " + expected + " active loans");
            }
            if (!IsbnValidator.isValidIsbn(book.getIsbn())) {
                throw new IllegalStateException("Demo data inconsistent: invalid ISBN on book " + book.getId());
            }
        }
        for (Member member : members.findAll()) {
            int expected = activePerMember.getOrDefault(member.getId(), 0);
            if (member.getActiveLoanCount() != expected || expected > LoanPolicy.MAX_ACTIVE_LOANS) {
                throw new IllegalStateException("Demo data inconsistent: member " + member.getId() + " counter "
                        + member.getActiveLoanCount() + " but " + expected + " active loans");
            }
        }
    }
}
