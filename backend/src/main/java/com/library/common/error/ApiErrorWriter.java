package com.library.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Writes the standard {@link ApiError} body straight to the servlet response, for code that runs outside Spring MVC
 * (the request filters, DR-6). Uses the shared {@link ObjectMapper} and the injected {@link Clock}.
 */
@Component
public class ApiErrorWriter {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorWriter.class);

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApiErrorWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        write(response, ApiError.of(clock, code, message, List.of()));
    }

    public void write(HttpServletResponse response, ApiError error) throws IOException {
        if (response.isCommitted()) {
            log.warn("Cannot write error body: response already committed (code={})", error.code());
            return;
        }
        response.resetBuffer();
        response.setStatus(error.status());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(objectMapper.writeValueAsBytes(error));
        response.flushBuffer();
    }
}
