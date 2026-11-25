package com.library.book;

import com.library.book.dto.BookRequest;
import com.library.book.dto.BookResponse;
import com.library.book.dto.BookSearchCriteria;
import com.library.common.error.ConflictException;
import com.library.common.error.ConstraintViolationTranslator;
import com.library.common.error.ConstraintViolationTranslator.Operation;
import com.library.common.error.ErrorCode;
import com.library.common.error.FieldErrorDto;
import com.library.common.error.NotFoundException;
import com.library.common.error.ValidationException;
import com.library.common.persistence.RowLocks;
import com.library.common.validation.IsbnNormalizer;
import com.library.common.web.PageRequestParams;
import com.library.common.web.PageResponse;
import com.library.loan.LoanRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Book catalogue use cases. One transaction per method (architecture 7.2). Every write to an existing book takes the book
 * row lock first, through {@link RowLocks}, and only then reads the state it decides on (7.3, DR-2). Creating a book
 * inserts a new row and needs no lock. Each writing method flushes through the repository and translates a
 * {@link DataIntegrityViolationException} per operation (DR-5), so a race that slips past the checks still yields the
 * right business error instead of a 500.
 */
@Service
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    static final String ISBN_EXISTS_MESSAGE = "A book with this ISBN already exists.";
    static final String BELOW_ACTIVE_LOANS_MESSAGE =
            "Total copies cannot be lower than the number of copies currently on loan.";

    private final BookRepository books;
    private final LoanRepository loans;
    private final RowLocks rowLocks;
    private final ConstraintViolationTranslator translator;

    public BookService(
            BookRepository books, LoanRepository loans, RowLocks rowLocks, ConstraintViolationTranslator translator) {
        this.books = books;
        this.loans = loans;
        this.rowLocks = rowLocks;
        this.translator = translator;
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        String isbn = IsbnNormalizer.normalize(request.isbn());
        if (books.existsByIsbn(isbn)) {
            throw isbnExists();
        }
        Book book = new Book(request.title(), request.author(), isbn, request.totalCopies());
        try {
            books.saveAndFlush(book);
        } catch (DataIntegrityViolationException e) {
            throw translator.translate(Operation.CREATE_BOOK, e);
        }
        log.info("Book created: bookId={}", book.getId());
        return BookResponse.from(book);
    }

    @Transactional(readOnly = true)
    public BookResponse get(Long id) {
        return books.findById(id).map(BookResponse::from).orElseThrow(() -> NotFoundException.book(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookResponse> search(BookSearchCriteria criteria, PageRequestParams paging) {
        return PageResponse.from(
                books.findAll(BookSpecifications.matching(criteria), paging.toPageable()), BookResponse::from);
    }

    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = rowLocks.lock(Book.class, id);
        String isbn = IsbnNormalizer.normalize(request.isbn());
        // lookups run after the lock and before the entity is modified (nothing dirty to auto-flush)
        if (books.existsByIsbnAndIdNot(isbn, id)) {
            throw isbnExists();
        }
        if (request.totalCopies() < book.getActiveLoanCount()) {
            log.info("Book update rejected: code={} bookId={}", ErrorCode.TOTAL_COPIES_BELOW_ACTIVE_LOANS, id);
            throw new ValidationException(ErrorCode.TOTAL_COPIES_BELOW_ACTIVE_LOANS,
                    ErrorCode.TOTAL_COPIES_BELOW_ACTIVE_LOANS.defaultMessage(),
                    List.of(new FieldErrorDto("totalCopies", BELOW_ACTIVE_LOANS_MESSAGE)));
        }
        book.update(request.title(), request.author(), isbn, request.totalCopies());
        try {
            books.saveAndFlush(book);
        } catch (DataIntegrityViolationException e) {
            throw translator.translate(Operation.UPDATE_BOOK, e);
        }
        return BookResponse.from(book);
    }

    /**
     * Delete order (DR-12): lock the book, check there is no active loan, bulk-delete the returned loans, delete the
     * book. The loan rows are touched after the book lock, which reverses the global lock order; no cycle can form
     * because a book without active loans has no loan that a return could lock first (architecture 7.3).
     */
    @Transactional
    public void delete(Long id) {
        Book book = rowLocks.lock(Book.class, id);
        if (book.getActiveLoanCount() > 0) {
            log.info("Book delete rejected: code={} bookId={}", ErrorCode.BOOK_HAS_ACTIVE_LOANS, id);
            throw new ConflictException(ErrorCode.BOOK_HAS_ACTIVE_LOANS);
        }
        try {
            loans.deleteReturnedByBookId(id);
            books.delete(book);
            books.flush();
        } catch (DataIntegrityViolationException e) {
            throw translator.translate(Operation.DELETE_BOOK, e);
        }
        log.info("Book deleted: bookId={}", id);
    }

    private static ConflictException isbnExists() {
        return new ConflictException(ErrorCode.ISBN_ALREADY_EXISTS, ISBN_EXISTS_MESSAGE,
                List.of(new FieldErrorDto("isbn", ISBN_EXISTS_MESSAGE)));
    }
}
