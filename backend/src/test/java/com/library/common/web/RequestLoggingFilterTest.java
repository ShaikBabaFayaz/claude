package com.library.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.library.support.ApiRequests;
import com.library.support.ErrorProducers;
import com.library.support.LogCapture;
import com.library.support.TestClockConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** One INFO line per request with parameter NAMES only; values and bodies never reach any log (AC-X.1, DR-14). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestClockConfig.class, ErrorProducers.class})
class RequestLoggingFilterTest {

    @Autowired
    private MockMvc mvc;

    private static List<ILoggingEvent> requestLines(LogCapture logs) {
        return logs.events().stream().filter(e -> e.getLoggerName().equals(RequestLoggingFilter.class.getName())).toList();
    }

    @Test
    @DisplayName("DR-14 logs method, path, parameter names, status and duration - never the values")
    void DR_14_parameterNamesButNeverValues() throws Exception {
        try (LogCapture all = LogCapture.root()) {
            // Sent values contain 'z'/'q' (never part of a hex UUID) and are far from any duration or timestamp, so they
            // cannot match the random requestId or the timing in the log line by accident. Non-numeric page/size -> 400.
            mvc.perform(ApiRequests.get("/test-errors/paging?page=zq-page-value&size=zq-size-value"
                    + "&search=zq-ada@example.org&title=zq-top-secret"))
                    .andReturn();

            List<ILoggingEvent> lines = requestLines(all);
            assertThat(lines).hasSize(1);
            ILoggingEvent line = lines.get(0);
            assertThat(line.getLevel()).isEqualTo(Level.INFO);
            String message = line.getFormattedMessage();
            assertThat(message).contains("method=GET").contains("path=/test-errors/paging")
                    .contains("params=[page, size, search, title]").contains("status=400").contains("durationMs=");
            // no value anywhere in any log line of this request
            assertThat(all.messages()).noneMatch(m -> m.contains("zq-page-value") || m.contains("zq-size-value")
                    || m.contains("zq-ada@example.org") || m.contains("zq-top-secret") || m.contains("example.org"));
            // a name followed by '=' would mean a value was logged (the params list is "[name, name]", never "name=")
            assertThat(message).doesNotContain("page=").doesNotContain("size=").doesNotContain("search=")
                    .doesNotContain("title=");
        }
    }

    @Test
    @DisplayName("DR-14 request bodies are never logged, not even for rejected requests")
    void DR_14_bodiesNeverLogged() throws Exception {
        try (LogCapture all = LogCapture.root()) {
            mvc.perform(ApiRequests.post("/test-errors/body").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"body-secret-title\",\"totalCopies\":0,\"isbn\":\"body-secret-isbn\"}"))
                    .andReturn();
            mvc.perform(ApiRequests.post("/test-errors/body").contentType(MediaType.APPLICATION_JSON)
                    .content("{ body-secret-malformed")).andReturn();

            assertThat(all.messages()).noneMatch(m -> m.contains("body-secret"));
            assertThat(all.events()).noneMatch(e -> e.getThrowableProxy() != null
                    && e.getThrowableProxy().getMessage() != null && e.getThrowableProxy().getMessage().contains("body-secret"));
        }
    }

    @Test
    @DisplayName("NFR-9 exactly one request line per request, also for a request rejected by the header filter")
    void NFR_9_oneLinePerRequestIncludingRejected() throws Exception {
        try (LogCapture all = LogCapture.root()) {
            mvc.perform(ApiRequests.get("/actuator/health")).andReturn();
            mvc.perform(MockMvcRequestBuilders.post("/test-errors/body").header("Host", ApiRequests.HOST)).andReturn();

            List<ILoggingEvent> lines = requestLines(all);
            assertThat(lines).hasSize(2);
            assertThat(lines.get(0).getFormattedMessage()).contains("path=/actuator/health").contains("status=200");
            assertThat(lines.get(1).getFormattedMessage()).contains("method=POST").contains("status=403");
        }
    }

    @Test
    @DisplayName("NFR-9 a request without query parameters logs an empty list")
    void emptyParameterList() throws Exception {
        try (LogCapture all = LogCapture.root()) {
            mvc.perform(ApiRequests.get("/actuator/health")).andReturn();

            assertThat(requestLines(all).get(0).getFormattedMessage()).contains("params=[]");
        }
    }

    @Test
    @DisplayName("NFR-9 X-Request-Id: a well-formed id is echoed and logged, anything else is replaced")
    void requestIdCorrelation() throws Exception {
        try (LogCapture all = LogCapture.root()) {
            MvcResult echoed = mvc.perform(ApiRequests.get("/actuator/health").header("X-Request-Id", "abc-123.X_y"))
                    .andReturn();
            assertThat(echoed.getResponse().getHeader("X-Request-Id")).isEqualTo("abc-123.X_y");
            assertThat(requestLines(all).get(0).getFormattedMessage()).contains("requestId=abc-123.X_y");

            MvcResult replaced = mvc.perform(ApiRequests.get("/actuator/health").header("X-Request-Id", "bad id\twith space"))
                    .andReturn();
            assertThat(replaced.getResponse().getHeader("X-Request-Id"))
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

            MvcResult generated = mvc.perform(ApiRequests.get("/actuator/health")).andReturn();
            assertThat(generated.getResponse().getHeader("X-Request-Id")).isNotBlank();
        }
    }

    @Test
    @DisplayName("NFR-9 the MDC request id is cleared after the request")
    void mdcCleared() throws Exception {
        mvc.perform(ApiRequests.get("/actuator/health")).andReturn();

        assertThat(org.slf4j.MDC.get(RequestLoggingFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("parameterNames drops values, handles flags, empties and caps the number and length of names")
    void parameterNamesParsing() {
        assertThat(RequestLoggingFilter.parameterNames(null)).isEmpty();
        assertThat(RequestLoggingFilter.parameterNames("")).isEmpty();
        assertThat(RequestLoggingFilter.parameterNames("a=1&b&&c=x=y&a=2")).containsExactly("a", "b", "c");
        assertThat(RequestLoggingFilter.parameterNames("n%0d%0aINFO=1")).containsExactly("n%0d%0aINFO");
        assertThat(RequestLoggingFilter.parameterNames("a\r\nb=1")).containsExactly("a??b");

        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            many.append("p").append(i).append("=v&");
        }
        assertThat(RequestLoggingFilter.parameterNames(many.toString())).hasSize(20);
        assertThat(RequestLoggingFilter.parameterNames("x".repeat(100) + "=1").iterator().next())
                .hasSize(43).endsWith("...");
    }
}
