package dev.worktime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.worktime.support.ApiTestBase;

/** Часовой пояс по умолчанию в тестах — Europe/Moscow (UTC+3), см. application-test.yml. */
@DisplayName("Отчёты")
class ReportApiTest extends ApiTestBase {

    @Test
    @DisplayName("пустой отчёт: нет ни сотрудников, ни записей — 200 и нули, а не ошибка")
    void emptyReportWithoutEmployees() throws Exception {
        mvc.perform(get("/api/reports/summary").param("from", "2026-11-01").param("to", "2026-11-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empty").value(true))
                .andExpect(jsonPath("$.totalSeconds").value(0))
                .andExpect(jsonPath("$.entryCount").value(0))
                .andExpect(jsonPath("$.employees.length()").value(0));

        mvc.perform(get("/api/reports/entries").param("from", "2026-11-01").param("to", "2026-11-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("пустой отчёт: сотрудники есть, но в периоде записей нет — у каждого 0")
    void emptyReportWithEmployees() throws Exception {
        long anna = createEmployee("Анна");
        createEmployee("Пётр");
        // запись есть, но в другом периоде
        start(anna, "Старая задача");
        clock.advance(Duration.ofHours(1));
        stop(anna);

        mvc.perform(get("/api/reports/summary").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empty").value(true))
                .andExpect(jsonPath("$.totalSeconds").value(0))
                .andExpect(jsonPath("$.employees.length()").value(2))
                .andExpect(jsonPath("$.employees[0].totalSeconds").value(0))
                .andExpect(jsonPath("$.employees[1].totalSeconds").value(0));
    }

    @Test
    @DisplayName("итоги суммируются по каждому сотруднику, лидер — первым")
    void totalsPerEmployee() throws Exception {
        long anna = createEmployee("Анна");
        long petr = createEmployee("Пётр");

        start(anna, "Задача А1"); clock.advance(Duration.ofHours(1)); stop(anna);
        start(anna, "Задача А2"); clock.advance(Duration.ofMinutes(30)); stop(anna);
        start(petr, "Задача П1"); clock.advance(Duration.ofHours(3)); stop(petr);

        mvc.perform(get("/api/reports/summary").param("from", "2026-11-04").param("to", "2026-11-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empty").value(false))
                .andExpect(jsonPath("$.totalSeconds").value(5400 + 10800))
                .andExpect(jsonPath("$.entryCount").value(3))
                .andExpect(jsonPath("$.employees[0].name").value("Пётр"))
                .andExpect(jsonPath("$.employees[0].totalSeconds").value(10800))
                .andExpect(jsonPath("$.employees[1].name").value("Анна"))
                .andExpect(jsonPath("$.employees[1].totalSeconds").value(5400))
                .andExpect(jsonPath("$.employees[1].entryCount").value(2));

        // фильтр записей по сотруднику, новые сверху
        mvc.perform(get("/api/reports/entries")
                        .param("from", "2026-11-04").param("to", "2026-11-04")
                        .param("employeeId", String.valueOf(anna)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].description").value("Задача А2"))
                .andExpect(jsonPath("$[1].description").value("Задача А1"));
    }

    @Test
    @DisplayName("запись через полночь делится между днями по границе периода")
    void entryAcrossMidnightIsClipped() throws Exception {
        long anna = createEmployee("Анна");
        clock.set(Instant.parse("2026-11-04T20:00:00Z"));      // 23:00 по Москве
        start(anna, "Ночной релиз");
        clock.advance(Duration.ofHours(2));                        // 01:00 следующего дня
        stop(anna);

        mvc.perform(get("/api/reports/summary").param("from", "2026-11-04").param("to", "2026-11-04"))
                .andExpect(jsonPath("$.totalSeconds").value(3600));
        mvc.perform(get("/api/reports/summary").param("from", "2026-11-05").param("to", "2026-11-05"))
                .andExpect(jsonPath("$.totalSeconds").value(3600));
        mvc.perform(get("/api/reports/summary").param("from", "2026-11-04").param("to", "2026-11-05"))
                .andExpect(jsonPath("$.totalSeconds").value(7200));

        mvc.perform(get("/api/reports/entries").param("from", "2026-11-05").param("to", "2026-11-05"))
                .andExpect(jsonPath("$[0].durationSeconds").value(7200))
                .andExpect(jsonPath("$[0].secondsInPeriod").value(3600));
    }

    @Test
    @DisplayName("идущий таймер учитывается в отчёте до текущего момента")
    void runningTimerCountsUntilNow() throws Exception {
        long anna = createEmployee("Анна");
        start(anna, "Созвон с клиентом");
        clock.advance(Duration.ofMinutes(45));

        mvc.perform(get("/api/reports/summary").param("from", "2026-11-04").param("to", "2026-11-04"))
                .andExpect(jsonPath("$.totalSeconds").value(2700))
                .andExpect(jsonPath("$.employees[0].running").value(true));
        mvc.perform(get("/api/reports/entries").param("from", "2026-11-04").param("to", "2026-11-04"))
                .andExpect(jsonPath("$[0].running").value(true))
                .andExpect(jsonPath("$[0].durationSeconds").value(2700));
    }

    @Test
    @DisplayName("идущий таймер не попадает в отчёт за будущий период")
    void runningTimerNotInFuturePeriod() throws Exception {
        long anna = createEmployee("Анна");
        start(anna, "Задача");

        mvc.perform(get("/api/reports/summary").param("from", "2026-11-10").param("to", "2026-11-12"))
                .andExpect(jsonPath("$.empty").value(true))
                .andExpect(jsonPath("$.totalSeconds").value(0));
    }

    @Test
    @DisplayName("параметр tz меняет границы дней")
    void timeZoneParameter() throws Exception {
        long anna = createEmployee("Анна");
        clock.set(Instant.parse("2026-11-04T20:00:00Z"));      // 23:00 в Москве, но уже 01:00 5 ноября в Бишкеке
        start(anna, "Задача");
        clock.advance(Duration.ofMinutes(30));
        stop(anna);

        mvc.perform(get("/api/reports/summary").param("from", "2026-11-04").param("to", "2026-11-04"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Moscow"))
                .andExpect(jsonPath("$.totalSeconds").value(1800));
        mvc.perform(get("/api/reports/summary").param("from", "2026-11-04").param("to", "2026-11-04")
                        .param("tz", "Asia/Bishkek"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Bishkek"))
                .andExpect(jsonPath("$.totalSeconds").value(0));
    }

    @Test
    @DisplayName("некорректные параметры периода — 400 с объяснением")
    void invalidParameters() throws Exception {
        mvc.perform(get("/api/reports/summary").param("from", "2026-11-07").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("позже даты конца")));
        mvc.perform(get("/api/reports/summary").param("from", "2020-01-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("слишком длинный")));
        mvc.perform(get("/api/reports/summary").param("from", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Не указан обязательный параметр «to»"));
        mvc.perform(get("/api/reports/summary").param("from", "вчера").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("«from»")));
        mvc.perform(get("/api/reports/summary").param("from", "2026-11-01").param("to", "2026-11-01")
                        .param("tz", "Mars/Olympus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("часовой пояс")));
        mvc.perform(get("/api/reports/entries").param("from", "2026-11-01").param("to", "2026-11-01")
                        .param("employeeId", "777777"))
                .andExpect(status().isNotFound());
    }
}
