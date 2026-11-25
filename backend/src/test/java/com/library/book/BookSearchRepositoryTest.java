package com.library.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.library.book.dto.BookSearchCriteria;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * The catalogue specification against the real H2 schema (T-13): predicates, LIKE escaping, derived availability, order
 * and paging. Rolled back after each test; the context has its own unique database (DR-9).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookSearchRepositoryTest {

    @Autowired
    private BookRepository books;

    private Book save(String title, String author, String isbn, int total, int lent) {
        Book book = new Book(title, author, isbn, total);
        for (int i = 0; i < lent; i++) {
            book.lendCopy();
        }
        return books.saveAndFlush(book);
    }

    private List<String> titles(BookSearchCriteria criteria) {
        return books.findAll(BookSpecifications.matching(criteria), PageRequest.of(0, 100)).map(Book::getTitle).getContent();
    }

    private static BookSearchCriteria title(String title) {
        return new BookSearchCriteria(title, null, null, null);
    }

    @Test
    @DisplayName("AC-2.BE.1 title and author filters are case-insensitive 'contains'")
    void AC_2_BE_1_titleAndAuthorContainsIgnoreCase() {
        save("Clean Code", "Robert C. Martin", "9780132350884", 1, 0);
        save("Refactoring", "Martin Fowler", "9780134757599", 1, 0);
        save("Spring in Action", "Craig Walls", "9781617294945", 1, 0);

        assertThat(titles(title("CLEAN"))).containsExactly("Clean Code");
        assertThat(titles(title("ing"))).containsExactly("Refactoring", "Spring in Action");
        assertThat(titles(new BookSearchCriteria(null, "martin", null, null))).containsExactly("Clean Code", "Refactoring");
    }

    @Test
    @DisplayName("AC-2.BE.1 filters combine with AND")
    void AC_2_BE_1_filtersAreAnded() {
        save("Clean Code", "Robert C. Martin", "9780132350884", 1, 0);
        save("Clean Architecture", "Robert C. Martin", "9780134494166", 1, 0);
        save("Clean Water", "Someone Else", "9781617294945", 1, 0);

        assertThat(titles(new BookSearchCriteria("clean", "martin", null, null)))
                .containsExactly("Clean Architecture", "Clean Code");
        assertThat(titles(new BookSearchCriteria("water", "martin", null, null))).isEmpty();
    }

    @Test
    @DisplayName("AC-2.BE.1 the ISBN filter is a 'contains' on the stored normalised ISBN")
    void AC_2_BE_1_isbnContains() {
        save("A", "x", "9780132350884", 1, 0);
        save("B", "x", "080442957X", 1, 0);

        assertThat(titles(new BookSearchCriteria(null, null, "0132350", null))).containsExactly("A");
        assertThat(titles(new BookSearchCriteria(null, null, "957X", null))).containsExactly("B");
    }

    @Test
    @DisplayName("AC-2.BE.1 LIKE wildcards in the input are literal: '%', '_' and '\\' do not match everything")
    void AC_2_BE_1_wildcardsAreEscaped() {
        save("100% Java", "a", "9780132350884", 1, 0);
        save("Java Basics", "a", "9780134757599", 1, 0);
        save("snake_case", "a", "9781617294945", 1, 0);
        save("snakeXcase", "a", "9780134494166", 1, 0);
        save("back\\slash", "a", "9780306406157", 1, 0);

        assertThat(titles(title("%"))).containsExactly("100% Java");
        assertThat(titles(title("_"))).containsExactly("snake_case");
        assertThat(titles(title("\\"))).containsExactly("back\\slash");
        assertThat(titles(title("100%"))).containsExactly("100% Java");
        assertThat(titles(title("%%"))).isEmpty();
    }

    @Test
    @DisplayName("AC-2.BE.2 availability: available means >= 1 free copy, unavailable means exactly 0")
    void AC_2_BE_2_availability() {
        save("Free", "a", "9780132350884", 3, 0);
        save("Some out", "a", "9780134757599", 3, 2);
        save("All out", "a", "9781617294945", 2, 2);
        save("Single out", "a", "9780134494166", 1, 1);

        BookSearchCriteria available = new BookSearchCriteria(null, null, null, Availability.AVAILABLE);
        BookSearchCriteria unavailable = new BookSearchCriteria(null, null, null, Availability.UNAVAILABLE);

        assertThat(titles(available)).containsExactly("Free", "Some out");
        assertThat(titles(unavailable)).containsExactly("All out", "Single out");
        assertThat(titles(BookSearchCriteria.none())).hasSize(4);
    }

    @Test
    @DisplayName("AC-2.BE.4 order is lower(title), then ISBN: case-insensitive and stable for equal titles")
    void AC_2_BE_4_order() {
        save("banana", "a", "9780134757599", 1, 0);
        save("Zebra", "a", "9780132350884", 1, 0);
        save("apple", "a", "9781617294945", 1, 0);
        save("Apple", "a", "9780134494166", 1, 0);
        save("Apple", "a", "080442957X", 1, 0);

        Page<Book> page = books.findAll(BookSpecifications.matching(BookSearchCriteria.none()), PageRequest.of(0, 20));

        assertThat(page.map(b -> b.getTitle() + "|" + b.getIsbn()).getContent()).containsExactly(
                "Apple|080442957X", "Apple|9780134494166", "apple|9781617294945", "banana|9780134757599",
                "Zebra|9780132350884");
    }

    @Test
    @DisplayName("AC-2.BE.3 paging returns the page, the total count and the total pages")
    void AC_2_BE_3_paging() {
        for (int i = 0; i < 45; i++) {
            save(String.format("Book %02d", i), "a", com.library.support.TestData.isbn13(i), 1, 0);
        }
        BookSearchCriteria none = BookSearchCriteria.none();

        Page<Book> last = books.findAll(BookSpecifications.matching(none), PageRequest.of(2, 20));
        Page<Book> beyond = books.findAll(BookSpecifications.matching(none), PageRequest.of(9, 20));

        assertThat(last.getContent()).hasSize(5);
        assertThat(last.getTotalElements()).isEqualTo(45);
        assertThat(last.getTotalPages()).isEqualTo(3);
        assertThat(last.getContent().get(0).getTitle()).isEqualTo("Book 40");
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(45);
    }

    @Test
    @DisplayName("AC-2.BE.5 a filter that matches nothing yields an empty page with total 0")
    void AC_2_BE_5_empty() {
        save("Clean Code", "a", "9780132350884", 1, 0);

        Page<Book> page = books.findAll(BookSpecifications.matching(title("nothing like this")), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
    }
}
