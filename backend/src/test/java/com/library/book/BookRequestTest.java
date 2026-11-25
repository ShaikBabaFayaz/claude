package com.library.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.book.dto.BookRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DR-15: the request record trims (null-safe) before Bean Validation judges the values. */
class BookRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    private static Set<String> invalidFields(BookRequest request) {
        Set<String> fields = new TreeSet<>();
        for (ConstraintViolation<BookRequest> v : validator.validate(request)) {
            fields.add(v.getPropertyPath().toString());
        }
        return fields;
    }

    @Test
    @DisplayName("AC-1.BE.2 the constructor trims title, author and ISBN")
    void trimsText() {
        BookRequest request = new BookRequest("  Clean Code ", "\tRobert C. Martin  ", " 9780132350884 ", 3);

        assertThat(request.title()).isEqualTo("Clean Code");
        assertThat(request.author()).isEqualTo("Robert C. Martin");
        assertThat(request.isbn()).isEqualTo("9780132350884");
    }

    @Test
    @DisplayName("AC-1.BE.2 null values stay null (no NullPointerException) and fail validation")
    void nullSafe() {
        BookRequest request = new BookRequest(null, null, null, null);

        assertThat(request.title()).isNull();
        assertThat(invalidFields(request)).containsExactly("author", "isbn", "title", "totalCopies");
    }

    @Test
    @DisplayName("AC-1.BE.2 text that is blank only because of whitespace fails @NotBlank")
    void whitespaceOnlyIsBlank() {
        assertThat(invalidFields(new BookRequest("   ", " \t ", "9780132350884", 1))).containsExactly("author", "title");
    }

    @Test
    @DisplayName("DR-15 255 characters plus surrounding spaces pass @Size(max=255); 256 real characters fail")
    void sizeAppliesToTrimmedValue() {
        String max = "x".repeat(255);

        assertThat(invalidFields(new BookRequest("  " + max + "  ", max + " ", "9780132350884", 1))).isEmpty();
        assertThat(invalidFields(new BookRequest(max + "x", max, "9780132350884", 1))).containsExactly("title");
    }

    @Test
    @DisplayName("AC-1.BE.3 a hyphenated ISBN with a valid check digit passes @Isbn after trimming")
    void isbnValidated() {
        assertThat(invalidFields(new BookRequest("T", "A", " 978-0-13-235088-4 ", 1))).isEmpty();
        assertThat(invalidFields(new BookRequest("T", "A", "978-0-13-235088-5", 1))).containsExactly("isbn");
    }

    @Test
    @DisplayName("AC-1.BE.2 total copies must be 1..999 and present")
    void totalCopiesRange() {
        assertThat(invalidFields(new BookRequest("T", "A", "9780132350884", 1))).isEmpty();
        assertThat(invalidFields(new BookRequest("T", "A", "9780132350884", 999))).isEmpty();
        assertThat(invalidFields(new BookRequest("T", "A", "9780132350884", 0))).containsExactly("totalCopies");
        assertThat(invalidFields(new BookRequest("T", "A", "9780132350884", 1000))).containsExactly("totalCopies");
        assertThat(invalidFields(new BookRequest("T", "A", "9780132350884", null))).containsExactly("totalCopies");
    }

    @Test
    @DisplayName("AC-1.BE.2 trimming also happens when the record is deserialised from JSON")
    void trimsWhenDeserialised() throws Exception {
        BookRequest request = new ObjectMapper().readValue(
                "{\"title\":\"  T \",\"author\":\" A\",\"isbn\":\"9780132350884 \",\"totalCopies\":2}", BookRequest.class);

        assertThat(request.title()).isEqualTo("T");
        assertThat(request.author()).isEqualTo("A");
        assertThat(request.isbn()).isEqualTo("9780132350884");
    }
}
