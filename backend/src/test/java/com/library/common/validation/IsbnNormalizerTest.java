package com.library.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.library.support.SharedVectors;
import com.library.support.SharedVectors.Vector;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class IsbnNormalizerTest {

    static Stream<Vector> validVectors() {
        return SharedVectors.isbnValid().stream();
    }

    @ParameterizedTest(name = "AC-1.BE.3 normalises {0}")
    @MethodSource("validVectors")
    void AC_1_BE_3_normalisesSharedVectors(Vector vector) {
        assertThat(IsbnNormalizer.normalize(vector.input())).isEqualTo(vector.normalized());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "\"0-306-40615-2\"|0306406152",
            "\" 978 0 306 40615 7 \"|9780306406157",
            "080442957x|080442957X",
            "0x30640615|0x30640615",
            "\"x\"|X",
    })
    void stripsSeparatorsAndUpperCasesTrailingX(String input, String expected) {
        assertThat(IsbnNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a string of only separators normalises to empty")
    void onlySeparators() {
        assertThat(IsbnNormalizer.normalize("- - -")).isEmpty();
    }

    @Test
    @DisplayName("AC-2.BE.1 ISBN filter normalisation: null stays null and empty stays empty")
    void AC_2_BE_1_nullAndEmpty() {
        assertThat(IsbnNormalizer.normalize(null)).isNull();
        assertThat(IsbnNormalizer.normalize("")).isEmpty();
    }

    @Test
    @DisplayName("Q-A1 ISBN-10 is NOT converted to ISBN-13")
    void Q_A1_doesNotConvertIsbn10ToIsbn13() {
        for (String[] pair : SharedVectors.isbnNotEquivalent()) {
            assertThat(IsbnNormalizer.normalize(pair[0])).isNotEqualTo(IsbnNormalizer.normalize(pair[1]));
        }
        assertThat(IsbnNormalizer.normalize("0306406152")).isEqualTo("0306406152");
    }
}
