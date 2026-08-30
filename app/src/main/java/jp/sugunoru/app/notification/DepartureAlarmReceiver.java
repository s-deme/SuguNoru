package jp.sugunoru.app.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import jp.sugunoru.app.MainActivity;
import jp.sugunoru.app.R;

public final class DepartureAlarmReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "departure_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "出発リマインダー", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("登録した便に間に合う出発時刻を知らせます");
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);

        Intent launch = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = intent.getStringExtra("title");
        String detail = intent.getStringExtra("detail");
        android.app.Notification notification = new android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.rgb(0, 107, 79))
                .setContentTitle(title == null ? "そろそろ出発です" : title)
                .setContentText(detail == null ? "登録した便の時刻を確認してください" : detail)
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(android.app.Notification.CATEGORY_REMINDER)
                .build();
        manager.notify(intent.getIntExtra("notificationId", 1001), notification);
    }
}
