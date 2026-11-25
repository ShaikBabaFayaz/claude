package com.library.common.persistence;

/**
 * Builds safe SQL {@code LIKE} patterns from user text. The text is always passed to the database as a bind parameter;
 * this class only neutralises the {@code LIKE} wildcards inside it, so that a filter of {@code 100%} or {@code a_b}
 * matches those literal characters instead of everything (AC-2.BE.1).
 */
public final class LikePatterns {

    /** The escape character to pass to {@code CriteriaBuilder.like(expression, pattern, ESCAPE)}. */
    public static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    /** {@code %text%} with {@code %}, {@code _} and the escape character in {@code text} escaped. */
    public static String contains(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 2).append('%');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '%' || c == '_' || c == ESCAPE) {
                sb.append(ESCAPE);
            }
            sb.append(c);
        }
        return sb.append('%').toString();
    }
}
