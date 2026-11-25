package com.library.support;

import com.library.common.error.ConflictException;
import com.library.common.error.ErrorCode;
import com.library.common.error.FieldErrorDto;
import com.library.common.error.InternalErrorException;
import com.library.common.error.NotFoundException;
import com.library.common.error.ValidationException;
import com.library.common.validation.Isbn;
import com.library.common.web.PageRequestParams;
import jakarta.persistence.LockTimeoutException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Throw-away controller that makes every error producer fire. It lives in the test source set only. Spring 6 only
 * detects {@code @Controller} beans, and a top-level {@code @Controller} would be component-scanned into every
 * context (for example the OpenAPI document test), so it is nested in a {@code @TestConfiguration} (which Spring Boot's
 * test type-exclude filter keeps out of scanning) and tests opt in with {@code @Import(ErrorProducers.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ErrorProducers {

    @Controller
    @RequestMapping("/test-errors")
    @ResponseBody
    public static class ErrorThrowingController {

        public record Body(
                @NotBlank String title,
                @NotNull @Min(1) @Max(999) Integer totalCopies,
                @Isbn String isbn) {
        }

        private final Validator validator;

        public ErrorThrowingController(Validator validator) {
            this.validator = validator;
        }

        @PostMapping("/body")
        public String body(@Valid @RequestBody Body body) {
            return "ok";
        }

        @GetMapping("/not-found/{id}")
        public String notFound(@PathVariable long id) {
            throw NotFoundException.book(id);
        }

        @GetMapping("/conflict")
        public String conflict() {
            throw new ConflictException(ErrorCode.ISBN_ALREADY_EXISTS, "A book with this ISBN already exists.",
                    List.of(new FieldErrorDto("isbn", "ISBN is already in use.")));
        }

        @GetMapping("/validation-exception")
        public String validationException() {
            throw new ValidationException(ErrorCode.TOTAL_COPIES_BELOW_ACTIVE_LOANS,
                    ErrorCode.TOTAL_COPIES_BELOW_ACTIVE_LOANS.defaultMessage(),
                    List.of(new FieldErrorDto("totalCopies", "Total copies cannot be below 2.")));
        }

        @GetMapping("/unsorted-field-errors")
        public String unsortedFieldErrors() {
            throw new ValidationException(List.of(
                    new FieldErrorDto("title", "b message"),
                    new FieldErrorDto("author", "z message"),
                    new FieldErrorDto("title", "a message"),
                    new FieldErrorDto("author", "a message")));
        }

        @GetMapping("/paging")
        public String paging(@RequestParam(required = false) String page, @RequestParam(required = false) String size) {
            PageRequestParams.parse(page, size);
            return "ok";
        }

        @GetMapping("/long/{id}")
        public String longPath(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/query-bound")
        public String queryBound(@RequestParam(required = false) @Min(1) @Max(100) Integer size) {
            return "ok";
        }

        @GetMapping("/required-param")
        public String requiredParam(@RequestParam String name) {
            return "ok";
        }

        @GetMapping("/constraint-violation")
        public String constraintViolation() {
            throw new ConstraintViolationException(validator.validate(new Body(null, 0, "x")));
        }

        @GetMapping("/pessimistic")
        public String pessimistic() {
            throw new CannotAcquireLockException("Timeout trying to lock table \"BOOK\"; SQL statement: SELECT secret");
        }

        @GetMapping("/jpa-lock-timeout")
        public String jpaLockTimeout() {
            throw new LockTimeoutException("raw JPA lock timeout");
        }

        @GetMapping("/optimistic")
        public String optimistic() {
            throw new ObjectOptimisticLockingFailureException("com.library.book.Book", 1L);
        }

        @GetMapping("/integrity")
        public String integrity() {
            throw new DataIntegrityViolationException(
                    "could not execute statement [Check constraint violation: \"CK_BOOK_ACTIVE_LOANS\"] SELECT secret");
        }

        @GetMapping("/internal")
        public String internal() {
            throw new InternalErrorException("broken invariant secret", new IllegalStateException("cause secret"));
        }

        @GetMapping("/boom")
        public String boom() {
            throw new IllegalStateException("secret detail from com.library.internal.Thing");
        }

        @GetMapping(value = "/json-only", produces = "application/json")
        public String jsonOnly() {
            return "{}";
        }

        @PostMapping(value = "/json-consume", consumes = "application/json")
        public String jsonConsume(@RequestBody String body) {
            return "ok";
        }

        @GetMapping("/get-only")
        public String getOnly() {
            return "ok";
        }
    }
}
