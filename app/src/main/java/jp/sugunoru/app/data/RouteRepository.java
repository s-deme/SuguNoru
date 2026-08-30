package jp.sugunoru.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import jp.sugunoru.app.model.RoutePlan;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public final class RouteRepository {
    private static final String TAG = "RouteRepository";
    private static final String PREFS = "sugunoru_routes";
    private static final String KEY_ROUTES = "routes_v1";
    private static final String KEY_BACKUP = "routes_backup_v1";
    private static final int SCHEMA_VERSION = 2;
    private final SharedPreferences preferences;

    public RouteRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<RoutePlan> load() {
        String raw = preferences.getString(KEY_ROUTES, "[]");
        List<RoutePlan> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                try {
                    result.add(RoutePlan.fromJson(array.getJSONObject(i)));
                } catch (RuntimeException | JSONException itemError) {
                    Log.w(TAG, "壊れた登録データを1件スキップしました", itemError);
                }
            }
        } catch (JSONException error) {
            Log.e(TAG, "保存データを読み込めません", error);
            String backup = preferences.getString(KEY_BACKUP, null);
            if (backup != null) {
                try {
                    result.addAll(decode(new JSONArray(backup)));
                    Log.w(TAG, "自動バックアップから復旧しました");
                } catch (JSONException backupError) {
                    Log.e(TAG, "自動バックアップも読み込めません", backupError);
                }
            }
        }
        return result;
    }

    public boolean save(List<RoutePlan> plans) {
        String encoded;
        try {
            encoded = encode(plans);
        } catch (JSONException error) {
            Log.e(TAG, "保存用データを作れません", error);
            return false;
        }
        String previous = preferences.getString(KEY_ROUTES, "[]");
        return preferences.edit()
                .putString(KEY_BACKUP, previous)
                .putString(KEY_ROUTES, encoded)
                .commit();
    }

    public String exportJson(List<RoutePlan> plans) throws JSONException {
        org.json.JSONObject root = new org.json.JSONObject();
        root.put("app", "すぐのる");
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("exportedAt", System.currentTimeMillis());
        root.put("routes", new JSONArray(encode(plans)));
        return root.toString(2);
    }

    public List<RoutePlan> importJson(String raw) throws JSONException {
        String trimmed = raw == null ? "" : raw.trim();
        JSONArray array = trimmed.startsWith("[")
                ? new JSONArray(trimmed)
                : new org.json.JSONObject(trimmed).getJSONArray("routes");
        List<RoutePlan> result = decode(array);
        if (result.size() != array.length()) {
            throw new JSONException("読み込めない登録が含まれています");
        }
        return result;
    }

    public boolean restoreAutomaticBackup() {
        String backup = preferences.getString(KEY_BACKUP, null);
        return backup != null && preferences.edit().putString(KEY_ROUTES, backup).commit();
    }

    private String encode(List<RoutePlan> plans) throws JSONException {
        JSONArray array = new JSONArray();
        for (RoutePlan plan : plans) array.put(plan.toJson());
        return array.toString();
    }

    private List<RoutePlan> decode(JSONArray array) {
        List<RoutePlan> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            try { result.add(RoutePlan.fromJson(array.getJSONObject(i))); }
            catch (RuntimeException | JSONException error) { Log.w(TAG, "登録を読み込めません", error); }
        }
        return result;
    }
}
