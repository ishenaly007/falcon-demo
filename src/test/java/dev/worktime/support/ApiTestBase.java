package dev.worktime.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import dev.worktime.employee.EmployeeRepository;
import dev.worktime.timer.TimeEntryRepository;

/**
 * Общая база для API-тестов: поднимает приложение целиком (контроллеры, сервисы, Liquibase, H2 в памяти),
 * перед каждым тестом очищает БД и возвращает часы в исходную точку.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class ApiTestBase {

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private TimeEntryRepository entries;

    @Autowired
    private EmployeeRepository employees;

    @BeforeEach
    void resetState() {
        entries.deleteAllInBatch();
        employees.deleteAllInBatch();
        clock.set(TestClockConfig.START);
    }

    protected long createEmployee(String name) throws Exception {
        String body = mvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"position\":\"Разработчик\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Matcher m = ID.matcher(body);
        if (!m.find()) {
            throw new AssertionError("В ответе нет id: " + body);
        }
        return Long.parseLong(m.group(1));
    }

    protected ResultActions start(long employeeId, String description) throws Exception {
        return mvc.perform(post("/api/employees/{id}/timer/start", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"%s\"}".formatted(description)));
    }

    protected ResultActions stop(long employeeId) throws Exception {
        return mvc.perform(post("/api/employees/{id}/timer/stop", employeeId));
    }
}
