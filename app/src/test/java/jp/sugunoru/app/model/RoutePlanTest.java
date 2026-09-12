package jp.sugunoru.app.model;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class RoutePlanTest {
    @Test public void refreshAndDuplicateKeepTheAlightingSelection() {
        RoutePlan plan = new RoutePlan("trip", RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                "都07", "錦糸町駅前", "木場駅前", 0, 0, 0, true,
                List.of(LocalTime.of(7, 0)), List.of(), List.of(), "", null, 1,
                "", 1, 1, "", true, true);
        assertTrue(plan.duplicate("都07").destinationIsStop());
        assertTrue(plan.withFetchedOdptTimetable(plan.weekdayTimes(), List.of(), List.of(), 2).destinationIsStop());
        assertTrue(plan.withFetchedOfficialTimetable(plan.weekdayTimes(), List.of(), List.of(), 2).destinationIsStop());
        assertTrue(plan.withOfficialTimetableFetchFailure(3, "offline").destinationIsStop());
        assertEquals("木場駅前", plan.duplicate("都07").destination());
    }

    @Test public void trimsRequiredRouteLabels() {
        RoutePlan plan = RoutePlan.create(RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                "\t 路線 \n", " 停留所 ", " 方面 ", 0, 0,
                List.of(LocalTime.of(8, 0)), List.of());
        assertEquals("路線", plan.routeName());
        assertEquals("停留所", plan.stopName());
        assertEquals("方面", plan.destination());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptySchedulesWithoutAnOfficialSource() {
        RoutePlan.create(RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                "路線", "停留所", "方面", 0, 0, List.of(), List.of());
    }

    @Test public void duplicateKeepsRouteDetailsButUsesANewIdentity() {
        RoutePlan source = new RoutePlan("source-id", RoutePlan.Direction.RETURN, RoutePlan.Mode.BUS,
                "循環バス", "駅前", "市役所", 4, 18, 6, false,
                List.of(LocalTime.of(8, 10)), List.of(LocalTime.of(9, 10)),
                List.of(LocalTime.of(10, 10)), "2番乗り場", LocalDate.of(2026, 12, 31), 1);

        RoutePlan copy = source.duplicate("循環バス コピー");

        assertNotEquals(source.id(), copy.id());
        assertEquals("循環バス コピー", copy.routeName());
        assertEquals(source.stopName(), copy.stopName());
        assertEquals(source.destination(), copy.destination());
        assertEquals(source.walkMinutes(), copy.walkMinutes());
        assertEquals(source.rideMinutes(), copy.rideMinutes());
        assertEquals(source.finalWalkMinutes(), copy.finalWalkMinutes());
        assertEquals(source.enabled(), copy.enabled());
        assertEquals(source.weekdayTimes(), copy.weekdayTimes());
        assertEquals(source.weekendTimes(), copy.weekendTimes());
        assertEquals(source.holidayTimes(), copy.holidayTimes());
        assertEquals(source.notes(), copy.notes());
        assertEquals(source.validUntil(), copy.validUntil());
    }

    @Test public void officialTimetableFailureKeepsTheLastFetchedSchedule() {
        RoutePlan source = new RoutePlan("source-id", RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.TRAIN,
                "公式線", "公式駅", "都心方面", 5, 20, 0, true,
                List.of(LocalTime.of(7, 10)), List.of(LocalTime.of(8, 10)), List.of(), "", null, 10,
                "https://example.com/timetable", 100, 100, "");

        RoutePlan failed = source.withOfficialTimetableFetchFailure(200, "インターネットに接続できません");
        assertEquals(List.of(LocalTime.of(7, 10)), failed.weekdayTimes());
        assertEquals(List.of(LocalTime.of(8, 10)), failed.weekendTimes());
        assertEquals(100, failed.officialTimetableFetchedAtEpochMillis());
        assertEquals(200, failed.officialTimetableAttemptedAtEpochMillis());
        assertEquals("インターネットに接続できません", failed.officialTimetableLastError());
    }

    @Test public void allowsAnOfficialSourceBeforeItsFirstSuccessfulFetch() {
        RoutePlan waiting = new RoutePlan(null, RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                "公式バス", "駅前", "市役所", 2, 12, 0, true,
                List.of(), List.of(), List.of(), "", null, 1,
                "https://example.com/bus", 0, 0, "");

        assertTrue(waiting.hasOfficialTimetableSource());
        assertTrue(!waiting.hasCachedTimetable());
    }
}
