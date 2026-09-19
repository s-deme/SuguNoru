package jp.sugunoru.app.data;

import org.junit.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class OdptTimetableFetcherTest {
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
    @Test public void legacyDestinationStillFiltersBoardingAndDirection() {
        BusRoutePattern pattern = new BusRoutePattern("p", List.of(
                new BusRoutePattern.Stop(1, "a", "駅前", true, true),
                new BusRoutePattern.Stop(2, "b", "終点", true, true)));
        assertEquals(List.of(LocalTime.of(7, 5)), OdptTimetableFetcher.departuresToward(pattern, List.of(
                new OdptTimetableFetcher.TripStop("a", "07:05", true, true, "新橋駅前"),
                new OdptTimetableFetcher.TripStop("a", "07:10", true, true, "渋谷駅前"),
                new OdptTimetableFetcher.TripStop("a", "07:20", false, true, "新橋駅前")),
                "駅前", "新橋駅前方面"));
    }
}
