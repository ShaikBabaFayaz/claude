package com.library.seed;

import static com.library.support.ApiRequests.get;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.support.TestClockConfig;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AC-X.4 / FR-24: with seeding switched on and a fixed clock, the start-up data is visible through the API: 10 books,
 * 5 members, 3 active loans, exactly one overdue (6 days), the due-today loan is not overdue, one book is fully out, and
 * the counters match the loans. Seeding is off in all other tests (test application.yml).
 */
@SpringBootTest(properties = "library.demo-data.enabled=true")
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class DemoDataSeederTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate TODAY = TestClockConfig.FIXED_DATE;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private JsonNode getJson(String url) throws Exception {
        MvcResult result = mvc.perform(get(url)).andReturn();
        assertThat(result.getResponse().getStatus()).as("GET %s", url).isEqualTo(200);
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("AC-X.4 demo data: 10 books, 5 members, 3 active loans, one overdue by 6 days, one book with all copies out")
    void AC_X_4_demoDataIsLoadedWithConsistentCounters() throws Exception {
        assertThat(getJson("/api/books?size=100").get("totalElements").asLong()).isEqualTo(10);
        assertThat(getJson("/api/members?size=100").get("totalElements").asLong()).isEqualTo(5);

        JsonNode active = getJson("/api/loans/active?size=100");
        assertThat(active.get("totalElements").asLong()).isEqualTo(3);
        // ordered by due date: overdue (today-6), due today, due today+11
        assertThat(active.get("content").get(0).get("dueDate").asText()).isEqualTo(TODAY.minusDays(6).toString());
        assertThat(active.get("content").get(1).get("dueDate").asText()).isEqualTo(TODAY.toString());
        assertThat(active.get("content").get(1).get("overdue").asBoolean()).as("due today is not overdue").isFalse();
        assertThat(active.get("content").get(2).get("dueDate").asText()).isEqualTo(TODAY.plusDays(11).toString());

        JsonNode overdue = getJson("/api/loans/overdue");
        assertThat(overdue).hasSize(1);
        assertThat(overdue.get(0).get("daysOverdue").asLong()).isEqualTo(6);

        JsonNode soldOut = getJson("/api/books?availability=unavailable");
        assertThat(soldOut.get("totalElements").asLong()).isEqualTo(1);
        assertThat(soldOut.get("content").get(0).get("availableCopies").asInt()).isZero();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE status <> 'ACTIVE'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book b WHERE b.active_loan_count <> "
                + "(SELECT COUNT(*) FROM loan l WHERE l.book_id = b.id AND l.return_date IS NULL)", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member m WHERE m.active_loan_count <> "
                + "(SELECT COUNT(*) FROM loan l WHERE l.member_id = m.id AND l.return_date IS NULL)", Long.class)).isZero();
    }
}
