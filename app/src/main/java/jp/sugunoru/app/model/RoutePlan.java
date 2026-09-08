package jp.sugunoru.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RoutePlan {
    public enum Direction { OUTBOUND, RETURN }
    public enum Mode { TRAIN, BUS }

    private final String id;
    private final Direction direction;
    private final Mode mode;
    private final String routeName;
    private final String stopName;
    private final String destination;
    // Retained only to read and write existing backups. New registrations store zero and
    // ScheduleEngine no longer uses these values when finding the next departure.
    private final int walkMinutes;
    private final int rideMinutes;
    private final int finalWalkMinutes;
    private final boolean enabled;
    private final List<LocalTime> weekdayTimes;
    private final List<LocalTime> weekendTimes;
    private final List<LocalTime> holidayTimes;
    private final String notes;
    private final LocalDate validUntil;
    private final long updatedAtEpochMillis;
    private final String officialTimetableUrl;
    private final long officialTimetableFetchedAtEpochMillis;
    private final long officialTimetableAttemptedAtEpochMillis;
    private final String officialTimetableLastError;

    public RoutePlan(
            String id,
            Direction direction,
            Mode mode,
            String routeName,
            String stopName,
            String destination,
            int walkMinutes,
            int rideMinutes,
            List<LocalTime> weekdayTimes,
            List<LocalTime> weekendTimes
    ) {
        this(id, direction, mode, routeName, stopName, destination, walkMinutes, rideMinutes,
                0, true, weekdayTimes, weekendTimes, List.of(), "", null,
                System.currentTimeMillis());
    }

    public RoutePlan(
            String id, Direction direction, Mode mode, String routeName, String stopName,
            String destination, int walkMinutes, int rideMinutes, int finalWalkMinutes,
            boolean enabled, List<LocalTime> weekdayTimes, List<LocalTime> weekendTimes,
            List<LocalTime> holidayTimes, String notes, LocalDate validUntil,
            long updatedAtEpochMillis
    ) {
        this(id, direction, mode, routeName, stopName, destination, walkMinutes, rideMinutes,
                finalWalkMinutes, enabled, weekdayTimes, weekendTimes, holidayTimes, notes,
                validUntil, updatedAtEpochMillis, "", 0, 0, "");
    }

    public RoutePlan(
            String id, Direction direction, Mode mode, String routeName, String stopName,
            String destination, int walkMinutes, int rideMinutes, int finalWalkMinutes,
            boolean enabled, List<LocalTime> weekdayTimes, List<LocalTime> weekendTimes,
            List<LocalTime> holidayTimes, String notes, LocalDate validUntil,
            long updatedAtEpochMillis, String officialTimetableUrl,
            long officialTimetableFetchedAtEpochMillis,
            long officialTimetableAttemptedAtEpochMillis,
            String officialTimetableLastError
    ) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.direction = Objects.requireNonNull(direction);
        this.mode = Objects.requireNonNull(mode);
        this.routeName = requireText(routeName, "路線名");
        this.stopName = requireText(stopName, "駅・停留所名");
        this.destination = requireText(destination, "行き先");
        this.walkMinutes = requireRange(walkMinutes, 0, 180, "徒歩時間");
        this.rideMinutes = requireRange(rideMinutes, 0, 600, "乗車時間");
        this.finalWalkMinutes = requireRange(finalWalkMinutes, 0, 180, "到着後の徒歩時間");
        this.enabled = enabled;
        this.weekdayTimes = sortedCopy(weekdayTimes);
        this.weekendTimes = sortedCopy(weekendTimes);
        this.holidayTimes = sortedCopy(holidayTimes);
        this.notes = notes == null ? "" : notes.trim();
        this.validUntil = validUntil;
        this.updatedAtEpochMillis = updatedAtEpochMillis > 0 ? updatedAtEpochMillis : System.currentTimeMillis();
        this.officialTimetableUrl = normalizeOfficialTimetableUrl(officialTimetableUrl);
        this.officialTimetableFetchedAtEpochMillis = Math.max(0, officialTimetableFetchedAtEpochMillis);
        this.officialTimetableAttemptedAtEpochMillis = Math.max(0, officialTimetableAttemptedAtEpochMillis);
        this.officialTimetableLastError = officialTimetableLastError == null
                ? "" : officialTimetableLastError.trim();
        if (!hasCachedTimetable() && this.officialTimetableUrl.isEmpty()) {
            throw new IllegalArgumentException("時刻を1件以上入力してください");
        }
    }

    public static RoutePlan create(
            Direction direction, Mode mode, String routeName, String stopName,
            String destination, int walkMinutes, int rideMinutes,
            List<LocalTime> weekdayTimes, List<LocalTime> weekendTimes
    ) {
        return new RoutePlan(null, direction, mode, routeName, stopName, destination,
                walkMinutes, rideMinutes, weekdayTimes, weekendTimes);
    }

    /** Returns an independent copy with a new identifier and update timestamp. */
    public RoutePlan duplicate(String routeName) {
        return new RoutePlan(null, direction, mode, routeName, stopName, destination,
                walkMinutes, rideMinutes, finalWalkMinutes, enabled,
                weekdayTimes, weekendTimes, holidayTimes, notes, validUntil,
                System.currentTimeMillis(), officialTimetableUrl,
                officialTimetableFetchedAtEpochMillis, officialTimetableAttemptedAtEpochMillis,
                officialTimetableLastError);
    }

    /** Returns a copy whose timetable came from the configured official source. */
    public RoutePlan withFetchedOfficialTimetable(
            List<LocalTime> weekdayTimes, List<LocalTime> weekendTimes,
            List<LocalTime> holidayTimes, long fetchedAtEpochMillis
    ) {
        return new RoutePlan(id, direction, mode, routeName, stopName, destination,
                walkMinutes, rideMinutes, finalWalkMinutes, enabled,
                weekdayTimes, weekendTimes, holidayTimes, notes, validUntil,
                System.currentTimeMillis(), officialTimetableUrl, fetchedAtEpochMillis,
                fetchedAtEpochMillis, "");
    }

    /** Keeps the last successful timetable intact while recording a failed refresh. */
    public RoutePlan withOfficialTimetableFetchFailure(long attemptedAtEpochMillis, String error) {
        return new RoutePlan(id, direction, mode, routeName, stopName, destination,
                walkMinutes, rideMinutes, finalWalkMinutes, enabled,
                weekdayTimes, weekendTimes, holidayTimes, notes, validUntil,
                System.currentTimeMillis(), officialTimetableUrl,
                officialTimetableFetchedAtEpochMillis, attemptedAtEpochMillis, error);
    }

    public static String normalizeOfficialTimetableUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return "";
        if (normalized.length() > 2_048) {
            throw new IllegalArgumentException("公式時刻表URLが長すぎます");
        }
        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().isBlank() || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("公式時刻表URLは https:// で始まる公式ページを入力してください");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("公式時刻表URLの形式を確認してください");
        }
    }

    private static String requireText(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + "を入力してください");
        }
        return normalized;
    }

    private static int requireRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + "は" + min + "〜" + max + "分で入力してください");
        }
        return value;
    }

    private static List<LocalTime> sortedCopy(List<LocalTime> values) {
        ArrayList<LocalTime> result = new ArrayList<>(values == null ? List.of() : values);
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("direction", direction.name());
        json.put("mode", mode.name());
        json.put("routeName", routeName);
        json.put("stopName", stopName);
        json.put("destination", destination);
        json.put("walkMinutes", walkMinutes);
        json.put("rideMinutes", rideMinutes);
        json.put("finalWalkMinutes", finalWalkMinutes);
        json.put("enabled", enabled);
        json.put("weekdayTimes", timesToJson(weekdayTimes));
        json.put("weekendTimes", timesToJson(weekendTimes));
        json.put("holidayTimes", timesToJson(holidayTimes));
        json.put("notes", notes);
        if (validUntil != null) json.put("validUntil", validUntil.toString());
        json.put("updatedAt", updatedAtEpochMillis);
        if (!officialTimetableUrl.isEmpty()) json.put("officialTimetableUrl", officialTimetableUrl);
        if (officialTimetableFetchedAtEpochMillis > 0) {
            json.put("officialTimetableFetchedAt", officialTimetableFetchedAtEpochMillis);
        }
        if (officialTimetableAttemptedAtEpochMillis > 0) {
            json.put("officialTimetableAttemptedAt", officialTimetableAttemptedAtEpochMillis);
        }
        if (!officialTimetableLastError.isEmpty()) {
            json.put("officialTimetableLastError", officialTimetableLastError);
        }
        return json;
    }

    public static RoutePlan fromJson(JSONObject json) throws JSONException {
        LocalDate validUntil = json.has("validUntil") && !json.isNull("validUntil")
                ? LocalDate.parse(json.getString("validUntil")) : null;
        return new RoutePlan(
                json.getString("id"),
                Direction.valueOf(json.getString("direction")),
                Mode.valueOf(json.getString("mode")),
                json.getString("routeName"),
                json.getString("stopName"),
                json.getString("destination"),
                json.getInt("walkMinutes"),
                json.getInt("rideMinutes"),
                json.optInt("finalWalkMinutes", 0),
                json.optBoolean("enabled", true),
                timesFromJson(json.getJSONArray("weekdayTimes")),
                timesFromJson(json.getJSONArray("weekendTimes")),
                json.has("holidayTimes") ? timesFromJson(json.getJSONArray("holidayTimes")) : List.of(),
                json.optString("notes", ""),
                validUntil,
                json.optLong("updatedAt", System.currentTimeMillis()),
                json.optString("officialTimetableUrl", ""),
                json.optLong("officialTimetableFetchedAt", 0),
                json.optLong("officialTimetableAttemptedAt", 0),
                json.optString("officialTimetableLastError", "")
        );
    }

    private static JSONArray timesToJson(List<LocalTime> times) {
        JSONArray result = new JSONArray();
        for (LocalTime time : times) result.put(time.toString());
        return result;
    }

    private static List<LocalTime> timesFromJson(JSONArray values) throws JSONException {
        List<LocalTime> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) result.add(LocalTime.parse(values.getString(i)));
        return result;
    }

    public String id() { return id; }
    public Direction direction() { return direction; }
    public Mode mode() { return mode; }
    public String routeName() { return routeName; }
    public String stopName() { return stopName; }
    public String destination() { return destination; }
    public int walkMinutes() { return walkMinutes; }
    public int rideMinutes() { return rideMinutes; }
    public int finalWalkMinutes() { return finalWalkMinutes; }
    public boolean enabled() { return enabled; }
    public List<LocalTime> weekdayTimes() { return weekdayTimes; }
    public List<LocalTime> weekendTimes() { return weekendTimes; }
    public List<LocalTime> holidayTimes() { return holidayTimes; }
    public String notes() { return notes; }
    public LocalDate validUntil() { return validUntil; }
    public long updatedAtEpochMillis() { return updatedAtEpochMillis; }
    public String officialTimetableUrl() { return officialTimetableUrl; }
    public long officialTimetableFetchedAtEpochMillis() { return officialTimetableFetchedAtEpochMillis; }
    public long officialTimetableAttemptedAtEpochMillis() { return officialTimetableAttemptedAtEpochMillis; }
    public String officialTimetableLastError() { return officialTimetableLastError; }
    public boolean hasOfficialTimetableSource() { return !officialTimetableUrl.isEmpty(); }
    public boolean hasCachedTimetable() {
        return !weekdayTimes.isEmpty() || !weekendTimes.isEmpty() || !holidayTimes.isEmpty();
    }
}
