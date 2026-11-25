package com.library.book.dto;

import com.library.book.Availability;
import com.library.common.error.FieldErrorDto;
import com.library.common.validation.IsbnNormalizer;
import com.library.common.web.QueryParams;
import java.util.List;

/**
 * Validated filters of {@code GET /api/books} (AC-2.BE.1, AC-2.BE.2, DR-18). A null component means "no filter".
 * Text values are trimmed; the ISBN is normalised exactly like a stored ISBN (hyphens/spaces removed, trailing
 * {@code x} upper-cased) so that a hyphenated filter matches. Case-insensitivity is applied in the query.
 */
public record BookSearchCriteria(String title, String author, String isbn, Availability availability) {

    /** Maximum length of the title, author and ISBN filter text (DR-18). */
    public static final int MAX_TEXT_LENGTH = 255;

    public static BookSearchCriteria none() {
        return new BookSearchCriteria(null, null, null, null);
    }

    /** Adds one field error per rejected parameter to {@code errors}; rejected parameters are treated as absent. */
    public static BookSearchCriteria parse(
            String rawTitle, String rawAuthor, String rawIsbn, String rawAvailability, List<FieldErrorDto> errors) {
        String title = QueryParams.text("title", rawTitle, MAX_TEXT_LENGTH, errors);
        String author = QueryParams.text("author", rawAuthor, MAX_TEXT_LENGTH, errors);
        String isbn = QueryParams.text("isbn", rawIsbn, MAX_TEXT_LENGTH, errors);
        if (isbn != null) {
            String normalized = IsbnNormalizer.normalize(isbn);
            isbn = normalized.isEmpty() ? null : normalized;
        }
        Availability availability = QueryParams.option(
                "availability", rawAvailability, Availability::fromValue, "available, unavailable", errors);
        return new BookSearchCriteria(title, author, isbn, availability);
    }
}
