package jp.sugunoru.app.model;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScheduleEngineTest {
    private RoutePlan plan(
            String name, int walk, int ride, RoutePlan.Direction direction,
            List<LocalTime> weekday, List<LocalTime> weekend
    ) {
        return RoutePlan.create(direction, RoutePlan.Mode.TRAIN, name, "テスト駅", "都心方面",
                walk, ride, weekday, weekend);
    }

    @Test public void parsesCommonJapaneseTimetableInput() {
        assertEquals(
                List.of(LocalTime.of(7, 5), LocalTime.of(7, 35), LocalTime.of(8, 10)),
                ScheduleEngine.parseTimes("07：35、0705\n8:10 07:35")
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidTime() {
        ScheduleEngine.parseTimes("07:20 25:10");
    }

    @Test public void walkingTimeSkipsUnreachableDeparture() {
        RoutePlan plan = plan("A線", 8, 20, RoutePlan.Direction.OUTBOUND,
                List.of(LocalTime.of(9, 5), LocalTime.of(9, 12), LocalTime.of(9, 30)), List.of());
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 9, 0);

        List<ScheduleEngine.Departure> result = ScheduleEngine.nextDepartures(plan, now, 2);

        assertEquals(LocalTime.of(9, 12), result.get(0).at().toLocalTime());
        assertEquals(12, result.get(0).waitMinutes());
    }

    @Test public void waitMinutesRoundUpWhenCurrentTimeHasSeconds() {
        RoutePlan plan = plan("A線", 0, 20, RoutePlan.Direction.OUTBOUND,
                List.of(LocalTime.of(9, 12)), List.of());
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 9, 0, 30);

        ScheduleEngine.Departure result = ScheduleEngine.nextDepartures(plan, now, 1).get(0);

        assertEquals(12, result.waitMinutes());
    }

    @Test public void comparesByEstimatedArrivalNotOnlyDeparture() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 9, 0);
        RoutePlan local = plan("各停", 0, 40, RoutePlan.Direction.OUTBOUND,
                List.of(LocalTime.of(9, 5)), List.of());
        RoutePlan express = plan("快速", 0, 15, RoutePlan.Direction.OUTBOUND,
                List.of(LocalTime.of(9, 12)), List.of());

        List<ScheduleEngine.RouteOption> result = ScheduleEngine.compare(
                List.of(local, express), RoutePlan.Direction.OUTBOUND, now);

        assertEquals("快速", result.get(0).plan().routeName());
        assertEquals(LocalTime.of(9, 27), result.get(0).estimatedArrival().toLocalTime());
    }

    @Test public void findsNextDayAfterLastService() {
        RoutePlan plan = plan("A線", 0, 10, RoutePlan.Direction.OUTBOUND,
                List.of(LocalTime.of(6, 30), LocalTime.of(23, 10)),
                List.of(LocalTime.of(7, 0)));
        LocalDateTime sundayNight = LocalDateTime.of(2026, 8, 30, 23, 30);

        ScheduleEngine.Departure next = ScheduleEngine.nextDepartures(plan, sundayNight, 1).get(0);

        assertEquals(LocalDateTime.of(2026, 8, 31, 6, 30), next.at());
        assertTrue(next.nextDay());
    }

    @Test public void holidayScheduleOverridesDayOfWeek() {
        RoutePlan plan = new RoutePlan(null, RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                "祝日バス", "駅前", "公園", 0, 10, 0, true,
                List.of(LocalTime.of(8, 0)), List.of(LocalTime.of(9, 0)),
                List.of(LocalTime.of(10, 0)), "", null, 1);
        LocalDateTime holiday = LocalDateTime.of(2026, 8, 31, 7, 0);

        ScheduleEngine.Departure result = ScheduleEngine.nextDepartures(
                plan, holiday, 1, Set.of(holiday.toLocalDate())).get(0);

        assertEquals(LocalTime.of(10, 0), result.at().toLocalTime());
    }

    @Test public void disabledRoutesAreExcludedFromComparison() {
        RoutePlan disabled = new RoutePlan(null, RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.TRAIN,
                "休止中", "駅", "方面", 0, 10, 0, false,
                List.of(LocalTime.of(9, 10)), List.of(), List.of(), "", null, 1);

        assertTrue(ScheduleEngine.compare(List.of(disabled), RoutePlan.Direction.OUTBOUND,
                LocalDateTime.of(2026, 8, 31, 9, 0)).isEmpty());
    }

    @Test public void finalWalkIsIncludedInArrivalComparison() {
        RoutePlan nearExit = new RoutePlan(null, RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.TRAIN,
                "出口近く", "駅", "方面", 0, 20, 0, true,
                List.of(LocalTime.of(9, 10)), List.of(), List.of(), "", null, 1);
        RoutePlan farExit = new RoutePlan(null, RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.TRAIN,
                "出口遠く", "駅", "方面", 0, 15, 20, true,
                List.of(LocalTime.of(9, 5)), List.of(), List.of(), "", null, 1);

        List<ScheduleEngine.RouteOption> result = ScheduleEngine.compare(
                List.of(farExit, nearExit), RoutePlan.Direction.OUTBOUND,
                LocalDateTime.of(2026, 8, 31, 9, 0));

        assertEquals("出口近く", result.get(0).plan().routeName());
    }
}
