package com.library.common.error;

/** 404 for an unknown identifier: {@code BOOK_NOT_FOUND}, {@code MEMBER_NOT_FOUND} or {@code LOAN_NOT_FOUND}. */
public class NotFoundException extends ApiException {

    public NotFoundException(ErrorCode code, String message) {
        super(code, message, null, null);
    }

    public static NotFoundException book(Object id) {
        return new NotFoundException(ErrorCode.BOOK_NOT_FOUND, "Book " + id + " was not found.");
    }

    public static NotFoundException member(Object id) {
        return new NotFoundException(ErrorCode.MEMBER_NOT_FOUND, "Member " + id + " was not found.");
    }

    public static NotFoundException loan(Object id) {
        return new NotFoundException(ErrorCode.LOAN_NOT_FOUND, "Loan " + id + " was not found.");
    }
}
