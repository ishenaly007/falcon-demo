package dev.worktime.report;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.worktime.common.BadRequestException;
import dev.worktime.config.AppProperties;
import dev.worktime.employee.Employee;
import dev.worktime.employee.EmployeeRepository;
import dev.worktime.employee.EmployeeService;
import dev.worktime.report.ReportDtos.EmployeeTotal;
import dev.worktime.report.ReportDtos.ReportEntry;
import dev.worktime.report.ReportDtos.SummaryResponse;
import dev.worktime.timer.TimeEntry;
import dev.worktime.timer.TimeEntryRepository;

@Service
public class ReportService {

    private final EmployeeRepository employees;
    private final TimeEntryRepository entries;
    private final AppProperties props;
    private final Clock clock;

    public ReportService(EmployeeRepository employees, TimeEntryRepository entries, AppProperties props, Clock clock) {
        this.employees = employees;
        this.entries = entries;
        this.props = props;
        this.clock = clock;
    }

    /** Записи за период (новые сверху), опционально — только одного сотрудника. */
    @Transactional(readOnly = true)
    public List<ReportEntry> entries(LocalDate from, LocalDate to, Long employeeId, String timeZone) {
        Period period = period(from, to, timeZone);
        if (employeeId != null && !employees.existsById(employeeId)) {
            throw EmployeeService.notFound(employeeId);
        }
        Instant now = clock.instant();
        return load(period, employeeId, now).stream()
                .map(e -> toReportEntry(e, period, now))
                .toList();
    }

    /** Итоги по каждому сотруднику за период. Сотрудники без записей тоже попадают в отчёт — с нулём. */
    @Transactional(readOnly = true)
    public SummaryResponse summary(LocalDate from, LocalDate to, String timeZone) {
        Period period = period(from, to, timeZone);
        Instant now = clock.instant();

        Map<Long, long[]> acc = new HashMap<>();           // employeeId -> [секунды, кол-во записей]
        Map<Long, Boolean> running = new HashMap<>();
        for (TimeEntry e : load(period, null, now)) {
            long[] a = acc.computeIfAbsent(e.getEmployee().getId(), k -> new long[2]);
            a[0] += TimeMath.overlapSeconds(e.getStartedAt(), e.effectiveEnd(now), period.start(), period.end());
            a[1]++;
            if (e.isRunning()) {
                running.put(e.getEmployee().getId(), true);
            }
        }

        List<EmployeeTotal> totals = employees.findAll(Sort.by("name", "id")).stream()
                .map(emp -> total(emp, acc.get(emp.getId()), running.containsKey(emp.getId())))
                .sorted(Comparator.comparingLong(EmployeeTotal::totalSeconds).reversed()) // стабильная сортировка: при равенстве — по имени
                .toList();

        long totalSeconds = totals.stream().mapToLong(EmployeeTotal::totalSeconds).sum();
        int entryCount = totals.stream().mapToInt(EmployeeTotal::entryCount).sum();
        return new SummaryResponse(period.from(), period.to(), period.zone().getId(),
                totalSeconds, entryCount, entryCount == 0, totals);
    }

    /** Часовой пояс берётся из запроса (браузер присылает свой), иначе — из настроек приложения. */
    private Period period(LocalDate from, LocalDate to, String timeZone) {
        return Period.of(from, to, resolveZone(timeZone), props.maxReportDays());
    }

    private ZoneId resolveZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return props.timeZone();
        }
        try {
            return ZoneId.of(timeZone.strip());
        } catch (DateTimeException e) {
            throw new BadRequestException("Неизвестный часовой пояс «" + timeZone + "»");
        }
    }

    /**
     * Запрос к БД отбирает пересекающиеся записи; здесь дополнительно отсекаем идущие таймеры,
     * если период целиком в будущем (таймер стартовал раньше конца периода, но "сейчас" ещё до его начала).
     */
    private List<TimeEntry> load(Period period, Long employeeId, Instant now) {
        return entries.findOverlapping(period.start(), period.end(), employeeId).stream()
                .filter(e -> !e.isRunning() || now.isAfter(period.start()))
                .toList();
    }

    private static EmployeeTotal total(Employee emp, long[] acc, boolean running) {
        return new EmployeeTotal(emp.getId(), emp.getName(), emp.getPosition(),
                acc == null ? 0 : acc[0],
                acc == null ? 0 : (int) acc[1],
                running);
    }

    private static ReportEntry toReportEntry(TimeEntry e, Period period, Instant now) {
        return new ReportEntry(
                e.getId(),
                e.getEmployee().getId(),
                e.getEmployee().getName(),
                e.getDescription(),
                e.getStartedAt(),
                e.getEndedAt(),
                e.isRunning(),
                e.duration(now).toSeconds(),
                TimeMath.overlapSeconds(e.getStartedAt(), e.effectiveEnd(now), period.start(), period.end()));
    }
}
