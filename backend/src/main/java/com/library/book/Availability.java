package com.library.book;

import java.util.Arrays;
import java.util.Optional;

/** Values of the {@code availability} query parameter of {@code GET /api/books} (AC-2.BE.2). */
public enum Availability {

    /** At least one copy can be lent: {@code total_copies - active_loan_count >= 1}. */
    AVAILABLE("available"),
    /** All copies are out: {@code total_copies - active_loan_count = 0}. */
    UNAVAILABLE("unavailable");

    private final String value;

    Availability(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Exact, case-sensitive match on the API value; empty for anything else. */
    public static Optional<Availability> fromValue(String value) {
        return Arrays.stream(values()).filter(a -> a.value.equals(value)).findFirst();
    }
}
