package jp.sugunoru.app.data;

import org.junit.Test;

import jp.sugunoru.app.model.RoutePlan;

import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransitCatalogTest {
    @Test public void containsStableToeiRouteIdentities() {
        List<TransitCatalog.Service> services = TransitCatalog.services();

        assertFalse(services.isEmpty());
        assertEquals(57, services.size());
        assertTrue(services.stream().allMatch(service -> service.id().startsWith("toei.")));
        assertEquals(services.size(), services.stream().map(TransitCatalog.Service::id).distinct().count());
        assertTrue(services.stream().anyMatch(service -> service.displayName().startsWith("都01")));
    }

    @Test public void retainsRoutesAddedForTheFiveRequestedStations() {
        for (String id : List.of("to02", "to08", "kin37", "kin40", "kin18", "kin13-kou",
                "kin13-otsu", "kyuko05", "chokko03", "to07", "ryo28", "higashi22",
                "kin28", "kin25", "kin27", "kin11", "kame26", "kame23", "kame29", "kame24",
                "kusa24", "ue26", "kame21", "sato22", "mon33", "you20", "ki11-kou", "mon21",
                "you12-1", "you12-2", "gyo10", "kyuko06", "umi01", "mon19")) {
            assertFalse(service(id).displayName().isBlank());
        }
    }

    private static TransitCatalog.Service service(String id) {
        return TransitCatalog.services().stream().filter(service -> service.id().equals("toei." + id))
                .findFirst().orElseThrow(() -> new AssertionError("Missing route: " + id));
    }

    @Test public void supportsOnlyRoutesFromTheToeiBusDirectory() {
        TransitCatalog.Service service = TransitCatalog.services().get(0);
        RoutePlan supported = new RoutePlan(null, RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                service.displayName(), "乗車停留所", "降車停留所",
                0, 0, List.of(LocalTime.of(9, 0)), List.of());
        RoutePlan unsupported = new RoutePlan(null, RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                "別のバス", "駅前", "終点方面", 0, 0, List.of(LocalTime.of(9, 0)), List.of());

        assertTrue(TransitCatalog.isSupported(supported));
        assertFalse(TransitCatalog.isSupported(unsupported));
    }
}
