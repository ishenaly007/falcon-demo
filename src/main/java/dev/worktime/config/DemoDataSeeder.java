package dev.worktime.config;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.worktime.employee.Employee;
import dev.worktime.employee.EmployeeRepository;
import dev.worktime.timer.TimeEntry;
import dev.worktime.timer.TimeEntryRepository;

/**
 * Демо-данные для знакомства с приложением: включается профилем {@code demo}
 * и срабатывает только на пустой БД. Все имена и задачи вымышленные.
 */
@Component
@Profile("demo")
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private record Person(String name, String position, List<String> tasks) {
    }

    private static final List<Person> PEOPLE = List.of(
            new Person("Алина Морозова", "Продакт-менеджер",
                    List.of("Планирование спринта", "Интервью с пользователями", "Приоритизация бэклога", "Демо для заказчика")),
            new Person("Тимур Асанов", "Backend-разработчик",
                    List.of("API отчётов", "Ревью pull request'ов", "Оптимизация запросов к БД", "Миграции Liquibase")),
            new Person("Дарья Ким", "UI/UX дизайнер",
                    List.of("Макеты экрана отчётов", "Дизайн-система: кнопки", "Прототип мобильной версии")),
            new Person("Эрлан Беков", "QA-инженер",
                    List.of("Регрессионное тестирование", "Автотесты API", "Проверка мобильной вёрстки")));

    private final EmployeeRepository employees;
    private final TimeEntryRepository entries;
    private final AppProperties props;
    private final Clock clock;

    DemoDataSeeder(EmployeeRepository employees, TimeEntryRepository entries, AppProperties props, Clock clock) {
        this.employees = employees;
        this.entries = entries;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (employees.count() > 0) {
            log.info("Демо-данные не нужны: в БД уже есть сотрудники");
            return;
        }
        ZoneId zone = props.timeZone();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        LocalDate today = LocalDate.ofInstant(now, zone);
        Random random = new Random(42); // фиксированное зерно — одинаковые данные при каждом запуске

        for (int p = 0; p < PEOPLE.size(); p++) {
            Person person = PEOPLE.get(p);
            Employee employee = employees.save(new Employee(person.name(), person.position(), now.minus(15, ChronoUnit.DAYS)));

            for (int dayOffset = 14; dayOffset >= 1; dayOffset--) {
                LocalDate day = today.minusDays(dayOffset);
                if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    continue;
                }
                Instant cursor = day.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant()
                        .plus(random.nextInt(60), ChronoUnit.MINUTES);
                int blocks = 2 + random.nextInt(3);
                for (int b = 0; b < blocks; b++) {
                    String task = person.tasks().get(random.nextInt(person.tasks().size()));
                    Instant end = cursor.plus(45 + random.nextInt(150), ChronoUnit.MINUTES);
                    TimeEntry entry = new TimeEntry(employee, task, cursor);
                    entry.stop(end);
                    entries.save(entry);
                    cursor = end.plus(10 + random.nextInt(50), ChronoUnit.MINUTES);
                }
            }

            // двое "сейчас работают", чтобы сразу было видно живые таймеры
            if (p == 1 || p == 2) {
                entries.save(new TimeEntry(employee, person.tasks().getFirst(),
                        now.minus(20 + 35L * p, ChronoUnit.MINUTES)));
            }
        }
        log.info("Добавлены демо-данные: сотрудников — {}, записей — {}", employees.count(), entries.count());
    }
}
