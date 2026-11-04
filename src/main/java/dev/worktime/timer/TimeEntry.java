package dev.worktime.timer;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import dev.worktime.employee.Employee;

/**
 * Одна запись учёта времени: сотрудник работал над задачей {@code description}
 * с {@code startedAt} по {@code endedAt}. Пока {@code endedAt == null}, таймер идёт.
 */
@Entity
@Table(name = "time_entry")
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected TimeEntry() {
        // для JPA
    }

    public TimeEntry(Employee employee, String description, Instant startedAt) {
        this.employee = employee;
        this.description = description;
        this.startedAt = startedAt;
    }

    public boolean isRunning() {
        return endedAt == null;
    }

    /** Останавливает таймер. Если часы "ушли назад", длительность не станет отрицательной. */
    public void stop(Instant at) {
        if (!isRunning()) {
            throw new IllegalStateException("Таймер уже остановлен");
        }
        this.endedAt = at.isBefore(startedAt) ? startedAt : at;
    }

    /** Фактический конец: время остановки, а для идущего таймера — "сейчас". */
    public Instant effectiveEnd(Instant now) {
        return isRunning() ? now : endedAt;
    }

    public Duration duration(Instant now) {
        Duration d = Duration.between(startedAt, effectiveEnd(now));
        return d.isNegative() ? Duration.ZERO : d;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
