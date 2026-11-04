package dev.worktime.timer;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.worktime.common.ConflictException;
import dev.worktime.employee.Employee;
import dev.worktime.employee.EmployeeRepository;
import dev.worktime.employee.EmployeeService;
import dev.worktime.timer.TimerDtos.EntryResponse;

/**
 * Запуск и остановка таймера.
 *
 * <p>Правила:
 * <ul>
 *   <li>у сотрудника одновременно идёт не больше одного таймера —
 *       повторный старт возвращает 409 и <b>не</b> сбрасывает текущий таймер;</li>
 *   <li>остановить можно только идущий таймер — иначе 409;</li>
 *   <li>разные сотрудники работают независимо.</li>
 * </ul>
 * Гонки двух одновременных запросов закрыты дважды: блокировкой строки сотрудника
 * и уникальным индексом в БД (changelog 003).
 */
@Service
public class TimerService {

    private final EmployeeRepository employees;
    private final TimeEntryRepository entries;
    private final Clock clock;

    public TimerService(EmployeeRepository employees, TimeEntryRepository entries, Clock clock) {
        this.employees = employees;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional
    public EntryResponse start(long employeeId, String description) {
        Employee employee = lockEmployee(employeeId);
        entries.findByEmployeeIdAndEndedAtIsNull(employeeId).ifPresent(running -> {
            throw new ConflictException("Таймер для «%s» уже запущен (задача «%s»). Сначала остановите его."
                    .formatted(employee.getName(), running.getDescription()));
        });
        Instant now = now();
        // saveAndFlush — чтобы нарушение уникального индекса всплыло прямо здесь, внутри транзакции
        TimeEntry entry = entries.saveAndFlush(new TimeEntry(employee, description.strip(), now));
        return EntryResponse.of(entry, now);
    }

    @Transactional
    public EntryResponse stop(long employeeId) {
        Employee employee = lockEmployee(employeeId);
        TimeEntry running = entries.findByEmployeeIdAndEndedAtIsNull(employeeId)
                .orElseThrow(() -> new ConflictException(
                        "У «%s» нет запущенного таймера".formatted(employee.getName())));
        Instant now = now();
        running.stop(now);
        return EntryResponse.of(running, now);
    }

    private Employee lockEmployee(long employeeId) {
        return employees.findByIdForUpdate(employeeId)
                .orElseThrow(() -> EmployeeService.notFound(employeeId));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
