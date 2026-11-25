package com.library.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implements the email rule of AC-3.BE.2 without a regular expression (no backtracking risk, easy to port). */
public class LibraryEmailValidator implements ConstraintValidator<LibraryEmail, String> {

    public static final int MAX_LENGTH = 254;

    /** Programmatic entry point. {@code null} is not a valid email here. */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty() || email.length() > MAX_LENGTH) {
            return false;
        }
        int at = -1;
        for (int i = 0; i < email.length(); i++) {
            char c = email.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                return false;
            }
            if (c == '@') {
                if (at >= 0) {
                    return false;
                }
                at = i;
            }
        }
        if (at <= 0) {
            return false;
        }
        return isValidDomain(email.substring(at + 1));
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || isValidEmail(value);
    }

    private static boolean isValidDomain(String domain) {
        if (domain.indexOf('.') < 0) {
            return false;
        }
        int labelLength = 0;
        for (int i = 0; i < domain.length(); i++) {
            if (domain.charAt(i) == '.') {
                if (labelLength == 0) {
                    return false;
                }
                labelLength = 0;
            } else {
                labelLength++;
            }
        }
        return labelLength > 0;
    }
}
