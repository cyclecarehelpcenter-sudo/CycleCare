package com.cyclecare.notifications;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class CycleCareMessagingService extends FirebaseMessagingService {
    private static final String TAG = "CycleCareFCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New Firebase Device Token: " + token);
        getSharedPreferences("cyclecare_prefs", MODE_PRIVATE)
                .edit()
                .putString("fcm_device_token", token)
                .apply();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        String title = "New care essential available";
        String body = "Check out the latest addition to CycleCare Store.";
        String productId = null;
        String imageUrl = null;
        String deepLink = null;

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
            if (remoteMessage.getNotification().getImageUrl() != null) {
                imageUrl = remoteMessage.getNotification().getImageUrl().toString();
            }
        }

        Map<String, String> data = remoteMessage.getData();
        if (data != null && !data.isEmpty()) {
            if (data.containsKey("title")) title = data.get("title");
            if (data.containsKey("body")) body = data.get("body");
            if (data.containsKey("product_id")) productId = data.get("product_id");
            if (data.containsKey("image_url")) imageUrl = data.get("image_url");
            if (data.containsKey("deep_link")) deepLink = data.get("deep_link");
        }

        DiscreetNotificationManager manager = new DiscreetNotificationManager(this);

        if (productId != null || deepLink != null) {
            manager.showProductNotification(title, body, productId, imageUrl, deepLink);
        } else {
            manager.showReminderNotification(title, body);
        }
    }
}
