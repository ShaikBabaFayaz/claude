package com.library.book;

import static com.library.support.ApiRequests.delete;
import static com.library.support.ApiRequests.get;
import static com.library.support.ApiRequests.postJson;
import static com.library.support.ApiRequests.putJson;
import static com.library.support.ErrorBodyAssert.assertError;
import static com.library.support.ErrorBodyAssert.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.loan.LoanRepository;
import com.library.member.MemberRepository;
import com.library.support.LoanFixtures;
import com.library.support.SharedVectors;
import com.library.support.TestClockConfig;
import com.library.support.TestData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * US-1 / AG-29: one test (or parameterised group) per AC-1.BE.n, through the full stack with MockMvc and the real H2
 * database. Not transactional: every request commits, as in production; the tables are emptied before each test.
 * Every error response is checked against the AC-X.1 body shape.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class BookApiTest {

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

    private LoanFixtures fixtures;

    @BeforeEach
    void cleanDatabase() {
        fixtures = new LoanFixtures(books, members, loans, jdbc, transactionManager);
        fixtures.deleteAll();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private MvcResult create(String json) throws Exception {
        return mvc.perform(postJson("/api/books", json)).andReturn();
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String json(Object title, Object author, Object isbn, Object totalCopies) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", title);
        map.put("author", author);
        map.put("isbn", isbn);
        map.put("totalCopies", totalCopies);
        return JSON.writeValueAsString(map);
    }

    private long createBook(String isbn, int total) throws Exception {
        MvcResult result = create(TestData.bookJson("Clean Code", "Robert C. Martin", isbn, total));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).get("id").asLong();
    }

    // ---- AC-1.BE.1 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-1.BE.1 create returns 201, a generated id, Location, and available copies = total copies")
    void AC_1_BE_1_createReturnsIdAndAvailableEqualsTotal() throws Exception {
        MvcResult result = create(TestData.bookJson("Clean Code", "Robert C. Martin", "9780132350884", 3));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode created = body(result);
        assertThat(created.get("id").asLong()).isPositive();
        assertThat(created.get("title").asText()).isEqualTo("Clean Code");
        assertThat(created.get("author").asText()).isEqualTo("Robert C. Martin");
        assertThat(created.get("isbn").asText()).isEqualTo("9780132350884");
        assertThat(created.get("totalCopies").asInt()).isEqualTo(3);
        assertThat(created.get("availableCopies").asInt()).isEqualTo(3);
        assertThat(created.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "title", "author", "isbn", "totalCopies", "availableCopies");
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/books/" + created.get("id").asLong());

        Book stored = books.findById(created.get("id").asLong()).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("Clean Code");
        assertThat(stored.getActiveLoanCount()).isZero();
    }

    @Test
    @DisplayName("AC-1.BE.1 the ISBN is stored and returned in normalised form")
    void AC_1_BE_1_isbnIsNormalised() throws Exception {
        MvcResult result = create(TestData.bookJson("Title", "Author", " 978-0-13-235088-4 ", 1));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result).get("isbn").asText()).isEqualTo("9780132350884");
        assertThat(books.findByIsbn("9780132350884")).isPresent();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("com.library.book.BookApiTest#validIsbnVectors")
    @DisplayName("AC-1.BE.1 / AC-1.BE.3 every shared valid ISBN vector is accepted and stored normalised")
    void AC_1_BE_3_sharedValidVectorsAreAccepted(SharedVectors.Vector vector) throws Exception {
        MvcResult result = create(TestData.bookJson("Title", "Author", vector.input(), 1));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result).get("isbn").asText()).isEqualTo(vector.normalized());
    }

    static Stream<SharedVectors.Vector> validIsbnVectors() {
        return SharedVectors.isbnValid().stream();
    }

    static Stream<SharedVectors.Vector> invalidIsbnVectors() {
        return SharedVectors.isbnInvalid().stream();
    }

    // ---- AC-1.BE.2 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-1.BE.2 an empty body object names every required field and stores nothing")
    void AC_1_BE_2_missingFieldsAreAllReported() throws Exception {
        MvcResult result = create("{}");

        JsonNode error = assertError(result, 400, "VALIDATION_FAILED");
        assertThat(fieldNames(error)).contains("title", "author", "isbn", "totalCopies").doesNotHaveDuplicates();
        assertThat(books.count()).isZero();
    }

    @Test
    @DisplayName("AC-1.BE.2 blank title, author and ISBN (spaces only) are rejected, each field named")
    void AC_1_BE_2_blankFieldsAreRejected() throws Exception {
        MvcResult result = create(json("   ", "\t ", "  ", 1));

        JsonNode error = assertError(result, 400, "VALIDATION_FAILED");
        assertThat(fieldNames(error)).contains("title", "author", "isbn").doesNotContain("totalCopies");
        assertThat(books.count()).isZero();
    }

    @ParameterizedTest(name = "[{index}] totalCopies={0}")
    @MethodSource("com.library.book.BookApiTest#badTotalCopies")
    @DisplayName("AC-1.BE.2 total copies outside 1..999, fractional, textual or null is rejected on totalCopies")
    void AC_1_BE_2_badTotalCopiesIsRejected(Object totalCopies) throws Exception {
        MvcResult result = create(json("Title", "Author", "9780132350884", totalCopies));

        JsonNode error = assertError(result, 400, "VALIDATION_FAILED");
        assertThat(fieldNames(error)).containsExactly("totalCopies");
        assertThat(books.count()).isZero();
    }

    static Stream<Object> badTotalCopies() {
        return java.util.Arrays.<Object>asList(0, 1000, -1, 2.5, "5", "abc", true, null).stream();
    }

    @Test
    @DisplayName("AC-1.BE.2 a missing totalCopies property is rejected on totalCopies")
    void AC_1_BE_2_missingTotalCopies() throws Exception {
        MvcResult result = create("{\"title\":\"T\",\"author\":\"A\",\"isbn\":\"9780132350884\"}");

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly("totalCopies");
    }

    @Test
    @DisplayName("AC-1.BE.2 title or author longer than 255 characters is rejected, naming each field")
    void AC_1_BE_2_titleAndAuthorTooLong() throws Exception {
        MvcResult result = create(json("t".repeat(256), "a".repeat(256), "9780132350884", 1));

        JsonNode error = assertError(result, 400, "VALIDATION_FAILED");
        assertThat(fieldNames(error)).containsExactly("author", "title");
        assertThat(books.count()).isZero();
    }

    @Test
    @DisplayName("AC-1.BE.2 / DR-15 255 characters of title padded with spaces are accepted and stored trimmed")
    void AC_1_BE_2_paddedMaximumLengthTitleIsAccepted() throws Exception {
        String title = "t".repeat(255);
        MvcResult result = create(json("  " + title + "  ", " " + "a".repeat(255) + " ", "9780132350884", 1));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result).get("title").asText()).isEqualTo(title);
        assertThat(books.findByIsbn("9780132350884").orElseThrow().getTitle()).hasSize(255);
    }

    @Test
    @DisplayName("AC-1.BE.2 title and author are stored trimmed")
    void AC_1_BE_2_titleAndAuthorAreTrimmed() throws Exception {
        MvcResult result = create(json("  Clean Code ", " Robert C. Martin\t", "9780132350884", 1));

        JsonNode created = body(result);
        assertThat(created.get("title").asText()).isEqualTo("Clean Code");
        assertThat(created.get("author").asText()).isEqualTo("Robert C. Martin");
    }

    @Test
    @DisplayName("AC-1.BE.2 a non-text title (number) is a 400 field error, not a silent conversion")
    void AC_1_BE_2_numericTitleIsRejected() throws Exception {
        MvcResult result = create("{\"title\":123,\"author\":\"A\",\"isbn\":\"9780132350884\",\"totalCopies\":1}");

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly("title");
    }

    @Test
    @DisplayName("AC-1.BE.2 update validates the same rules and leaves the book unchanged")
    void AC_1_BE_2_updateIsValidatedToo() throws Exception {
        long id = createBook("9780132350884", 3);

        MvcResult result = mvc.perform(putJson("/api/books/" + id, json("", "A", "bad", 0))).andReturn();

        JsonNode error = assertError(result, 400, "VALIDATION_FAILED");
        assertThat(fieldNames(error)).contains("title", "isbn", "totalCopies");
        Book stored = books.findById(id).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("Clean Code");
        assertThat(stored.getTotalCopies()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-X.1 a body that is not JSON is 400 MALFORMED_REQUEST")
    void AC_X_1_malformedJson() throws Exception {
        assertError(create("{not json"), 400, "MALFORMED_REQUEST");
        assertThat(books.count()).isZero();
    }

    // ---- AC-1.BE.3 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-1.BE.3 an ISBN with a wrong check digit is rejected on the isbn field")
    void AC_1_BE_3_wrongCheckDigit() throws Exception {
        MvcResult result = create(TestData.bookJson("T", "A", "9780132350885", 1));

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly("isbn");
        assertThat(books.count()).isZero();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("com.library.book.BookApiTest#invalidIsbnVectors")
    @DisplayName("AC-1.BE.3 every shared invalid ISBN vector is rejected on the isbn field")
    void AC_1_BE_3_sharedInvalidVectorsAreRejected(SharedVectors.Vector vector) throws Exception {
        MvcResult result = create(json("T", "A", vector.input(), 1));

        JsonNode error = assertError(result, 400, "VALIDATION_FAILED");
        assertThat(fieldNames(error)).contains("isbn");
        assertThat(books.count()).isZero();
    }

    @Test
    @DisplayName("AC-1.BE.3 update to an invalid ISBN is rejected on isbn")
    void AC_1_BE_3_updateWithInvalidIsbn() throws Exception {
        long id = createBook("9780132350884", 1);

        MvcResult result = mvc.perform(putJson("/api/books/" + id, TestData.bookJson("T", "A", "123", 1))).andReturn();

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly("isbn");
        assertThat(books.findById(id).orElseThrow().getIsbn()).isEqualTo("9780132350884");
    }

    // ---- AC-1.BE.4 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-1.BE.4 a duplicate ISBN (hyphenated vs plain) is 409 ISBN_ALREADY_EXISTS with fieldErrors[isbn]")
    void AC_1_BE_4_duplicateIsbnAfterNormalisation() throws Exception {
        createBook("9780132350884", 1);

        MvcResult result = create(TestData.bookJson("Other", "Other", "978-0-13-235088-4", 2));

        JsonNode error = assertError(result, 409, "ISBN_ALREADY_EXISTS");
        assertThat(fieldNames(error)).containsExactly("isbn");
        assertThat(books.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-1.BE.4 updating a different book to an existing ISBN is 409 and changes nothing")
    void AC_1_BE_4_updateToExistingIsbn() throws Exception {
        createBook("9780132350884", 1);
        long other = createBook("9781617294945", 1);

        MvcResult result = mvc.perform(
                putJson("/api/books/" + other, TestData.bookJson("Changed", "Changed", "978 0 13 235088 4", 5))).andReturn();

        assertThat(fieldNames(assertError(result, 409, "ISBN_ALREADY_EXISTS"))).containsExactly("isbn");
        Book stored = books.findById(other).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("Clean Code");
        assertThat(stored.getIsbn()).isEqualTo("9781617294945");
        assertThat(stored.getTotalCopies()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-1.BE.4 updating a book while keeping its own ISBN succeeds (also written hyphenated)")
    void AC_1_BE_4_ownIsbnUpdateSucceeds() throws Exception {
        long id = createBook("9780132350884", 1);

        MvcResult result = mvc.perform(
                putJson("/api/books/" + id, TestData.bookJson("New title", "New author", "978-0-13-235088-4", 4))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode updated = body(result);
        assertThat(updated.get("title").asText()).isEqualTo("New title");
        assertThat(updated.get("isbn").asText()).isEqualTo("9780132350884");
        assertThat(updated.get("totalCopies").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-1.BE.4 / Q-A1 an ISBN-10 and its ISBN-13 equivalent are two distinct, both storable ISBNs")
    void AC_1_BE_4_isbn10AndEquivalentIsbn13AreDistinct() throws Exception {
        assertThat(create(TestData.bookJson("A", "A", TestData.ISBN_10_B, 1)).getResponse().getStatus()).isEqualTo(201);
        assertThat(create(TestData.bookJson("B", "B", TestData.ISBN_13_B, 1)).getResponse().getStatus()).isEqualTo(201);

        assertThat(books.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-1.BE.4 two parallel creates with the same ISBN give exactly one 201 and one 409 (uk_book_isbn)")
    void AC_1_BE_4_parallelCreatesWithSameIsbn() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 15; i++) {
                String isbn = TestData.isbn13(i);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> futures = new ArrayList<>();
                for (int t = 0; t < 2; t++) {
                    Callable<Integer> call = () -> {
                        start.await();
                        MvcResult r = create(TestData.bookJson("Race", "Race", isbn, 1));
                        if (r.getResponse().getStatus() == 409) {
                            assertError(r, 409, "ISBN_ALREADY_EXISTS");
                        }
                        return r.getResponse().getStatus();
                    };
                    futures.add(pool.submit(call));
                }
                start.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> f : futures) {
                    statuses.add(f.get(30, TimeUnit.SECONDS));
                }
                assertThat(statuses).as("iteration %d", i).containsExactlyInAnyOrder(201, 409);
                assertThat(books.findByIsbn(isbn)).isPresent();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(books.count()).isEqualTo(15);
    }

    // ---- AC-1.BE.5 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-1.BE.5 get returns the details and the current available copies")
    void AC_1_BE_5_getReturnsDetailsAndAvailability() throws Exception {
        long id = createBook("9780132350884", 3);
        long member = fixtures.member("Ada Lovelace").getId();
        fixtures.activeLoan(member, id, TODAY, TODAY.plusDays(14));

        MvcResult result = mvc.perform(get("/api/books/" + id)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode found = body(result);
        assertThat(found.get("id").asLong()).isEqualTo(id);
        assertThat(found.get("totalCopies").asInt()).isEqualTo(3);
        assertThat(found.get("availableCopies").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-1.BE.5 get of an unknown id is 404 BOOK_NOT_FOUND")
    void AC_1_BE_5_getUnknown() throws Exception {
        assertError(mvc.perform(get("/api/books/987654")).andReturn(), 404, "BOOK_NOT_FOUND");
    }

    @Test
    @DisplayName("AC-1.BE.5 update of an unknown id is 404 BOOK_NOT_FOUND")
    void AC_1_BE_5_updateUnknown() throws Exception {
        MvcResult result = mvc.perform(putJson("/api/books/987654", TestData.validBookJson("9780132350884"))).andReturn();

        assertError(result, 404, "BOOK_NOT_FOUND");
        assertThat(books.count()).isZero();
    }

    @Test
    @DisplayName("AC-1.BE.5 delete of an unknown id is 404 BOOK_NOT_FOUND")
    void AC_1_BE_5_deleteUnknown() throws Exception {
        assertError(mvc.perform(delete("/api/books/987654")).andReturn(), 404, "BOOK_NOT_FOUND");
    }

    @Test
    @DisplayName("AC-1.BE.5 a non-numeric id is a 400 field error on id (AC-X.1 shape)")
    void AC_1_BE_5_nonNumericId() throws Exception {
        MvcResult result = mvc.perform(get("/api/books/abc")).andReturn();

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly("id");
    }

    // ---- AC-1.BE.6 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-1.BE.6 total copies below the active loans is 400 TOTAL_COPIES_BELOW_ACTIVE_LOANS on totalCopies")
    void AC_1_BE_6_belowActiveLoansIsRejected() throws Exception {
        long id = createBook("9780132350884", 5);
        long m1 = fixtures.member("Ada Lovelace").getId();
        long m2 = fixtures.member("Grace Hopper").getId();
        fixtures.activeLoan(m1, id, TODAY, TODAY.plusDays(14));
        fixtures.activeLoan(m2, id, TODAY, TODAY.plusDays(14));

        MvcResult result = mvc.perform(putJson("/api/books/" + id, TestData.bookJson("Clean Code", "RCM", "9780132350884", 1)))
                .andReturn();

        JsonNode error = assertError(result, 400, "TOTAL_COPIES_BELOW_ACTIVE_LOANS");
        assertThat(fieldNames(error)).containsExactly("totalCopies");
        assertThat(books.findById(id).orElseThrow().getTotalCopies()).isEqualTo(5);
    }

    @Test
    @DisplayName("AC-1.BE.6 total copies equal to or above the active loans succeeds and available is recalculated")
    void AC_1_BE_6_atOrAboveActiveLoansSucceeds() throws Exception {
        long id = createBook("9780132350884", 5);
        long m1 = fixtures.member("Ada Lovelace").getId();
        long m2 = fixtures.member("Grace Hopper").getId();
        fixtures.activeLoan(m1, id, TODAY, TODAY.plusDays(14));
        fixtures.activeLoan(m2, id, TODAY, TODAY.plusDays(14));

        JsonNode equal = body(mvc.perform(putJson("/api/books/" + id, TestData.bookJson("Clean Code", "RCM", "9780132350884", 2)))
                .andReturn());
        assertThat(equal.get("totalCopies").asInt()).isEqualTo(2);
        assertThat(equal.get("availableCopies").asInt()).isZero();

        JsonNode above = body(mvc.perform(putJson("/api/books/" + id, TestData.bookJson("Clean Code", "RCM", "9780132350884", 10)))
                .andReturn());
        assertThat(above.get("availableCopies").asInt()).isEqualTo(8);
        assertThat(books.findById(id).orElseThrow().getActiveLoanCount()).as("counter untouched by an edit").isEqualTo(2);
    }

    // ---- AC-1.BE.7 / AC-1.BE.8 -----------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-1.BE.7 deleting a book with an active loan is 409 BOOK_HAS_ACTIVE_LOANS and the book remains")
    void AC_1_BE_7_deleteWithActiveLoanIsRejected() throws Exception {
        long id = createBook("9780132350884", 2);
        long member = fixtures.member("Ada Lovelace").getId();
        fixtures.activeLoan(member, id, TODAY, TODAY.plusDays(14));

        assertError(mvc.perform(delete("/api/books/" + id)).andReturn(), 409, "BOOK_HAS_ACTIVE_LOANS");

        assertThat(books.existsById(id)).isTrue();
        assertThat(fixtures.countLoans()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-1.BE.8 deleting a book with only returned loans is 204, removes its loans, and later GET is 404")
    void AC_1_BE_8_deleteWithReturnedLoansOnly() throws Exception {
        long id = createBook("9780132350884", 2);
        long other = createBook("9781617294945", 2);
        long member = fixtures.member("Ada Lovelace").getId();
        fixtures.returnedLoan(member, id, TODAY.minusDays(20), TODAY.minusDays(6), TODAY.minusDays(7));
        fixtures.returnedLoan(member, id, TODAY.minusDays(30), TODAY.minusDays(16), TODAY.minusDays(18));
        fixtures.returnedLoan(member, other, TODAY.minusDays(10), TODAY.plusDays(4), TODAY.minusDays(1));

        MvcResult result = mvc.perform(delete("/api/books/" + id)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(books.existsById(id)).isFalse();
        assertThat(fixtures.countLoans()).as("only the other book's loan remains").isEqualTo(1);
        assertError(mvc.perform(get("/api/books/" + id)).andReturn(), 404, "BOOK_NOT_FOUND");
        assertThat(books.existsById(other)).isTrue();
    }

    @Test
    @DisplayName("AC-1.BE.8 deleting a book that never had a loan is 204")
    void AC_1_BE_8_deleteWithoutLoans() throws Exception {
        long id = createBook("9780132350884", 1);

        assertThat(mvc.perform(delete("/api/books/" + id)).andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(books.count()).isZero();
    }

    // ---- AC-X.1 --------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-X.1 a mutating request without the required header is 403 MISSING_REQUIRED_HEADER in the same body")
    void AC_X_1_missingRequiredHeader() throws Exception {
        MvcResult result = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/books")
                        .header("Host", "localhost:8080")
                        .contentType("application/json")
                        .content(TestData.validBookJson("9780132350884"))).andReturn();

        assertError(result, 403, "MISSING_REQUIRED_HEADER");
        assertThat(books.count()).isZero();
    }

}
