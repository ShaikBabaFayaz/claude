package com.library.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.stream.Stream;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API documentation settings (NFR-13). Declares the {@code X-Requested-With: library-ui} header as a required parameter
 * with that default on every write operation, so Swagger UI's "Try it out" sends it (otherwise the request would be
 * rejected with 403 {@code MISSING_REQUIRED_HEADER}, DR-17).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String HEADER_NAME = "X-Requested-With";
    static final String HEADER_VALUE = "library-ui";

    @Bean
    public OpenApiCustomizer libraryApiInfoAndHeader() {
        return openApi -> {
            openApi.info(new Info()
                    .title("Library Management System API")
                    .version("1.0")
                    .description("Local-use API without authentication. Every write request (POST, PUT, DELETE) "
                            + "must carry the header X-Requested-With: library-ui. Errors use one JSON shape: "
                            + "status, code, message, timestamp, fieldErrors."));
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> Stream.of(path.getPost(), path.getPut(), path.getDelete(),
                    path.getPatch()).filter(java.util.Objects::nonNull).forEach(OpenApiConfig::addRequiredHeader));
        };
    }

    private static void addRequiredHeader(Operation operation) {
        boolean present = operation.getParameters() != null
                && operation.getParameters().stream().anyMatch(p -> HEADER_NAME.equalsIgnoreCase(p.getName()));
        if (!present) {
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name(HEADER_NAME)
                    .required(true)
                    .description("CSRF guard: must be library-ui on every write request.")
                    .schema(new StringSchema()._default(HEADER_VALUE)));
        }
    }
}
