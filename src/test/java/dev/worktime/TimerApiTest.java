package dev.worktime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.worktime.support.ApiTestBase;
import dev.worktime.support.TestClockConfig;
import dev.worktime.timer.TimeEntryRepository;

@DisplayName("Таймер")
class TimerApiTest extends ApiTestBase {

    @Autowired
    private TimeEntryRepository entries;

    @Test
    @DisplayName("старт → идёт, стоп через 1,5 часа → 5400 секунд")
    void startAndStop() throws Exception {
        long anna = createEmployee("Анна");

        start(anna, "  Вёрстка лендинга  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.description").value("Вёрстка лендинга"))
                .andExpect(jsonPath("$.startedAt").value(TestClockConfig.START.toString()))
                .andExpect(jsonPath("$.endedAt").value(nullValue()));

        mvc.perform(get("/api/employees/{id}", anna))
                .andExpect(jsonPath("$.activeTimer.description").value("Вёрстка лендинга"));

        clock.advance(Duration.ofMinutes(90));

        stop(anna)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.durationSeconds").value(5400))
                .andExpect(jsonPath("$.endedAt").value(TestClockConfig.START.plus(Duration.ofMinutes(90)).toString()));

        mvc.perform(get("/api/employees/{id}", anna))
                .andExpect(jsonPath("$.activeTimer").value(nullValue()));
    }

    @Test
    @DisplayName("повторный запуск идущего таймера — 409, текущий таймер не сбрасывается")
    void secondStartIsRejected() throws Exception {
        long anna = createEmployee("Анна");
        start(anna, "Задача 1").andExpect(status().isCreated());
        clock.advance(Duration.ofMinutes(10));

        start(anna, "Задача 2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("уже запущен")))
                .andExpect(jsonPath("$.detail").value(containsString("Задача 1")));

        // первый таймер жив и не перезапущен: та же задача, то же время старта
        mvc.perform(get("/api/employees/{id}", anna))
                .andExpect(jsonPath("$.activeTimer.description").value("Задача 1"))
                .andExpect(jsonPath("$.activeTimer.startedAt").value(TestClockConfig.START.toString()));
        assertThat(entries.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("после остановки таймер можно запустить снова — появляется новая запись")
    void restartAfterStop() throws Exception {
        long anna = createEmployee("Анна");
        start(anna, "Утро").andExpect(status().isCreated());
        clock.advance(Duration.ofHours(1));
        stop(anna).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(30));

        start(anna, "После обеда")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("После обеда"));
        assertThat(entries.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("стоп без запущенного таймера и повторный стоп — 409")
    void stopWithoutRunningTimer() throws Exception {
        long anna = createEmployee("Анна");
        stop(anna)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("У «Анна» нет запущенного таймера"));

        start(anna, "Работа").andExpect(status().isCreated());
        stop(anna).andExpect(status().isOk());
        stop(anna).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("пустое описание задачи — 400")
    void blankDescriptionRejected() throws Exception {
        long anna = createEmployee("Анна");
        start(anna, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Опишите задачу, над которой идёт работа"));
        assertThat(entries.count()).isZero();
    }

    @Test
    @DisplayName("таймер несуществующего сотрудника — 404")
    void unknownEmployee() throws Exception {
        start(424242, "Задача").andExpect(status().isNotFound());
        stop(424242).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("таймеры разных сотрудников независимы")
    void independentTimers() throws Exception {
        long anna = createEmployee("Анна");
        long petr = createEmployee("Пётр");
        start(anna, "Дизайн").andExpect(status().isCreated());
        start(petr, "Бэкенд").andExpect(status().isCreated());

        stop(anna).andExpect(status().isOk());
        mvc.perform(get("/api/employees/{id}", petr))
                .andExpect(jsonPath("$.activeTimer.description").value("Бэкенд"));
    }
}
