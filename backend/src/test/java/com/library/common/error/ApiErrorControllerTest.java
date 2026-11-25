package com.library.common.error;

import static com.library.support.ApiRequests.get;
import static com.library.support.ApiRequests.post;
import static com.library.support.ErrorBodyAssert.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.library.support.TestClockConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

class ApiErrorControllerTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import(TestClockConfig.class)
    class ForwardedToErrorPath {

        @Autowired
        private MockMvc mvc;

        @ParameterizedTest(name = "AC-X.1 /error with status {0} -> {1} / {2}")
        @CsvSource({
                "400, MALFORMED_REQUEST, 400",
                "404, NOT_FOUND, 404",
                "405, METHOD_NOT_ALLOWED, 405",
                "415, UNSUPPORTED_MEDIA_TYPE, 415",
                "418, REQUEST_REJECTED, 418",
                "431, REQUEST_REJECTED, 431",
                "500, INTERNAL_ERROR, 500",
                "503, INTERNAL_ERROR, 503",
        })
        void AC_X_1_statusIsMappedToCode(int status, String code, int expectedStatus) throws Exception {
            var result = mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status)
                    .requestAttr(RequestDispatcher.ERROR_MESSAGE, "secret container message")
                    .requestAttr(RequestDispatcher.ERROR_EXCEPTION,
                            new IllegalStateException("secret exception from com.library.Internal")))
                    .andReturn();

            assertError(result, expectedStatus, code);
        }

        @Test
        @DisplayName("AC-X.1 /error works for any HTTP method (POST forwarded after a failed write)")
        void AC_X_1_anyMethod() throws Exception {
            var result = mvc.perform(post("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)).andReturn();

            assertError(result, 400, "MALFORMED_REQUEST");
        }

        @Test
        @DisplayName("AC-X.1 /error without a status attribute is an internal error, never an empty or HTML page")
        void AC_X_1_missingStatusAttribute() throws Exception {
            assertError(mvc.perform(get("/error")).andReturn(), 500, "INTERNAL_ERROR");
        }

        @Test
        @DisplayName("NFR-12 the /error timestamp comes from the injected Clock")
        void timestampFromClock() throws Exception {
            JsonNode body = assertError(
                    mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)).andReturn(),
                    404, "NOT_FOUND");

            assertThat(body.get("timestamp").asText()).isEqualTo("2026-11-25T12:00:00.000+00:00");
        }
    }

    /**
     * Real embedded Tomcat: an exception escaping a filter, or a filter calling {@code sendError}, is rendered through
     * {@code /error}. Proves that {@link ApiErrorController} replaced Spring Boot's default error rendering (DR-6).
     * A raw socket is used so the {@code Host} header can be set (the JDK HTTP client forbids it).
     * <p>Known limit (reported to the Orchestrator): Tomcat rejects an unparseable request line (for example invalid
     * percent-encoding) before any context exists, so it never reaches {@code /error}; Tomcat answers with its own
     * minimal HTML page that contains no stack trace or internal detail.
     */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(FailingFilters.class)
    class EscapingFilterErrors {

        @LocalServerPort
        private int port;

        @Test
        @DisplayName("AC-X.1 an exception escaping a filter -> standard 500 body via /error")
        void AC_X_1_exceptionEscapingFilter() throws Exception {
            RawResponse response = rawGet("/filter-boom");

            assertThat(response.statusLine()).startsWith("HTTP/1.1 500");
            assertThat(response.headers()).containsIgnoringCase("Content-Type: application/json");
            com.library.support.ErrorBodyAssert.assertBody(response.json(), 500, "INTERNAL_ERROR");
        }

        @Test
        @DisplayName("AC-X.1 a filter calling sendError(418) -> standard REQUEST_REJECTED body via /error")
        void AC_X_1_sendErrorFromFilter() throws Exception {
            RawResponse response = rawGet("/filter-teapot");

            assertThat(response.statusLine()).startsWith("HTTP/1.1 418");
            com.library.support.ErrorBodyAssert.assertBody(response.json(), 418, "REQUEST_REJECTED");
        }

        @Test
        @DisplayName("AC-X.1 a filter calling sendError(400) -> standard MALFORMED_REQUEST body via /error")
        void AC_X_1_sendError400FromFilter() throws Exception {
            RawResponse response = rawGet("/filter-bad-request");

            assertThat(response.statusLine()).startsWith("HTTP/1.1 400");
            com.library.support.ErrorBodyAssert.assertBody(response.json(), 400, "MALFORMED_REQUEST");
        }

        private RawResponse rawGet(String path) throws Exception {
            try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port)) {
                socket.setSoTimeout(5000);
                OutputStream out = socket.getOutputStream();
                String request = String.join(CRLF,
                        "GET " + path + " HTTP/1.1",
                        "Host: localhost:8080",
                        "Accept: application/json",
                        "Connection: close",
                        "",
                        "");
                out.write(request.getBytes(StandardCharsets.US_ASCII));
                out.flush();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                InputStream in = socket.getInputStream();
                in.transferTo(buffer);
                return new RawResponse(buffer.toString(StandardCharsets.UTF_8));
            }
        }
    }

    private static final String CRLF = "\r\n";

    record RawResponse(String raw) {
        String statusLine() {
            return raw.substring(0, raw.indexOf(CRLF));
        }

        String headers() {
            return raw.substring(0, raw.indexOf(CRLF + CRLF));
        }

        /** The JSON document; chunked transfer encoding may wrap it in size lines, so cut at the outer braces. */
        String json() {
            String body = raw.substring(raw.indexOf(CRLF + CRLF) + 4);
            return body.substring(body.indexOf('{'), body.lastIndexOf('}') + 1);
        }
    }

    /** Test-only filters that fail in the three ways described above. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingFilters {

        @Bean
        FilterRegistrationBean<OncePerRequestFilter> failingFilter() {
            FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(
                    new OncePerRequestFilter() {
                        @Override
                        protected void doFilterInternal(
                                HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                                throws ServletException, IOException {
                            switch (request.getRequestURI()) {
                                case "/filter-boom" -> throw new IllegalStateException("secret filter failure");
                                case "/filter-teapot" -> response.sendError(418, "secret teapot");
                                case "/filter-bad-request" -> response.sendError(400, "secret bad request");
                                default -> chain.doFilter(request, response);
                            }
                        }
                    });
            registration.addUrlPatterns("/*");
            return registration;
        }
    }
}
