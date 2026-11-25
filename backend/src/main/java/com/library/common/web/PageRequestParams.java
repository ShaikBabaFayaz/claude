package com.library.common.web;

import com.library.common.error.FieldErrorDto;
import com.library.common.error.ValidationException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Validated page/size query parameters (AC-2.BE.3, ADR-11).
 *
 * <p>Spring Data's {@code Pageable} resolver silently clamps oversize values; the API must reject them, so the raw
 * query values are parsed here. {@code page >= 0} (default 0), {@code 1 <= size <= 100} (default 20). A missing
 * parameter means "default"; an empty or non-numeric value is rejected.
 */
public record PageRequestParams(int page, int size) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    static final String PAGE_MESSAGE = "Page must be a whole number of 0 or more.";
    static final String SIZE_MESSAGE = "Size must be a whole number between 1 and " + MAX_SIZE + ".";

    /**
     * @param rawPage raw {@code page} query value or null
     * @param rawSize raw {@code size} query value or null
     * @throws ValidationException listing every invalid parameter by name
     */
    public static PageRequestParams parse(String rawPage, String rawSize) {
        List<FieldErrorDto> errors = new ArrayList<>();
        int page = parseBounded("page", rawPage, DEFAULT_PAGE, 0, Integer.MAX_VALUE, PAGE_MESSAGE, errors);
        int size = parseBounded("size", rawSize, DEFAULT_SIZE, 1, MAX_SIZE, SIZE_MESSAGE, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new PageRequestParams(page, size);
    }

    public static PageRequestParams defaults() {
        return new PageRequestParams(DEFAULT_PAGE, DEFAULT_SIZE);
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page, size, sort);
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size);
    }

    private static int parseBounded(
            String field, String raw, int defaultValue, int min, int max, String message, List<FieldErrorDto> errors) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the field error below; the raw value is intentionally not echoed or logged
        }
        errors.add(new FieldErrorDto(field, message));
        return defaultValue;
    }
}
