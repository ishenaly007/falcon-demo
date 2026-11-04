package dev.worktime.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("TimeMath.overlapSeconds")
class TimeMathTest {

    private static final Instant P_START = Instant.parse("2026-11-04T00:00:00Z");
    private static final Instant P_END = Instant.parse("2026-11-05T00:00:00Z");

    @ParameterizedTest(name = "[{0} — {1}) → {2} c")
    @CsvSource({
            // целиком внутри периода
            "2026-11-04T10:00:00Z, 2026-11-04T11:30:00Z, 5400",
            // начинается до периода
            "2026-11-03T23:00:00Z, 2026-11-04T01:00:00Z, 3600",
            // заканчивается после периода
            "2026-11-04T23:00:00Z, 2026-11-05T02:00:00Z, 3600",
            // накрывает период целиком
            "2026-11-03T00:00:00Z, 2026-11-06T00:00:00Z, 86400",
            // целиком до периода
            "2026-11-03T10:00:00Z, 2026-11-03T12:00:00Z, 0",
            // целиком после периода
            "2026-11-05T10:00:00Z, 2026-11-05T12:00:00Z, 0",
            // касается границы, но не пересекает (полуинтервал)
            "2026-11-05T00:00:00Z, 2026-11-05T01:00:00Z, 0",
            // нулевая длительность
            "2026-11-04T10:00:00Z, 2026-11-04T10:00:00Z, 0"
    })
    void overlap(Instant start, Instant end, long expected) {
        assertThat(TimeMath.overlapSeconds(start, end, P_START, P_END)).isEqualTo(expected);
    }
}
