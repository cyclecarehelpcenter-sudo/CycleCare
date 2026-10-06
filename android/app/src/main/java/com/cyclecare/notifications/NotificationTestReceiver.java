package com.cyclecare.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationTestReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        if (title == null) title = "CycleCare Reminder 🌸";
        if (body == null) body = "Your cycle preparation care kit is ready!";

        DiscreetNotificationManager manager = new DiscreetNotificationManager(context);
        manager.showReminderNotification(title, body);
    }
}
