package jp.sugunoru.app.data;

import android.test.InstrumentationTestCase;
import org.json.JSONArray;
import org.json.JSONObject;
import jp.sugunoru.app.model.RoutePlan;
import java.time.LocalTime;
import java.util.List;

/** Runs against Android's real JSON implementation, including cache and backup serialization. */
public class JourneySelectionTest extends InstrumentationTestCase {
    private String screenshotPrefix = "";

    public void testSelectionScreenAndMissingToken() throws Exception {
        checkSelection(false);
        if (android.os.Build.VERSION.SDK_INT >= 31) checkSelection(true);
    }

    private void checkSelection(boolean dark) throws Exception {
        android.content.Context context = getInstrumentation().getTargetContext();
        assertEquals("Use -I scripts/verification.gradle to protect personal data",
                "jp.sugunoru.app.verification", context.getPackageName());
        screenshotPrefix = dark ? "dark-" : "light-";
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(android.app.UiModeManager.class).setApplicationNightMode(
                    dark ? android.app.UiModeManager.MODE_NIGHT_YES : android.app.UiModeManager.MODE_NIGHT_NO);
        }
        String route = TransitCatalog.services().get(0).displayName();
        AppPreferences preferences = new AppPreferences(context);
        preferences.setRoutePatterns(route, routeFixture());
        android.app.Activity activity = getInstrumentation().startActivitySync(
                new android.content.Intent(context, jp.sugunoru.app.MainActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            click("出かける路線を追加");
            click("路線・系統を選ぶ");
            click(route);
            click("乗車テストA");
            assertTrue(visible("降車テストC"));
            screenshot("alighting-options.png");
            click("降車テストC");
            assertTrue(visible("乗車テストA"));
            assertTrue(visible("降車テストC"));
            screenshot("selected-journey.png");
            click("路線・系統を選ぶ");
            click(TransitCatalog.services().get(1).displayName());
            assertTrue(visible("停留所データを取得するには"));
            screenshot("missing-token.png");
            click("戻る");
        } finally {
            getInstrumentation().runOnMainSync(activity::finish);
            preferences.setRoutePatterns(route, "");
        }
    }

    private boolean visible(String text) {
        getInstrumentation().waitForIdleSync();
        android.view.accessibility.AccessibilityNodeInfo root =
                getInstrumentation().getUiAutomation().getRootInActiveWindow();
        return root != null && !root.findAccessibilityNodeInfosByText(text).isEmpty();
    }

    private void click(String text) throws Exception {
        for (int retry = 0; retry < 30; retry++) {
            getInstrumentation().waitForIdleSync();
            android.view.accessibility.AccessibilityNodeInfo root =
                    getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (android.view.accessibility.AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                    while (node != null && !node.isClickable()) node = node.getParent();
                    if (node != null && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                        getInstrumentation().getUiAutomation().waitForIdle(100, 3000);
                        return;
                    }
                }
            }
            Thread.sleep(100);
        }
        fail("Control not found: " + text);
    }

    private void screenshot(String name) throws Exception {
        // Accessibility can settle before the dialog's window animation finishes.
        Thread.sleep(500);
        android.graphics.Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        java.io.File file = new java.io.File(getInstrumentation().getTargetContext().getExternalFilesDir(null),
                screenshotPrefix + name);
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
    }

    public static String routeFixture() {
        return "[{\"owl:sameAs\":\"pattern:out\",\"odpt:busstopPoleOrder\":["
                + "{\"odpt:index\":2,\"odpt:busstopPole\":\"c\",\"stopName\":\"降車テストC\"},"
                + "{\"odpt:index\":0,\"odpt:busstopPole\":\"a\",\"stopName\":\"乗車テストA\"},"
                + "{\"odpt:index\":1,\"odpt:busstopPole\":\"b\",\"stopName\":\"途中テストB\"}]}]";
    }

    public void testCacheAndTripOrder() throws Exception {
        AppPreferences preferences = new AppPreferences(getInstrumentation().getContext());
        preferences.setRoutePatterns("test-only", routeFixture());
        List<BusRoutePattern> patterns = OdptTimetableFetcher.parsePatterns(
                new JSONArray(preferences.routePatterns("test-only")));
        assertEquals(List.of("途中テストB", "降車テストC"),
                BusRoutePattern.alightingStops(patterns, "乗車テストA"));
        JSONArray trip = new JSONArray("["
                + "{\"odpt:index\":2,\"odpt:busstopPole\":\"c\",\"odpt:canGetOff\":true},"
                + "{\"odpt:index\":0,\"odpt:busstopPole\":\"a\",\"odpt:departureTime\":\"07:00\"},"
                + "{\"odpt:index\":1,\"odpt:busstopPole\":\"b\",\"odpt:departureTime\":\"07:10\"}]");
        assertEquals(List.of(LocalTime.of(7, 0)), OdptTimetableFetcher.departuresBetween(
                patterns.get(0), OdptTimetableFetcher.parseTripStops(trip), "乗車テストA", "降車テストC"));
        trip.getJSONObject(0).put("odpt:canGetOff", false);
        assertEquals(List.of(), OdptTimetableFetcher.departuresBetween(
                patterns.get(0), OdptTimetableFetcher.parseTripStops(trip), "乗車テストA", "降車テストC"));
        trip.getJSONObject(0).remove("odpt:index");
        try {
            OdptTimetableFetcher.parseTripStops(trip);
            fail("Missing order must not silently become array order");
        } catch (OdptTimetableFetcher.FetchException expected) { }
    }

    public void testBackupRetainsAlightingAndOldBackupsRemainDirections() throws Exception {
        RoutePlan plan = new RoutePlan("trip", RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                "都07", "錦糸町駅前", "木場駅前", 0, 0, 0, true,
                List.of(LocalTime.of(7, 0)), List.of(), List.of(), "", null, 1, "", 1, 1, "", true, true);
        JSONObject json = plan.toJson();
        RoutePlan restored = RoutePlan.fromJson(new JSONObject(json.toString()));
        assertTrue(restored.destinationIsStop());
        assertTrue(restored.hasOdptTimetableSource());
        assertEquals("木場駅前", restored.destination());
        json.remove("destinationIsStop");
        assertFalse(RoutePlan.fromJson(json).destinationIsStop());
    }
}
