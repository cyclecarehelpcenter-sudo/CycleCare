package com.cyclecare.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import com.cyclecare.R;
import com.cyclecare.store.ProductDetailActivity;
import com.cyclecare.utils.ImageLoader;

public class DiscreetNotificationManager {
    private static final String CHANNEL_ID = "cyclecare_reminders_channel";
    private static final int BRAND_COLOR = 0xFFE91E63; // CycleCare Berry Pink
    private final Context context;

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
        showProductNotification(title, body, null, null, null);
    }

    public void showProductNotification(String title, String body, String productId, String imageUrl, String deepLink) {
        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);

        Intent intent = new Intent(context, ProductDetailActivity.class);
        if (productId != null && !productId.isEmpty()) {
            intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, productId);
        }
        if (deepLink != null && !deepLink.isEmpty()) {
            intent.setData(Uri.parse(deepLink));
        } else if (productId != null) {
            intent.setData(Uri.parse("cyclecare://product/" + productId));
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(),
                intent,
                flags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cyclecare_logo)
                .setLargeIcon(largeIcon)
                .setColor(BRAND_COLOR)
                .setContentTitle(title != null ? title : "New Care Essential Available")
                .setContentText(body != null ? body : "Check out the latest addition to CycleCare Store.")
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true);

        // Download large product image if provided
        Bitmap productBitmap = null;
        if (imageUrl != null && !imageUrl.isEmpty()) {
            try {
                productBitmap = ImageLoader.getInstance().downloadBitmapSync(imageUrl);
            } catch (Exception ignored) {}
        }

        if (productBitmap != null) {
            builder.setStyle(new NotificationCompat.BigPictureStyle()
                    .bigPicture(productBitmap)
                    .setSummaryText(body));
        } else {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
