package jp.sugunoru.app.data;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class BusRoutePatternTest {
    private static BusRoutePattern.Stop stop(int index, String name) {
        return new BusRoutePattern.Stop(index, name + "-pole", name, true, true);
    }

    @Test public void ordersStopsAndNeverInventsAReverseOrBranchConnection() {
        BusRoutePattern outward = new BusRoutePattern("out", List.of(stop(30, "C"), stop(10, "A"), stop(20, "B")));
        BusRoutePattern branch = new BusRoutePattern("branch", List.of(stop(0, "A"), stop(1, "D")));
        assertEquals(List.of("B", "C", "D"), BusRoutePattern.alightingStops(List.of(outward, branch), "A"));
        assertEquals(List.of("C"), BusRoutePattern.alightingStops(List.of(outward, branch), "B"));
        assertEquals(List.of(), BusRoutePattern.alightingStops(List.of(outward, branch), "D"));
        assertFalse(outward.serves("C", "A"));
        BusRoutePattern inward = new BusRoutePattern("in", List.of(stop(0, "C"), stop(1, "B"), stop(2, "A")));
        assertTrue(inward.serves("C", "A"));
        assertEquals(List.of("A", "B"), BusRoutePattern.boardingStops(List.of(outward, branch)));
    }

    @Test public void circularRoutesUseLaterOccurrencesAndRespectBoardingRestrictions() {
        BusRoutePattern loop = new BusRoutePattern("loop", List.of(stop(0, "A"), stop(1, "B"), stop(2, "A"), stop(3, "C")));
        assertEquals(List.of("B", "C"), BusRoutePattern.alightingStops(List.of(loop), "A"));
        assertEquals(List.of("A", "C"), BusRoutePattern.alightingStops(List.of(loop), "B"));
        BusRoutePattern restricted = new BusRoutePattern("restricted", List.of(
                new BusRoutePattern.Stop(0, "a", "A", false, true),
                new BusRoutePattern.Stop(1, "b", "B", true, false), stop(2, "C")));
        assertEquals(List.of("B"), BusRoutePattern.boardingStops(List.of(restricted)));
        assertEquals(List.of(), BusRoutePattern.alightingStops(List.of(restricted), "A"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAmbiguousStopOrder() {
        new BusRoutePattern("bad", List.of(stop(1, "A"), stop(1, "B")));
    }
}
