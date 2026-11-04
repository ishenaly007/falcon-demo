package dev.worktime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkTimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkTimeApplication.class, args);
    }
}
