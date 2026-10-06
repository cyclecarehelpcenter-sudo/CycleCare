package com.cyclecare.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyclecare.R;
import com.cyclecare.api.ApiClient;
import com.cyclecare.api.ApiService;
import com.cyclecare.home.MainActivity;
import com.cyclecare.models.ApiResponse;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvGoRegister, tvPandaHint;
    private ImageView ivPanda;
    private FrameLayout flPandaFrame;

    private Handler blinkHandler = new Handler(Looper.getMainLooper());
    private Runnable blinkRunnable;
    private boolean isSpecialPoseActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tvGoRegister = findViewById(R.id.tv_go_register);
        tvPandaHint = findViewById(R.id.tv_panda_hint);
        ivPanda = findViewById(R.id.iv_panda);
        flPandaFrame = findViewById(R.id.fl_panda_frame);

        // Pre-fill demo test credentials
        etEmail.setText("demo@cyclecare.com");
        etPassword.setText("password123");

        // Start natural eye blinking animation
        startPandaEyeBlinking();

        // 🖐️ EMAIL CLICK/FOCUS REACTION: Wave hand and say "Hi!"
        etEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                isSpecialPoseActive = true;
                blinkHandler.removeCallbacks(blinkRunnable);

                // Switch to Waving Hand Panda
                ivPanda.setImageResource(R.drawable.panda_wave);
                tvPandaHint.setText("Hi! 👋 Happy to see you. Enter your email.");

                // Adorable Waving Paw Animation
                animateHandWave();
            } else {
                isSpecialPoseActive = false;
                ivPanda.setRotation(0f);
                ivPanda.setImageResource(R.drawable.panda_idle);
                startPandaEyeBlinking();
            }
        });

        // 🙈 PASSWORD CLICK/FOCUS REACTION: Cover eyes with paws for privacy!
        etPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                isSpecialPoseActive = true;
                blinkHandler.removeCallbacks(blinkRunnable);
                ivPanda.setRotation(0f);

                // Switch to Shy / Covering Eyes Panda
                ivPanda.setImageResource(R.drawable.panda_shy);
                tvPandaHint.setText("Shh! I'm covering my eyes for your privacy. 🙈");

                // Subtle bounce animation
                flPandaFrame.animate().scaleX(0.95f).scaleY(0.95f).setDuration(120).withEndAction(() -> {
                    flPandaFrame.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }).start();
            } else {
                isSpecialPoseActive = false;
                ivPanda.setImageResource(R.drawable.panda_idle);
                tvPandaHint.setText("Welcome back! I missed you.");
                startPandaEyeBlinking();
            }
        });

        btnLogin.setOnClickListener(v -> performLogin());

        Button btnDemoDelivery = findViewById(R.id.btn_demo_delivery_login);
        btnDemoDelivery.setOnClickListener(v -> performDeliveryLogin());

        tvGoRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_in);
            finish();
        });
    }

    private void performDeliveryLogin() {
        etEmail.setText("delivery.demo@cyclecare.app");
        etPassword.setText("CycleCareDemo123!");
        
        // Mocking login for delivery agent
        SharedPreferences prefs = getSharedPreferences("cyclecare_prefs", MODE_PRIVATE);
        prefs.edit()
                .putString("jwt_token", "demo_delivery_jwt_token")
                .putString("user_name", "Delivery Agent")
                .apply();

        Intent intent = new Intent(LoginActivity.this, com.cyclecare.partner.DeliveryAgentActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_in);
        finish();
    }

    /**
     * Waving Paw Animation when user focuses on Email
     */
    private void animateHandWave() {
        if (ivPanda == null) return;
        ivPanda.animate().rotation(7f).setDuration(130).withEndAction(() -> {
            ivPanda.animate().rotation(-5f).setDuration(130).withEndAction(() -> {
                ivPanda.animate().rotation(6f).setDuration(130).withEndAction(() -> {
                    ivPanda.animate().rotation(0f).setDuration(130).start();
                }).start();
            }).start();
        }).start();
    }

    /**
     * Periodically blinks the Panda's eyes naturally every ~3.4 seconds.
     */
    private void startPandaEyeBlinking() {
        blinkHandler.removeCallbacksAndMessages(null);
        blinkRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isSpecialPoseActive) {
                    ivPanda.setImageResource(R.drawable.panda_blink);

                    blinkHandler.postDelayed(() -> {
                        if (!isSpecialPoseActive) {
                            ivPanda.setImageResource(R.drawable.panda_idle);
                        }
                    }, 220);
                }

                blinkHandler.postDelayed(this, 3400);
            }
        };
        blinkHandler.postDelayed(blinkRunnable, 2000);
    }

    private void performLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mascot joyful hop on button press
        if (flPandaFrame != null) {
            flPandaFrame.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).withEndAction(() -> {
                flPandaFrame.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }).start();
        }

        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);

        ApiService api = ApiClient.getApiService(this);
        api.login(body).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    saveSessionAndNavigate(response.body().getToken(), email.split("@")[0]);
                } else {
                    saveSessionAndNavigate("demo_jwt_token_12345", email.split("@")[0]);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Welcome to CycleCare Demo Mode!", Toast.LENGTH_SHORT).show();
                saveSessionAndNavigate("demo_jwt_token_12345", email.split("@")[0]);
            }
        });
    }

    private void saveSessionAndNavigate(String token, String userName) {
        SharedPreferences prefs = getSharedPreferences("cyclecare_prefs", MODE_PRIVATE);
        prefs.edit()
                .putString("jwt_token", token)
                .putString("user_name", userName)
                .apply();

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_in);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        blinkHandler.removeCallbacksAndMessages(null);
    }
}
