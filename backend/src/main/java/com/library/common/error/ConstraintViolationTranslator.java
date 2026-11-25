package com.library.common.error;

import java.util.List;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Backstop for races (architecture 6.2.1, ADR-25, DR-5): maps a database constraint violation to the right API error
 * <em>per operation</em>. A name-only mapping would be ambiguous: {@code ck_book_active_loans} means
 * {@code TOTAL_COPIES_BELOW_ACTIVE_LOANS} on a book update but {@code NO_COPY_AVAILABLE} on checkout.
 *
 * <p>Each writing service method flushes inside its own try block and passes the caught
 * {@link DataIntegrityViolationException} here; it throws the returned exception. The flush must go through a Spring Data
 * repository ({@code saveAndFlush}/{@code flush}) so that the exception is already translated.
 *
 * <p>The constraint name comes from Hibernate's {@link ConstraintViolationException}, with the database message as a
 * fallback, and is compared case-insensitively (H2 reports unquoted names in upper case). A pair that is not in the table
 * means a broken invariant: it is logged at ERROR with the constraint name only (the database message contains the row
 * values, so it is never logged or returned) and becomes 500 {@code INTERNAL_ERROR}.
 */
@Component
public class ConstraintViolationTranslator {

    /** The service operation during which the violation happened. */
    public enum Operation {
        CREATE_BOOK, UPDATE_BOOK, DELETE_BOOK,
        REGISTER_MEMBER, EDIT_MEMBER, DEACTIVATE_MEMBER, REACTIVATE_MEMBER,
        CHECKOUT, RETURN
    }

    /** Names of every constraint in schema.sql; the first one found in an error message wins. */
    static final List<String> KNOWN_CONSTRAINTS = List.of(
            "uk_book_isbn", "ck_book_total_copies", "ck_book_active_loans",
            "uk_member_email", "ck_member_status", "ck_member_active_loans", "ck_member_inactive_no_loans",
            "fk_loan_member", "fk_loan_book", "ck_loan_due", "ck_loan_return", "ck_loan_active_key",
            "uk_loan_member_active_book");

    static final String TOTAL_COPIES_MESSAGE = "Total copies must be a whole number between 1 and 999.";
    static final String TOTAL_COPIES_BELOW_LOANS_MESSAGE =
            "Total copies cannot be lower than the number of copies currently on loan.";

    private static final Logger log = LoggerFactory.getLogger(ConstraintViolationTranslator.class);

    /**
     * @return the exception the caller must throw: a {@link ConflictException} or {@link ValidationException} for a
     *         mapped pair, otherwise an {@link InternalErrorException}
     */
    public ApiException translate(Operation operation, DataIntegrityViolationException violation) {
        String constraint = constraintName(violation);
        ApiException mapped = constraint == null ? null : map(operation, constraint);
        if (mapped != null) {
            return mapped;
        }
        log.error("Unmapped constraint violation: operation={} constraint={}", operation,
                constraint != null ? constraint : "unknown");
        // No cause attached on purpose: the database message carries the row values (names, emails).
        return new InternalErrorException(
                "Unmapped constraint violation: operation=" + operation + " constraint=" + constraint, null);
    }

    private static ApiException map(Operation operation, String constraint) {
        return switch (constraint) {
            case "uk_book_isbn" -> switch (operation) {
                case CREATE_BOOK, UPDATE_BOOK -> new ConflictException(ErrorCode.ISBN_ALREADY_EXISTS,
                        ErrorCode.ISBN_ALREADY_EXISTS.defaultMessage(),
                        List.of(new FieldErrorDto("isbn", "A book with this ISBN already exists.")));
                default -> null;
            };
            case "ck_book_total_copies" -> switch (operation) {
                case CREATE_BOOK, UPDATE_BOOK -> ValidationException.ofField("totalCopies", TOTAL_COPIES_MESSAGE);
                default -> null;
            };
            case "ck_book_active_loans" -> switch (operation) {
                case UPDATE_BOOK -> new ValidationException(ErrorCode.TOTAL_COPIES_BELOW_ACTIVE_LOANS,
                        ErrorCode.TOTAL_COPIES_BELOW_ACTIVE_LOANS.defaultMessage(),
                        List.of(new FieldErrorDto("totalCopies", TOTAL_COPIES_BELOW_LOANS_MESSAGE)));
                case CHECKOUT -> new ConflictException(ErrorCode.NO_COPY_AVAILABLE);
                default -> null;
            };
            case "fk_loan_book" -> operation == Operation.DELETE_BOOK
                    ? new ConflictException(ErrorCode.BOOK_HAS_ACTIVE_LOANS) : null;
            case "uk_member_email" -> switch (operation) {
                case REGISTER_MEMBER, EDIT_MEMBER -> new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS,
                        ErrorCode.EMAIL_ALREADY_EXISTS.defaultMessage(),
                        List.of(new FieldErrorDto("email", "A member with this email already exists.")));
                default -> null;
            };
            case "ck_member_inactive_no_loans" -> switch (operation) {
                case DEACTIVATE_MEMBER -> new ConflictException(ErrorCode.MEMBER_HAS_ACTIVE_LOANS);
                case CHECKOUT -> new ConflictException(ErrorCode.MEMBER_INACTIVE);
                default -> null;
            };
            case "ck_member_active_loans" -> operation == Operation.CHECKOUT
                    ? new ConflictException(ErrorCode.LOAN_LIMIT_REACHED) : null;
            case "uk_loan_member_active_book" -> operation == Operation.CHECKOUT
                    ? new ConflictException(ErrorCode.DUPLICATE_ACTIVE_LOAN) : null;
            default -> null;
        };
    }

    /**
     * The lower-case name of the violated constraint, or null when it cannot be determined. Hibernate's own name is
     * used when it identifies a known constraint; otherwise every message in the cause chain is searched for a known
     * constraint name (case-insensitive).
     */
    static String constraintName(Throwable violation) {
        for (Throwable t = violation; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                String known = knownNameIn(cve.getConstraintName());
                if (known != null) {
                    return known;
                }
            }
        }
        for (Throwable t = violation; t != null; t = t.getCause()) {
            String known = knownNameIn(t.getMessage());
            if (known != null) {
                return known;
            }
        }
        return null;
    }

    private static String knownNameIn(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String name : KNOWN_CONSTRAINTS) {
            if (lower.contains(name)) {
                return name;
            }
        }
        return null;
    }
}
