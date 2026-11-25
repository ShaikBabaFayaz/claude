package com.library.common.validation;

import java.util.Locale;

/**
 * Text normalisation shared by request records and entities (architecture 5.3, DR-15).
 *
 * <p><b>Trimming rule for request records:</b> title, author, name, ISBN and email are trimmed in the record's compact
 * constructor, i.e. <em>before</em> Bean Validation runs, so {@code @NotBlank}/{@code @Size} judge the trimmed value:
 * <pre>{@code
 * public BookRequest {
 *     title = TextNormalizer.trim(title);   // null-safe: null stays null and fails @NotBlank
 * }
 * }</pre>
 *
 * <p>Every case conversion uses {@link Locale#ROOT}: under a Turkish default locale {@code "I".toLowerCase()} would
 * yield a dotless i and break case-insensitive uniqueness.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** Null-safe {@link String#strip()}; Unicode-aware, leaves {@code null} as {@code null}. */
    public static String trim(String value) {
        return value == null ? null : value.strip();
    }

    /** Null-safe lower-casing with {@link Locale#ROOT}, independent of the JVM default locale. */
    public static String lowerCase(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /** Uniqueness key of an email: trimmed, lower-cased with {@link Locale#ROOT} (member.email_normalized). */
    public static String normalizeEmail(String email) {
        return lowerCase(trim(email));
    }
}
