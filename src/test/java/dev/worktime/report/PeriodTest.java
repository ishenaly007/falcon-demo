package dev.worktime.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.worktime.common.BadRequestException;

@DisplayName("Period")
class PeriodTest {

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    @Test
    @DisplayName("даты включительно превращаются в полуинтервал [полночь from, полночь to+1)")
    void convertsDatesToInstants() {
        Period p = Period.of(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-07"), MSK, 366);
        assertThat(p.start()).isEqualTo(Instant.parse("2026-10-31T21:00:00Z"));
        assertThat(p.end()).isEqualTo(Instant.parse("2026-11-07T21:00:00Z"));
    }

    @Test
    @DisplayName("один день — валидный период")
    void singleDay() {
        Period p = Period.of(LocalDate.parse("2026-11-04"), LocalDate.parse("2026-11-04"), MSK, 366);
        assertThat(p.end()).isEqualTo(p.start().plusSeconds(86_400));
    }

    @Test
    @DisplayName("from позже to — ошибка")
    void fromAfterTo() {
        assertThatThrownBy(() -> Period.of(LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-04"), MSK, 366))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("слишком длинный период — ошибка")
    void tooLong() {
        assertThatThrownBy(() -> Period.of(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"), MSK, 30))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("31");
    }
}
