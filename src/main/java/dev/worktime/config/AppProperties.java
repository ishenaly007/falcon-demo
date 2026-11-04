package dev.worktime.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки приложения из секции {@code app.*} в application.yml.
 *
 * @param timeZone      часовой пояс, в котором даты периода ("с 1 по 7 ноября")
 *                      превращаются в точные моменты времени
 * @param maxReportDays максимальная длина периода отчёта — защита от запросов "за 100 лет"
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(ZoneId timeZone, int maxReportDays) {

    public AppProperties {
        if (timeZone == null) {
            timeZone = ZoneId.systemDefault();
        }
        if (maxReportDays <= 0) {
            maxReportDays = 366;
        }
    }
}
