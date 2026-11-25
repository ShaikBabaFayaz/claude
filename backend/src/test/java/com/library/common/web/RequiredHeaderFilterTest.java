package com.library.common.web;

import static com.library.support.ErrorBodyAssert.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.library.support.ApiRequests;
import com.library.support.ErrorProducers;
import com.library.support.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** The DR-7 / DR-17 matrix: Host allowlist, Origin allowlist on writes, required header on every unsafe method. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestClockConfig.class, ErrorProducers.class})
class RequiredHeaderFilterTest {

    private static final String WRITE_URL = "/test-errors/get-only"; // exists for GET only: a passing PUT gets 405, not 403

    @Autowired
    private MockMvc mvc;

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn();
    }

    /** A request with no standard headers at all, so each test sets exactly what it wants. */
    private static MockHttpServletRequestBuilder bare(HttpMethod method, String url) {
        return MockMvcRequestBuilders.request(method, url);
    }

    // ---- Host allowlist (any method) ------------------------------------------------------------------------------

    @ParameterizedTest(name = "DR-7 allowed Host {0} passes on GET")
    @ValueSource(strings = {"localhost:8080", "127.0.0.1:8080", "localhost:5173", "127.0.0.1:5173", "LOCALHOST:8080"})
    void DR_7_allowedHostPasses(String host) throws Exception {
        MvcResult result = perform(bare(HttpMethod.GET, "/actuator/health").header("Host", host));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @ParameterizedTest(name = "DR-7 disallowed Host \"{0}\" -> 403 FORBIDDEN_ORIGIN on GET")
    @ValueSource(strings = {"evil.example", "localhost:9999", "localhost", "127.0.0.1", "evil.example:8080",
            "localhost:8080.evil.example", "[::1]:8080", "0.0.0.0:8080"})
    void DR_7_disallowedHostRejected(String host) throws Exception {
        MvcResult result = perform(bare(HttpMethod.GET, "/actuator/health").header("Host", host));

        JsonNode body = assertError(result, 403, "FORBIDDEN_ORIGIN");
        assertThat(body.get("message").asText())
                .contains("library.security.allowed-hosts")
                .contains("library.security.allowed-origins");
    }

    @Test
    @DisplayName("DR-7 a request without a Host header is rejected")
    void DR_7_missingHostRejected() throws Exception {
        assertError(perform(bare(HttpMethod.GET, "/actuator/health")), 403, "FORBIDDEN_ORIGIN");
    }

    @Test
    @DisplayName("DR-7 the Host check also applies to unknown routes and every method")
    void DR_7_hostCheckAppliesToAllMethods() throws Exception {
        for (HttpMethod method : new HttpMethod[] {HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS,
                HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH}) {
            MvcResult result = perform(bare(method, "/nowhere").header("Host", "evil.example")
                    .header(ApiRequests.REQUIRED_HEADER, ApiRequests.REQUIRED_HEADER_VALUE));
            assertThat(result.getResponse().getStatus()).as(method.name()).isEqualTo(403);
        }
    }

    // ---- Origin allowlist (mutating requests only) ---------------------------------------------------------------

    @Test
    @DisplayName("DR-7 allowed Host and no Origin on a write passes the filter")
    void DR_7_noOriginPasses() throws Exception {
        MvcResult result = perform(ApiRequests.postJson("/test-errors/body", "{\"title\":\"x\",\"totalCopies\":1}"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @ParameterizedTest(name = "DR-7 allowed Origin {0} passes on a write")
    @ValueSource(strings = {"http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:8080",
            "http://127.0.0.1:8080", "HTTP://LOCALHOST:5173"})
    void DR_7_allowedOriginPasses(String origin) throws Exception {
        MvcResult result = perform(ApiRequests.postJson("/test-errors/body", "{\"title\":\"x\",\"totalCopies\":1}")
                .header("Origin", origin));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @ParameterizedTest(name = "DR-7 disallowed Origin \"{0}\" -> 403 FORBIDDEN_ORIGIN on POST")
    @ValueSource(strings = {"http://evil.example", "https://localhost:5173", "http://localhost:3000", "null",
            "http://localhost:5173.evil.example"})
    void DR_7_disallowedOriginRejected(String origin) throws Exception {
        MvcResult result = perform(ApiRequests.postJson("/test-errors/body", "{\"title\":\"x\",\"totalCopies\":1}")
                .header("Origin", origin));

        assertError(result, 403, "FORBIDDEN_ORIGIN");
    }

    @Test
    @DisplayName("DR-17 a disallowed Origin is rejected on PUT, DELETE and PATCH too")
    void DR_17_disallowedOriginRejectedOnAllUnsafeMethods() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                ApiRequests.put(WRITE_URL), ApiRequests.delete(WRITE_URL), ApiRequests.patch(WRITE_URL)}) {
            assertError(perform(request.header("Origin", "http://evil.example")), 403, "FORBIDDEN_ORIGIN");
        }
    }

    @Test
    @DisplayName("DR-7 a disallowed Origin on a safe method (GET) is not checked")
    void DR_7_originNotCheckedOnSafeMethods() throws Exception {
        MvcResult result = perform(ApiRequests.get("/actuator/health").header("Origin", "http://evil.example"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    // ---- Required header (every method except GET/HEAD/OPTIONS) --------------------------------------------------

    @ParameterizedTest(name = "DR-17 {0} without X-Requested-With -> 403 MISSING_REQUIRED_HEADER")
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void DR_17_missingHeaderRejected(String method) throws Exception {
        MvcResult result = perform(bare(HttpMethod.valueOf(method), WRITE_URL).header("Host", ApiRequests.HOST)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));

        JsonNode body = assertError(result, 403, "MISSING_REQUIRED_HEADER");
        assertThat(body.get("message").asText()).contains("X-Requested-With");
    }

    @ParameterizedTest(name = "DR-17 {0} with a wrong X-Requested-With value -> 403 MISSING_REQUIRED_HEADER")
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void DR_17_wrongHeaderValueRejected(String method) throws Exception {
        MvcResult result = perform(bare(HttpMethod.valueOf(method), WRITE_URL).header("Host", ApiRequests.HOST)
                .header("X-Requested-With", "XMLHttpRequest"));

        assertError(result, 403, "MISSING_REQUIRED_HEADER");
    }

    @ParameterizedTest(name = "DR-17 {0} with the header passes the filter")
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void DR_17_headerPresentPasses(String method) throws Exception {
        MvcResult result = perform(bare(HttpMethod.valueOf(method), WRITE_URL).header("Host", ApiRequests.HOST)
                .header("X-Requested-With", "library-ui"));

        assertThat(result.getResponse().getStatus()).as("not a filter rejection").isNotEqualTo(403);
    }

    @ParameterizedTest(name = "DR-17 {0} needs no X-Requested-With header")
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void DR_17_safeMethodsNeedNoHeader(String method) throws Exception {
        MvcResult result = perform(bare(HttpMethod.valueOf(method), "/actuator/health").header("Host", ApiRequests.HOST));

        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
    }

    @Test
    @DisplayName("DR-7 order of checks: Host first, then Origin, then the required header")
    void DR_7_orderOfChecks() throws Exception {
        // bad Host and no header -> FORBIDDEN_ORIGIN
        assertError(perform(bare(HttpMethod.POST, WRITE_URL).header("Host", "evil.example")), 403, "FORBIDDEN_ORIGIN");
        // good Host, bad Origin and no header -> FORBIDDEN_ORIGIN
        assertError(perform(bare(HttpMethod.POST, WRITE_URL).header("Host", ApiRequests.HOST)
                .header("Origin", "http://evil.example")), 403, "FORBIDDEN_ORIGIN");
        // good Host, good Origin and no header -> MISSING_REQUIRED_HEADER
        assertError(perform(bare(HttpMethod.POST, WRITE_URL).header("Host", ApiRequests.HOST)
                .header("Origin", "http://localhost:5173")), 403, "MISSING_REQUIRED_HEADER");
    }

    // ---- error body (AC-X.1) ------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-X.1 filter rejections use the standard body, timestamp from the injected clock")
    void AC_X_1_filterRejectionBody() throws Exception {
        JsonNode body = assertError(perform(bare(HttpMethod.POST, WRITE_URL).header("Host", ApiRequests.HOST)), 403,
                "MISSING_REQUIRED_HEADER");

        assertThat(body.get("timestamp").asText()).isEqualTo("2026-11-25T12:00:00.000+00:00");
        assertThat(body.get("fieldErrors")).isEmpty();
    }
}
