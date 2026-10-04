package jp.sugunoru.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import jp.sugunoru.app.model.RoutePlan;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public final class AppPreferences {
    private final SharedPreferences preferences;
    private final SecureTokenStore tokens;

    public AppPreferences(Context context) {
        preferences = context.getSharedPreferences("sugunoru_settings", Context.MODE_PRIVATE);
        tokens = new SecureTokenStore(preferences);
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

    /** The token stays in this app's private, non-backed-up storage and is never exported. */
    public String odptAccessToken() {
        return tokens.read();
    }

    public boolean hasOdptAccessToken() {
        return !odptAccessToken().isEmpty();
    }

    public String routePatterns(String routeName) {
        return preferences.getString("route_patterns:" + routeName, "");
    }

    public void setRoutePatterns(String routeName, String json) {
        preferences.edit().putString("route_patterns:" + routeName, json).apply();
    }

    public void setOdptAccessToken(String value) {
        String token = value == null ? "" : value.trim();
        if (token.length() > 512) throw new IllegalArgumentException("ODPTアクセストークンが長すぎます");
        tokens.write(token);
    }
}
