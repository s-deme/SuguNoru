package jp.sugunoru.app.data;

import android.test.InstrumentationTestCase;
import android.os.Bundle;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import jp.sugunoru.app.model.DatedTimetable;
import jp.sugunoru.app.model.RoutePlan;

/** Opt-in live check. Credentials are read only from test-app private storage, never arguments/logs. */
public class OdptLiveTest extends InstrumentationTestCase {
    public void testLiveCatalogWhenConfigured() throws Exception {
        java.io.File config = getInstrumentation().getTargetContext().getFileStreamPath("odpt-live.json");
        if (!config.exists()) {
            Bundle skipped = new Bundle();
            skipped.putString("stream", "Live API check SKIPPED: no private configuration\n");
            getInstrumentation().sendStatus(0, skipped);
            return;
        }
        assertEquals("jp.sugunoru.app.verification", getInstrumentation().getTargetContext().getPackageName());
        JSONObject settings = new JSONObject(new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8));
        String host = settings.optString("host", "api.odpt.org");
        String token = settings.getString("token");
        try {
            new OdptTimetableFetcher().routePatterns("invalid-verification-token", TransitCatalog.services().get(0).displayName());
            fail("The authenticated endpoint accepted an invalid token");
        } catch (OdptTimetableFetcher.FetchException expected) {
            assertTrue("Authentication failure must be explained without exposing credentials",
                    expected.getMessage().contains("アクセストークン"));
        }
        var executor = Executors.newFixedThreadPool(3);
        List<Future<String>> checks = new ArrayList<>();
        try {
            for (TransitCatalog.Service service : TransitCatalog.services()) {
                checks.add(executor.submit(() -> {
                    try {
                        OdptTimetableFetcher fetcher = new OdptTimetableFetcher(host);
                        List<BusRoutePattern> patterns = fetcher.routePatterns(token, service.displayName()).patterns();
                        BusRoutePattern longest = patterns.stream().max(Comparator.comparingInt(p -> p.stops().size())).orElseThrow();
                        String boarding = BusRoutePattern.boardingStops(List.of(longest)).get(0);
                        List<String> destinations = BusRoutePattern.alightingStops(List.of(longest), boarding);
                        String destination = destinations.get(destinations.size() - 1);
                        var result = fetcher.fetch(token, service.displayName(), boarding, destination, true).timetable();
                        DatedTimetable dated = result.datedTimetable();
                        assertNotNull(dated);
                        assertFalse(dated.lastDate().isBefore(LocalDate.now()));
                        RoutePlan plan = new RoutePlan(null, RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                                service.displayName(), boarding, destination, 0, 0, 0, true,
                                List.of(), List.of(), List.of(), "", null, 1, "", 1, 1, "", true, true, dated);
                        RoutePlan restored = RoutePlan.fromJson(plan.toJson());
                        assertEquals(dated, restored.datedTimetable());
                        assertEquals(dated, restored.withOfficialTimetableFetchFailure(2, "offline").datedTimetable());
                        String summary = service.id() + " OK: " + patterns.size() + " patterns, "
                                + dated.services().size() + " calendars, " + boarding + " → " + destination;
                        Bundle progress = new Bundle();
                        progress.putString("stream", summary + "\n");
                        getInstrumentation().sendStatus(0, progress);
                        return "";
                    } catch (OdptTimetableFetcher.FetchException error) {
                        return service.id() + ": " + error.getMessage(); // Never print request URLs or causes.
                    } catch (Exception | AssertionError error) {
                        return service.id() + ": validation failed (" + error.getClass().getSimpleName() + ")";
                    }
                }));
            }
            List<String> failures = new ArrayList<>();
            for (Future<String> check : checks) {
                String failure = check.get();
                if (!failure.isEmpty()) failures.add(failure);
            }
            assertTrue(String.join("\n", failures), failures.isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }
}
