package com.library.book;

import static com.library.support.ApiRequests.get;
import static com.library.support.ErrorBodyAssert.assertError;
import static com.library.support.ErrorBodyAssert.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.loan.LoanRepository;
import com.library.member.MemberRepository;
import com.library.support.LoanFixtures;
import com.library.support.TestClockConfig;
import com.library.support.TestData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;

/** US-2 / AG-31: {@code GET /api/books} through the full stack. One test per AC-2.BE.n aspect; error bodies are AC-X.1. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class BookSearchApiTest {

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

    private Book add(String title, String author, String isbn, int total) {
        return books.saveAndFlush(new Book(title, author, isbn, total));
    }

    /** {@code query} is a URI template: pass values containing %, spaces or other special characters as variables. */
    private JsonNode list(String query, Object... vars) throws Exception {
        MvcResult result = mvc.perform(get("/api/books" + query, vars)).andReturn();
        assertThat(result.getResponse().getStatus()).as("GET /api/books%s", query).isEqualTo(200);
        return JSON.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static List<String> titles(JsonNode page) {
        List<String> titles = new ArrayList<>();
        page.get("content").forEach(b -> titles.add(b.get("title").asText()));
        return titles;
    }

    private void seedCatalogue() {
        add("Clean Code", "Robert C. Martin", "9780132350884", 3);
        add("Clean Architecture", "Robert C. Martin", "9780134494166", 1);
        add("Refactoring", "Martin Fowler", "9780134757599", 2);
        add("Spring in Action", "Craig Walls", "9781617294945", 1);
    }

    // ---- AC-2.BE.1 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-2.BE.1 title filter: case-insensitive contains")
    void AC_2_BE_1_titleFilter() throws Exception {
        seedCatalogue();

        assertThat(titles(list("?title=cLeAn"))).containsExactly("Clean Architecture", "Clean Code");
        assertThat(titles(list("?title={t}", "in act"))).containsExactly("Spring in Action");
    }

    @Test
    @DisplayName("AC-2.BE.1 author filter: case-insensitive contains")
    void AC_2_BE_1_authorFilter() throws Exception {
        seedCatalogue();

        assertThat(titles(list("?author=MARTIN"))).containsExactly("Clean Architecture", "Clean Code", "Refactoring");
    }

    @Test
    @DisplayName("AC-2.BE.1 ISBN filter matches regardless of hyphens and spaces")
    void AC_2_BE_1_isbnFilterIgnoresHyphensAndSpaces() throws Exception {
        seedCatalogue();

        assertThat(titles(list("?isbn=978-0-13-235088-4"))).containsExactly("Clean Code");
        assertThat(titles(list("?isbn={i}", "978 0 13"))).containsExactly("Clean Architecture", "Clean Code", "Refactoring");
        assertThat(titles(list("?isbn=0132350"))).containsExactly("Clean Code");
    }

    @Test
    @DisplayName("AC-2.BE.1 an ISBN-10 with check digit X is found by a lower-case 'x' filter")
    void AC_2_BE_1_isbnFilterLowerCaseX() throws Exception {
        add("Ten", "a", "080442957X", 1);

        assertThat(titles(list("?isbn=0-8044-2957-x"))).containsExactly("Ten");
    }

    @Test
    @DisplayName("AC-2.BE.1 filters combine with AND")
    void AC_2_BE_1_filtersCombine() throws Exception {
        seedCatalogue();

        assertThat(titles(list("?title=clean&author=martin&isbn=9780134"))).containsExactly("Clean Architecture");
        assertThat(titles(list("?title=clean&author=fowler"))).isEmpty();
    }

    @Test
    @DisplayName("AC-2.BE.1 a '%' in a filter is a literal character, it does not match everything")
    void AC_2_BE_1_percentIsLiteral() throws Exception {
        add("100% Java", "a", "9780132350884", 1);
        add("Plain", "a", "9780134757599", 1);

        assertThat(titles(list("?title={t}", "%"))).containsExactly("100% Java");
        assertThat(titles(list("?title={t}", "%%"))).isEmpty();
        assertThat(titles(list("?author=_"))).isEmpty();
    }

    @Test
    @DisplayName("AC-2.BE.1 filter text is trimmed; blank or absent filters mean no filter")
    void AC_2_BE_1_blankFiltersAreIgnored() throws Exception {
        seedCatalogue();

        assertThat(titles(list("?title={t}", "  clean "))).containsExactly("Clean Architecture", "Clean Code");
        assertThat(list("?title=&author={a}&isbn=&availability=", " ").get("totalElements").asInt()).isEqualTo(4);
        assertThat(list("?isbn=---").get("totalElements").asInt()).as("ISBN that normalises to nothing").isEqualTo(4);
    }

    @Test
    @DisplayName("AC-2.BE.1 unknown query parameters are ignored")
    void AC_2_BE_1_unknownParametersIgnored() throws Exception {
        seedCatalogue();

        assertThat(list("?foo=bar&sort=title,desc&title=refactoring").get("totalElements").asInt()).isEqualTo(1);
    }

    // ---- AC-2.BE.2 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-2.BE.2 availability=available / unavailable / absent")
    void AC_2_BE_2_availability() throws Exception {
        seedCatalogue();
        long member = fixtures.member("Ada Lovelace").getId();
        long cleanArchitecture = books.findByIsbn("9780134494166").orElseThrow().getId();
        long cleanCode = books.findByIsbn("9780132350884").orElseThrow().getId();
        fixtures.activeLoan(member, cleanArchitecture, TODAY, TODAY.plusDays(14)); // 1 of 1 -> all out
        fixtures.activeLoan(member, cleanCode, TODAY, TODAY.plusDays(14)); // 1 of 3 -> still available

        assertThat(titles(list("?availability=unavailable"))).containsExactly("Clean Architecture");
        assertThat(titles(list("?availability=available"))).containsExactly("Clean Code", "Refactoring", "Spring in Action");
        assertThat(list("").get("totalElements").asInt()).isEqualTo(4);
        assertThat(titles(list("?availability=available&title=clean"))).containsExactly("Clean Code");
    }

    @Test
    @DisplayName("AC-2.BE.2 an unknown availability value is 400 with a field error on availability")
    void AC_2_BE_2_badAvailability() throws Exception {
        MvcResult result = mvc.perform(get("/api/books?availability=foo")).andReturn();

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly("availability");
    }

    @Test
    @DisplayName("AC-2.BE.2 availability values are exact: 'AVAILABLE' (upper case) is rejected")
    void AC_2_BE_2_availabilityIsCaseSensitive() throws Exception {
        MvcResult result = mvc.perform(get("/api/books?availability=AVAILABLE")).andReturn();

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly("availability");
    }

    // ---- AC-2.BE.3 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-2.BE.3 page, size, totals and the zero-based page index")
    void AC_2_BE_3_paging() throws Exception {
        for (int i = 0; i < 25; i++) {
            add(String.format("Book %02d", i), "a", TestData.isbn13(i), 1);
        }

        JsonNode first = list("?page=0&size=10");
        assertThat(first.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");
        assertThat(first.get("content")).hasSize(10);
        assertThat(first.get("page").asInt()).isZero();
        assertThat(first.get("size").asInt()).isEqualTo(10);
        assertThat(first.get("totalElements").asInt()).isEqualTo(25);
        assertThat(first.get("totalPages").asInt()).isEqualTo(3);
        assertThat(titles(first).get(0)).isEqualTo("Book 00");

        JsonNode last = list("?page=2&size=10");
        assertThat(last.get("content")).hasSize(5);
        assertThat(titles(last).get(0)).isEqualTo("Book 20");
        assertThat(last.get("page").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-2.BE.3 default page size is 20 and size 100 is accepted")
    void AC_2_BE_3_defaultsAndMaximum() throws Exception {
        for (int i = 0; i < 25; i++) {
            add(String.format("Book %02d", i), "a", TestData.isbn13(i), 1);
        }

        JsonNode defaults = list("");
        assertThat(defaults.get("size").asInt()).isEqualTo(20);
        assertThat(defaults.get("page").asInt()).isZero();
        assertThat(defaults.get("content")).hasSize(20);
        assertThat(list("?size=100").get("content")).hasSize(25);
    }

    @Test
    @DisplayName("AC-2.BE.3 a page beyond the last page is 200 with empty content and the real totals")
    void AC_2_BE_3_pageBeyondEnd() throws Exception {
        seedCatalogue();

        JsonNode beyond = list("?page=5&size=10");

        assertThat(beyond.get("content")).isEmpty();
        assertThat(beyond.get("totalElements").asInt()).isEqualTo(4);
        assertThat(beyond.get("totalPages").asInt()).isEqualTo(1);
    }

    @ParameterizedTest(name = "[{index}] ?{0}")
    @ValueSource(strings = {"size=101", "size=0", "size=-1", "size=abc", "size=", "size=2.5", "size=99999999999"})
    @DisplayName("AC-2.BE.3 an invalid size is 400 VALIDATION_FAILED with a field error on size")
    void AC_2_BE_3_invalidSize(String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/books?" + query)).andReturn();

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly("size");
    }

    @ParameterizedTest(name = "[{index}] ?{0}")
    @ValueSource(strings = {"page=-1", "page=abc", "page=1.5", "page="})
    @DisplayName("AC-2.BE.3 an invalid page is 400 VALIDATION_FAILED with a field error on page")
    void AC_2_BE_3_invalidPage(String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/books?" + query)).andReturn();

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly("page");
    }

    @Test
    @DisplayName("AC-2.BE.3 every bad parameter is named in one 400 (paging and filters together)")
    void AC_2_BE_3_allBadParametersNamed() throws Exception {
        MvcResult result = mvc.perform(get("/api/books?page=-1&size=101&availability=x&title=" + "t".repeat(256))).andReturn();

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED")))
                .containsExactly("availability", "page", "size", "title");
    }

    // ---- AC-2.BE.4 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-2.BE.4 no filters: all books, ordered by title ignoring case, then ISBN, identically on every call")
    void AC_2_BE_4_stableOrder() throws Exception {
        add("banana", "a", "9780134757599", 1);
        add("Zebra", "a", "9780132350884", 1);
        add("Apple", "a", "9781617294945", 1);
        add("apple", "a", "9780134494166", 1);
        add("Apple", "a", "080442957X", 1);

        JsonNode first = list("");
        List<String> isbns = new ArrayList<>();
        first.get("content").forEach(b -> isbns.add(b.get("isbn").asText()));

        assertThat(titles(first)).containsExactly("Apple", "apple", "Apple", "banana", "Zebra");
        assertThat(isbns).containsExactly("080442957X", "9780134494166", "9781617294945", "9780134757599", "9780132350884");
        assertThat(list("")).isEqualTo(first);
    }

    @Test
    @DisplayName("AC-2.BE.4 paging through equal titles never repeats or skips a book")
    void AC_2_BE_4_pagingIsStable() throws Exception {
        for (int i = 0; i < 12; i++) {
            add("Same title", "a", TestData.isbn13(i), 1);
        }

        List<String> seen = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            list("?size=5&page=" + page).get("content").forEach(b -> seen.add(b.get("isbn").asText()));
        }

        assertThat(seen).hasSize(12).doesNotHaveDuplicates().isSorted();
    }

    // ---- AC-2.BE.5 / AC-2.BE.6 -----------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-2.BE.5 filters that match nothing give 200 with an empty page and totals 0")
    void AC_2_BE_5_emptyResult() throws Exception {
        seedCatalogue();

        JsonNode empty = list("?title=zzz");

        assertThat(empty.get("content")).isEmpty();
        assertThat(empty.get("totalElements").asInt()).isZero();
        assertThat(empty.get("totalPages").asInt()).isZero();
        assertThat(empty.get("page").asInt()).isZero();
    }

    @Test
    @DisplayName("AC-2.BE.5 an empty catalogue is 200 with an empty page")
    void AC_2_BE_5_emptyCatalogue() throws Exception {
        JsonNode empty = list("");

        assertThat(empty.get("content")).isEmpty();
        assertThat(empty.get("totalElements").asInt()).isZero();
        assertThat(empty.get("totalPages").asInt()).isZero();
    }

    @Test
    @DisplayName("AC-2.BE.6 every book carries totalCopies and availableCopies (after a loan)")
    void AC_2_BE_6_copyCounts() throws Exception {
        long book = add("Clean Code", "a", "9780132350884", 3).getId();
        long member = fixtures.member("Ada Lovelace").getId();
        fixtures.activeLoan(member, book, TODAY, TODAY.plusDays(14));

        JsonNode item = list("").get("content").get(0);

        assertThat(item.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "title", "author", "isbn", "totalCopies", "availableCopies");
        assertThat(item.get("totalCopies").asInt()).isEqualTo(3);
        assertThat(item.get("availableCopies").asInt()).isEqualTo(2);
    }

    // ---- DR-18 ---------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"title", "author", "isbn"})
    @DisplayName("DR-18 a text filter of 256 characters is 400 with a field error on that filter; 255 is accepted")
    void DR_18_filterLengthLimit(String filter) throws Exception {
        MvcResult tooLong = mvc.perform(get("/api/books?" + filter + "=" + "1".repeat(256))).andReturn();
        assertThat(fieldNames(assertError(tooLong, 400, "VALIDATION_FAILED"))).containsExactly(filter);

        assertThat(list("?" + filter + "=" + "1".repeat(255)).get("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("AC-X.1 DELETE on the list route is 405 in the standard body")
    void AC_X_1_deleteOnListRouteIs405() throws Exception {
        MvcResult result = mvc.perform(com.library.support.ApiRequests.delete("/api/books")).andReturn();

        assertError(result, 405, "METHOD_NOT_ALLOWED");
    }
}
