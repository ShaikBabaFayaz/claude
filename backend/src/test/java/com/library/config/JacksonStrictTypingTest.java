package com.library.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JacksonStrictTypingTest {

    record Copies(int totalCopies) {
    }

    record BoxedCopies(Integer totalCopies) {
    }

    record Titled(String title) {
    }

    record Dated(LocalDate day) {
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("AC-1.BE.2 whole numbers are accepted")
    void AC_1_BE_2_wholeNumberAccepted() throws Exception {
        assertThat(objectMapper.readValue("{\"totalCopies\":5}", Copies.class).totalCopies()).isEqualTo(5);
    }

    @ParameterizedTest(name = "AC-1.BE.2 {0} is rejected for an int field")
    @ValueSource(strings = {"2.5", "2.0", "\"5\"", "\"abc\"", "true"})
    void AC_1_BE_2_nonWholeNumbersRejectedForInt(String literal) {
        assertThatThrownBy(() -> objectMapper.readValue("{\"totalCopies\":" + literal + "}", Copies.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @ParameterizedTest(name = "AC-1.BE.2 {0} is rejected for an Integer field")
    @ValueSource(strings = {"2.5", "\"5\""})
    void AC_1_BE_2_nonWholeNumbersRejectedForInteger(String literal) {
        assertThatThrownBy(() -> objectMapper.readValue("{\"totalCopies\":" + literal + "}", BoxedCopies.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("a number is not silently converted to a string field")
    void numberNotCoercedToString() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"title\":123}", Titled.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("trailing tokens after the JSON document are rejected")
    void trailingTokensRejected() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"totalCopies\":5} {\"x\":1}", Copies.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("NFR-12 dates are ISO-8601 strings, not timestamps")
    void datesAreIsoStrings() throws Exception {
        assertThat(objectMapper.writeValueAsString(new Dated(LocalDate.of(2026, 11, 25))))
                .isEqualTo("{\"day\":\"2026-11-25\"}");
        assertThat(objectMapper.readValue("{\"day\":\"2026-12-09\"}", Dated.class).day())
                .isEqualTo(LocalDate.of(2026, 12, 9));
    }
}
