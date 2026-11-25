package com.library.common.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.ServletException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converts every exception raised inside Spring MVC into the single error body (FR-22, AC-X.1, architecture 10.2).
 * Messages are built here, never taken from exceptions of the framework, so no class name, SQL, constraint name or
 * stack trace can reach a client. Log levels follow architecture 10.3: 409/400 INFO, 404 DEBUG, 500 ERROR with stack.
 * Request bodies and query-parameter values are never logged (DR-14); only field and parameter names are.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String INVALID_VALUE = "This value has an invalid type or format.";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // ---- domain exceptions --------------------------------------------------------------------------------------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        ErrorCode code = ex.getCode();
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("Internal error: {}", ex.getMessage(), ex);
            return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), List.of(), null);
        }
        logClientError(code, ex.getFieldErrors(), ex.getMessage());
        return respond(code, ex.getMessage(), ex.getFieldErrors(), null);
    }

    // ---- validation and binding ---------------------------------------------------------------------------------

    @ExceptionHandler(BindException.class) // includes MethodArgumentNotValidException
    public ResponseEntity<ApiError> handleBind(BindException ex) {
        List<FieldErrorDto> fields = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.add(new FieldErrorDto(fe.getField(), messageOrDefault(fe.getDefaultMessage())));
        }
        for (ObjectError oe : ex.getBindingResult().getGlobalErrors()) {
            fields.add(new FieldErrorDto(oe.getObjectName(), messageOrDefault(oe.getDefaultMessage())));
        }
        return validationFailed(fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorDto> fields = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(fe ->
                        fields.add(new FieldErrorDto(fe.getField(), messageOrDefault(fe.getDefaultMessage()))));
                errors.getGlobalErrors().forEach(oe ->
                        fields.add(new FieldErrorDto(parameterName(result), messageOrDefault(oe.getDefaultMessage()))));
            } else {
                String name = parameterName(result);
                result.getResolvableErrors().forEach(e ->
                        fields.add(new FieldErrorDto(name, messageOrDefault(e.getDefaultMessage()))));
            }
        }
        return validationFailed(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDto> fields = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            fields.add(new FieldErrorDto(lastNodeName(violation.getPropertyPath()),
                    messageOrDefault(violation.getMessage())));
        }
        return validationFailed(fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validationFailed(List.of(new FieldErrorDto(ex.getName(), INVALID_VALUE)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return validationFailed(List.of(new FieldErrorDto(ex.getParameterName(), "This parameter is required.")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex) {
        String path = typeMismatchPath(ex);
        if (path != null) {
            return validationFailed(List.of(new FieldErrorDto(path, INVALID_VALUE)));
        }
        log.info("Malformed request body rejected: code={}", ErrorCode.MALFORMED_REQUEST);
        return respond(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.defaultMessage(), List.of(), null);
    }

    // ---- routing and protocol -----------------------------------------------------------------------------------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        log.debug("No route for {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), List.of(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.info("Method not allowed: code={}", ErrorCode.METHOD_NOT_ALLOWED);
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(), List.of(),
                ex.getHeaders());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.info("Unsupported media type: code={}", ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage(),
                List.of(), ex.getHeaders());
    }

    /** Remaining Spring MVC protocol errors (for example 406): keep their 4xx status, generic body. */
    @ExceptionHandler(ServletException.class)
    public ResponseEntity<ApiError> handleOtherServletException(ServletException ex) {
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = errorResponse.getStatusCode();
            ErrorCode code = ErrorCode.forStatus(status.value());
            log.info("Request rejected: status={} code={}", status.value(), code);
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                    .body(ApiError.of(clock, status.value(), code, code.defaultMessage(), List.of()));
        }
        return handleUnexpected(ex);
    }

    // ---- persistence and concurrency ----------------------------------------------------------------------------

    @ExceptionHandler({PessimisticLockingFailureException.class, PessimisticLockException.class,
            LockTimeoutException.class})
    public ResponseEntity<ApiError> handlePessimisticLock(Exception ex) {
        log.info("Lock wait failed, asking the client to retry: code={} type={}",
                ErrorCode.CONCURRENT_UPDATE, ex.getClass().getSimpleName());
        return respond(ErrorCode.CONCURRENT_UPDATE, ErrorCode.CONCURRENT_UPDATE.defaultMessage(), List.of(), null);
    }

    /** Version check failed: reachable only if the locking rules of architecture 7.3 are broken, hence WARN (DR-3). */
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiError> handleOptimisticLock(Exception ex) {
        log.warn("Optimistic lock conflict (locking rule gap?): type={}", ex.getClass().getSimpleName(), ex);
        return respond(ErrorCode.CONCURRENT_UPDATE, ErrorCode.CONCURRENT_UPDATE.defaultMessage(), List.of(), null);
    }

    /** A violation its service did not translate (6.2.1): the invariant is broken, so 500, detail in the log only. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleUntranslatedIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Untranslated data integrity violation", ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), List.of(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), List.of(), null);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private ResponseEntity<ApiError> validationFailed(List<FieldErrorDto> fields) {
        log.info("Validation failed: code={} fields={}", ErrorCode.VALIDATION_FAILED,
                fields.stream().map(FieldErrorDto::field).distinct().sorted().toList());
        return respond(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), fields, null);
    }

    private void logClientError(ErrorCode code, List<FieldErrorDto> fields, String message) {
        if (code.status() == 404) {
            log.debug("Not found: code={} {}", code, message);
        } else {
            log.info("Request rejected: code={} fields={}", code, fields.stream().map(FieldErrorDto::field).distinct().sorted().toList());
        }
    }

    private ResponseEntity<ApiError> respond(
            ErrorCode code, String message, List<FieldErrorDto> fields, HttpHeaders extraHeaders) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.status()).contentType(MediaType.APPLICATION_JSON);
        if (extraHeaders != null) {
            extraHeaders.forEach((name, values) -> {
                if (!HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                    builder.header(name, values.toArray(String[]::new));
                }
            });
        }
        return builder.body(ApiError.of(clock, code, message, fields));
    }

    private static String messageOrDefault(String message) {
        return message == null || message.isBlank() ? "Invalid value." : message;
    }

    private static String parameterName(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name != null ? name : "arg" + result.getMethodParameter().getParameterIndex();
    }

    private static String lastNodeName(Path path) {
        String last = null;
        for (Path.Node node : path) {
            if (node.getName() != null) {
                last = node.getName();
            }
        }
        return last != null ? last : path.toString();
    }

    /**
     * The JSON path of the offending value (for example {@code totalCopies} or {@code items[0].name}) when the body is
     * well-formed JSON whose value has the wrong type/format; {@code null} for unparseable JSON, a missing body or a
     * mismatch at the document root.
     */
    static String typeMismatchPath(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
                return toPath(mismatch.getPath());
            }
            if (t instanceof JsonProcessingException && !(t instanceof JsonMappingException)) {
                return null; // syntax error: not a field problem
            }
        }
        return null;
    }

    private static String toPath(List<JsonMappingException.Reference> references) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : references) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.isEmpty() ? "body" : sb.toString();
    }
}
