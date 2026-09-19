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
import java.time.LocalDate;
import java.util.Map;
import java.util.HashMap;
import jp.sugunoru.app.model.DatedTimetable;
import java.util.ArrayList;
import java.util.List;
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
    private static final int STOP_POLE_BATCH_SIZE = 10; // ODPT rejects 40 OR conditions.
    private final String authority;

    public OdptTimetableFetcher() { this("api.odpt.org"); }

    // Tests exercise the same requests against ODPT's unauthenticated public mirror.
    OdptTimetableFetcher(String authority) {
        if (!Set.of("api.odpt.org", "api-public.odpt.org").contains(authority)) {
            throw new IllegalArgumentException("Unknown ODPT host");
        }
        this.authority = authority;
    }

    public record FetchResult(OfficialTimetableParser.Timetable timetable) {}

    /** Fetches the actual ordered patterns, including reverse, short and circular services. */
    public record RoutePatterns(List<BusRoutePattern> patterns, String cacheJson) {}

    public RoutePatterns routePatterns(String accessToken, String routeName) throws FetchException {
        if (accessToken == null || accessToken.isBlank()) {
            throw new FetchException("設定でODPTアクセストークンを保存してください");
        }
        String route = TransitCatalog.odptBusrouteId(routeName);
        if (route.isEmpty()) throw new FetchException("この路線名は公式データで確認できません。路線・系統を選び直してください");
        JSONArray selected = selectRoute(request("odpt:BusroutePattern", accessToken,
                "odpt:operator", OPERATOR, "odpt:busroute", route), routeName);
        if (selected.length() == 0) throw new FetchException("選択した路線の経路をODPTから取得できませんでした");
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
        for (int i = 0; i < ids.size(); i += STOP_POLE_BATCH_SIZE) {
            JSONArray recordsWithNames = request("odpt:BusstopPole", accessToken, "owl:sameAs",
                    String.join(",", ids.subList(i, Math.min(i + STOP_POLE_BATCH_SIZE, ids.size()))));
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

    static JSONArray selectRoute(JSONArray records, String routeName) {
        JSONArray selected = new JSONArray();
        String route = TransitCatalog.odptBusrouteId(routeName);
        if (route.isEmpty()) return selected;
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null && OPERATOR.equals(record.optString("odpt:operator"))
                    && route.equals(record.optString("odpt:busroute"))) selected.put(record);
        }
        return selected;
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
        List<BusRoutePattern> patterns = routePatterns(accessToken, routeName).patterns().stream()
                .filter(pattern -> destinationIsStop ? pattern.serves(boarding, destination)
                        : pattern.stops().stream().anyMatch(stop -> stop.canGetOn() && sameText(stop.name(), boarding)))
                .collect(java.util.stream.Collectors.toList());
        if (patterns.isEmpty()) throw new FetchException("選択した順に乗車・降車できる経路がありません");
        if (patterns.size() > MAX_ROUTE_PATTERNS) throw new FetchException("経路候補が多すぎます");
        Map<String, Set<LocalTime>> departures = new HashMap<>();
        for (BusRoutePattern pattern : patterns) {
            JSONArray trips = request("odpt:BusTimetable", accessToken,
                    "odpt:operator", OPERATOR, "odpt:busroutePattern", pattern.id());
            for (int i = 0; i < trips.length(); i++) {
                JSONObject trip = trips.optJSONObject(i);
                if (trip == null) continue;
                JSONArray objects = trip.optJSONArray("odpt:busTimetableObject");
                if (objects == null) continue;
                List<TripStop> stops = parseTripStops(objects);
                List<LocalTime> times = destinationIsStop
                        ? departuresBetween(pattern, stops, boarding, destination)
                        : departuresToward(pattern, stops, boarding, destination);
                if (!times.isEmpty()) {
                    String calendar = trip.optString("odpt:calendar");
                    if (calendar.isBlank()) throw new FetchException("便の運行日カレンダーがありません");
                    departures.computeIfAbsent(calendar, ignored -> new TreeSet<>()).addAll(times);
                }
            }
        }
        if (departures.isEmpty()) {
            throw new FetchException("降車停留所まで乗車できる便の時刻表がありません");
        }
        DatedTimetable dated = datedTimetable(departures,
                request("odpt:Calendar", accessToken, "odpt:operator", OPERATOR));
        return new FetchResult(new OfficialTimetableParser.Timetable(List.of(), List.of(), List.of(), dated));
    }

    record TripStop(String pole, String departureTime, boolean canGetOn, boolean canGetOff, String destination) {
        TripStop(String pole, String departureTime, boolean canGetOn, boolean canGetOff) {
            this(pole, departureTime, canGetOn, canGetOff, "");
        }
    }

    static DatedTimetable datedTimetable(Map<String, Set<LocalTime>> departures, JSONArray calendars)
            throws FetchException {
        Map<String, JSONObject> byId = new HashMap<>();
        for (int i = 0; i < calendars.length(); i++) {
            JSONObject calendar = calendars.optJSONObject(i);
            if (calendar != null) byId.put(calendar.optString("owl:sameAs"), calendar);
        }
        List<DatedTimetable.Service> services = new ArrayList<>();
        try {
            for (Map.Entry<String, Set<LocalTime>> entry : departures.entrySet()) {
                JSONObject calendar = byId.get(entry.getKey());
                if (calendar == null) throw new FetchException("便の運行日カレンダーを取得できませんでした");
                JSONArray days = calendar.getJSONArray("odpt:day");
                Set<LocalDate> dates = new TreeSet<>();
                String[] duration = calendar.getString("odpt:duration").split("/", -1);
                if (duration.length != 2) throw new IllegalArgumentException("Invalid duration");
                LocalDate start = LocalDate.parse(duration[0]), end = LocalDate.parse(duration[1]);
                for (int i = 0; i < days.length(); i++) {
                    LocalDate date = LocalDate.parse(days.getString(i));
                    if (!date.isBefore(start) && !date.isAfter(end)) dates.add(date);
                }
                if (!dates.isEmpty() && !entry.getValue().isEmpty()) {
                    services.add(new DatedTimetable.Service(dates, List.copyOf(entry.getValue())));
                }
            }
            if (services.isEmpty()) throw new FetchException("運行日が確認できる時刻表がありません");
            return new DatedTimetable(services);
        } catch (JSONException | IllegalArgumentException error) {
            throw new FetchException("運行日カレンダーを読み取れませんでした", error);
        }
    }

    static List<TripStop> parseTripStops(JSONArray objects) throws FetchException {
        List<JSONObject> ordered = new ArrayList<>();
        Set<Integer> indices = new java.util.HashSet<>();
        try {
            for (int i = 0; i < objects.length(); i++) {
                JSONObject stop = objects.getJSONObject(i);
                int index = stop.getInt("odpt:index");
                String departure = stop.optString("odpt:departureTime");
                if (!departure.isBlank() && parseTime(departure) == null) {
                    throw new FetchException("便の発車時刻を読み取れませんでした");
                }
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
                        !stop.has("odpt:canGetOff") || stop.optBoolean("odpt:canGetOff", false),
                        stop.optString("odpt:destinationSign")));
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
        return fetch(accessToken, routeName, stopName, destination, false);
    }

    static List<LocalTime> departuresToward(BusRoutePattern pattern, List<TripStop> trip,
                                           String boarding, String destination) {
        Set<String> poles = new java.util.HashSet<>();
        for (BusRoutePattern.Stop stop : pattern.stops()) {
            if (stop.canGetOn() && sameText(stop.name(), boarding)) poles.add(stop.pole());
        }
        Set<LocalTime> times = new TreeSet<>();
        for (TripStop stop : trip) {
            LocalTime time = parseTime(stop.departureTime());
            if (time != null && stop.canGetOn() && poles.contains(stop.pole())
                    && matchesDestination(stop.destination(), destination)) times.add(time);
        }
        return List.copyOf(times);
    }

    private JSONArray request(String resource, String accessToken, String... parameters)
            throws FetchException {
        String label = switch (resource) {
            case "odpt:BusroutePattern" -> "路線の経路";
            case "odpt:BusstopPole" -> "停留所";
            case "odpt:Calendar" -> "運行日";
            default -> "時刻表";
        };
        Uri.Builder uri = new Uri.Builder()
                .scheme("https")
                .authority(authority)
                .appendPath("api")
                .appendPath("v4")
                .appendPath(resource);
        if (authority.equals("api.odpt.org")) uri.appendQueryParameter("acl:consumerKey", accessToken);
        for (int index = 0; index < parameters.length; index += 2) {
            uri.appendQueryParameter(parameters[index], parameters[index + 1]);
        }
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new java.net.URL(uri.build().toString()).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "SuguNoru/2.1 (odpt-timetable-refresh)");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw new FetchException("ODPTアクセストークンを確認してください");
            }
            if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new FetchException("このODPTアクセストークンには" + label + "の利用権限がありません");
            }
            if (status < HttpURLConnection.HTTP_OK || status >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new FetchException("ODPTから" + label + "を取得できませんでした（HTTP " + status + "）");
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

    private static boolean canGetOn(JSONObject value) {
        if (!value.has("odpt:canGetOn")) return true;
        return Boolean.parseBoolean(value.optString("odpt:canGetOn", "false"));
    }

    private static LocalTime parseTime(String value) {
        try { return LocalTime.parse(value.trim()); }
        catch (RuntimeException ignored) { return null; }
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

    public static final class FetchException extends Exception {
        public FetchException(String message) { super(message); }
        public FetchException(String message, Throwable cause) { super(message, cause); }
    }
}
