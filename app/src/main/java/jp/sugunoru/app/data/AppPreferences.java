package jp.sugunoru.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import jp.sugunoru.app.model.RoutePlan;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public final class AppPreferences {
    private final SharedPreferences preferences;

    public AppPreferences(Context context) {
        preferences = context.getSharedPreferences("sugunoru_settings", Context.MODE_PRIVATE);
    }

    public RoutePlan.Direction direction() {
        try { return RoutePlan.Direction.valueOf(preferences.getString("direction", "OUTBOUND")); }
        catch (IllegalArgumentException error) { return RoutePlan.Direction.OUTBOUND; }
    }

    public void setDirection(RoutePlan.Direction value) {
        preferences.edit().putString("direction", value.name()).apply();
    }

    public Set<LocalDate> holidays() {
        Set<LocalDate> result = new HashSet<>();
        for (String value : preferences.getStringSet("holidays", Set.of())) {
            try { result.add(LocalDate.parse(value)); } catch (RuntimeException ignored) {}
        }
        return result;
    }

    public void setHolidays(Set<LocalDate> dates) {
        Set<String> values = new HashSet<>();
        for (LocalDate date : dates) values.add(date.toString());
        preferences.edit().putStringSet("holidays", values).apply();
    }
}
