package dev.worktime.timer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    Optional<TimeEntry> findByEmployeeIdAndEndedAtIsNull(Long employeeId);

    List<TimeEntry> findByEndedAtIsNull();

    /**
     * Записи, которые хоть частично пересекаются с полуинтервалом [from, to).
     * Сюда попадают и записи, начатые до периода, и ещё идущие таймеры —
     * их время потом обрезается по границам периода (см. {@code TimeMath}).
     */
    @Query("""
            select t from TimeEntry t
            join fetch t.employee
            where t.startedAt < :to
              and (t.endedAt is null or t.endedAt > :from)
              and (:employeeId is null or t.employee.id = :employeeId)
            order by t.startedAt desc, t.id desc
            """)
    List<TimeEntry> findOverlapping(@Param("from") Instant from,
                                    @Param("to") Instant to,
                                    @Param("employeeId") Long employeeId);
}
