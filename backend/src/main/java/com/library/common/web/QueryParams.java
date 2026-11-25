package com.library.common.web;

import com.library.common.error.FieldErrorDto;
import com.library.common.error.ValidationException;
import com.library.common.validation.TextNormalizer;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Explicit validation of optional list query parameters (architecture 6.3 notes, DR-18). Every problem is collected into
 * one list so that a request with several bad parameters gets one 400 naming each of them; the raw values are never
 * echoed or logged (DR-14). Unknown parameters are simply not read, so they are ignored.
 */
public final class QueryParams {

    private QueryParams() {
    }

    /**
     * A text filter: trimmed; absent or blank means "no filter" (null). Longer than {@code maxLength} after trimming is a
     * field error.
     */
    public static String text(String field, String raw, int maxLength, List<FieldErrorDto> errors) {
        String value = TextNormalizer.trim(raw);
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            errors.add(new FieldErrorDto(field, "Must be at most " + maxLength + " characters."));
            return null;
        }
        return value;
    }

    /**
     * An optional enumerated value; absent or blank means "not set" (null). Anything the parser does not accept is a
     * field error that lists the allowed values.
     */
    public static <E> E option(
            String field, String raw, Function<String, Optional<E>> parser, String allowed, List<FieldErrorDto> errors) {
        String value = TextNormalizer.trim(raw);
        if (value == null || value.isEmpty()) {
            return null;
        }
        Optional<E> parsed = parser.apply(value);
        if (parsed.isEmpty()) {
            errors.add(new FieldErrorDto(field, "Must be one of: " + allowed + "."));
            return null;
        }
        return parsed.get();
    }

    /** An optional identifier filter: absent or blank means "not set" (null); otherwise a whole number >= 1. */
    public static Long optionalId(String field, String raw, List<FieldErrorDto> errors) {
        String value = TextNormalizer.trim(raw);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            long id = Long.parseLong(value);
            if (id >= 1) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the field error below; the raw value is intentionally not echoed or logged
        }
        errors.add(new FieldErrorDto(field, "Must be a whole number of 1 or more."));
        return null;
    }

    /** {@code page}/{@code size} parsed with {@link PageRequestParams}; its field errors are added to {@code errors}. */
    public static PageRequestParams paging(String rawPage, String rawSize, List<FieldErrorDto> errors) {
        try {
            return PageRequestParams.parse(rawPage, rawSize);
        } catch (ValidationException e) {
            errors.addAll(e.getFieldErrors());
            return PageRequestParams.defaults();
        }
    }

    /** Throws one 400 {@code VALIDATION_FAILED} if any parameter was rejected. */
    public static void failIfAny(List<FieldErrorDto> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
