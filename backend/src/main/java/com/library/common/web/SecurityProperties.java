package com.library.common.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Host and Origin allowlists of {@link RequiredHeaderFilter} (architecture 10.1, 10.4, DR-7). Both lists are
 * configuration because they must match the ports in use (R-12): change {@code server.port} or the Vite port and these
 * two properties together. Comma-separated in YAML/properties, for example
 * {@code library.security.allowed-hosts=localhost:8080,127.0.0.1:8080}.
 *
 * @param allowedHosts   accepted {@code Host} header values (host and port), compared case-insensitively
 * @param allowedOrigins accepted {@code Origin} header values of mutating requests, compared case-insensitively
 */
@ConfigurationProperties(prefix = "library.security")
public record SecurityProperties(List<String> allowedHosts, List<String> allowedOrigins) {

    public static final String HOSTS_PROPERTY = "library.security.allowed-hosts";
    public static final String ORIGINS_PROPERTY = "library.security.allowed-origins";

    public SecurityProperties {
        allowedHosts = normalise(allowedHosts);
        allowedOrigins = normalise(allowedOrigins);
    }

    private static List<String> normalise(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
    }
}
