package com.library.common.validation;

/**
 * Canonical ISBN form (architecture 5.3): hyphens and spaces removed, a trailing {@code x} upper-cased.
 * An ISBN-10 is deliberately NOT converted to ISBN-13 (Q-A1, ADR-22): uniqueness compares these strings only.
 */
public final class IsbnNormalizer {

    private IsbnNormalizer() {
    }

    /** @return the normalised string, or {@code null} when the input is {@code null} */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '-' && c != ' ') {
                sb.append(c);
            }
        }
        int last = sb.length() - 1;
        if (last >= 0 && sb.charAt(last) == 'x') {
            sb.setCharAt(last, 'X');
        }
        return sb.toString();
    }
}
