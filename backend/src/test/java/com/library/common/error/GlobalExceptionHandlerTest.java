package com.library.common.error;

import static com.library.support.ApiRequests.get;
import static com.library.support.ApiRequests.post;
import static com.library.support.ErrorBodyAssert.assertError;
import static com.library.support.ErrorBodyAssert.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.library.support.ErrorProducers;
import com.library.support.LogCapture;
import com.library.support.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** One test per error producer inside Spring MVC (architecture 10.2); AC-X.1 is asserted on every body. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestClockConfig.class, ErrorProducers.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    private MvcResult perform(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn();
    }

    // ---- domain exceptions ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-X.1 NotFoundException -> 404 with its specific code")
    void AC_X_1_notFoundException() throws Exception {
        JsonNode body = assertError(perform(get("/test-errors/not-found/42")), 404, "BOOK_NOT_FOUND");
        assertThat(body.get("fieldErrors")).isEmpty();
    }

    @Test
    @DisplayName("AC-X.1 ConflictException -> 409 and keeps its field error")
    void AC_X_1_conflictException() throws Exception {
        JsonNode body = assertError(perform(get("/test-errors/conflict")), 409, "ISBN_ALREADY_EXISTS");
        assertThat(fieldNames(body)).containsExactly("isbn");
    }

    @Test
    @DisplayName("AC-1.BE.6 ValidationException with another 400 code -> 400 TOTAL_COPIES_BELOW_ACTIVE_LOANS")
    void AC_1_BE_6_validationExceptionWithOtherCode() throws Exception {
        JsonNode body = assertError(
                perform(get("/test-errors/validation-exception")), 400, "TOTAL_COPIES_BELOW_ACTIVE_LOANS");
        assertThat(fieldNames(body)).containsExactly("totalCopies");
    }

    @Test
    @DisplayName("AC-X.1 fieldErrors are sorted by field, then message")
    void AC_X_1_fieldErrorsSorted() throws Exception {
        JsonNode body = assertError(perform(get("/test-errors/unsorted-field-errors")), 400, "VALIDATION_FAILED");
        assertThat(body.get("fieldErrors").toString()).isEqualTo(
                "[{\"field\":\"author\",\"message\":\"a message\"},{\"field\":\"author\",\"message\":\"z message\"},"
                        + "{\"field\":\"title\",\"message\":\"a message\"},{\"field\":\"title\",\"message\":\"b message\"}]");
    }

    @Test
    @DisplayName("AC-X.1 InternalErrorException -> generic 500 without its message or cause")
    void AC_X_1_internalErrorException() throws Exception {
        assertError(perform(get("/test-errors/internal")), 500, "INTERNAL_ERROR");
    }

    // ---- bean validation -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-1.BE.2 MethodArgumentNotValid -> 400 listing each offending field")
    void AC_1_BE_2_methodArgumentNotValid() throws Exception {
        MvcResult result = perform(post("/test-errors/body").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \",\"totalCopies\":0,\"isbn\":\"123\"}"));

        JsonNode body = assertError(result, 400, "VALIDATION_FAILED");
        assertThat(fieldNames(body)).containsExactly("isbn", "title", "totalCopies");
    }

    @Test
    @DisplayName("AC-1.BE.2 a missing required value is reported by field name")
    void AC_1_BE_2_missingFieldsReported() throws Exception {
        MvcResult result = perform(post("/test-errors/body").contentType(MediaType.APPLICATION_JSON).content("{}"));

        JsonNode body = assertError(result, 400, "VALIDATION_FAILED");
        assertThat(fieldNames(body)).containsExactly("title", "totalCopies");
    }

    @Test
    @DisplayName("AC-1.BE.2 totalCopies 2.5 and \"5\" -> VALIDATION_FAILED on totalCopies (JSON path mapping)")
    void AC_1_BE_2_typeMismatchMapsToJsonPath() throws Exception {
        for (String value : new String[] {"2.5", "\"5\"", "\"abc\"", "true"}) {
            MvcResult result = perform(post("/test-errors/body").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"x\",\"totalCopies\":" + value + "}"));

            JsonNode body = assertError(result, 400, "VALIDATION_FAILED");
            assertThat(fieldNames(body)).as("value %s", value).containsExactly("totalCopies");
        }
    }

    @Test
    @DisplayName("AC-X.1 malformed JSON -> 400 MALFORMED_REQUEST, no field errors")
    void AC_X_1_malformedJson() throws Exception {
        for (String content : new String[] {"{", "not json", "[1,2]", "{\"title\":\"x\"} trailing"}) {
            MvcResult result = perform(post("/test-errors/body").contentType(MediaType.APPLICATION_JSON).content(content));

            JsonNode body = assertError(result, 400, "MALFORMED_REQUEST");
            assertThat(body.get("fieldErrors")).as(content).isEmpty();
        }
    }

    @Test
    @DisplayName("AC-X.1 missing request body -> 400 MALFORMED_REQUEST")
    void AC_X_1_missingBody() throws Exception {
        assertError(perform(post("/test-errors/body").contentType(MediaType.APPLICATION_JSON)), 400,
                "MALFORMED_REQUEST");
    }

    @Test
    @DisplayName("AC-2.BE.3 PageRequestParams failure -> 400 with field errors on page and size")
    void AC_2_BE_3_pagingFailureIsFieldError() throws Exception {
        JsonNode body = assertError(perform(get("/test-errors/paging?page=-1&size=101")), 400, "VALIDATION_FAILED");
        assertThat(fieldNames(body)).containsExactly("page", "size");

        assertError(perform(get("/test-errors/paging?size=abc")), 400, "VALIDATION_FAILED");
        assertThat(perform(get("/test-errors/paging?page=0&size=100")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("AC-X.1 MethodArgumentTypeMismatch (non-numeric path id) -> 400 on the parameter name")
    void AC_X_1_typeMismatch() throws Exception {
        JsonNode body = assertError(perform(get("/test-errors/long/abc")), 400, "VALIDATION_FAILED");
        assertThat(fieldNames(body)).containsExactly("id");
    }

    @Test
    @DisplayName("AC-X.1 HandlerMethodValidation (constraint on a request parameter) -> 400 on the parameter name")
    void AC_X_1_handlerMethodValidation() throws Exception {
        JsonNode body = assertError(perform(get("/test-errors/query-bound?size=0")), 400, "VALIDATION_FAILED");
        assertThat(fieldNames(body)).containsExactly("size");
    }

    @Test
    @DisplayName("AC-X.1 missing required request parameter -> 400 on the parameter name")
    void AC_X_1_missingRequiredParameter() throws Exception {
        JsonNode body = assertError(perform(get("/test-errors/required-param")), 400, "VALIDATION_FAILED");
        assertThat(fieldNames(body)).containsExactly("name");
    }

    @Test
    @DisplayName("AC-X.1 ConstraintViolationException -> 400 with property names")
    void AC_X_1_constraintViolationException() throws Exception {
        JsonNode body = assertError(perform(get("/test-errors/constraint-violation")), 400, "VALIDATION_FAILED");
        assertThat(fieldNames(body)).containsExactly("isbn", "title", "totalCopies");
    }

    // ---- routing and protocol ------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-X.1 unknown route -> 404 NOT_FOUND")
    void AC_X_1_unknownRoute() throws Exception {
        assertError(perform(get("/api/nothing-here")), 404, "NOT_FOUND");
    }

    @Test
    @DisplayName("AC-X.1 wrong method -> 405 METHOD_NOT_ALLOWED with an Allow header")
    void AC_X_1_wrongMethod() throws Exception {
        MvcResult result = perform(post("/test-errors/get-only"));

        assertError(result, 405, "METHOD_NOT_ALLOWED");
        assertThat(result.getResponse().getHeader("Allow")).contains("GET");
    }

    @Test
    @DisplayName("AC-X.1 text/plain body on a JSON endpoint -> 415 UNSUPPORTED_MEDIA_TYPE")
    void AC_X_1_unsupportedMediaType() throws Exception {
        assertError(perform(post("/test-errors/json-consume").contentType(MediaType.TEXT_PLAIN).content("hello")),
                415, "UNSUPPORTED_MEDIA_TYPE");
        assertError(perform(post("/test-errors/body").contentType(MediaType.TEXT_PLAIN).content("hello")),
                415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("AC-X.1 not acceptable (406) keeps its 4xx status with the standard body")
    void AC_X_1_notAcceptable() throws Exception {
        MvcResult result = perform(get("/test-errors/json-only").accept(MediaType.TEXT_XML));

        assertError(result, 406, "REQUEST_REJECTED");
    }

    // ---- persistence and concurrency -----------------------------------------------------------------------------

    @Test
    @DisplayName("DR-3 PessimisticLockingFailure (lock wait timeout) -> 409 CONCURRENT_UPDATE")
    void DR_3_pessimisticLockFailure() throws Exception {
        assertError(perform(get("/test-errors/pessimistic")), 409, "CONCURRENT_UPDATE");
        assertError(perform(get("/test-errors/jpa-lock-timeout")), 409, "CONCURRENT_UPDATE");
    }

    @Test
    @DisplayName("DR-3 OptimisticLockingFailure -> 409 CONCURRENT_UPDATE, logged at WARN")
    void DR_3_optimisticLockFailureLoggedAtWarn() throws Exception {
        try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
            assertError(perform(get("/test-errors/optimistic")), 409, "CONCURRENT_UPDATE");

            assertThat(logs.events(Level.WARN)).hasSize(1);
        }
    }

    @Test
    @DisplayName("DR-5 untranslated DataIntegrityViolation -> 500 INTERNAL_ERROR, detail only in the ERROR log")
    void DR_5_untranslatedIntegrityViolation() throws Exception {
        try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
            assertError(perform(get("/test-errors/integrity")), 500, "INTERNAL_ERROR");

            assertThat(logs.events(Level.ERROR)).hasSize(1);
            assertThat(logs.events(Level.ERROR).get(0).getThrowableProxy()).isNotNull();
        }
    }

    @Test
    @DisplayName("AC-X.1 unexpected exception -> 500 INTERNAL_ERROR without leaking its message")
    void AC_X_1_unexpectedException() throws Exception {
        try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
            assertError(perform(get("/test-errors/boom")), 500, "INTERNAL_ERROR");

            assertThat(logs.events(Level.ERROR)).hasSize(1);
        }
    }

    // ---- logging levels (architecture 10.3) ---------------------------------------------------------------------

    @Test
    @DisplayName("10.3 404 is logged at DEBUG, validation failure at INFO without values")
    void logLevelsFollowArchitecture() throws Exception {
        try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
            perform(get("/test-errors/not-found/42"));
            assertThat(logs.events(Level.DEBUG)).hasSize(1);
            assertThat(logs.events(Level.INFO)).isEmpty();

            perform(post("/test-errors/body").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"top-secret-title\",\"totalCopies\":0}"));
            assertThat(logs.events(Level.INFO)).hasSize(1);
            assertThat(logs.messages()).noneMatch(m -> m.contains("top-secret-title"));
        }
    }

    // ---- clock ---------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("NFR-12 the timestamp comes from the injected Clock and has a numeric offset")
    void timestampComesFromClock() throws Exception {
        JsonNode body = assertError(perform(get("/test-errors/not-found/1")), 404, "BOOK_NOT_FOUND");

        assertThat(body.get("timestamp").asText()).isEqualTo("2026-11-25T12:00:00.000+00:00");
    }
}
