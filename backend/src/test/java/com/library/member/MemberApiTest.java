package com.library.member;

import static com.library.support.ApiRequests.get;
import static com.library.support.ApiRequests.post;
import static com.library.support.ApiRequests.postJson;
import static com.library.support.ApiRequests.putJson;
import static com.library.support.ErrorBodyAssert.assertError;
import static com.library.support.ErrorBodyAssert.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.book.BookRepository;
import com.library.loan.LoanRepository;
import com.library.support.LoanFixtures;
import com.library.support.SharedVectors;
import com.library.support.TestClockConfig;
import com.library.support.TestData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * US-3 / AG-33: one test (or parameterised group) per AC-3.BE.n, through the full stack with MockMvc and the real H2
 * database. Not transactional: every request commits, as in production; the tables are emptied before each test.
 * Every error response is checked against the AC-X.1 body shape.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class MemberApiTest {

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

    private MvcResult register(String name, String email) throws Exception {
        return mvc.perform(postJson("/api/members", TestData.memberJson(name, email))).andReturn();
    }

    private long registerOk(String name, String email) throws Exception {
        MvcResult result = register(name, email);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).get("id").asLong();
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode list(String query, Object... vars) throws Exception {
        MvcResult result = mvc.perform(get("/api/members" + query, vars)).andReturn();
        assertThat(result.getResponse().getStatus()).as("GET /api/members%s", query).isEqualTo(200);
        return body(result);
    }

    private static List<String> names(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("content").forEach(m -> names.add(m.get("name").asText()));
        return names;
    }

    private MvcResult update(long id, String name, String email) throws Exception {
        return mvc.perform(putJson("/api/members/" + id, TestData.memberJson(name, email))).andReturn();
    }

    private MvcResult action(long id, String action) throws Exception {
        return mvc.perform(post("/api/members/" + id + "/" + action)).andReturn();
    }

    // ---- AC-3.BE.1 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-3.BE.1 register returns 201, a generated id, status ACTIVE, 0 active loans and a Location")
    void AC_3_BE_1_registerReturnsActiveMember() throws Exception {
        MvcResult result = register("Ada Lovelace", "ada@example.org");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode created = body(result);
        assertThat(created.get("id").asLong()).isPositive();
        assertThat(created.get("name").asText()).isEqualTo("Ada Lovelace");
        assertThat(created.get("email").asText()).isEqualTo("ada@example.org");
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.get("activeLoanCount").asInt()).isZero();
        assertThat(created.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "name", "email", "status", "activeLoanCount");
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/members/" + created.get("id").asLong());

        Member stored = members.findById(created.get("id").asLong()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(stored.getEmailNormalized()).isEqualTo("ada@example.org");
    }

    @Test
    @DisplayName("AC-3.BE.1 the email is stored and returned as entered (trimmed); the unique key is its lower-case form")
    void AC_3_BE_1_emailKeptAsEnteredKeyLowerCased() throws Exception {
        MvcResult result = register("  Ada Lovelace ", "  Ada.Lovelace@Example.ORG ");

        JsonNode created = body(result);
        assertThat(created.get("name").asText()).isEqualTo("Ada Lovelace");
        assertThat(created.get("email").asText()).isEqualTo("Ada.Lovelace@Example.ORG");
        assertThat(members.findById(created.get("id").asLong()).orElseThrow().getEmailNormalized())
                .isEqualTo("ada.lovelace@example.org");
    }

    // ---- AC-3.BE.2 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-3.BE.2 an empty body object names name and email, and stores nothing")
    void AC_3_BE_2_missingFields() throws Exception {
        MvcResult result = mvc.perform(postJson("/api/members", "{}")).andReturn();

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).contains("name", "email");
        assertThat(members.count()).isZero();
    }

    @Test
    @DisplayName("AC-3.BE.2 blank (whitespace-only) name and email are rejected, each field named")
    void AC_3_BE_2_blankFields() throws Exception {
        MvcResult result = register("   ", "\t ");

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).contains("name", "email");
        assertThat(members.count()).isZero();
    }

    @Test
    @DisplayName("AC-3.BE.2 a name of 101 characters is rejected on name; 100 characters (padded) is accepted")
    void AC_3_BE_2_nameLength() throws Exception {
        MvcResult tooLong = register("n".repeat(101), "ada@example.org");
        assertThat(fieldNames(assertError(tooLong, 400, "VALIDATION_FAILED"))).containsExactly("name");

        MvcResult maximum = register("  " + "n".repeat(100) + "  ", "ada@example.org");
        assertThat(maximum.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(maximum).get("name").asText()).hasSize(100);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("com.library.member.MemberApiTest#invalidEmailVectors")
    @DisplayName("AC-3.BE.2 every shared invalid email vector is rejected on the email field")
    void AC_3_BE_2_sharedInvalidEmails(SharedVectors.Vector vector) throws Exception {
        MvcResult result = register("Ada", vector.input());

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).contains("email");
        assertThat(members.count()).isZero();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("com.library.member.MemberApiTest#validEmailVectors")
    @DisplayName("AC-3.BE.2 every shared valid email vector is accepted")
    void AC_3_BE_2_sharedValidEmails(SharedVectors.Vector vector) throws Exception {
        MvcResult result = register("Ada", vector.input());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result).get("email").asText()).isEqualTo(vector.input().strip());
    }

    static Stream<SharedVectors.Vector> invalidEmailVectors() {
        return SharedVectors.emailInvalid().stream();
    }

    static Stream<SharedVectors.Vector> validEmailVectors() {
        return SharedVectors.emailValid().stream();
    }

    @Test
    @DisplayName("AC-3.BE.2 an email of 255 characters is rejected on email")
    void AC_3_BE_2_emailTooLong() throws Exception {
        String email = "a".repeat(251) + "@b.co"; // 256 characters

        assertThat(fieldNames(assertError(register("Ada", email), 400, "VALIDATION_FAILED"))).contains("email");
        assertThat(members.count()).isZero();
    }

    @Test
    @DisplayName("AC-3.BE.2 update validates the same rules and leaves the member unchanged")
    void AC_3_BE_2_updateIsValidatedToo() throws Exception {
        long id = registerOk("Ada Lovelace", "ada@example.org");

        MvcResult result = update(id, "", "not-an-email");

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).contains("name", "email");
        assertThat(members.findById(id).orElseThrow().getName()).isEqualTo("Ada Lovelace");
        assertThat(members.findById(id).orElseThrow().getEmail()).isEqualTo("ada@example.org");
    }

    @Test
    @DisplayName("AC-X.1 a member body that is not JSON is 400 MALFORMED_REQUEST")
    void AC_X_1_malformedJson() throws Exception {
        MvcResult result = mvc.perform(postJson("/api/members", "{oops")).andReturn();

        assertError(result, 400, "MALFORMED_REQUEST");
    }

    // ---- AC-3.BE.3 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-3.BE.3 registering an email that exists (any letter case) is 409 EMAIL_ALREADY_EXISTS with fieldErrors[email]")
    void AC_3_BE_3_duplicateOnRegisterIsCaseInsensitive() throws Exception {
        registerOk("Ada Lovelace", "ada@example.org");

        MvcResult result = register("Someone Else", "ADA@Example.ORG");

        assertThat(fieldNames(assertError(result, 409, "EMAIL_ALREADY_EXISTS"))).containsExactly("email");
        assertThat(members.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-3.BE.3 editing a different member to an existing email (any case) is 409 and changes nothing")
    void AC_3_BE_3_duplicateOnEdit() throws Exception {
        registerOk("Ada Lovelace", "ada@example.org");
        long other = registerOk("Grace Hopper", "grace@example.org");

        MvcResult result = update(other, "Grace Changed", "Ada@example.org");

        assertThat(fieldNames(assertError(result, 409, "EMAIL_ALREADY_EXISTS"))).containsExactly("email");
        Member stored = members.findById(other).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Grace Hopper");
        assertThat(stored.getEmail()).isEqualTo("grace@example.org");
    }

    @Test
    @DisplayName("AC-3.BE.3 editing a member while keeping its own email succeeds, even with different letter case")
    void AC_3_BE_3_ownEmailEditSucceeds() throws Exception {
        long id = registerOk("Ada Lovelace", "ada@example.org");

        MvcResult same = update(id, "Ada King", "ada@example.org");
        MvcResult recased = update(id, "Ada King", "ADA@example.org");

        assertThat(same.getResponse().getStatus()).isEqualTo(200);
        assertThat(recased.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(recased).get("email").asText()).isEqualTo("ADA@example.org");
        assertThat(members.findById(id).orElseThrow().getEmailNormalized()).isEqualTo("ada@example.org");
    }

    @Test
    @DisplayName("AC-3.BE.3 / DR-15 duplicate detection is not broken by a Turkish default locale (I and i)")
    void AC_3_BE_3_turkishDefaultLocale() throws Exception {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            registerOk("Info Desk", "INFO@Example.org");

            MvcResult duplicate = register("Other Desk", "info@example.org");

            assertThat(fieldNames(assertError(duplicate, 409, "EMAIL_ALREADY_EXISTS"))).containsExactly("email");
            assertThat(jdbc.queryForObject("SELECT email_normalized FROM member", String.class))
                    .isEqualTo("info@example.org");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("AC-3.BE.3 two parallel registrations with the same email give exactly one 201 and one 409 (uk_member_email)")
    void AC_3_BE_3_parallelRegistrations() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 15; i++) {
                String email = "race" + i + "@example.org";
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> futures = new ArrayList<>();
                for (int t = 0; t < 2; t++) {
                    String spelling = t == 0 ? email : email.toUpperCase(Locale.ROOT);
                    Callable<Integer> call = () -> {
                        start.await();
                        MvcResult r = register("Race", spelling);
                        if (r.getResponse().getStatus() == 409) {
                            assertError(r, 409, "EMAIL_ALREADY_EXISTS");
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
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(members.count()).isEqualTo(15);
    }

    // ---- AC-3.BE.4 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-3.BE.4 get returns the member; edit with valid values is stored")
    void AC_3_BE_4_getAndEdit() throws Exception {
        long id = registerOk("Ada Lovelace", "ada@example.org");

        MvcResult found = mvc.perform(get("/api/members/" + id)).andReturn();
        assertThat(found.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(found).get("name").asText()).isEqualTo("Ada Lovelace");

        MvcResult edited = update(id, "  Ada King ", "ada.king@example.org");
        assertThat(edited.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(edited).get("name").asText()).isEqualTo("Ada King");

        JsonNode reloaded = body(mvc.perform(get("/api/members/" + id)).andReturn());
        assertThat(reloaded.get("name").asText()).isEqualTo("Ada King");
        assertThat(reloaded.get("email").asText()).isEqualTo("ada.king@example.org");
    }

    @Test
    @DisplayName("AC-3.BE.4 / DR-1 an edit never changes the status or the loan counter")
    void AC_3_BE_4_editKeepsStatusAndCounter() throws Exception {
        long id = registerOk("Ada Lovelace", "ada@example.org");
        long book = fixtures.book("9780132350884", 2).getId();
        fixtures.activeLoan(id, book, TODAY, TODAY.plusDays(14));

        JsonNode edited = body(update(id, "Ada King", "ada@example.org"));

        assertThat(edited.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(edited.get("activeLoanCount").asInt()).isEqualTo(1);
        assertThat(members.findById(id).orElseThrow().getActiveLoanCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-3.BE.4 an inactive member stays inactive when edited")
    void AC_3_BE_4_editKeepsInactive() throws Exception {
        long id = registerOk("Ada Lovelace", "ada@example.org");
        action(id, "deactivate");

        JsonNode edited = body(update(id, "Ada King", "ada@example.org"));

        assertThat(edited.get("status").asText()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("AC-3.BE.4 get, update, deactivate and reactivate of an unknown id are 404 MEMBER_NOT_FOUND")
    void AC_3_BE_4_unknownId() throws Exception {
        assertError(mvc.perform(get("/api/members/987654")).andReturn(), 404, "MEMBER_NOT_FOUND");
        assertError(update(987654, "Ada", "ada@example.org"), 404, "MEMBER_NOT_FOUND");
        assertError(action(987654, "deactivate"), 404, "MEMBER_NOT_FOUND");
        assertError(action(987654, "reactivate"), 404, "MEMBER_NOT_FOUND");
        assertThat(members.count()).isZero();
    }

    @Test
    @DisplayName("AC-3.BE.4 a non-numeric id is a 400 field error on id (AC-X.1 shape)")
    void AC_3_BE_4_nonNumericId() throws Exception {
        MvcResult result = mvc.perform(get("/api/members/abc")).andReturn();

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly("id");
    }

    // ---- AC-3.BE.5 / 6 / 7 + idempotency (D9) ---------------------------------------------------------------------

    @Test
    @DisplayName("AC-3.BE.5 an active member without active loans is deactivated: status INACTIVE")
    void AC_3_BE_5_deactivate() throws Exception {
        long id = registerOk("Ada Lovelace", "ada@example.org");

        MvcResult result = action(id, "deactivate");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("status").asText()).isEqualTo("INACTIVE");
        assertThat(members.findById(id).orElseThrow().getStatus()).isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    @DisplayName("AC-3.BE.5 a member whose loans are all returned can be deactivated")
    void AC_3_BE_5_deactivateWithReturnedLoansOnly() throws Exception {
        long id = registerOk("Ada Lovelace", "ada@example.org");
        long book = fixtures.book("9780132350884", 2).getId();
        fixtures.returnedLoan(id, book, TODAY.minusDays(20), TODAY.minusDays(6), TODAY.minusDays(7));

        assertThat(body(action(id, "deactivate")).get("status").asText()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("AC-3.BE.6 deactivating a member with an active loan is 409 MEMBER_HAS_ACTIVE_LOANS and the status stays ACTIVE")
    void AC_3_BE_6_deactivateWithActiveLoan() throws Exception {
        long id = registerOk("Ada Lovelace", "ada@example.org");
        long book = fixtures.book("9780132350884", 2).getId();
        fixtures.activeLoan(id, book, TODAY, TODAY.plusDays(14));

        assertError(action(id, "deactivate"), 409, "MEMBER_HAS_ACTIVE_LOANS");

        Member stored = members.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(stored.getActiveLoanCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-3.BE.7 reactivating an INACTIVE member makes it ACTIVE")
    void AC_3_BE_7_reactivate() throws Exception {
        long id = registerOk("Ada Lovelace", "ada@example.org");
        action(id, "deactivate");

        MvcResult result = action(id, "reactivate");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(members.findById(id).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("AC-3.BE.7 / D9 deactivate and reactivate are idempotent: no error when already in the target state")
    void AC_3_BE_7_idempotent() throws Exception {
        long id = registerOk("Ada Lovelace", "ada@example.org");

        assertThat(body(action(id, "reactivate")).get("status").asText()).as("reactivate an active member").isEqualTo("ACTIVE");
        assertThat(body(action(id, "deactivate")).get("status").asText()).isEqualTo("INACTIVE");
        MvcResult again = action(id, "deactivate");
        assertThat(again.getResponse().getStatus()).as("deactivate an inactive member").isEqualTo(200);
        assertThat(body(again).get("status").asText()).isEqualTo("INACTIVE");
        assertThat(body(action(id, "reactivate")).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(action(id, "reactivate").getResponse().getStatus()).isEqualTo(200);
    }

    // ---- AC-3.BE.8 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-3.BE.8 the list is paginated with the envelope, page index from 0, default size 20")
    void AC_3_BE_8_pagination() throws Exception {
        for (int i = 0; i < 25; i++) {
            registerOk(String.format("Member %02d", i), "m" + i + "@example.org");
        }

        JsonNode defaults = list("");
        assertThat(defaults.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");
        assertThat(defaults.get("content")).hasSize(20);
        assertThat(defaults.get("size").asInt()).isEqualTo(20);
        assertThat(defaults.get("totalElements").asInt()).isEqualTo(25);
        assertThat(defaults.get("totalPages").asInt()).isEqualTo(2);

        JsonNode second = list("?page=1&size=20");
        assertThat(second.get("content")).hasSize(5);
        assertThat(second.get("page").asInt()).isEqualTo(1);
        assertThat(names(second).get(0)).isEqualTo("Member 20");
        assertThat(list("?size=100").get("content")).hasSize(25);
        assertThat(list("?page=9").get("content")).isEmpty();
    }

    @Test
    @DisplayName("AC-3.BE.8 order is name ascending ignoring case, then id")
    void AC_3_BE_8_order() throws Exception {
        long first = registerOk("bob", "bob1@example.org");
        long second = registerOk("Bob", "bob2@example.org");
        registerOk("alice", "alice@example.org");
        registerOk("Zed", "zed@example.org");

        JsonNode page = list("");

        assertThat(names(page)).containsExactly("alice", "bob", "Bob", "Zed");
        assertThat(page.get("content").get(1).get("id").asLong()).isEqualTo(first);
        assertThat(page.get("content").get(2).get("id").asLong()).isEqualTo(second);
        assertThat(list("")).isEqualTo(page);
    }

    @Test
    @DisplayName("AC-3.BE.8 the status filter returns only members in that status")
    void AC_3_BE_8_statusFilter() throws Exception {
        registerOk("Ada", "ada@example.org");
        long grace = registerOk("Grace", "grace@example.org");
        registerOk("Linus", "linus@example.org");
        action(grace, "deactivate");

        assertThat(names(list("?status=ACTIVE"))).containsExactly("Ada", "Linus");
        assertThat(names(list("?status=INACTIVE"))).containsExactly("Grace");
        assertThat(list("").get("totalElements").asInt()).isEqualTo(3);
        assertThat(list("?status=INACTIVE").get("content").get(0).get("status").asText()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("AC-3.BE.8 / D9 search is a case-insensitive contains on name or email and combines with status")
    void AC_3_BE_8_search() throws Exception {
        registerOk("Ada Lovelace", "ada@example.org");
        long grace = registerOk("Grace Hopper", "grace.hopper@navy.example");
        registerOk("Linus Torvalds", "linus@kernel.example");
        action(grace, "deactivate");

        assertThat(names(list("?search=LOVELACE"))).containsExactly("Ada Lovelace");
        assertThat(names(list("?search=NAVY.EXAMPLE"))).containsExactly("Grace Hopper");
        assertThat(names(list("?search=example"))).containsExactly("Ada Lovelace", "Grace Hopper", "Linus Torvalds");
        assertThat(names(list("?search={s}", "  s tor "))).containsExactly("Linus Torvalds");
        assertThat(names(list("?search=example&status=INACTIVE"))).containsExactly("Grace Hopper");
        assertThat(names(list("?search=nobody"))).isEmpty();
        assertThat(list("?search=nobody").get("totalElements").asInt()).isZero();
        assertThat(list("?search=").get("totalElements").asInt()).as("blank search is no filter").isEqualTo(3);
    }

    @Test
    @DisplayName("AC-3.BE.8 LIKE wildcards in search are literal characters")
    void AC_3_BE_8_searchWildcardsAreLiteral() throws Exception {
        registerOk("Ada 100% Lovelace", "ada@example.org");
        registerOk("Grace Hopper", "grace_hopper@example.org");
        registerOk("Linus Torvalds", "linus@example.org");

        assertThat(names(list("?search={s}", "%"))).containsExactly("Ada 100% Lovelace");
        assertThat(names(list("?search={s}", "_"))).containsExactly("Grace Hopper");
        assertThat(names(list("?search={s}", "%%"))).isEmpty();
    }

    @Test
    @DisplayName("AC-3.BE.8 an unknown status value is 400 with a field error on status")
    void AC_3_BE_8_badStatus() throws Exception {
        for (String value : List.of("x", "active", "ALL")) {
            MvcResult result = mvc.perform(get("/api/members?status={s}", value)).andReturn();
            assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly("status");
        }
    }

    @ParameterizedTest(name = "[{index}] ?{0}")
    @ValueSource(strings = {"size=101", "size=0", "size=-1", "size=abc", "page=-1", "page=abc"})
    @DisplayName("AC-3.BE.8 page/size follow the AC-2.BE.3 rules: invalid values are 400 with a field error")
    void AC_3_BE_8_invalidPaging(String query) throws Exception {
        MvcResult result = mvc.perform(get("/api/members?" + query)).andReturn();

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED"))).containsExactly(query.substring(0, 4));
    }

    @Test
    @DisplayName("DR-18 a search text of 255 characters is 400 on search; 254 is accepted")
    void DR_18_searchLengthLimit() throws Exception {
        MvcResult tooLong = mvc.perform(get("/api/members?search=" + "a".repeat(255))).andReturn();
        assertThat(fieldNames(assertError(tooLong, 400, "VALIDATION_FAILED"))).containsExactly("search");

        assertThat(list("?search=" + "a".repeat(254)).get("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("AC-3.BE.8 every bad parameter is named in one 400 and unknown parameters are ignored")
    void AC_3_BE_8_allBadParametersNamedUnknownIgnored() throws Exception {
        MvcResult result = mvc.perform(get("/api/members?status=x&size=0&page=-1&search=" + "a".repeat(255))).andReturn();

        assertThat(fieldNames(assertError(result, 400, "VALIDATION_FAILED")))
                .containsExactly("page", "search", "size", "status");
        registerOk("Ada", "ada@example.org");
        assertThat(list("?sort=name,desc&foo=bar").get("totalElements").asInt()).isEqualTo(1);
    }
}
