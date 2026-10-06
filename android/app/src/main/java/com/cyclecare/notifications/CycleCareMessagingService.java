package com.cyclecare.notifications;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class CycleCareMessagingService extends FirebaseMessagingService {
    private static final String TAG = "CycleCareFCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New Firebase Device Token: " + token);
        // Persist token in SharedPreferences
        getSharedPreferences("cyclecare_prefs", MODE_PRIVATE)
                .edit()
                .putString("fcm_device_token", token)
                .apply();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        String title = "CycleCare Alert";
        String body = "You have a new update";

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        } else if (remoteMessage.getData().size() > 0) {
            title = remoteMessage.getData().getOrDefault("title", title);
            body = remoteMessage.getData().getOrDefault("body", body);
        }

        DiscreetNotificationManager manager = new DiscreetNotificationManager(this);
        manager.showReminderNotification(title, body);
    }
}
