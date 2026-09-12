package jp.sugunoru.app.data;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.text.Normalizer;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

/** Fetches one selected Toei Bus timetable from ODPT without persisting or logging its token. */
public final class OdptTimetableFetcher {
    private static final String OPERATOR = "odpt.Operator:Toei";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 15_000;
    private static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ROUTE_PATTERNS = 50;

    public record FetchResult(OfficialTimetableParser.Timetable timetable) {}

    /** Fetches the actual ordered patterns, including reverse, short and circular services. */
    public record RoutePatterns(List<BusRoutePattern> patterns, String cacheJson) {}

    public RoutePatterns routePatterns(String accessToken, String routeName) throws FetchException {
        if (accessToken == null || accessToken.isBlank()) {
            throw new FetchException("設定でODPTアクセストークンを保存してください");
        }
        String route = TransitCatalog.odptBusrouteId(routeName);
        JSONArray records = route.isEmpty() ? new JSONArray()
                : request("odpt:BusroutePattern", accessToken, "odpt:operator", OPERATOR, "odpt:busroute", route);
        JSONArray selected = selectRoute(records, routeName);
        if (selected.length() == 0) {
            selected = selectRoute(request("odpt:BusroutePattern", accessToken, "odpt:operator", OPERATOR), routeName);
        }
        if (selected.length() == 0) throw new FetchException("この系統の停留所データがODPTにありません");
        // Resolve names from BusstopPole: a pattern's optional note is not a stop-name contract.
        Set<String> poles = new TreeSet<>();
        for (int i = 0; i < selected.length(); i++) {
            JSONArray order = selected.optJSONObject(i).optJSONArray("odpt:busstopPoleOrder");
            if (order == null) throw new FetchException("停留所の順序を取得できません");
            for (int j = 0; j < order.length(); j++) {
                JSONObject stop = order.optJSONObject(j);
                if (stop == null || stop.optString("odpt:busstopPole").isBlank()) {
                    throw new FetchException("停留所のIDを取得できません");
                }
                poles.add(stop.optString("odpt:busstopPole"));
            }
        }
        java.util.Map<String, String> names = new java.util.HashMap<>();
        List<String> ids = new ArrayList<>(poles);
        for (int i = 0; i < ids.size(); i += 40) {
            JSONArray recordsWithNames = request("odpt:BusstopPole", accessToken, "owl:sameAs",
                    String.join(",", ids.subList(i, Math.min(i + 40, ids.size()))));
            for (int j = 0; j < recordsWithNames.length(); j++) {
                JSONObject stop = recordsWithNames.optJSONObject(j);
                if (stop != null) names.put(stop.optString("owl:sameAs"), stop.optString("dc:title"));
            }
        }
        try {
            for (int i = 0; i < selected.length(); i++) {
                JSONArray order = selected.getJSONObject(i).getJSONArray("odpt:busstopPoleOrder");
                for (int j = 0; j < order.length(); j++) {
                    JSONObject stop = order.getJSONObject(j);
                    String name = names.get(stop.getString("odpt:busstopPole"));
                    if (name == null || name.isBlank()) throw new FetchException("停留所名を取得できません");
                    stop.put("stopName", name);
                }
            }
        } catch (JSONException error) {
            throw new FetchException("停留所データを読み取れません", error);
        }
        List<BusRoutePattern> patterns = parsePatterns(selected);
        if (patterns.isEmpty()) throw new FetchException("停留所の順序と名称を確認できません");
        return new RoutePatterns(patterns, selected.toString());
    }

    private static JSONArray selectRoute(JSONArray records, String routeName) {
        JSONArray selected = new JSONArray();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null && OPERATOR.equals(record.optString("odpt:operator"))
                    && matchesRoute(record.optString("dc:title"), routeName)) selected.put(record);
        }
        return selected;
    }

    static boolean matchesRoute(String title, String routeName) {
        String route = normalizedRoute(routeName);
        String value = normalized(title);
        if (route.isEmpty() || !value.startsWith(route)) return false;
        if (value.length() == route.length()) return true;
        char next = value.charAt(route.length());
        return !Character.isLetterOrDigit(next) && next != '-';
    }

    public static List<BusRoutePattern> parsePatterns(JSONArray records) throws FetchException {
        List<BusRoutePattern> result = new ArrayList<>();
        try {
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.getJSONObject(i);
                JSONArray order = record.getJSONArray("odpt:busstopPoleOrder");
                List<BusRoutePattern.Stop> stops = new ArrayList<>();
                for (int j = 0; j < order.length(); j++) {
                    JSONObject stop = order.getJSONObject(j);
                    stops.add(new BusRoutePattern.Stop(stop.getInt("odpt:index"),
                            stop.getString("odpt:busstopPole"), stop.getString("stopName"),
                            doorsAllowed(stop, "odpt:openingDoorsToGetOn"),
                            doorsAllowed(stop, "odpt:openingDoorsToGetOff")));
                }
                result.add(new BusRoutePattern(record.getString("owl:sameAs"), stops));
            }
        } catch (JSONException | IllegalArgumentException error) {
            throw new FetchException("停留所データが不完全です。停留所を再取得してください", error);
        }
        return List.copyOf(result);
    }

    private static boolean doorsAllowed(JSONObject stop, String key) {
        if (!stop.has(key)) return true;
        JSONArray doors = stop.optJSONArray(key);
        return doors != null && doors.length() > 0;
    }

    public FetchResult fetch(String accessToken, String routeName, String boarding, String destination,
                             boolean destinationIsStop) throws FetchException {
        if (!destinationIsStop) return fetch(accessToken, routeName, boarding, destination);
        List<BusRoutePattern> patterns = routePatterns(accessToken, routeName).patterns().stream()
                .filter(pattern -> pattern.serves(boarding, destination))
                .collect(java.util.stream.Collectors.toList());
        if (patterns.isEmpty()) throw new FetchException("選択した順に乗車・降車できる経路がありません");
        if (patterns.size() > MAX_ROUTE_PATTERNS) throw new FetchException("経路候補が多すぎます");
        OfficialTimetableParser.Timetable result = emptyTimetable();
        for (BusRoutePattern pattern : patterns) {
            JSONArray trips = request("odpt:BusTimetable", accessToken,
                    "odpt:operator", OPERATOR, "odpt:busroutePattern", pattern.id());
            for (int i = 0; i < trips.length(); i++) {
                JSONObject trip = trips.optJSONObject(i);
                if (trip == null) continue;
                JSONArray objects = trip.optJSONArray("odpt:busTimetableObject");
                if (objects == null) continue;
                List<TripStop> stops = parseTripStops(objects);
                CalendarTimes departures = new CalendarTimes();
                departures.add(trip.optString("odpt:calendar"),
                        departuresBetween(pattern, stops, boarding, destination));
                result = merge(result, departures.timetable());
            }
        }
        if (result.weekdayTimes().isEmpty() && result.weekendTimes().isEmpty() && result.holidayTimes().isEmpty()) {
            throw new FetchException("降車停留所まで乗車できる便の時刻表がありません");
        }
        return new FetchResult(result);
    }

    record TripStop(String pole, String departureTime, boolean canGetOn, boolean canGetOff) {}

    static List<TripStop> parseTripStops(JSONArray objects) throws FetchException {
        List<JSONObject> ordered = new ArrayList<>();
        Set<Integer> indices = new java.util.HashSet<>();
        try {
            for (int i = 0; i < objects.length(); i++) {
                JSONObject stop = objects.getJSONObject(i);
                int index = stop.getInt("odpt:index");
                if (index < 0 || !indices.add(index) || stop.getString("odpt:busstopPole").isBlank()) {
                    throw new IllegalArgumentException("停留所の順序が不正です");
                }
                ordered.add(stop);
            }
            ordered.sort(java.util.Comparator.comparingInt(stop -> stop.optInt("odpt:index")));
            List<TripStop> result = new ArrayList<>();
            for (JSONObject stop : ordered) {
                result.add(new TripStop(stop.getString("odpt:busstopPole"),
                        stop.optString("odpt:departureTime"), canGetOn(stop),
                        !stop.has("odpt:canGetOff") || stop.optBoolean("odpt:canGetOff", false)));
            }
            return result;
        } catch (JSONException | IllegalArgumentException error) {
            throw new FetchException("便の停留所順序を確認できません", error);
        }
    }

    /** Evaluate each trip separately: a short-working trip must not inherit another trip's destination. */
    static List<LocalTime> departuresBetween(BusRoutePattern pattern, List<TripStop> trip,
                                             String boarding, String alighting) {
        Set<String> boardingPoles = new java.util.HashSet<>();
        Set<String> alightingPoles = new java.util.HashSet<>();
        for (BusRoutePattern.Stop stop : pattern.stops()) {
            if (stop.name().equals(boarding) && stop.canGetOn()) boardingPoles.add(stop.pole());
            if (stop.name().equals(alighting) && stop.canGetOff()) alightingPoles.add(stop.pole());
        }
        Set<LocalTime> departures = new TreeSet<>();
        for (int i = 0; i < trip.size(); i++) {
            TripStop on = trip.get(i);
            LocalTime time = parseTime(on.departureTime());
            if (time == null || !on.canGetOn() || !boardingPoles.contains(on.pole())) continue;
            for (int j = i + 1; j < trip.size(); j++) {
                TripStop off = trip.get(j);
                if (off.canGetOff() && alightingPoles.contains(off.pole())) {
                    departures.add(time);
                    break;
                }
            }
        }
        return List.copyOf(departures);
    }

    public FetchResult fetch(String accessToken, String routeName, String stopName, String destination)
            throws FetchException {
        List<RoutePattern> candidates = matchingPatterns(
                routePatterns(accessToken, routeName).patterns(), stopName);
        if (candidates.isEmpty()) {
            throw new FetchException("ODPTに選択した路線と停留所が見つかりませんでした");
        }
        if (candidates.size() > MAX_ROUTE_PATTERNS) {
            throw new FetchException("ODPTの経路候補が多すぎます。公式時刻表ページを使用してください");
        }

        OfficialTimetableParser.Timetable result = emptyTimetable();
        for (RoutePattern candidate : candidates) {
            JSONArray timetables = request("odpt:BusTimetable", accessToken,
                    "odpt:operator", OPERATOR,
                    "odpt:busroutePattern", candidate.id());
            result = merge(result, timetableAt(timetables, candidate.boardingStopPole(), destination));
        }
        if (result.weekdayTimes().isEmpty() && result.weekendTimes().isEmpty()
                && result.holidayTimes().isEmpty()) {
            throw new FetchException("ODPTに選択した方面の発車時刻が見つかりませんでした");
        }
        return new FetchResult(result);
    }

    static OfficialTimetableParser.Timetable timetableAt(
            JSONArray records, String boardingStopPole, String destination
    ) {
        List<TimetableRecord> values = new ArrayList<>();
        for (int index = 0; index < records.length(); index++) {
            JSONObject record = records.optJSONObject(index);
            if (record == null) continue;
            JSONArray objects = record.optJSONArray("odpt:busTimetableObject");
            if (objects == null) continue;
            for (int objectIndex = 0; objectIndex < objects.length(); objectIndex++) {
                JSONObject object = objects.optJSONObject(objectIndex);
                if (object == null) continue;
                values.add(new TimetableRecord(record.optString("odpt:calendar", ""),
                        object.optString("odpt:busstopPole", ""),
                        object.optString("odpt:departureTime", ""),
                        object.optString("odpt:destinationSign", ""), canGetOn(object)));
            }
        }
        return timetableAt(values, boardingStopPole, destination);
    }

    static OfficialTimetableParser.Timetable timetableAt(
            List<TimetableRecord> records, String boardingStopPole, String destination
    ) {
        CalendarTimes times = new CalendarTimes();
        for (TimetableRecord record : records) {
            if (!boardingStopPole.equals(record.busstopPole()) || !record.canGetOn()
                    || !matchesDestination(record.destination(), destination)) continue;
            LocalTime time = parseTime(record.departureTime());
            if (time != null) times.add(record.calendar(), List.of(time));
        }
        return times.timetable();
    }

    /** Both destination modes use the same canonical pole names and boarding permissions. */
    private static List<RoutePattern> matchingPatterns(List<BusRoutePattern> patterns, String stopName) {
        Set<RoutePattern> result = new java.util.LinkedHashSet<>();
        for (BusRoutePattern pattern : patterns) {
            for (BusRoutePattern.Stop stop : pattern.stops()) {
                if (stop.canGetOn() && sameText(stop.name(), stopName)) {
                    result.add(new RoutePattern(pattern.id(), stop.pole()));
                }
            }
        }
        return List.copyOf(result);
    }

    static final class CalendarTimes {
        private final Set<LocalTime> weekday = new TreeSet<>();
        private final Set<LocalTime> weekend = new TreeSet<>();
        private final Set<LocalTime> holiday = new TreeSet<>();

        void add(String calendar, List<LocalTime> times) {
            switch (calendarKind(calendar)) {
                case WEEKDAY -> weekday.addAll(times);
                case WEEKEND -> weekend.addAll(times);
                case HOLIDAY -> {
                    weekend.addAll(times);
                    holiday.addAll(times);
                }
                default -> { }
            }
        }

        OfficialTimetableParser.Timetable timetable() {
            return new OfficialTimetableParser.Timetable(
                    List.copyOf(weekday), List.copyOf(weekend), List.copyOf(holiday));
        }
    }

    private JSONArray request(String resource, String accessToken, String... parameters)
            throws FetchException {
        Uri.Builder uri = new Uri.Builder()
                .scheme("https")
                .authority("api.odpt.org")
                .appendPath("api")
                .appendPath("v4")
                .appendPath(resource)
                .appendQueryParameter("acl:consumerKey", accessToken);
        for (int index = 0; index < parameters.length; index += 2) {
            uri.appendQueryParameter(parameters[index], parameters[index + 1]);
        }
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new java.net.URL(uri.build().toString()).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "SuguNoru/2.1 (odpt-timetable-refresh)");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw new FetchException("ODPTアクセストークンを確認してください");
            }
            if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new FetchException("このODPTアクセストークンには時刻表の利用権限がありません");
            }
            if (status < HttpURLConnection.HTTP_OK || status >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new FetchException("ODPTから時刻表を取得できませんでした（HTTP " + status + "）");
            }
            try (InputStream input = connection.getInputStream()) {
                return new JSONArray(new String(readLimited(input), StandardCharsets.UTF_8));
            }
        } catch (FetchException error) {
            throw error;
        } catch (SocketTimeoutException error) {
            throw new FetchException("ODPTへの接続がタイムアウトしました", error);
        } catch (UnknownHostException error) {
            throw new FetchException("インターネットに接続できません", error);
        } catch (SSLException error) {
            throw new FetchException("ODPTへの安全な接続を確認できません", error);
        } catch (JSONException error) {
            throw new FetchException("ODPTの応答を読み取れませんでした", error);
        } catch (IOException error) {
            throw new FetchException("ODPTに接続できませんでした", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException, FetchException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > MAX_RESPONSE_BYTES) throw new FetchException("ODPTの応答が大きすぎます");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static OfficialTimetableParser.Timetable merge(
            OfficialTimetableParser.Timetable first, OfficialTimetableParser.Timetable second
    ) {
        return new OfficialTimetableParser.Timetable(
                merged(first.weekdayTimes(), second.weekdayTimes()),
                merged(first.weekendTimes(), second.weekendTimes()),
                merged(first.holidayTimes(), second.holidayTimes()));
    }

    private static List<LocalTime> merged(List<LocalTime> first, List<LocalTime> second) {
        Set<LocalTime> values = new TreeSet<>(first);
        values.addAll(second);
        return new ArrayList<>(values);
    }

    private static OfficialTimetableParser.Timetable emptyTimetable() {
        return new OfficialTimetableParser.Timetable(List.of(), List.of(), List.of());
    }

    private static boolean canGetOn(JSONObject value) {
        if (!value.has("odpt:canGetOn")) return true;
        return Boolean.parseBoolean(value.optString("odpt:canGetOn", "false"));
    }

    private static LocalTime parseTime(String value) {
        try { return LocalTime.parse(value.trim()); }
        catch (RuntimeException ignored) { return null; }
    }

    private static CalendarKind calendarKind(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("weekday")) return CalendarKind.WEEKDAY;
        if (normalized.contains("holiday")) return CalendarKind.HOLIDAY;
        if (normalized.contains("saturday") || normalized.contains("sunday")
                || normalized.contains("weekend")) return CalendarKind.WEEKEND;
        return CalendarKind.OTHER;
    }

    private static boolean matchesDestination(String value, String destination) {
        String actual = normalizedDestination(value);
        String expected = normalizedDestination(destination);
        return !actual.isEmpty() && !expected.isEmpty()
                && (actual.equals(expected) || actual.contains(expected) || expected.contains(actual));
    }

    private static boolean sameText(String first, String second) {
        return normalized(first).equals(normalized(second));
    }

    private static String normalizedRoute(String value) {
        String route = value == null ? "" : value;
        route = normalized(route);
        int description = route.indexOf('(');
        return normalized(description >= 0 ? route.substring(0, description) : route);
    }

    private static String normalizedDestination(String value) {
        String result = normalized(value);
        for (String suffix : List.of("方面", "行き", "行")) {
            if (result.endsWith(suffix)) result = result.substring(0, result.length() - suffix.length());
        }
        return result;
    }

    private static String normalized(String value) {
        StringBuilder result = new StringBuilder();
        for (char character : Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .replace('−', '-').toCharArray()) {
            if (character >= '０' && character <= '９') result.append((char) ('0' + character - '０'));
            else if (!Character.isWhitespace(character) && character != '　') result.append(character);
        }
        return result.toString();
    }

    private enum CalendarKind { WEEKDAY, WEEKEND, HOLIDAY, OTHER }
    private record RoutePattern(String id, String boardingStopPole) {}
    record TimetableRecord(
            String calendar, String busstopPole, String departureTime, String destination,
            boolean canGetOn
    ) {}

    public static final class FetchException extends Exception {
        public FetchException(String message) { super(message); }
        public FetchException(String message, Throwable cause) { super(message, cause); }
    }
}
