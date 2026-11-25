package com.library.book.dto;

import com.library.book.Book;

/** Response shape of every book endpoint (architecture 6.1): {@code availableCopies = totalCopies - active loans}. */
public record BookResponse(Long id, String title, String author, String isbn, int totalCopies, int availableCopies) {

    public static BookResponse from(Book book) {
        return new BookResponse(book.getId(), book.getTitle(), book.getAuthor(), book.getIsbn(), book.getTotalCopies(),
                book.getAvailableCopies());
    }
}
