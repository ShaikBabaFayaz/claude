package com.library.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.library.support.ErrorBodyAssert;
import com.library.support.TestClockConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;

@SpringBootTest
@Import(TestClockConfig.class)
class ApiErrorWriterTest {

    @Autowired
    private ApiErrorWriter writer;

    @Test
    @DisplayName("AC-X.1 writes status, JSON content type and the standard body outside MVC")
    void AC_X_1_writesStandardBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, ErrorCode.FORBIDDEN_ORIGIN, "The host is not allowed.");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        var body = ErrorBodyAssert.assertBody(response.getContentAsString(StandardCharsets.UTF_8), 403, "FORBIDDEN_ORIGIN");
        assertThat(body.get("message").asText()).isEqualTo("The host is not allowed.");
        assertThat(body.get("fieldErrors")).isEmpty();
    }

    @Test
    @DisplayName("NFR-12 the timestamp comes from the injected Clock")
    void timestampFromClock() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, ErrorCode.MISSING_REQUIRED_HEADER, "missing");

        assertThat(response.getContentAsString()).contains("\"timestamp\":\"2026-11-25T12:00:00.000+00:00\"");
    }

    @Test
    @DisplayName("an ApiError with field errors is written with the errors sorted")
    void writesGivenApiError() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiError error = new ApiError(400, ErrorCode.VALIDATION_FAILED, "bad",
                java.time.OffsetDateTime.parse("2026-01-02T03:04:05.678+01:00"),
                List.of(new FieldErrorDto("b", "x"), new FieldErrorDto("a", "y")));

        writer.write(response, error);

        assertThat(response.getContentAsString()).contains("\"timestamp\":\"2026-01-02T03:04:05.678+01:00\"")
                .contains("\"fieldErrors\":[{\"field\":\"a\",\"message\":\"y\"},{\"field\":\"b\",\"message\":\"x\"}]");
    }

    @Test
    @DisplayName("a response that is already committed is left untouched")
    void committedResponseNotTouched() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.getWriter().write("partial");
        response.flushBuffer();

        writer.write(response, ErrorCode.INTERNAL_ERROR, "ignored");

        assertThat(response.getContentAsString()).isEqualTo("partial");
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
