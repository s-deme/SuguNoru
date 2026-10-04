package jp.sugunoru.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Provider service dates, kept separate even when their weekday names are identical. */
public record DatedTimetable(List<Service> services) {
    public record Journey(LocalTime departure, LocalTime arrival) {
        public Journey {
            java.util.Objects.requireNonNull(departure);
            java.util.Objects.requireNonNull(arrival);
        }
    }

    public record Service(Set<LocalDate> dates, List<LocalTime> times, List<Journey> journeys) {
        public Service(Set<LocalDate> dates, List<LocalTime> times) {
            this(dates, times, List.of());
        }
        public Service {
            dates = Set.copyOf(dates);
            times = List.copyOf(new TreeSet<>(times));
            journeys = List.copyOf(journeys);
            for (Journey journey : journeys) if (!times.contains(journey.departure())) {
                throw new IllegalArgumentException("到着時刻に対応する発車時刻がありません");
            }
            if (dates.isEmpty() || times.isEmpty()) throw new IllegalArgumentException("運行日と時刻が必要です");
        }
    }

    public DatedTimetable {
        services = List.copyOf(services);
        if (services.isEmpty()) throw new IllegalArgumentException("運行する便がありません");
    }

    public List<LocalTime> timesFor(LocalDate date) {
        Set<LocalTime> times = new TreeSet<>();
        for (Service service : services) if (service.dates().contains(date)) times.addAll(service.times());
        return List.copyOf(times);
    }

    public LocalDate lastDate() {
        return services.stream().flatMap(service -> service.dates().stream()).max(LocalDate::compareTo).orElseThrow();
    }

    public List<java.time.LocalDateTime> arrivalsFor(java.time.LocalDateTime departure) {
        Set<java.time.LocalDateTime> result = new TreeSet<>();
        for (Service service : services) {
            if (!service.dates().contains(departure.toLocalDate())) continue;
            for (Journey journey : service.journeys()) {
                if (!journey.departure().equals(departure.toLocalTime())) continue;
                var arrival = departure.toLocalDate().atTime(journey.arrival());
                if (arrival.isBefore(departure)) arrival = arrival.plusDays(1);
                result.add(arrival);
            }
        }
        return List.copyOf(result);
    }

    public JSONArray toJson() throws JSONException {
        JSONArray result = new JSONArray();
        for (Service service : services) {
            JSONArray dates = new JSONArray();
            for (LocalDate date : new TreeSet<>(service.dates())) dates.put(date.toString());
            JSONArray times = new JSONArray();
            for (LocalTime time : service.times()) times.put(time.toString());
            JSONArray journeys = new JSONArray();
            for (Journey journey : service.journeys()) journeys.put(new JSONObject()
                    .put("departure", journey.departure().toString()).put("arrival", journey.arrival().toString()));
            result.put(new JSONObject().put("dates", dates).put("times", times).put("journeys", journeys));
        }
        return result;
    }

    public static DatedTimetable fromJson(JSONArray array) throws JSONException {
        List<Service> services = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            JSONArray days = item.getJSONArray("dates"), clocks = item.getJSONArray("times");
            Set<LocalDate> dates = new TreeSet<>();
            List<LocalTime> times = new ArrayList<>();
            for (int j = 0; j < days.length(); j++) dates.add(LocalDate.parse(days.getString(j)));
            for (int j = 0; j < clocks.length(); j++) times.add(LocalTime.parse(clocks.getString(j)));
            List<Journey> journeys = new ArrayList<>();
            JSONArray pairs = item.optJSONArray("journeys");
            if (pairs != null) for (int j = 0; j < pairs.length(); j++) {
                JSONObject pair = pairs.getJSONObject(j);
                journeys.add(new Journey(LocalTime.parse(pair.getString("departure")),
                        LocalTime.parse(pair.getString("arrival"))));
            }
            services.add(new Service(dates, times, journeys));
        }
        return new DatedTimetable(services);
    }
}
