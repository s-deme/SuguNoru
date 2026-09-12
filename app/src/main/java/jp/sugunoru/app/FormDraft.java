package jp.sugunoru.app;

import java.time.LocalTime;
import java.util.List;
import jp.sugunoru.app.model.RoutePlan;

/** Mutable state owned by one registration form, including its pending selection version. */
final class FormDraft {
    String route, stop, destination, fetchedUrl, lastError;
    boolean destinationIsStop, usesOdpt, fetchedInThisSession;
    long fetchedAt, attemptedAt;
    int selectionVersion;

    FormDraft(RoutePlan existing) {
        route = existing == null ? "" : existing.routeName();
        stop = existing == null ? "" : existing.stopName();
        destination = existing == null ? "" : existing.destination();
        destinationIsStop = existing == null || existing.destinationIsStop();
        fetchedUrl = existing == null ? "" : existing.officialTimetableUrl();
        lastError = existing == null ? "" : existing.officialTimetableLastError();
        fetchedAt = existing == null ? 0 : existing.officialTimetableFetchedAtEpochMillis();
        attemptedAt = existing == null ? 0 : existing.officialTimetableAttemptedAtEpochMillis();
        usesOdpt = existing != null && existing.hasOdptTimetableSource();
    }

    void select(String route, String stop, String destination) {
        this.route = route;
        this.stop = stop;
        this.destination = destination;
        destinationIsStop = true;
        selectionVersion++;
        clearFetchStatus();
        fetchedUrl = "";
        usesOdpt = false;
        fetchedInThisSession = false;
    }

    void fetched(String url, boolean odpt) {
        fetchedAt = attemptedAt = System.currentTimeMillis();
        lastError = "";
        fetchedUrl = url;
        usesOdpt = odpt;
        fetchedInThisSession = true;
    }

    void prepareSave(String url, RoutePlan existing, List<LocalTime> weekdays,
                     List<LocalTime> weekends, List<LocalTime> holidays) {
        if (!url.equals(fetchedUrl) && !usesOdpt) clearFetchStatus();
        boolean changed = existing != null && (!route.equals(existing.routeName())
                || !stop.equals(existing.stopName()) || !destination.equals(existing.destination())
                || destinationIsStop != existing.destinationIsStop()
                || !weekdays.equals(existing.weekdayTimes())
                || !weekends.equals(existing.weekendTimes()) || !holidays.equals(existing.holidayTimes()));
        if (changed && !fetchedInThisSession) {
            clearFetchStatus();
            usesOdpt = false;
        }
    }

    private void clearFetchStatus() {
        fetchedAt = attemptedAt = 0;
        lastError = "";
    }
}
