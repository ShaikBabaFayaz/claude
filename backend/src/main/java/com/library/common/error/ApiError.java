package com.library.common.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * The single error body of the API (FR-22, AC-X.1, architecture 6.2). Exactly these five properties, always.
 * {@code fieldErrors} is never null and is sorted by field, then message, for deterministic output.
 * The timestamp always carries a numeric offset (for example {@code +01:00}, also for UTC {@code +00:00}).
 */
public record ApiError(
        int status,
        ErrorCode code,
        String message,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSxxx") OffsetDateTime timestamp,
        List<FieldErrorDto> fieldErrors) {

    private static final Comparator<FieldErrorDto> ORDER =
            Comparator.comparing(FieldErrorDto::field).thenComparing(FieldErrorDto::message);

    public ApiError {
        fieldErrors = fieldErrors == null
                ? List.of()
                : fieldErrors.stream().sorted(ORDER).toList();
    }

    public static ApiError of(Clock clock, int status, ErrorCode code, String message, List<FieldErrorDto> fieldErrors) {
        return new ApiError(status, code, message, OffsetDateTime.now(clock), fieldErrors);
    }

    public static ApiError of(Clock clock, ErrorCode code, String message, List<FieldErrorDto> fieldErrors) {
        return of(clock, code.status(), code, message, fieldErrors);
    }

    public static ApiError of(Clock clock, ErrorCode code) {
        return of(clock, code, code.defaultMessage(), List.of());
    }
}
