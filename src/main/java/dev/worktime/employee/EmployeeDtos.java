package dev.worktime.employee;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import dev.worktime.timer.TimeEntry;

/** DTO сотрудников: что приходит в API и что уходит из него. */
public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    public record CreateEmployeeRequest(
            @NotBlank(message = "Укажите имя сотрудника")
            @Size(max = 100, message = "Имя не длиннее 100 символов")
            String name,

            @Size(max = 100, message = "Должность не длиннее 100 символов")
            String position) {
    }

    /** Сотрудник + его запущенный таймер (null, если таймер не идёт). */
    public record EmployeeResponse(
            Long id,
            String name,
            String position,
            Instant createdAt,
            ActiveTimer activeTimer) {

        public static EmployeeResponse of(Employee employee, TimeEntry running) {
            return new EmployeeResponse(
                    employee.getId(),
                    employee.getName(),
                    employee.getPosition(),
                    employee.getCreatedAt(),
                    running == null ? null : new ActiveTimer(running.getId(), running.getDescription(), running.getStartedAt()));
        }
    }

    public record ActiveTimer(Long entryId, String description, Instant startedAt) {
    }
}
