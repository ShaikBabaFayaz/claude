package com.library.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/** DR-15: case conversion must not depend on the JVM default locale. */
@ResourceLock("java.util.Locale")
class LocaleSafeNormalizationTest {

    private Locale original;

    @BeforeEach
    void switchToTurkish() {
        original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    @DisplayName("DR-15 precondition: the platform lower-casing really differs under tr-TR")
    void preconditionDefaultLocaleIsAffected() {
        assertThat("TITLE@EXAMPLE.ORG".toLowerCase()).isNotEqualTo("title@example.org");
    }

    @Test
    @DisplayName("DR-15 email uniqueness key is lower-cased with Locale.ROOT under a Turkish default locale")
    void DR_15_emailLowerCasingIsLocaleIndependent() {
        assertThat(TextNormalizer.normalizeEmail("TITLE@EXAMPLE.ORG")).isEqualTo("title@example.org");
        assertThat(TextNormalizer.normalizeEmail("  Iris.INGRID@Example.Org ")).isEqualTo("iris.ingrid@example.org");
        assertThat(TextNormalizer.lowerCase("I")).isEqualTo("i");
    }

    @Test
    @DisplayName("DR-15 ISBN normalisation upper-cases x independent of the default locale")
    void DR_15_isbnUpperCaseIsLocaleIndependent() {
        assertThat(IsbnNormalizer.normalize("080442957x")).isEqualTo("080442957X");
        assertThat(IsbnValidator.isValidIsbn("080442957x")).isTrue();
    }

    @Test
    @DisplayName("trim is null-safe and strips surrounding whitespace only")
    void trimIsNullSafe() {
        assertThat(TextNormalizer.trim(null)).isNull();
        assertThat(TextNormalizer.trim("  Clean  Code \t")).isEqualTo("Clean  Code");
        assertThat(TextNormalizer.trim("   ")).isEmpty();
        assertThat(TextNormalizer.lowerCase(null)).isNull();
    }
}
