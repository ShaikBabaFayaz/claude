package com.library.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Strict JSON typing (ADR-21, AC-1.BE.2): {@code 2.5} or {@code "5"} for a whole-number field is a 400 field
 * error, never a silent truncation or conversion. Dates are ISO-8601 strings (NFR-12).
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictJsonCustomizer() {
        return builder -> builder
                .featuresToDisable(
                        DeserializationFeature.ACCEPT_FLOAT_AS_INT,
                        MapperFeature.ALLOW_COERCION_OF_SCALARS,
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .featuresToEnable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .postConfigurer(mapper -> {
                    // ALLOW_COERCION_OF_SCALARS does not cover scalar -> String; reject {"title": 123} as well.
                    var textual = mapper.coercionConfigFor(LogicalType.Textual);
                    textual.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                    textual.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                    textual.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                });
    }
}
