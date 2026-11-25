package com.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.library.book.dto.BookRequest;
import com.library.common.error.ApiException;
import com.library.common.error.ConflictException;
import com.library.common.error.ConstraintViolationTranslator;
import com.library.common.error.ErrorCode;
import com.library.common.error.NotFoundException;
import com.library.common.error.ValidationException;
import com.library.common.persistence.RowLocks;
import com.library.loan.LoanRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit test of the lock order and the constraint translation in {@link BookService} (architecture 7.3, DR-5, DR-12):
 * collaborators are mocks, so the order of calls can be verified exactly.
 */
class BookServiceTest {

    private static final long ID = 42L;

    private BookRepository books;
    private LoanRepository loans;
    private RowLocks rowLocks;
    private BookService service;

    @BeforeEach
    void setUp() {
        books = mock(BookRepository.class);
        loans = mock(LoanRepository.class);
        rowLocks = mock(RowLocks.class);
        service = new BookService(books, loans, rowLocks, new ConstraintViolationTranslator());
    }

    private static BookRequest request(String isbn, int total) {
        return new BookRequest("Title", "Author", isbn, total);
    }

    private Book lockedBook(int total, int active) {
        Book book = new Book("Old", "Old", "9780132350884", total);
        for (int i = 0; i < active; i++) {
            book.lendCopy();
        }
        when(rowLocks.lock(Book.class, ID)).thenReturn(book);
        return book;
    }

    // ---- update --------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DR-1/ADR-24 update takes the book lock before the uniqueness lookup and before saving")
    void updateLocksFirst() {
        lockedBook(5, 1);
        when(books.existsByIsbnAndIdNot("9780132350884", ID)).thenReturn(false);

        service.update(ID, request("978-0-13-235088-4", 5));

        InOrder order = inOrder(rowLocks, books);
        order.verify(rowLocks).lock(Book.class, ID);
        order.verify(books).existsByIsbnAndIdNot("9780132350884", ID);
        order.verify(books).saveAndFlush(any(Book.class));
    }

    @Test
    @DisplayName("AC-1.BE.5 update of a missing book fails at the lock and reads or writes nothing else")
    void updateMissingBookStopsAtLock() {
        when(rowLocks.lock(Book.class, ID)).thenThrow(NotFoundException.book(ID));

        assertThatThrownBy(() -> service.update(ID, request("9780132350884", 1)))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BOOK_NOT_FOUND));
        verifyNoInteractions(books, loans);
    }

    @Test
    @DisplayName("AC-1.BE.6 update below the locked active-loan count is rejected before anything is written")
    void updateBelowActiveLoans() {
        Book book = lockedBook(5, 3);

        assertThatThrownBy(() -> service.update(ID, request("9780132350884", 2)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.TOTAL_COPIES_BELOW_ACTIVE_LOANS);
                    assertThat(e.getFieldErrors()).extracting("field").containsExactly("totalCopies");
                });
        verify(books, never()).saveAndFlush(any());
        assertThat(book.getTotalCopies()).as("entity not modified").isEqualTo(5);
    }

    @Test
    @DisplayName("DR-5 a ck_book_active_loans violation on flush during update is translated to TOTAL_COPIES_BELOW_ACTIVE_LOANS")
    void updateTranslatesCheckViolation() {
        lockedBook(5, 0);
        when(books.saveAndFlush(any(Book.class))).thenThrow(new DataIntegrityViolationException(
                "Check constraint violation: \"CK_BOOK_ACTIVE_LOANS: ACTIVE_LOAN_COUNT <= TOTAL_COPIES\""));

        assertThatThrownBy(() -> service.update(ID, request("9780132350884", 2)))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.TOTAL_COPIES_BELOW_ACTIVE_LOANS));
    }

    @Test
    @DisplayName("DR-5 a uk_book_isbn violation on flush during update is translated to ISBN_ALREADY_EXISTS")
    void updateTranslatesUniqueViolation() {
        lockedBook(5, 0);
        when(books.saveAndFlush(any(Book.class))).thenThrow(new DataIntegrityViolationException(
                "Unique index or primary key violation: \"PUBLIC.UK_BOOK_ISBN_INDEX_1 ON PUBLIC.BOOK(ISBN NULLS FIRST)\""));

        assertThatThrownBy(() -> service.update(ID, request("9780132350884", 2)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ISBN_ALREADY_EXISTS));
    }

    // ---- delete --------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DR-12 delete order: lock the book, bulk-delete the returned loans, delete the book, flush")
    void deleteSequence() {
        Book book = lockedBook(2, 0);

        service.delete(ID);

        InOrder order = inOrder(rowLocks, loans, books);
        order.verify(rowLocks).lock(Book.class, ID);
        order.verify(loans).deleteReturnedByBookId(ID);
        order.verify(books).delete(book);
        order.verify(books).flush();
    }

    @Test
    @DisplayName("AC-1.BE.7 delete with a locked active-loan count above 0 is 409 and deletes nothing")
    void deleteWithActiveLoans() {
        lockedBook(2, 1);

        assertThatThrownBy(() -> service.delete(ID))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BOOK_HAS_ACTIVE_LOANS));
        verifyNoInteractions(loans);
        verify(books, never()).delete(any(Book.class));
    }

    @Test
    @DisplayName("AC-1.BE.5 delete of a missing book fails at the lock")
    void deleteMissingBook() {
        when(rowLocks.lock(Book.class, ID)).thenThrow(NotFoundException.book(ID));

        assertThatThrownBy(() -> service.delete(ID)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(loans, books);
    }

    @Test
    @DisplayName("DR-5 an fk_loan_book violation at delete (a loan appeared despite the lock) is BOOK_HAS_ACTIVE_LOANS")
    void deleteTranslatesForeignKeyViolation() {
        lockedBook(2, 0);
        doThrow(new DataIntegrityViolationException(
                "Referential integrity constraint violation: \"FK_LOAN_BOOK: PUBLIC.LOAN FOREIGN KEY(BOOK_ID)\""))
                .when(books).flush();

        assertThatThrownBy(() -> service.delete(ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BOOK_HAS_ACTIVE_LOANS));
    }

    // ---- create --------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-1.BE.4 create looks the normalised ISBN up (no lock: a new row) and rejects a duplicate")
    void createDuplicateByLookup() {
        when(books.existsByIsbn("9780132350884")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("978-0-13-235088-4", 1)))
                .isInstanceOfSatisfying(ConflictException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.ISBN_ALREADY_EXISTS);
                    assertThat(e.getFieldErrors()).extracting("field").containsExactly("isbn");
                });
        verifyNoInteractions(rowLocks, loans);
        verify(books, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("AC-1.BE.4 a race past the lookup is caught by uk_book_isbn on flush and translated to 409")
    void createTranslatesUniqueViolation() {
        when(books.existsByIsbn("9780132350884")).thenReturn(false);
        when(books.saveAndFlush(any(Book.class))).thenThrow(new DataIntegrityViolationException(
                "Unique index or primary key violation: \"PUBLIC.UK_BOOK_ISBN_INDEX_1 ON PUBLIC.BOOK(ISBN NULLS FIRST)\""));

        assertThatThrownBy(() -> service.create(request("9780132350884", 1)))
                .isInstanceOfSatisfying(ConflictException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.ISBN_ALREADY_EXISTS);
                    assertThat(e.getFieldErrors()).extracting("field").containsExactly("isbn");
                });
    }

    @Test
    @DisplayName("AC-1.BE.5 get of a missing book is BOOK_NOT_FOUND")
    void getMissing() {
        when(books.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BOOK_NOT_FOUND));
    }
}
