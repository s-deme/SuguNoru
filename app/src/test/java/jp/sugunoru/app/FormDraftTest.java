package jp.sugunoru.app;

import org.junit.Test;
import java.time.LocalTime;
import java.util.List;
import jp.sugunoru.app.model.RoutePlan;
import static org.junit.Assert.*;

public class FormDraftTest {
    @Test public void changingJourneyInvalidatesPendingFetchAndClearsItsSource() {
        FormDraft draft = new FormDraft(null);
        draft.fetched("https://example.com", false);
        int pendingVersion = draft.selectionVersion;
        draft.select("都07", "錦糸町駅前", "木場駅前");
        assertNotEquals(pendingVersion, draft.selectionVersion);
        assertEquals(0, draft.fetchedAt);
        assertEquals(0, draft.attemptedAt);
        assertEquals("", draft.fetchedUrl);
        assertFalse(draft.fetchedInThisSession);
        assertFalse(draft.usesOdpt);
        assertTrue(draft.destinationIsStop);
    }

    @Test public void manualEditsClearOldFetchButFreshOdptFetchSurvivesUrlChange() {
        List<LocalTime> original = List.of(LocalTime.of(7, 0));
        List<LocalTime> edited = List.of(LocalTime.of(8, 0));
        RoutePlan plan = new RoutePlan("trip", RoutePlan.Direction.OUTBOUND, RoutePlan.Mode.BUS,
                "都07", "錦糸町駅前", "木場駅前", 0, 0, 0, true,
                original, original, List.of(), "", null, 1, "", 1, 1, "", true, true);
        FormDraft draft = new FormDraft(plan);
        draft.prepareSave("", plan, original, original, List.of());
        assertEquals(1, draft.fetchedAt);
        draft.prepareSave("", plan, edited, original, List.of());
        assertEquals(0, draft.fetchedAt);
        assertFalse(draft.usesOdpt);
        draft.fetched("", true);
        draft.prepareSave("https://example.com", plan, edited, edited, List.of());
        assertTrue(draft.fetchedAt > 0);
        assertTrue(draft.usesOdpt);
        draft.fetched("https://example.com", false);
        draft.prepareSave("https://example.org", plan, edited, edited, List.of());
        assertEquals(0, draft.fetchedAt);
    }
}
