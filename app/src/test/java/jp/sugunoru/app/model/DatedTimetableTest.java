package jp.sugunoru.app.model;

import org.junit.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class DatedTimetableTest {
    @Test public void datesSurviveRefreshFailureAndNeverFallbackToAnotherDay() {
        LocalDate saturday = LocalDate.of(2026, 9, 19), sunday = saturday.plusDays(1);
        DatedTimetable dates = new DatedTimetable(List.of(
                new DatedTimetable.Service(Set.of(saturday), List.of(LocalTime.of(8, 0))),
                new DatedTimetable.Service(Set.of(sunday, sunday.plusDays(1)), List.of(LocalTime.of(9, 0)))));
        RoutePlan plan = RoutePlan.create(RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                "都07", "駅前", "終点", 0, 0, List.of(LocalTime.NOON), List.of())
                .withFetchedOdptTimetable(dates, 10);
        assertEquals(List.of(LocalTime.of(8, 0)), ScheduleEngine.timesFor(plan, saturday));
        assertEquals(List.of(LocalTime.of(9, 0)), ScheduleEngine.timesFor(plan, sunday));
        assertEquals(List.of(LocalTime.of(9, 0)), ScheduleEngine.timesFor(plan, sunday.plusDays(1)));
        assertEquals(List.of(), ScheduleEngine.timesFor(plan, sunday.plusDays(2)));
        assertEquals(sunday.atTime(9, 0), ScheduleEngine.nextDepartures(plan, saturday.atTime(9, 0), 1).get(0).at());
        assertTrue(ScheduleEngine.nextDepartures(plan, sunday.plusDays(2).atStartOfDay(), 1).isEmpty());
        assertEquals(dates, plan.withOfficialTimetableFetchFailure(11, "offline").datedTimetable());
        assertEquals(dates, plan.duplicate("copy").datedTimetable());
        assertNull(plan.withFetchedOfficialTimetable(List.of(LocalTime.NOON), List.of(), List.of(), 12).datedTimetable());
        assertTrue(plan.hasCachedTimetable());
    }
}
