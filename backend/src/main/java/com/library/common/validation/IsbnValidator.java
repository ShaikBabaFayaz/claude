package com.library.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** ISBN-10 (mod 11) and ISBN-13 (mod 10, weights 1/3) check-digit validation on the normalised value. */
public class IsbnValidator implements ConstraintValidator<Isbn, String> {

    /** Programmatic entry point, also used by services and tests. {@code null} is not a valid ISBN here. */
    public static boolean isValidIsbn(String raw) {
        if (raw == null) {
            return false;
        }
        String isbn = IsbnNormalizer.normalize(raw);
        return switch (isbn.length()) {
            case 10 -> isValidIsbn10(isbn);
            case 13 -> isValidIsbn13(isbn);
            default -> false;
        };
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || isValidIsbn(value);
    }

    private static boolean isValidIsbn10(String isbn) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = isbn.charAt(i);
            int digit;
            if (isAsciiDigit(c)) {
                digit = c - '0';
            } else if (c == 'X' && i == 9) {
                digit = 10;
            } else {
                return false;
            }
            sum += (10 - i) * digit;
        }
        return sum % 11 == 0;
    }

    private static boolean isValidIsbn13(String isbn) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = isbn.charAt(i);
            if (!isAsciiDigit(c)) {
                return false;
            }
            sum += (c - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return sum % 10 == 0;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
