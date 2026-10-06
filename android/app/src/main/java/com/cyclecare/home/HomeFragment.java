package com.cyclecare.home;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cyclecare.R;
import com.cyclecare.cycle.PeriodLogActivity;
import com.cyclecare.database.AppDatabase;
import com.cyclecare.database.entity.PeriodLogEntity;
import com.cyclecare.store.CartActivity;
import com.cyclecare.store.CheckoutActivity;
import com.cyclecare.wellness.AskAIAssistantActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {

    private Button btnQuickLog, btnPrepareNow, btnViewCareKit, btnEmergencyMode, btnAskAi;
    private TextView tvGreeting, tvDialDaysCount, tvDialStatusSub, tvDialPhaseTag, tvCycleDay;
    private LinearLayout llDialCore;
    private FrameLayout btnToggleActive, btnToggleSymptoms, btnToggleOvulation, btnToggleInsights;
    private ObjectAnimator pulseAnimator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tvGreeting = view.findViewById(R.id.tv_greeting);
        tvDialDaysCount = view.findViewById(R.id.tv_dial_days_count);
        tvDialStatusSub = view.findViewById(R.id.tv_dial_status_sub);
        tvDialPhaseTag = view.findViewById(R.id.tv_dial_phase_tag);
        tvCycleDay = view.findViewById(R.id.tv_cycle_day);
        llDialCore = view.findViewById(R.id.ll_dial_core);

        btnToggleActive = view.findViewById(R.id.btn_toggle_active);
        btnToggleSymptoms = view.findViewById(R.id.btn_toggle_symptoms);
        btnToggleOvulation = view.findViewById(R.id.btn_toggle_ovulation);
        btnToggleInsights = view.findViewById(R.id.btn_toggle_insights);

        btnQuickLog = view.findViewById(R.id.btn_quick_log);
        btnPrepareNow = view.findViewById(R.id.btn_prepare_now);
        btnViewCareKit = view.findViewById(R.id.btn_view_care_kit);
        btnEmergencyMode = view.findViewById(R.id.btn_emergency_mode);
        btnAskAi = view.findViewById(R.id.btn_ask_ai);

        // Load greeting
        if (getActivity() != null) {
            SharedPreferences prefs = getActivity().getSharedPreferences("cyclecare_prefs", Context.MODE_PRIVATE);
            String userName = prefs.getString("user_name", "User");
            tvGreeting.setText("Welcome, " + userName);
        }

        // Start pulse animation on the thermostat cycle dial core
        startDialPulseAnimation();

        // 📊 Compute Real Cycle Estimation from Room Database
        computeRealCycleEstimation();

        // Dial Core Click Action
        llDialCore.setOnClickListener(v -> {
            v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                startActivity(new Intent(getActivity(), PeriodLogActivity.class));
            }).start();
        });

        // Quick Row Toggles
        btnToggleActive.setOnClickListener(v -> startActivity(new Intent(getActivity(), PeriodLogActivity.class)));
        btnToggleSymptoms.setOnClickListener(v -> startActivity(new Intent(getActivity(), PeriodLogActivity.class)));
        btnToggleOvulation.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectNavigationTab(R.id.nav_calendar);
            }
        });
        btnToggleInsights.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectNavigationTab(R.id.nav_profile);
            }
        });

        btnQuickLog.setOnClickListener(v -> startActivity(new Intent(getActivity(), PeriodLogActivity.class)));

        btnPrepareNow.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectNavigationTab(R.id.nav_store);
            }
        });

        btnViewCareKit.setOnClickListener(v -> {
            Intent cartIntent = new Intent(getActivity(), CartActivity.class);
            startActivity(cartIntent);
        });

        btnEmergencyMode.setOnClickListener(v -> {
            Intent emergencyIntent = new Intent(getActivity(), CheckoutActivity.class);
            emergencyIntent.putExtra(CheckoutActivity.EXTRA_BUY_NOW_PRODUCT_ID, "a1111111-1111-1111-1111-111111111111");
            emergencyIntent.putExtra(CheckoutActivity.EXTRA_BUY_NOW_PRODUCT_NAME, "CycleCare Emergency Comfort & Period Kit");
            emergencyIntent.putExtra(CheckoutActivity.EXTRA_BUY_NOW_PRICE, 149.0);
            emergencyIntent.putExtra(CheckoutActivity.EXTRA_BUY_NOW_QTY, 1);
            emergencyIntent.putExtra(CheckoutActivity.EXTRA_BUY_NOW_IMAGE, "https://images.unsplash.com/photo-1583947215259-38e31be8751f?auto=format&fit=crop&w=600&q=80");
            startActivity(emergencyIntent);
        });

        btnAskAi.setOnClickListener(v -> startActivity(new Intent(getActivity(), AskAIAssistantActivity.class)));

        return view;
    }

    /**
     * Calculates real cycle progress and days remaining from the local database.
     */
    private void computeRealCycleEstimation() {
        if (getContext() == null) return;

        AppDatabase.getInstance(requireContext())
                .periodLogDao()
                .getAllPeriodLogs()
                .observe(getViewLifecycleOwner(), logs -> {
                    int cycleLength = 28;
                    int cycleDay = 24;
                    int daysRemaining = 4;
                    String phase = "Luteal Phase";

                    if (logs != null && !logs.isEmpty()) {
                        PeriodLogEntity latestLog = logs.get(0);
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            Date startDate = sdf.parse(latestLog.getStartDate());
                            if (startDate != null) {
                                long diffMs = Math.abs(System.currentTimeMillis() - startDate.getTime());
                                long diffDays = TimeUnit.MILLISECONDS.toDays(diffMs);
                                cycleDay = (int) (diffDays % cycleLength) + 1;
                                daysRemaining = cycleLength - cycleDay;
                                if (daysRemaining <= 0) daysRemaining = 1;

                                if (cycleDay <= 5) {
                                    phase = "Menstrual Phase";
                                } else if (cycleDay <= 11) {
                                    phase = "Follicular Phase";
                                } else if (cycleDay <= 16) {
                                    phase = "Ovulation Window";
                                } else {
                                    phase = "Luteal Phase";
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    int percent = (int) (((float) cycleDay / cycleLength) * 100);
                    tvDialDaysCount.setText(String.valueOf(daysRemaining));
                    tvDialStatusSub.setText("days left");
                    tvDialPhaseTag.setText(phase);
                    tvCycleDay.setText("Day " + cycleDay + " of " + cycleLength + " (" + percent + "%)");
                });
    }

    private void startDialPulseAnimation() {
        if (llDialCore != null) {
            PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.035f, 1.0f);
            PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.035f, 1.0f);
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(llDialCore, scaleX, scaleY);
            pulseAnimator.setDuration(2200);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.setRepeatMode(ValueAnimator.RESTART);
            pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            pulseAnimator.start();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
    }
}
