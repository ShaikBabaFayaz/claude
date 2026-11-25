package com.library.common.error;

import java.util.List;

/**
 * Base of the domain exceptions that carry an {@link ErrorCode} and optional field errors. The message is written for
 * the client (plain language, no internals) except for {@link InternalErrorException}, whose message is never returned.
 */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldErrorDto> fieldErrors;

    protected ApiException(ErrorCode code, String message, List<FieldErrorDto> fieldErrors, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public ErrorCode getCode() {
        return code;
    }

    public int getStatus() {
        return code.status();
    }

    public List<FieldErrorDto> getFieldErrors() {
        return fieldErrors;
    }
}
