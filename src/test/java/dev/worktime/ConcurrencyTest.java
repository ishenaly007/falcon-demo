package dev.worktime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import dev.worktime.common.ConflictException;
import dev.worktime.employee.Employee;
import dev.worktime.employee.EmployeeRepository;
import dev.worktime.support.ApiTestBase;
import dev.worktime.timer.TimeEntry;
import dev.worktime.timer.TimeEntryRepository;
import dev.worktime.timer.TimerService;

@DisplayName("Гонки и целостность данных")
class ConcurrencyTest extends ApiTestBase {

    @Autowired
    private TimerService timerService;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private TimeEntryRepository entries;

    @Test
    @DisplayName("8 одновременных «стартов» одного сотрудника → ровно один таймер")
    void parallelStartsCreateExactlyOneTimer() throws Exception {
        long id = createEmployee("Анна");
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String description = "Попытка " + i;
                Callable<Boolean> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        timerService.start(id, description);
                        return true;
                    } catch (ConflictException | DataIntegrityViolationException expected) {
                        return false;
                    }
                };
                results.add(pool.submit(task));
            }
            ready.await();
            go.countDown();

            int started = 0;
            for (Future<Boolean> r : results) {
                if (r.get(10, TimeUnit.SECONDS)) {
                    started++;
                }
            }
            assertThat(started).isEqualTo(1);
            assertThat(entries.findByEndedAtIsNull()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("уникальный индекс в БД не даёт создать второй запущенный таймер даже в обход сервиса")
    void databaseRejectsSecondRunningTimer() {
        Employee anna = employees.save(new Employee("Анна", null, Instant.now()));
        entries.saveAndFlush(new TimeEntry(anna, "Первый", Instant.now()));

        assertThatThrownBy(() -> entries.saveAndFlush(new TimeEntry(anna, "Второй", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("остановленных записей у сотрудника может быть сколько угодно")
    void manyStoppedEntriesAllowed() {
        Employee anna = employees.save(new Employee("Анна", null, Instant.now()));
        Instant t = Instant.parse("2026-11-01T06:00:00Z");
        for (int i = 0; i < 3; i++) {
            TimeEntry e = new TimeEntry(anna, "Задача " + i, t);
            e.stop(t.plus(Duration.ofHours(1)));
            entries.saveAndFlush(e);
            t = t.plus(Duration.ofHours(2));
        }
        assertThat(entries.count()).isEqualTo(3);
    }
}
