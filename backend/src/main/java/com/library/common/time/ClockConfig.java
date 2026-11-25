package com.library.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of "today" and "now" (ADR-12, NFR-12). Code must inject this {@link Clock} instead of calling
 * {@code LocalDate.now()} / {@code Instant.now()}. Tests replace it with a {@code @Primary} test clock.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
