package com.library.common.error;

/** One entry of {@code ApiError.fieldErrors}: the offending field (or query parameter) and a plain-language message. */
public record FieldErrorDto(String field, String message) {
}
