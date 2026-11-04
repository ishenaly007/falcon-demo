package dev.worktime.employee;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.worktime.common.NotFoundException;
import dev.worktime.employee.EmployeeDtos.CreateEmployeeRequest;
import dev.worktime.employee.EmployeeDtos.EmployeeResponse;
import dev.worktime.timer.TimeEntry;
import dev.worktime.timer.TimeEntryRepository;

@Service
public class EmployeeService {

    private final EmployeeRepository employees;
    private final TimeEntryRepository entries;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employees, TimeEntryRepository entries, Clock clock) {
        this.employees = employees;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        Employee employee = employees.save(new Employee(
                request.name().strip(),
                blankToNull(request.position()),
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)));
        return EmployeeResponse.of(employee, null);
    }

    /** Все сотрудники по алфавиту. Запущенные таймеры подтягиваются одним запросом, без N+1. */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> findAll() {
        Map<Long, TimeEntry> runningByEmployee = entries.findByEndedAtIsNull().stream()
                .collect(Collectors.toMap(e -> e.getEmployee().getId(), Function.identity()));
        return employees.findAll(Sort.by("name", "id")).stream()
                .map(e -> EmployeeResponse.of(e, runningByEmployee.get(e.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse findById(long id) {
        Employee employee = employees.findById(id).orElseThrow(() -> notFound(id));
        return EmployeeResponse.of(employee, entries.findByEmployeeIdAndEndedAtIsNull(id).orElse(null));
    }

    public static NotFoundException notFound(long id) {
        return new NotFoundException("Сотрудник с id=" + id + " не найден");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
