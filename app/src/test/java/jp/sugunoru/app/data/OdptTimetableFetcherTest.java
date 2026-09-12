package jp.sugunoru.app.data;

import org.junit.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class OdptTimetableFetcherTest {
    @Test public void directDeparturesAreSortedDeduplicatedAndRespectCalendar() {
        OdptTimetableFetcher.CalendarTimes times = new OdptTimetableFetcher.CalendarTimes();
        times.add("odpt.Calendar:Weekday", List.of(LocalTime.of(8, 0), LocalTime.of(7, 0)));
        times.add("odpt.Calendar:Weekday", List.of(LocalTime.of(7, 0)));
        times.add("odpt.Calendar:Holiday", List.of(LocalTime.of(9, 0)));
        times.add("unknown", List.of(LocalTime.of(10, 0)));
        assertEquals(List.of(LocalTime.of(7, 0), LocalTime.of(8, 0)), times.timetable().weekdayTimes());
        assertEquals(List.of(LocalTime.of(9, 0)), times.timetable().weekendTimes());
        assertEquals(List.of(LocalTime.of(9, 0)), times.timetable().holidayTimes());
    }

    @Test public void routeNamesMatchFullWidthButNotDifferentBranches() {
        assertTrue(OdptTimetableFetcher.matchesRoute("都０７", "都07（錦糸町駅前〜門前仲町）"));
        assertTrue(OdptTimetableFetcher.matchesRoute("陽１２－１", "陽12-1（東陽町駅前〜病院）"));
        assertFalse(OdptTimetableFetcher.matchesRoute("陽12-2", "陽12-1（病院）"));
        assertFalse(OdptTimetableFetcher.matchesRoute("錦13乙", "錦13甲（晴海）"));
        assertFalse(OdptTimetableFetcher.matchesRoute("都070", "都07"));
    }

    @Test public void onlyBoardsTripsThatReachTheAlightingStopLater() {
        BusRoutePattern pattern = new BusRoutePattern("loop", List.of(
                new BusRoutePattern.Stop(0, "a", "A", true, true),
                new BusRoutePattern.Stop(1, "b", "B", true, true),
                new BusRoutePattern.Stop(2, "a", "A", true, true),
                new BusRoutePattern.Stop(3, "c", "C", true, true)));
        List<OdptTimetableFetcher.TripStop> trip = List.of(
                new OdptTimetableFetcher.TripStop("a", "07:00", true, true),
                new OdptTimetableFetcher.TripStop("b", "07:10", true, true),
                new OdptTimetableFetcher.TripStop("a", "07:20", true, true),
                new OdptTimetableFetcher.TripStop("c", "", false, true));
        assertEquals(List.of(LocalTime.of(7, 0)),
                OdptTimetableFetcher.departuresBetween(pattern, trip, "A", "B"));
        assertEquals(List.of(LocalTime.of(7, 0), LocalTime.of(7, 20)),
                OdptTimetableFetcher.departuresBetween(pattern, trip, "A", "C"));
        assertEquals(List.of(), OdptTimetableFetcher.departuresBetween(pattern, trip.subList(0, 3), "A", "C"));
        assertEquals(List.of(), OdptTimetableFetcher.departuresBetween(pattern, List.of(
                new OdptTimetableFetcher.TripStop("a", "07:00", false, true),
                new OdptTimetableFetcher.TripStop("c", "", false, true)), "A", "C"));
        assertEquals(List.of(), OdptTimetableFetcher.departuresBetween(pattern, List.of(
                new OdptTimetableFetcher.TripStop("a", "07:00", true, true),
                new OdptTimetableFetcher.TripStop("c", "", false, false)), "A", "C"));
    }
    @Test public void groupsBoardingTimesByCalendarAndDestination() throws Exception {
        OfficialTimetableParser.Timetable result = OdptTimetableFetcher.timetableAt(
                List.of(
                        new OdptTimetableFetcher.TimetableRecord(
                                "odpt.Calendar:Weekday", "pole", "07:05", "新橋駅前", true),
                        new OdptTimetableFetcher.TimetableRecord(
                                "odpt.Calendar:Weekday", "pole", "07:10", "渋谷駅前", true),
                        new OdptTimetableFetcher.TimetableRecord(
                                "odpt.Calendar:Saturday", "pole", "08:20", "新橋駅前", true),
                        new OdptTimetableFetcher.TimetableRecord(
                                "odpt.Calendar:Holiday", "pole", "09:30", "新橋駅前", true)),
                "pole", "新橋駅前方面");

        assertEquals(List.of(LocalTime.of(7, 5)), result.weekdayTimes());
        assertEquals(List.of(LocalTime.of(8, 20), LocalTime.of(9, 30)), result.weekendTimes());
        assertEquals(List.of(LocalTime.of(9, 30)), result.holidayTimes());
    }
}
