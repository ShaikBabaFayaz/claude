package com.library.common.error;

/**
 * A broken invariant or unmapped failure that must become 500 {@code INTERNAL_ERROR}. The message and cause are for the
 * server log only; the client always gets the generic message.
 */
public class InternalErrorException extends ApiException {

    public InternalErrorException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, message, null, cause);
    }
}
