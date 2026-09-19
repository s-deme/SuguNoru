package jp.sugunoru.app.data;

import android.test.InstrumentationTestCase;
import org.json.JSONArray;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.sugunoru.app.model.DatedTimetable;
import jp.sugunoru.app.model.RoutePlan;
import jp.sugunoru.app.model.ScheduleEngine;

public class OdptCalendarTest extends InstrumentationTestCase {
    public void testSpecificDatesAndBackupRoundTrip() throws Exception {
        // Real calendar ID/form; include a special weekday service with no title.
        JSONArray calendars = new JSONArray("["
                + "{\"owl:sameAs\":\"odpt.Calendar:Specific.Toei.65-100\",\"dc:title\":\"休日\","
                + "\"odpt:duration\":\"2026-09-17/2029-09-16\",\"odpt:day\":[\"2026-09-20\",\"2026-09-21\"]},"
                + "{\"owl:sameAs\":\"special\",\"odpt:duration\":\"2026-09-17/2029-09-16\","
                + "\"odpt:day\":[\"2026-09-21\"]}]");
        DatedTimetable dated = OdptTimetableFetcher.datedTimetable(Map.of(
                "odpt.Calendar:Specific.Toei.65-100", Set.of(LocalTime.of(9, 0)),
                "special", Set.of(LocalTime.of(10, 0))), calendars);
        OfficialTimetableParser.Timetable result = new OfficialTimetableParser.Timetable(List.of(), List.of(), List.of(), dated);
        assertTrue(result.hasAnyTimes());
        RoutePlan plan = RoutePlan.create(RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                "都07", "駅前", "終点", 0, 0, List.of(LocalTime.NOON), List.of())
                .withFetchedOdptTimetable(result.datedTimetable(), 1);
        RouteRepository repository = new RouteRepository(getInstrumentation().getContext());
        RoutePlan restored = repository.importJson(repository.exportJson(List.of(plan))).get(0);
        assertEquals(List.of(), ScheduleEngine.timesFor(restored, LocalDate.of(2026, 9, 19)));
        assertEquals(List.of(LocalTime.of(9, 0)), ScheduleEngine.timesFor(restored, LocalDate.of(2026, 9, 20)));
        assertEquals(List.of(LocalTime.of(9, 0), LocalTime.of(10, 0)), ScheduleEngine.timesFor(restored, LocalDate.of(2026, 9, 21)));
        try {
            OdptTimetableFetcher.datedTimetable(Map.of("missing", Set.of(LocalTime.NOON)), calendars);
            fail("Missing calendar must not silently drop a service");
        } catch (OdptTimetableFetcher.FetchException expected) { }
    }
}
