package dev.worktime.report;

import java.time.Duration;
import java.time.Instant;

/** Чистые функции без Spring — легко покрываются юнит-тестами. */
public final class TimeMath {

    private TimeMath() {
    }

    /**
     * Сколько секунд интервала [start, end) попадает в период [periodStart, periodEnd).
     * Пример: запись 23:00–01:00, отчёт за "сегодня" → в отчёт попадёт только час до полуночи.
     */
    public static long overlapSeconds(Instant start, Instant end, Instant periodStart, Instant periodEnd) {
        Instant from = start.isAfter(periodStart) ? start : periodStart;
        Instant to = end.isBefore(periodEnd) ? end : periodEnd;
        return to.isAfter(from) ? Duration.between(from, to).toSeconds() : 0;
    }
}
