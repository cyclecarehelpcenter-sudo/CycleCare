package com.cyclecare.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class DiscreetNotificationManager {
    private static final String CHANNEL_ID = "cyclecare_reminders_channel";
    private Context context;

    public DiscreetNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "CycleCare Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Period preparation and logging reminders");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public void showReminderNotification(String title, String sensitiveBody) {
        SharedPreferences prefs = context.getSharedPreferences("cyclecare_prefs", Context.MODE_PRIVATE);
        boolean isDiscreet = prefs.getBoolean("discreet_notifications_enabled", true);

        String displayTitle = isDiscreet ? "CycleCare Reminder" : title;
        String displayBody = isDiscreet ? "You have a reminder from CycleCare." : sensitiveBody;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(displayTitle)
                .setContentText(displayBody)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
