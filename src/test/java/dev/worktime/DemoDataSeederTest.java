package dev.worktime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import dev.worktime.employee.EmployeeRepository;
import dev.worktime.timer.TimeEntryRepository;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo-seed;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "demo"})
@DisplayName("Профиль demo")
class DemoDataSeederTest {

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private TimeEntryRepository entries;

    @Test
    @DisplayName("на пустой БД создаёт вымышленных сотрудников, историю и два идущих таймера")
    void seedsDemoData() {
        assertThat(employees.count()).isEqualTo(4);
        assertThat(entries.count()).isGreaterThan(20);
        assertThat(entries.findByEndedAtIsNull()).hasSize(2);
    }
}
