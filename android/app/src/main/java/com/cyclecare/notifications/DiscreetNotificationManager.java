package com.cyclecare.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import com.cyclecare.R;

public class DiscreetNotificationManager {
    private static final String CHANNEL_ID = "cyclecare_reminders_channel";
    private static final int BRAND_COLOR = 0xFFE91E63; // CycleCare Berry Pink
    private Context context;

    public DiscreetNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "CycleCare Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Period preparation, reminders and store announcements");
            channel.enableVibration(true);
            channel.enableLights(true);
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

        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cyclecare_logo)
                .setLargeIcon(largeIcon)
                .setColor(BRAND_COLOR)
                .setContentTitle(displayTitle)
                .setContentText(displayBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    public void showStoreNotification(String title, String body) {
        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cyclecare_logo)
                .setLargeIcon(largeIcon)
                .setColor(BRAND_COLOR)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
