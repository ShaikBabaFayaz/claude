package com.library.support;

import com.library.book.Book;
import com.library.member.Member;

/**
 * Small factories for request bodies and well-known valid values used across API tests.
 * (Entity factories are added by the schema/entity task, T-07.)
 */
public final class TestData {

    /** Check-digit-valid ISBN-13 values, all distinct. */
    public static final String ISBN_13_A = "9780132350884";
    public static final String ISBN_13_B = "9780306406157";
    public static final String ISBN_13_C = "9781617294945";
    /** The ISBN-10 whose ISBN-13 equivalent is {@link #ISBN_13_B} (Q-A1: they are distinct values). */
    public static final String ISBN_10_B = "0306406152";

    private TestData() {
    }

    /** A transient book with a unique-looking title; the caller supplies a check-digit-valid, unique ISBN. */
    public static Book book(String isbn, int totalCopies) {
        return new Book("Book " + isbn, "Author " + isbn, isbn, totalCopies);
    }

    public static Book book(String title, String isbn, int totalCopies) {
        return new Book(title, "Some Author", isbn, totalCopies);
    }

    /** A transient active member whose email is derived from the name. */
    public static Member member(String name) {
        return new Member(name, name.toLowerCase(java.util.Locale.ROOT).replace(' ', '.') + "@example.org");
    }

    public static Member member(String name, String email) {
        return new Member(name, email);
    }

    /** A check-digit-valid ISBN-13 derived from {@code n} (0..999,999,999); different {@code n} give different ISBNs. */
    public static String isbn13(long n) {
        String body = String.format("978%09d", n);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return body + ((10 - sum % 10) % 10);
    }

    public static String bookJson(String title, String author, String isbn, Object totalCopies) {
        return "{\"title\":" + quote(title) + ",\"author\":" + quote(author) + ",\"isbn\":" + quote(isbn)
                + ",\"totalCopies\":" + (totalCopies instanceof String s ? quote(s) : String.valueOf(totalCopies)) + "}";
    }

    public static String validBookJson(String isbn) {
        return bookJson("Clean Code", "Robert C. Martin", isbn, 3);
    }

    public static String memberJson(String name, String email) {
        return "{\"name\":" + quote(name) + ",\"email\":" + quote(email) + "}";
    }

    public static String loanJson(long memberId, long bookId) {
        return "{\"memberId\":" + memberId + ",\"bookId\":" + bookId + "}";
    }

    /** JSON string literal; null becomes the JSON {@code null}. */
    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
