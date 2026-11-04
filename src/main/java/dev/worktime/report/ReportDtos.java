package dev.worktime.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    /**
     * Сводка за период. Если записей нет — {@code totalSeconds = 0},
     * у каждого сотрудника 0, {@code empty = true}: "пустой отчёт" — штатная ситуация, не ошибка.
     */
    public record SummaryResponse(
            LocalDate from,
            LocalDate to,
            String timeZone,
            long totalSeconds,
            int entryCount,
            boolean empty,
            List<EmployeeTotal> employees) {
    }

    public record EmployeeTotal(
            Long employeeId,
            String name,
            String position,
            long totalSeconds,
            int entryCount,
            boolean running) {
    }

    /**
     * Запись в отчёте. {@code durationSeconds} — полная длительность,
     * {@code secondsInPeriod} — часть, попавшая в выбранный период (именно она идёт в итоги).
     */
    public record ReportEntry(
            Long id,
            Long employeeId,
            String employeeName,
            String description,
            Instant startedAt,
            Instant endedAt,
            boolean running,
            long durationSeconds,
            long secondsInPeriod) {
    }
}
