package com.library.support;

import java.time.LocalDate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the production {@code Clock} with a mutable one fixed at {@link #FIXED_DATE}. Import with
 * {@code @Import(TestClockConfig.class)}; inject {@link MutableClock} to move time inside a test.
 * Tests that change the date must restore it (or use a fresh context) so they stay order-independent.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    public static final LocalDate FIXED_DATE = LocalDate.of(2026, 11, 25);

    @Bean
    @Primary
    public MutableClock testClock() {
        return MutableClock.at(FIXED_DATE);
    }
}
