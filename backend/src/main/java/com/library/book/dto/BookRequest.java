package com.library.book.dto;

import com.library.common.validation.Isbn;
import com.library.common.validation.TextNormalizer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/books} and {@code PUT /api/books/{id}} (architecture 6.3). The compact constructor trims
 * before Bean Validation runs (null-safe, DR-15), so a title padded with spaces up to 255 characters of real text is
 * accepted. {@code totalCopies} is an {@link Integer} so that a missing value is a field error, and Jackson's strict
 * typing rejects {@code 2.5} and {@code "5"} (AC-1.BE.2).
 */
public record BookRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 255, message = "Title must be at most 255 characters.")
        String title,

        @NotBlank(message = "Author is required.")
        @Size(max = 255, message = "Author must be at most 255 characters.")
        String author,

        @NotBlank(message = "ISBN is required.")
        @Isbn
        String isbn,

        @NotNull(message = "Total copies must be a whole number between 1 and 999.")
        @Min(value = 1, message = "Total copies must be a whole number between 1 and 999.")
        @Max(value = 999, message = "Total copies must be a whole number between 1 and 999.")
        Integer totalCopies) {

    public BookRequest {
        title = TextNormalizer.trim(title);
        author = TextNormalizer.trim(author);
        isbn = TextNormalizer.trim(isbn);
    }
}
