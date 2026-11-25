package com.library.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * DR-9 / ADR-30: two different Spring contexts in one JVM each get their own in-memory database, so schema.sql always
 * runs against an empty database and data cannot leak between contexts. The two nested classes have different
 * properties, hence different cached contexts that stay alive side by side.
 */
class TestDatabaseIsolationTest {

    private static final String INSERT_BOOK =
            "INSERT INTO book (title, author, isbn, total_copies) VALUES ('T', 'A', '9780132350884', 1)";

    abstract static class ContextCheck {

        @Autowired
        JdbcTemplate jdbc;

        @Autowired
        Environment environment;

        @Autowired
        DataSource dataSource;

        String jdbcUrl() throws Exception {
            try (var connection = dataSource.getConnection()) {
                return connection.getMetaData().getURL();
            }
        }

        void insertSameBookAndExpectOnlyOneRow() {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book", Integer.class))
                    .as("database starts empty").isZero();
            jdbc.update(INSERT_BOOK); // would fail with uk_book_isbn if the other context shared this database
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book", Integer.class)).isEqualTo(1);
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = "isolation.context=one")
    class ContextOne extends ContextCheck {

        @Test
        @DisplayName("DR-9 context one has its own empty database")
        void DR_9_contextOne() throws Exception {
            insertSameBookAndExpectOnlyOneRow();
            assertThat(jdbcUrl()).startsWith("jdbc:h2:mem:").doesNotContain("mem:library;");
            Holder.URL_ONE.set(jdbcUrl());
            Holder.assertDifferentIfBothKnown();
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = "isolation.context=two")
    class ContextTwo extends ContextCheck {

        @Test
        @DisplayName("DR-9 context two has its own empty database")
        void DR_9_contextTwo() throws Exception {
            insertSameBookAndExpectOnlyOneRow();
            assertThat(jdbcUrl()).startsWith("jdbc:h2:mem:").doesNotContain("mem:library;");
            Holder.URL_TWO.set(jdbcUrl());
            Holder.assertDifferentIfBothKnown();
        }
    }

    /** Remembers each context's JDBC URL so that whichever test runs second can compare them. */
    static final class Holder {
        static final java.util.concurrent.atomic.AtomicReference<String> URL_ONE =
                new java.util.concurrent.atomic.AtomicReference<>();
        static final java.util.concurrent.atomic.AtomicReference<String> URL_TWO =
                new java.util.concurrent.atomic.AtomicReference<>();

        static void assertDifferentIfBothKnown() {
            if (URL_ONE.get() != null && URL_TWO.get() != null) {
                assertThat(URL_ONE.get()).isNotEqualTo(URL_TWO.get());
            }
        }
    }
}
