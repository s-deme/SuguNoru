package jp.sugunoru.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import jp.sugunoru.app.MainActivity;
import jp.sugunoru.app.R;
import jp.sugunoru.app.data.AppPreferences;
import jp.sugunoru.app.data.RouteRepository;
import jp.sugunoru.app.model.RoutePlan;
import jp.sugunoru.app.model.ScheduleEngine;
import jp.sugunoru.app.ui.ScheduleDisplayFormatter;

import java.time.LocalDateTime;
import java.util.List;

public final class NextDepartureWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "jp.sugunoru.app.WIDGET_REFRESH";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) updateAll(context);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, NextDepartureWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) update(context, manager, id);
    }

    private static void update(Context context, AppWidgetManager manager, int id) {
        AppPreferences preferences = new AppPreferences(context);
        RoutePlan.Direction direction = preferences.direction();
        List<ScheduleEngine.RouteOption> options = ScheduleEngine.compare(
                new RouteRepository(context).load(), direction, LocalDateTime.now(), preferences.holidays());
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_next_departure);
        views.setTextViewText(R.id.widget_direction,
                direction == RoutePlan.Direction.OUTBOUND ? "→  出かける・次の便" : "←  帰る・次の便");
        if (options.isEmpty()) {
            views.setTextViewText(R.id.widget_time, "--:--");
            views.setTextViewText(R.id.widget_route, "路線を登録してください");
            views.setTextViewText(R.id.widget_wait, "タップして開く");
        } else {
            ScheduleEngine.RouteOption option = options.get(0);
            views.setTextViewText(R.id.widget_time, ScheduleDisplayFormatter.time(option.departure().at()));
            views.setTextViewText(R.id.widget_route,
                    option.plan().routeName() + "  " + option.plan().stopName());
            views.setTextViewText(R.id.widget_wait,
                    ScheduleEngine.formatMinutes(option.departure().waitMinutes()) + "  ·  "
                            + ScheduleDisplayFormatter.time(option.estimatedArrival()) + " 到着見込み");
        }
        Intent launch = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
                context, id, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent refresh = new Intent(context, NextDepartureWidget.class).setAction(ACTION_REFRESH);
        views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(
                context, id, refresh, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(id, views);
    }
}
