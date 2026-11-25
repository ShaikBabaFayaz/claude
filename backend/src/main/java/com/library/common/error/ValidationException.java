package com.library.common.error;

import java.util.List;

/**
 * 400 with field errors: {@code VALIDATION_FAILED} by default, or another 400 code such as
 * {@code TOTAL_COPIES_BELOW_ACTIVE_LOANS}.
 */
public class ValidationException extends ApiException {

    public ValidationException(List<FieldErrorDto> fieldErrors) {
        this(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), fieldErrors);
    }

    public ValidationException(ErrorCode code, String message, List<FieldErrorDto> fieldErrors) {
        super(code, message, fieldErrors, null);
    }

    public static ValidationException ofField(String field, String message) {
        return new ValidationException(List.of(new FieldErrorDto(field, message)));
    }
}
