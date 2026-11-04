package dev.worktime.report;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.worktime.report.ReportDtos.ReportEntry;
import dev.worktime.report.ReportDtos.SummaryResponse;

/**
 * Отчёты за период. Даты — календарные дни включительно; {@code tz} — IANA-пояс
 * (например, {@code Europe/Moscow}), в котором их трактовать. Фронтенд передаёт пояс браузера.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    /** GET /api/reports/summary?from=2026-11-01&to=2026-11-07[&tz=Europe/Moscow] */
    @GetMapping("/summary")
    public SummaryResponse summary(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) String tz) {
        return service.summary(from, to, tz);
    }

    /** GET /api/reports/entries?from=2026-11-01&to=2026-11-07[&employeeId=1][&tz=Europe/Moscow] */
    @GetMapping("/entries")
    public List<ReportEntry> entries(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) Long employeeId,
                                     @RequestParam(required = false) String tz) {
        return service.entries(from, to, employeeId, tz);
    }
}
