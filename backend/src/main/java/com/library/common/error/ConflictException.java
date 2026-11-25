package com.library.common.error;

import java.util.List;

/** 409 for a business-rule conflict (duplicate ISBN/email, limit reached, no copy available, ...). */
public class ConflictException extends ApiException {

    public ConflictException(ErrorCode code) {
        this(code, code.defaultMessage(), List.of());
    }

    public ConflictException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ConflictException(ErrorCode code, String message, List<FieldErrorDto> fieldErrors) {
        super(code, message, fieldErrors, null);
    }
}
