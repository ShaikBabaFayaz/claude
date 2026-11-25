package com.library.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.library.support.MutableClock;
import com.library.support.TestClockConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

class ClockConfigTest {

    @Nested
    @SpringBootTest
    class ProductionClock {

        @Autowired
        private Clock clock;

        @Test
        @DisplayName("the production clock is the system clock in the default zone")
        void systemClock() {
            assertThat(clock.getZone()).isEqualTo(Clock.systemDefaultZone().getZone());
            assertThat(LocalDate.now(clock)).isBetween(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        }
    }

    @Nested
    @SpringBootTest
    @Import(TestClockConfig.class)
    class TestClockOverride {

        @Autowired
        private Clock clock;

        @Autowired
        private MutableClock mutableClock;

        @Test
        @DisplayName("the fixed test clock overrides the production bean and can be moved")
        void fixedClockOverrides() {
            assertThat(clock).isSameAs(mutableClock);
            assertThat(LocalDate.now(clock)).isEqualTo(TestClockConfig.FIXED_DATE);

            mutableClock.advance(Duration.ofDays(2));
            try {
                assertThat(LocalDate.now(clock)).isEqualTo(TestClockConfig.FIXED_DATE.plusDays(2));
            } finally {
                mutableClock.setDate(TestClockConfig.FIXED_DATE);
            }
        }
    }
}
