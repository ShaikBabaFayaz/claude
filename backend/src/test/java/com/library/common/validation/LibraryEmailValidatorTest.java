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

class LibraryEmailValidatorTest {

    record Holder(@LibraryEmail String email) {
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
        return SharedVectors.emailValid().stream();
    }

    static Stream<Vector> invalidVectors() {
        return SharedVectors.emailInvalid().stream();
    }

    @ParameterizedTest(name = "AC-3.BE.2 valid: {0}")
    @MethodSource("validVectors")
    void AC_3_BE_2_validVectorsAccepted(Vector vector) {
        assertThat(LibraryEmailValidator.isValidEmail(vector.input())).isTrue();
        assertThat(validator.validate(new Holder(vector.input()))).isEmpty();
    }

    @ParameterizedTest(name = "AC-3.BE.2 invalid: {0}")
    @MethodSource("invalidVectors")
    void AC_3_BE_2_invalidVectorsRejected(Vector vector) {
        assertThat(LibraryEmailValidator.isValidEmail(vector.input())).isFalse();
        assertThat(validator.validate(new Holder(vector.input())))
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("email"));
    }

    @Test
    @DisplayName("AC-3.BE.2 the 254 / 255 character boundary is exact")
    void AC_3_BE_2_lengthBoundary() {
        String local254 = "a".repeat(254 - "@b.co".length());
        assertThat(LibraryEmailValidator.isValidEmail(local254 + "@b.co")).isTrue();
        assertThat(LibraryEmailValidator.isValidEmail(local254 + "a@b.co")).isFalse();
    }

    @Test
    @DisplayName("AC-3.BE.2 null is left to @NotBlank; the programmatic check treats null as invalid")
    void AC_3_BE_2_nullHandling() {
        assertThat(validator.validate(new Holder(null))).isEmpty();
        assertThat(LibraryEmailValidator.isValidEmail(null)).isFalse();
    }
}
