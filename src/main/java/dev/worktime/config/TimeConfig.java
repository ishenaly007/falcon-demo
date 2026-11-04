package dev.worktime.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Источник текущего времени вынесен в бин {@link Clock}.
 * Сервисы никогда не вызывают {@code Instant.now()} напрямую — поэтому в тестах
 * можно "перематывать" время и проверять длительности без {@code Thread.sleep}.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
