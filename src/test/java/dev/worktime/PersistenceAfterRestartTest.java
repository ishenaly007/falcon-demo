package dev.worktime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import dev.worktime.employee.EmployeeDtos.CreateEmployeeRequest;
import dev.worktime.employee.EmployeeDtos.EmployeeResponse;
import dev.worktime.employee.EmployeeService;
import dev.worktime.timer.TimeEntryRepository;
import dev.worktime.timer.TimerService;

/**
 * Честная проверка требования "данные сохраняются после перезапуска":
 * приложение поднимается на файловой БД, пишет данные, полностью останавливается
 * и поднимается заново на том же файле.
 */
@DisplayName("Сохранение данных после перезапуска")
class PersistenceAfterRestartTest {

    @TempDir
    Path dataDir;

    @Test
    @DisplayName("сотрудники, записи и даже идущий таймер переживают перезапуск")
    void dataSurvivesRestart() {
        long annaId;
        try (ConfigurableApplicationContext app = startApp()) {
            EmployeeService employees = app.getBean(EmployeeService.class);
            TimerService timers = app.getBean(TimerService.class);

            annaId = employees.create(new CreateEmployeeRequest("Анна", "Дизайнер")).id();
            long petrId = employees.create(new CreateEmployeeRequest("Пётр", null)).id();
            timers.start(petrId, "Закрытая задача");
            timers.stop(petrId);
            timers.start(annaId, "Задача, которая идёт во время перезапуска");
        }

        try (ConfigurableApplicationContext app = startApp()) {
            List<EmployeeResponse> all = app.getBean(EmployeeService.class).findAll();
            assertThat(all).extracting(EmployeeResponse::name).containsExactly("Анна", "Пётр");

            EmployeeResponse anna = all.getFirst();
            assertThat(anna.position()).isEqualTo("Дизайнер");
            assertThat(anna.activeTimer()).isNotNull();
            assertThat(anna.activeTimer().description()).isEqualTo("Задача, которая идёт во время перезапуска");

            assertThat(app.getBean(TimeEntryRepository.class).count()).isEqualTo(2);

            // таймер, запущенный до перезапуска, можно остановить после
            assertThat(app.getBean(TimerService.class).stop(annaId).running()).isFalse();
        }

        // данные действительно лежат во временной папке теста, а не в ./data проекта
        assertThat(dataDir.resolve("worktime.mv.db")).exists();
    }

    /**
     * URL БД передаётся аргументом командной строки, а не через {@code builder.properties(...)}:
     * те задают лишь значения по умолчанию с самым низким приоритетом, и application.yml
     * их перекрывает. Именно так первая версия теста незаметно писала в ./data проекта.
     */
    private ConfigurableApplicationContext startApp() {
        return new SpringApplicationBuilder(WorkTimeApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=jdbc:h2:file:" + dataDir.resolve("worktime").toAbsolutePath(),
                        "--spring.main.banner-mode=off");
    }
}
