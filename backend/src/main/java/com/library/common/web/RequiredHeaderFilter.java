package com.library.common.web;

import com.library.common.error.ApiErrorWriter;
import com.library.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Browser hardening for a no-authentication API (NFR-6, D7, ADR-27, DR-7, DR-17). Checks run in this order and the first
 * failure answers with the standard error body written through {@link ApiErrorWriter}:
 * <ol>
 *   <li>the {@code Host} header must be on the allowlist, for every request (403 {@code FORBIDDEN_ORIGIN}); this
 *       defends against DNS rebinding;</li>
 *   <li>a mutating request that carries an {@code Origin} header needs an allow-listed origin (403
 *       {@code FORBIDDEN_ORIGIN}); a request without {@code Origin} (curl, tests) passes;</li>
 *   <li>a mutating request needs {@code X-Requested-With: library-ui} (403 {@code MISSING_REQUIRED_HEADER}).</li>
 * </ol>
 * "Mutating" is every method except GET, HEAD and OPTIONS. Error and async dispatches are not filtered a second time
 * ({@link OncePerRequestFilter} defaults): the original request already passed (or failed) these checks.
 */
public class RequiredHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Requested-With";
    public static final String HEADER_VALUE = "library-ui";

    private static final Logger log = LoggerFactory.getLogger(RequiredHeaderFilter.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    static final String DISALLOWED_MESSAGE = "This request came from a host or origin that is not allowed. "
            + "If you changed a port, update " + SecurityProperties.HOSTS_PROPERTY + " and "
            + SecurityProperties.ORIGINS_PROPERTY + ".";
    static final String MISSING_HEADER_MESSAGE =
            "The header " + HEADER_NAME + ": " + HEADER_VALUE + " is required on this request.";

    private final Set<String> allowedHosts;
    private final Set<String> allowedOrigins;
    private final ApiErrorWriter errorWriter;

    public RequiredHeaderFilter(SecurityProperties properties, ApiErrorWriter errorWriter) {
        this.allowedHosts = lowerCased(properties.allowedHosts());
        this.allowedOrigins = lowerCased(properties.allowedOrigins());
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isAllowedHost(request.getHeader("Host"))) {
            reject(request, response, ErrorCode.FORBIDDEN_ORIGIN, DISALLOWED_MESSAGE, "host");
            return;
        }
        if (isMutating(request)) {
            String origin = request.getHeader("Origin");
            if (origin != null && !allowedOrigins.contains(origin.trim().toLowerCase(Locale.ROOT))) {
                reject(request, response, ErrorCode.FORBIDDEN_ORIGIN, DISALLOWED_MESSAGE, "origin");
                return;
            }
            if (!HEADER_VALUE.equals(request.getHeader(HEADER_NAME))) {
                reject(request, response, ErrorCode.MISSING_REQUIRED_HEADER, MISSING_HEADER_MESSAGE, "header");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowedHost(String host) {
        return host != null && allowedHosts.contains(host.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isMutating(HttpServletRequest request) {
        return !SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message,
            String check) throws IOException {
        // Header values are not logged: they are attacker-controlled. Method and check name are enough to diagnose.
        log.info("Request rejected: code={} check={} method={}", code, check, request.getMethod());
        errorWriter.write(response, code, message);
    }

    private static Set<String> lowerCased(java.util.List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }
}
