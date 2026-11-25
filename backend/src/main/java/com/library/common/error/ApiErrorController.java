package com.library.common.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Replaces Spring Boot's {@code BasicErrorController} (DR-6, ADR-26): everything that reaches {@code /error}
 * (exceptions escaping a filter, container-level rejections such as a malformed URL) gets the standard error body.
 * Boot backs off its own controller because this bean is an {@link ErrorController}.
 */
@Controller
@RequestMapping("${server.error.path:${error.path:/error}}")
public class ApiErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

    private final Clock clock;

    public ApiErrorController(Clock clock) {
        this.clock = clock;
    }

    @RequestMapping
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        int status = statusOf(request);
        ErrorCode code = ErrorCode.forStatus(status);
        // Only the generic text is returned: never the container's message or exception (AC-X.1).
        ApiError body = ApiError.of(clock, status, code, code.defaultMessage(), List.of());

        if (status >= 500) {
            Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            log.error("Request failed outside MVC: status={} code={}", status, code,
                    exception instanceof Throwable t ? t : null);
        } else {
            log.info("Request rejected outside MVC: status={} code={}", status, code);
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static int statusOf(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (attribute instanceof Integer status && status >= 400 && status < 600) {
            return status;
        }
        return 500;
    }
}
