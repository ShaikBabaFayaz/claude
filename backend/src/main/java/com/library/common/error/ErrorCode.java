package com.library.common.error;

/**
 * Every {@code code} value of the API error body (architecture 6.2) with its HTTP status and a default plain-language
 * message. {@link #REQUEST_REJECTED} stands for any other 4xx; its real status is passed explicitly where it is used.
 */
public enum ErrorCode {

    VALIDATION_FAILED(400, "The request contains invalid fields."),
    TOTAL_COPIES_BELOW_ACTIVE_LOANS(400,
            "Total copies cannot be lower than the number of copies currently on loan."),
    MALFORMED_REQUEST(400, "The request body is missing or is not valid JSON."),

    MISSING_REQUIRED_HEADER(403, "The required header X-Requested-With: library-ui is missing."),
    FORBIDDEN_ORIGIN(403, "The request came from a host or origin that is not allowed."),

    BOOK_NOT_FOUND(404, "Book not found."),
    MEMBER_NOT_FOUND(404, "Member not found."),
    LOAN_NOT_FOUND(404, "Loan not found."),
    NOT_FOUND(404, "The requested resource was not found."),

    METHOD_NOT_ALLOWED(405, "This method is not supported for this resource."),
    UNSUPPORTED_MEDIA_TYPE(415, "The request content type is not supported; use application/json."),
    REQUEST_REJECTED(400, "The request was rejected."),

    ISBN_ALREADY_EXISTS(409, "A book with this ISBN already exists."),
    EMAIL_ALREADY_EXISTS(409, "A member with this email already exists."),
    BOOK_HAS_ACTIVE_LOANS(409, "This book has active loans and cannot be deleted."),
    MEMBER_HAS_ACTIVE_LOANS(409, "This member has active loans and cannot be deactivated."),
    LOAN_LIMIT_REACHED(409, "This member already has the maximum of 3 active loans."),
    NO_COPY_AVAILABLE(409, "No copy of this book is available."),
    MEMBER_INACTIVE(409, "This member is inactive and cannot borrow books."),
    DUPLICATE_ACTIVE_LOAN(409, "This member already has an active loan of this book."),
    LOAN_ALREADY_RETURNED(409, "This loan has already been returned."),
    CONCURRENT_UPDATE(409, "The data was changed by someone else at the same time. Please try again."),

    INTERNAL_ERROR(500, "An unexpected error occurred. Please try again later.");

    private final int status;
    private final String defaultMessage;

    ErrorCode(int status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public int status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /**
     * Code for an HTTP status that reached {@code /error} (container-level rejection or exception escaping a filter),
     * architecture 10.2: 400 MALFORMED_REQUEST, 404 NOT_FOUND, 405, 415, other 4xx REQUEST_REJECTED, else INTERNAL_ERROR.
     */
    public static ErrorCode forStatus(int httpStatus) {
        return switch (httpStatus) {
            case 400 -> MALFORMED_REQUEST;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> httpStatus >= 400 && httpStatus < 500 ? REQUEST_REJECTED : INTERNAL_ERROR;
        };
    }
}
