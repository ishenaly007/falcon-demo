package dev.worktime.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import dev.worktime.common.BadRequestException;

/**
 * Период отчёта: календарные даты {@code from..to} включительно в часовом поясе {@code zone}.
 * Внутри превращается в полуинтервал моментов [start, end), где end — полночь дня после {@code to}.
 */
public record Period(LocalDate from, LocalDate to, ZoneId zone, Instant start, Instant end) {

    public static Period of(LocalDate from, LocalDate to, ZoneId zone, int maxDays) {
        if (from.isAfter(to)) {
            throw new BadRequestException("Дата начала периода (%s) позже даты конца (%s)".formatted(from, to));
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > maxDays) {
            throw new BadRequestException("Период слишком длинный: %d дн., максимум — %d".formatted(days, maxDays));
        }
        return new Period(from, to, zone,
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
