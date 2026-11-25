package com.library.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One INFO line per request (NFR-9, architecture 10.3): method, path, the NAMES of the query parameters, status and
 * duration. Parameter values and request bodies are never read or logged (DR-14), so search terms and emails cannot reach
 * the log. Also assigns a request id (taken from a well-formed {@code X-Request-Id} request header, otherwise generated):
 * it is put in the logging MDC as {@code requestId}, echoed in the {@code X-Request-Id} response header and included in
 * the log line.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^A-Za-z0-9._~\\-\\[\\]/%:@!$'()*+,;]");
    private static final int MAX_PARAMETER_NAMES = 20;
    private static final int MAX_NAME_LENGTH = 40;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            log.info("request method={} path={} params={} status={} durationMs={} requestId={}",
                    request.getMethod(), sanitise(request.getRequestURI()), parameterNames(request.getQueryString()),
                    response.getStatus(), millis, requestId);
            MDC.remove(MDC_KEY);
        }
    }

    private static String requestId(HttpServletRequest request) {
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        if (supplied != null && VALID_REQUEST_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    /** Names only: everything after '=' is dropped without being decoded or inspected. */
    static Set<String> parameterNames(String queryString) {
        Set<String> names = new LinkedHashSet<>();
        if (queryString == null || queryString.isEmpty()) {
            return names;
        }
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = sanitise(eq >= 0 ? pair.substring(0, eq) : pair);
            if (name.length() > MAX_NAME_LENGTH) {
                name = name.substring(0, MAX_NAME_LENGTH) + "...";
            }
            if (!name.isEmpty() && names.size() < MAX_PARAMETER_NAMES) {
                names.add(name);
            }
        }
        return names;
    }

    /** Keeps the log line on one line: anything outside a conservative URL-safe set becomes '?'. */
    private static String sanitise(String value) {
        return value == null ? "" : UNSAFE_CHARS.matcher(value).replaceAll("?");
    }
}
