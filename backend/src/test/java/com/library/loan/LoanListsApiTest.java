package com.library.loan;

import static com.library.support.ApiRequests.get;
import static com.library.support.ErrorBodyAssert.assertError;
import static com.library.support.ErrorBodyAssert.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.book.BookRepository;
import com.library.member.MemberRepository;
import com.library.support.LoanFixtures;
import com.library.support.TestClockConfig;
import com.library.support.TestData;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

/**
 * US-4 / AG-35 (T-18): the active and overdue loan lists through the full stack (fixed clock 2026-11-25). Not
 * transactional; tables are emptied before each test.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class LoanListsApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate TODAY = TestClockConfig.FIXED_DATE;

    @Autowired
    private MockMvc mvc;
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
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private LoanFixtures fixtures;

    @BeforeEach
    void cleanDatabase() {
        fixtures = new LoanFixtures(books, members, loans, jdbc, transactionManager);
        fixtures.deleteAll();
    }

    private JsonNode getJson(String url) throws Exception {
        MvcResult result = mvc.perform(get(url)).andReturn();
        assertThat(result.getResponse().getStatus()).as("GET %s", url).isEqualTo(200);
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    // ---- AC-4.BE.9 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-4.BE.9 overdue list: strictly before today, active only, most overdue first, with member/book/days")
    void AC_4_BE_9_overdueList() throws Exception {
        long ada = fixtures.member("Ada Lovelace").getId();
        long grace = fixtures.member("Grace Hopper").getId();
        long b1 = fixtures.book(TestData.isbn13(1), 3).getId();
        long b2 = fixtures.book(TestData.isbn13(2), 3).getId();
        long b3 = fixtures.book(TestData.isbn13(3), 3).getId();
        long b4 = fixtures.book(TestData.isbn13(4), 3).getId();

        long yesterday = fixtures.activeLoan(ada, b1, TODAY.minusDays(15), TODAY.minusDays(1)).getId();
        long sixDays = fixtures.activeLoan(grace, b2, TODAY.minusDays(20), TODAY.minusDays(6)).getId();
        long dueToday = fixtures.activeLoan(ada, b3, TODAY.minusDays(14), TODAY).getId();
        fixtures.returnedLoan(grace, b4, TODAY.minusDays(30), TODAY.minusDays(16), TODAY.minusDays(10));

        JsonNode overdue = getJson("/api/loans/overdue");

        assertThat(overdue.isArray()).isTrue();
        assertThat(ids(overdue)).as("most overdue first; due today and returned excluded")
                .containsExactly(sixDays, yesterday);
        assertThat(ids(overdue)).doesNotContain(dueToday);
        JsonNode first = overdue.get(0);
        assertThat(first.get("daysOverdue").asLong()).isEqualTo(6);
        assertThat(first.get("overdue").asBoolean()).isTrue();
        assertThat(first.get("member").get("name").asText()).isEqualTo("Grace Hopper");
        assertThat(first.get("book").get("id").asLong()).isEqualTo(b2);
        assertThat(first.get("book").get("title").asText()).isEqualTo("Book " + TestData.isbn13(2));
        assertThat(first.get("book").get("isbn").asText()).isEqualTo(TestData.isbn13(2));
        assertThat(first.get("checkoutDate").asText()).isEqualTo(TODAY.minusDays(20).toString());
        assertThat(first.get("dueDate").asText()).isEqualTo(TODAY.minusDays(6).toString());
        assertThat(first.get("returnDate").isNull()).isTrue();
        assertThat(overdue.get(1).get("daysOverdue").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-4.BE.9 no overdue loans gives 200 and an empty array")
    void AC_4_BE_9_emptyOverdueList() throws Exception {
        JsonNode overdue = getJson("/api/loans/overdue");
        assertThat(overdue.isArray()).isTrue();
        assertThat(overdue).isEmpty();
    }

    // ---- AC-4.BE.10 ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-4.BE.10 active list: filters by member, by book and both, excludes returned, paginated, ordered by due date")
    void AC_4_BE_10_activeList() throws Exception {
        long ada = fixtures.member("Ada Lovelace").getId();
        long grace = fixtures.member("Grace Hopper").getId();
        long b1 = fixtures.book(TestData.isbn13(1), 3).getId();
        long b2 = fixtures.book(TestData.isbn13(2), 3).getId();
        long b3 = fixtures.book(TestData.isbn13(3), 3).getId();

        long adaLate = fixtures.activeLoan(ada, b1, TODAY.minusDays(3), TODAY.plusDays(11)).getId();
        long adaEarly = fixtures.activeLoan(ada, b2, TODAY.minusDays(20), TODAY.minusDays(6)).getId();
        long graceLoan = fixtures.activeLoan(grace, b1, TODAY.minusDays(1), TODAY.plusDays(13)).getId();
        fixtures.returnedLoan(grace, b3, TODAY.minusDays(30), TODAY.minusDays(16), TODAY.minusDays(10));

        JsonNode all = getJson("/api/loans/active");
        assertThat(ids(all.get("content"))).as("by due date, returned excluded")
                .containsExactly(adaEarly, adaLate, graceLoan);
        assertThat(all.get("page").asInt()).isZero();
        assertThat(all.get("size").asInt()).isEqualTo(20);
        assertThat(all.get("totalElements").asLong()).isEqualTo(3);
        assertThat(all.get("totalPages").asInt()).isEqualTo(1);
        assertThat(all.get("content").get(0).get("overdue").asBoolean()).isTrue();
        assertThat(all.get("content").get(1).get("overdue").asBoolean()).isFalse();

        assertThat(ids(getJson("/api/loans/active?memberId=" + ada).get("content")))
                .containsExactly(adaEarly, adaLate);
        assertThat(ids(getJson("/api/loans/active?bookId=" + b1).get("content")))
                .containsExactly(adaLate, graceLoan);
        assertThat(ids(getJson("/api/loans/active?memberId=" + ada + "&bookId=" + b1).get("content")))
                .containsExactly(adaLate);

        JsonNode page = getJson("/api/loans/active?size=2&page=1");
        assertThat(ids(page.get("content"))).containsExactly(graceLoan);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);

        JsonNode none = getJson("/api/loans/active?memberId=" + (grace + 1000));
        assertThat(none.get("content")).isEmpty();
        assertThat(none.get("totalElements").asLong()).isZero();
        assertThat(none.get("totalPages").asInt()).isZero();
    }

    @Test
    @DisplayName("AC-4.BE.10 invalid memberId/bookId/page/size are 400 with a field error each")
    void AC_4_BE_10_invalidParameters() throws Exception {
        MvcResult result = mvc.perform(get("/api/loans/active?memberId=0&bookId=abc&size=101")).andReturn();
        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED")))
                .containsExactly("bookId", "memberId", "size");
    }

    // ---- DR-8 ----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DR-8 the lists issue a constant number of SQL statements (no N+1)")
    void DR_8_noNPlusOne() throws Exception {
        for (int i = 1; i <= 6; i++) {
            long member = fixtures.member("Member " + i).getId();
            long book = fixtures.book(TestData.isbn13(i), 1).getId();
            fixtures.activeLoan(member, book, TODAY.minusDays(20), TODAY.minusDays(i));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        assertThat(getJson("/api/loans/overdue")).hasSize(6);
        assertThat(statistics.getPrepareStatementCount()).as("overdue list").isEqualTo(1);

        statistics.clear();
        assertThat(getJson("/api/loans/active?size=50").get("content")).hasSize(6);
        assertThat(statistics.getPrepareStatementCount()).as("active list incl. count").isLessThanOrEqualTo(2);
    }
}
