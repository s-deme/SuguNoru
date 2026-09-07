package jp.sugunoru.app.data;

import org.junit.Test;

import jp.sugunoru.app.model.RoutePlan;

import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransitCatalogTest {
    @Test public void containsOnlyToeiBusRoutesWithBoardingAndDirectionChoices() {
        List<TransitCatalog.Service> services = TransitCatalog.services();

        assertFalse(services.isEmpty());
        assertEquals(32, services.size());
        assertTrue(services.stream().allMatch(service -> service.id().startsWith("toei.")));
        assertTrue(services.stream().allMatch(service -> service.destinations().size() == 2));
        assertTrue(services.stream().anyMatch(service -> service.displayName().startsWith("都01")));
    }

    @Test public void serviceMakesItsListsSafeToShareWithTheUi() {
        TransitCatalog.Service service = new TransitCatalog.Service(
                "test", "テスト系統", List.of("テスト停留所"), List.of("終点方面"));

        assertEquals(List.of("テスト停留所"), service.stops());
        try {
            service.stops().add("別の駅");
            throw new AssertionError("stops must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected: catalog entries must not be changed by a dialog or adapter.
        }
    }

    @Test public void supportsOnlyRoutesFromTheToeiBusDirectory() {
        TransitCatalog.Service service = TransitCatalog.services().get(0);
        RoutePlan supported = new RoutePlan(null, RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                service.displayName(), service.stops().get(0), service.destinations().get(0),
                0, 0, List.of(LocalTime.of(9, 0)), List.of());
        RoutePlan unsupported = new RoutePlan(null, RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                "別のバス", "駅前", "終点方面", 0, 0, List.of(LocalTime.of(9, 0)), List.of());

        assertTrue(TransitCatalog.isSupported(supported));
        assertFalse(TransitCatalog.isSupported(unsupported));
    }
}
