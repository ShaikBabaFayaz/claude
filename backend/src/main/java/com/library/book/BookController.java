package com.library.book;

import com.library.book.dto.BookRequest;
import com.library.book.dto.BookResponse;
import com.library.book.dto.BookSearchCriteria;
import com.library.common.error.FieldErrorDto;
import com.library.common.web.PageRequestParams;
import com.library.common.web.PageResponse;
import com.library.common.web.QueryParams;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API of the book catalogue (architecture 6.3). Controllers hold no transaction; the service does. */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    /**
     * Filtered, paginated catalogue (AC-2.BE.1..6). {@code page}/{@code size} and the filters are validated explicitly
     * (one 400 naming every bad parameter); unknown parameters are ignored.
     */
    @GetMapping
    public PageResponse<BookResponse> search(
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "author", required = false) String author,
            @RequestParam(name = "isbn", required = false) String isbn,
            @RequestParam(name = "availability", required = false) String availability,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        List<FieldErrorDto> errors = new ArrayList<>();
        BookSearchCriteria criteria = BookSearchCriteria.parse(title, author, isbn, availability, errors);
        PageRequestParams paging = QueryParams.paging(page, size, errors);
        QueryParams.failIfAny(errors);
        return service.search(criteria, paging);
    }

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/books/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
