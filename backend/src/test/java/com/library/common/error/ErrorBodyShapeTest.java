package com.library.common.error;

import static com.library.support.ApiRequests.get;
import static com.library.support.ApiRequests.post;
import static com.library.support.ErrorBodyAssert.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import com.library.support.ErrorProducers;
import com.library.support.TestClockConfig;
import java.util.EnumSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/** AC-X.1 across every producer: exactly status, code, message, timestamp, fieldErrors; nothing internal leaks. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestClockConfig.class, ErrorProducers.class})
class ErrorBodyShapeTest {

    @Autowired
    private MockMvc mvc;

    static Stream<Arguments> producers() {
        return Stream.of(
                Arguments.of("NotFoundException", get("/test-errors/not-found/1"), 404, "BOOK_NOT_FOUND"),
                Arguments.of("ConflictException", get("/test-errors/conflict"), 409, "ISBN_ALREADY_EXISTS"),
                Arguments.of("ValidationException", get("/test-errors/unsorted-field-errors"), 400, "VALIDATION_FAILED"),
                Arguments.of("InternalErrorException", get("/test-errors/internal"), 500, "INTERNAL_ERROR"),
                Arguments.of("bean validation", post("/test-errors/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"), 400, "VALIDATION_FAILED"),
                Arguments.of("malformed JSON", post("/test-errors/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{"), 400, "MALFORMED_REQUEST"),
                Arguments.of("type mismatch body", post("/test-errors/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"totalCopies\":2.5}"), 400, "VALIDATION_FAILED"),
                Arguments.of("type mismatch path", get("/test-errors/long/abc"), 400, "VALIDATION_FAILED"),
                Arguments.of("parameter constraint", get("/test-errors/query-bound?size=0"), 400, "VALIDATION_FAILED"),
                Arguments.of("paging", get("/test-errors/paging?size=101"), 400, "VALIDATION_FAILED"),
                Arguments.of("unknown route", get("/nope"), 404, "NOT_FOUND"),
                Arguments.of("method not allowed", post("/test-errors/get-only"), 405, "METHOD_NOT_ALLOWED"),
                Arguments.of("media type", post("/test-errors/json-consume").contentType(MediaType.TEXT_PLAIN)
                        .content("x"), 415, "UNSUPPORTED_MEDIA_TYPE"),
                Arguments.of("lock timeout", get("/test-errors/pessimistic"), 409, "CONCURRENT_UPDATE"),
                Arguments.of("optimistic lock", get("/test-errors/optimistic"), 409, "CONCURRENT_UPDATE"),
                Arguments.of("integrity violation", get("/test-errors/integrity"), 500, "INTERNAL_ERROR"),
                Arguments.of("unexpected exception", get("/test-errors/boom"), 500, "INTERNAL_ERROR"));
    }

    @ParameterizedTest(name = "AC-X.1 body shape for {0}")
    @MethodSource("producers")
    void AC_X_1_bodyShape(String name, RequestBuilder request, int status, String code) throws Exception {
        assertError(mvc.perform(request).andReturn(), status, code);
    }

    @Test
    @DisplayName("AC-X.1 every ErrorCode of architecture 6.2 exists, has a status and a message")
    void AC_X_1_errorCodeCatalogue() {
        assertThat(EnumSet.allOf(ErrorCode.class).stream().map(Enum::name)).containsExactlyInAnyOrder(
                "VALIDATION_FAILED", "TOTAL_COPIES_BELOW_ACTIVE_LOANS", "MALFORMED_REQUEST",
                "MISSING_REQUIRED_HEADER", "FORBIDDEN_ORIGIN",
                "BOOK_NOT_FOUND", "MEMBER_NOT_FOUND", "LOAN_NOT_FOUND", "NOT_FOUND",
                "METHOD_NOT_ALLOWED", "UNSUPPORTED_MEDIA_TYPE", "REQUEST_REJECTED",
                "ISBN_ALREADY_EXISTS", "EMAIL_ALREADY_EXISTS", "BOOK_HAS_ACTIVE_LOANS", "MEMBER_HAS_ACTIVE_LOANS",
                "LOAN_LIMIT_REACHED", "NO_COPY_AVAILABLE", "MEMBER_INACTIVE", "DUPLICATE_ACTIVE_LOAN",
                "LOAN_ALREADY_RETURNED", "CONCURRENT_UPDATE", "INTERNAL_ERROR");
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.status()).as(code.name()).isBetween(400, 500);
            assertThat(code.defaultMessage()).as(code.name()).isNotBlank();
        }
    }

    @Test
    @DisplayName("AC-X.1 ErrorCode.forStatus follows architecture 10.2")
    void AC_X_1_forStatus() {
        assertThat(ErrorCode.forStatus(400)).isEqualTo(ErrorCode.MALFORMED_REQUEST);
        assertThat(ErrorCode.forStatus(404)).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ErrorCode.forStatus(405)).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED);
        assertThat(ErrorCode.forStatus(415)).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertThat(ErrorCode.forStatus(403)).isEqualTo(ErrorCode.REQUEST_REJECTED);
        assertThat(ErrorCode.forStatus(499)).isEqualTo(ErrorCode.REQUEST_REJECTED);
        assertThat(ErrorCode.forStatus(500)).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ErrorCode.forStatus(599)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
