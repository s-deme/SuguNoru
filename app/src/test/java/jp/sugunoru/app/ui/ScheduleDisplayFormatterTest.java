package jp.sugunoru.app.ui;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ScheduleDisplayFormatterTest {
    @Test public void formatsDashboardAndWidgetTimesConsistently() {
        LocalDateTime value = LocalDateTime.of(2026, 8, 31, 7, 5);

        assertEquals("7:05", ScheduleDisplayFormatter.clock(value));
        assertEquals("07:05", ScheduleDisplayFormatter.time(value));
    }

    @Test public void formatsJapaneseDateAndEditableTimetable() {
        assertEquals("8月31日（月）", ScheduleDisplayFormatter.date(LocalDate.of(2026, 8, 31)));
        assertEquals("06:00 06:15 06:30 06:45 07:00 07:15\n07:30",
                ScheduleDisplayFormatter.times(List.of(
                        LocalTime.of(6, 0), LocalTime.of(6, 15), LocalTime.of(6, 30),
                        LocalTime.of(6, 45), LocalTime.of(7, 0), LocalTime.of(7, 15),
                        LocalTime.of(7, 30))));
    }
}
