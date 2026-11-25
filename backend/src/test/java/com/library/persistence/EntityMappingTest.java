package com.library.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.library.book.Book;
import com.library.book.BookRepository;
import com.library.loan.Loan;
import com.library.loan.LoanPolicy;
import com.library.loan.LoanRepository;
import com.library.member.Member;
import com.library.member.MemberRepository;
import com.library.member.MemberStatus;
import com.library.support.TestData;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Locale;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EntityMappingTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private BookRepository books;

    @Autowired
    private MemberRepository members;

    @Autowired
    private LoanRepository loans;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("DR-2 / RK-2: Loan.member and Loan.book are LAZY; reading their ids does not initialise the proxies")
    void DR_2_lazyAssociationsDoNotInitialiseOnIdAccess() {
        Book book = books.save(TestData.book("9780132350884", 3));
        Member member = members.save(TestData.member("Ada Lovelace"));
        Loan saved = loans.save(new Loan(member, book, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15)));
        flushAndClear();

        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        Loan loan = em.find(Loan.class, saved.getId());
        assertThat(Hibernate.isInitialized(loan.getMember())).isFalse();
        assertThat(Hibernate.isInitialized(loan.getBook())).isFalse();

        assertThat(loan.getMember().getId()).isEqualTo(member.getId());
        assertThat(loan.getBook().getId()).isEqualTo(book.getId());

        assertThat(Hibernate.isInitialized(loan.getMember())).isFalse();
        assertThat(Hibernate.isInitialized(loan.getBook())).isFalse();
        assertThat(stats.getPrepareStatementCount()).as("only the loan row was read").isEqualTo(1);
    }

    @Test
    @DisplayName("ADR-23: @Version increments on every update of book and member")
    void ADR_23_versionIncrements() {
        Book book = books.saveAndFlush(TestData.book("9780132350884", 3));
        Member member = members.saveAndFlush(TestData.member("Ada Lovelace"));
        assertThat(book.getVersion()).isZero();
        assertThat(member.getVersion()).isZero();

        book.update("New title", "New author", book.getIsbn(), 4);
        member.edit("Ada L.", "ada@example.org");
        em.flush();

        assertThat(book.getVersion()).isEqualTo(1);
        assertThat(member.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("counter methods are the only mutators and the derived available count follows them")
    void counterMethods() {
        Book book = books.saveAndFlush(TestData.book("9780132350884", 2));
        Member member = members.saveAndFlush(TestData.member("Ada Lovelace"));

        book.lendCopy();
        member.startLoan();
        em.flush();
        flushAndClear();

        Book reloaded = em.find(Book.class, book.getId());
        assertThat(reloaded.getActiveLoanCount()).isEqualTo(1);
        assertThat(reloaded.getAvailableCopies()).isEqualTo(1);
        assertThat(em.find(Member.class, member.getId()).getActiveLoanCount()).isEqualTo(1);

        reloaded.returnCopy();
        em.flush();
        assertThat(reloaded.getAvailableCopies()).isEqualTo(2);
    }

    @Test
    @DisplayName("DR-15 email_normalized is set on persist and update, with Locale.ROOT")
    void DR_15_emailNormalizedOnPersistAndUpdate() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            Member member = members.saveAndFlush(TestData.member("Iris", "IRIS.Ingrid@Example.ORG"));
            assertThat(member.getEmail()).isEqualTo("IRIS.Ingrid@Example.ORG");
            assertThat(member.getEmailNormalized()).isEqualTo("iris.ingrid@example.org");

            member.edit("Iris", "INGRID@EXAMPLE.ORG");
            em.flush();
            flushAndClear();

            assertThat(em.find(Member.class, member.getId()).getEmailNormalized()).isEqualTo("ingrid@example.org");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("new members are ACTIVE; deactivate/activate change only the status")
    void statusTransitions() {
        Member member = members.saveAndFlush(TestData.member("Ada Lovelace"));
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);

        member.deactivate();
        em.flush();
        assertThat(member.isActive()).isFalse();
        assertThat(member.getActiveLoanCount()).isZero();

        member.activate();
        em.flush();
        assertThat(member.isActive()).isTrue();
    }

    @Test
    @DisplayName("a new loan is active and keyed by its book id; markReturned releases the key")
    void loanLifecycleMapping() {
        Book book = books.saveAndFlush(TestData.book("9780132350884", 3));
        Member member = members.saveAndFlush(TestData.member("Ada Lovelace"));
        LocalDate checkout = LocalDate.of(2026, 11, 1);
        Loan loan = loans.saveAndFlush(new Loan(member, book, checkout, LoanPolicy.dueDate(checkout)));

        assertThat(loan.isActive()).isTrue();
        assertThat(loan.getActiveBookId()).isEqualTo(book.getId());
        assertThat(loan.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 15));

        loan.markReturned(LocalDate.of(2026, 11, 5));
        em.flush();
        flushAndClear();

        Loan reloaded = em.find(Loan.class, loan.getId());
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getReturnDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(reloaded.getActiveBookId()).isNull();
    }
}
