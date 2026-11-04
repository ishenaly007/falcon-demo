package dev.worktime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import dev.worktime.support.ApiTestBase;

@DisplayName("Сотрудники")
class EmployeeApiTest extends ApiTestBase {

    @Test
    @DisplayName("добавляются и выводятся списком по алфавиту")
    void createAndList() throws Exception {
        mvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Пётр Соколов  \",\"position\":\"  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/employees/")))
                .andExpect(jsonPath("$.name").value("Пётр Соколов"))      // пробелы по краям обрезаны
                .andExpect(jsonPath("$.position").value(nullValue()))     // пустая должность → null
                .andExpect(jsonPath("$.activeTimer").value(nullValue()));
        createEmployee("Анна Иванова");

        mvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Анна Иванова"))
                .andExpect(jsonPath("$[1].name").value("Пётр Соколов"));
    }

    @Test
    @DisplayName("без имени — 400 с понятным сообщением")
    void blankNameRejected() throws Exception {
        mvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Укажите имя сотрудника"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("слишком длинное имя — 400")
    void tooLongNameRejected() throws Exception {
        mvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted("Я".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Имя не длиннее 100 символов"));
    }

    @Test
    @DisplayName("битый JSON — 400, а не 500")
    void malformedJson() throws Exception {
        mvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Тело запроса должно быть корректным JSON"));
    }

    @Test
    @DisplayName("несуществующий сотрудник — 404")
    void unknownEmployee() throws Exception {
        mvc.perform(get("/api/employees/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Сотрудник с id=999999 не найден"));
    }
}
