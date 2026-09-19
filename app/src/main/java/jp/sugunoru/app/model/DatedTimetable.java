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
    public record Service(Set<LocalDate> dates, List<LocalTime> times) {
        public Service {
            dates = Set.copyOf(dates);
            times = List.copyOf(new TreeSet<>(times));
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

    public JSONArray toJson() throws JSONException {
        JSONArray result = new JSONArray();
        for (Service service : services) {
            JSONArray dates = new JSONArray();
            for (LocalDate date : new TreeSet<>(service.dates())) dates.put(date.toString());
            JSONArray times = new JSONArray();
            for (LocalTime time : service.times()) times.put(time.toString());
            result.put(new JSONObject().put("dates", dates).put("times", times));
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
            services.add(new Service(dates, times));
        }
        return new DatedTimetable(services);
    }
}
