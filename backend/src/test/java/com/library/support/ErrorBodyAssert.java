package com.library.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.test.web.servlet.MvcResult;

/** AC-X.1: asserts the exact shape of the standard error body and that nothing internal leaks into it. */
public final class ErrorBodyAssert {

    public static final Set<String> BODY_PROPERTIES = Set.of("status", "code", "message", "timestamp", "fieldErrors");
    public static final Pattern TIMESTAMP =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}");
    private static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            "Exception", "com.library", "org.springframework", "org.hibernate", "jakarta.", "java.lang", "\tat ",
            "SELECT", "INSERT", "UPDATE ", "secret", "CK_", "UK_", "FK_", "ck_", "uk_", "fk_");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorBodyAssert() {
    }

    /** Parses the response, asserts status/code and the five-property shape, returns the parsed body. */
    public static JsonNode assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as("HTTP status").isEqualTo(status);
        assertThat(result.getResponse().getContentType()).as("content type").startsWith("application/json");
        return assertBody(content(result), status, code);
    }

    public static JsonNode assertBody(String json, int status, String code) throws Exception {
        JsonNode body = MAPPER.readTree(json);
        Set<String> names = new TreeSet<>();
        body.fieldNames().forEachRemaining(names::add);
        assertThat(names).as("exactly the documented properties").containsExactlyInAnyOrderElementsOf(BODY_PROPERTIES);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("message").isTextual()).isTrue();
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(body.get("timestamp").asText()).matches(TIMESTAMP);
        assertThat(body.get("fieldErrors").isArray()).as("fieldErrors is always an array").isTrue();
        for (JsonNode fieldError : body.get("fieldErrors")) {
            assertThat(fieldError.get("field").asText()).isNotBlank();
            assertThat(fieldError.get("message").asText()).isNotBlank();
        }
        assertNoLeak(json);
        return body;
    }

    public static void assertNoLeak(String json) {
        for (String fragment : FORBIDDEN_FRAGMENTS) {
            assertThat(json).as("error body must not contain '%s'", fragment).doesNotContain(fragment);
        }
    }

    public static List<String> fieldNames(JsonNode body) {
        List<String> names = new ArrayList<>();
        body.get("fieldErrors").forEach(fe -> names.add(fe.get("field").asText()));
        return names;
    }

    private static String content(MvcResult result) throws UnsupportedEncodingException {
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
