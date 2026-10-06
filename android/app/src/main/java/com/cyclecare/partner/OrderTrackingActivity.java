package com.cyclecare.partner;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyclecare.R;
import com.cyclecare.api.ApiClient;
import com.cyclecare.api.ApiService;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderTrackingActivity extends AppCompatActivity {

    private TextView tvOrderTitle;
    private TextView tvEtaBadge;
    private TextView tvOtpDisplay;
    private TextView tvStatusStep3;
    private TextView tvStatusStep4;
    private TextView tvStatusStep5;
    private LinearLayout layoutCourierMarker;
    private ApiService apiService;
    private Handler handler;
    private int simulatedProgress = 25;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_tracking);

        apiService = ApiClient.getApiService(this);
        handler = new Handler(Looper.getMainLooper());

        tvOrderTitle = findViewById(R.id.tv_order_title);
        tvEtaBadge = findViewById(R.id.tv_eta_badge);
        tvOtpDisplay = findViewById(R.id.tv_otp_display);
        tvStatusStep3 = findViewById(R.id.tv_status_step3);
        tvStatusStep4 = findViewById(R.id.tv_status_step4);
        tvStatusStep5 = findViewById(R.id.tv_status_step5);
        layoutCourierMarker = findViewById(R.id.layout_courier_marker);

        fetchOrderTracking();
        startPeriodicUpdates();
    }

    private void fetchOrderTracking() {
        String orderId = getIntent().getStringExtra("order_id");
        if (orderId == null || orderId.isEmpty()) {
            orderId = "cc-demo-1001";
        }

        apiService.getOrderTracking(orderId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> body = response.body();
                    if (body.containsKey("order")) {
                        Map<String, Object> order = (Map<String, Object>) body.get("order");
                        if (order.containsKey("order_number")) {
                            tvOrderTitle.setText("Order " + order.get("order_number"));
                        }
                        if (order.containsKey("delivery_otp")) {
                            String otp = String.valueOf(order.get("delivery_otp"));
                            tvOtpDisplay.setText(otp.replace("", " ").trim());
                        }
                    }
                    if (body.containsKey("delivery")) {
                        Map<String, Object> del = (Map<String, Object>) body.get("delivery");
                        String status = String.valueOf(del.get("status"));
                        applyDeliveryStatus(status);
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Keep default simulated fallback state
            }
        });
    }

    private void applyDeliveryStatus(String status) {
        if ("ARRIVED".equalsIgnoreCase(status)) {
            tvEtaBadge.setText("Arrived at Door");
            tvStatusStep4.setText("Arrived");
            tvStatusStep4.setTextColor(android.graphics.Color.parseColor("#D97706"));
        } else if ("DELIVERED".equalsIgnoreCase(status)) {
            tvEtaBadge.setText("Delivered");
            tvStatusStep5.setText("Delivered");
            tvStatusStep5.setTextColor(android.graphics.Color.parseColor("#16A34A"));
        } else {
            tvEtaBadge.setText("ETA: 12–18 min");
            tvStatusStep3.setText("In Transit");
        }
    }

    private void startPeriodicUpdates() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Animate courier marker slightly across the map bar
                if (layoutCourierMarker != null) {
                    simulatedProgress = (simulatedProgress + 15);
                    if (simulatedProgress > 220) simulatedProgress = 60;
                    layoutCourierMarker.setTranslationX(simulatedProgress);
                }
                handler.postDelayed(this, 3000);
            }
        }, 1500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}
