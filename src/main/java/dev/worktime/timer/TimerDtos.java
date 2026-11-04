package dev.worktime.timer;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TimerDtos {

    private TimerDtos() {
    }

    public record StartTimerRequest(
            @NotBlank(message = "Опишите задачу, над которой идёт работа")
            @Size(max = 500, message = "Описание задачи не длиннее 500 символов")
            String description) {
    }

    public record EntryResponse(
            Long id,
            Long employeeId,
            String employeeName,
            String description,
            Instant startedAt,
            Instant endedAt,
            boolean running,
            long durationSeconds) {

        public static EntryResponse of(TimeEntry entry, Instant now) {
            return new EntryResponse(
                    entry.getId(),
                    entry.getEmployee().getId(),
                    entry.getEmployee().getName(),
                    entry.getDescription(),
                    entry.getStartedAt(),
                    entry.getEndedAt(),
                    entry.isRunning(),
                    entry.duration(now).toSeconds());
        }
    }
}
