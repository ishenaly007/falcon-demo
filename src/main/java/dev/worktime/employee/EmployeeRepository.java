package dev.worktime.employee;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Загружает сотрудника с блокировкой строки (SELECT … FOR UPDATE).
     * Все операции с таймером одного сотрудника выполняются строго по очереди,
     * поэтому два одновременных "старта" не создадут два таймера.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);
}
