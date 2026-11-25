package com.library.loan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/loans} (architecture 6.3): both identifiers are required positive whole numbers. {@code Long}
 * (not {@code long}) so that a missing value is reported as a field error instead of defaulting to 0.
 */
public record CheckoutRequest(
        @NotNull(message = "Member id is required.")
        @Min(value = 1, message = "Member id must be a positive whole number.")
        Long memberId,

        @NotNull(message = "Book id is required.")
        @Min(value = 1, message = "Book id must be a positive whole number.")
        Long bookId) {
}
