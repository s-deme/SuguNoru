package jp.sugunoru.app.data;

import jp.sugunoru.app.model.RoutePlan;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransitCatalogTest {
    @Test public void jrEastEntriesAreTrainOnlyAndHaveBoardingAndDirectionChoices() {
        List<TransitCatalog.Service> services = TransitCatalog.servicesFor(TransitCatalog.Provider.JR_EAST);

        assertFalse(services.isEmpty());
        assertEquals(17, services.size());
        assertTrue(services.stream().allMatch(service -> service.provider()
                == TransitCatalog.Provider.JR_EAST));
        assertTrue(services.stream().allMatch(service -> service.mode() == RoutePlan.Mode.TRAIN));
        assertTrue(services.stream().allMatch(service -> !service.stops().isEmpty()
                && !service.destinations().isEmpty()));
        assertTrue(services.stream().anyMatch(service -> service.displayName().contains("山手線")));
    }

    @Test public void toeiBusEntriesAreBusOnlyAndOfferBothTerminalDirections() {
        List<TransitCatalog.Service> services = TransitCatalog.servicesFor(TransitCatalog.Provider.TOEI_BUS);

        assertFalse(services.isEmpty());
        assertEquals(32, services.size());
        assertTrue(services.stream().allMatch(service -> service.provider()
                == TransitCatalog.Provider.TOEI_BUS));
        assertTrue(services.stream().allMatch(service -> service.mode() == RoutePlan.Mode.BUS));
        assertTrue(services.stream().allMatch(service -> service.destinations().size() == 2));
        assertTrue(services.stream().anyMatch(service -> service.displayName().startsWith("都01")));
    }

    @Test public void serviceMakesItsListsSafeToShareWithTheUi() {
        TransitCatalog.Service service = new TransitCatalog.Service("test", TransitCatalog.Provider.JR_EAST,
                RoutePlan.Mode.TRAIN, "テスト線", List.of("テスト駅"), List.of("東京方面"));

        assertEquals(List.of("テスト駅"), service.stops());
        try {
            service.stops().add("別の駅");
            throw new AssertionError("stops must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected: catalog entries must not be changed by a dialog or adapter.
        }
    }
}
