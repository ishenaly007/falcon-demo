package dev.worktime.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    /** Среда, 4 ноября 2026, 09:00 по Москве. */
    public static final Instant START = Instant.parse("2026-11-04T06:00:00Z");

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(START);
    }
}
