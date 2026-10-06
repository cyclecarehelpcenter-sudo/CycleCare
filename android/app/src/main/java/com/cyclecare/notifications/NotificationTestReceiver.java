package com.cyclecare.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationTestReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        DiscreetNotificationManager manager = new DiscreetNotificationManager(context);

        if ("com.cyclecare.ACTION_STORE_PRODUCT_ADDED".equals(action)) {
            String name = intent.getStringExtra("name");
            String price = intent.getStringExtra("price");
            String productId = intent.getStringExtra("product_id");
            String imageUrl = intent.getStringExtra("image_url");
            String deepLink = intent.getStringExtra("deep_link");

            String title = "New care essential available";
            String body = (name != null ? name : "A new product") + " is now live in CycleCare Store at ₹" + (price != null ? price : "99") + "! Tap to view.";

            manager.showProductNotification(title, body, productId, imageUrl, deepLink);
            return;
        }

        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        if (title == null) title = "CycleCare Reminder";
        if (body == null) body = "Your cycle preparation care kit is ready!";

        manager.showReminderNotification(title, body);
    }
}
