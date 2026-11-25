package com.library.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.library.support.SharedVectors;
import com.library.support.SharedVectors.Vector;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IsbnValidatorTest {

    record Holder(@Isbn String isbn) {
    }

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    static Stream<Vector> validVectors() {
        return SharedVectors.isbnValid().stream();
    }

    static Stream<Vector> invalidVectors() {
        return SharedVectors.isbnInvalid().stream();
    }

    @ParameterizedTest(name = "AC-1.BE.3 valid: {0}")
    @MethodSource("validVectors")
    void AC_1_BE_3_validVectorsAccepted(Vector vector) {
        assertThat(IsbnValidator.isValidIsbn(vector.input())).isTrue();
        assertThat(validator.validate(new Holder(vector.input()))).isEmpty();
    }

    @ParameterizedTest(name = "AC-1.BE.3 invalid: {0}")
    @MethodSource("invalidVectors")
    void AC_1_BE_3_invalidVectorsRejected(Vector vector) {
        assertThat(IsbnValidator.isValidIsbn(vector.input())).isFalse();
        assertThat(validator.validate(new Holder(vector.input())))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.getPropertyPath().toString()).isEqualTo("isbn");
                    assertThat(v.getMessage()).isEqualTo("ISBN must be a valid ISBN-10 or ISBN-13.");
                });
    }

    @Test
    @DisplayName("AC-1.BE.3 null is left to @NotBlank; the programmatic check treats null as invalid")
    void AC_1_BE_3_nullHandling() {
        assertThat(validator.validate(new Holder(null))).isEmpty();
        assertThat(IsbnValidator.isValidIsbn(null)).isFalse();
    }

    @Test
    @DisplayName("Q-A1 an ISBN-10 and its ISBN-13 equivalent are both valid")
    void Q_A1_isbn10AndIsbn13EquivalentBothValid() {
        for (String[] pair : SharedVectors.isbnNotEquivalent()) {
            assertThat(IsbnValidator.isValidIsbn(pair[0])).isTrue();
            assertThat(IsbnValidator.isValidIsbn(pair[1])).isTrue();
        }
    }
}
