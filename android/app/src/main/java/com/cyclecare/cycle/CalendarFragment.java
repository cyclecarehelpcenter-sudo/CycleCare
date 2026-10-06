package com.cyclecare.cycle;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cyclecare.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CalendarFragment extends Fragment {

    private CalendarView calendarView;
    private TextView tvSelectedDate, tvPhaseBadge, tvDateStatus, tvLoggedNotes;
    private Button btnLogForDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        calendarView = view.findViewById(R.id.calendar_view);
        tvSelectedDate = view.findViewById(R.id.tv_selected_date);
        tvPhaseBadge = view.findViewById(R.id.tv_phase_badge);
        tvDateStatus = view.findViewById(R.id.tv_date_status);
        tvLoggedNotes = view.findViewById(R.id.tv_logged_notes);
        btnLogForDate = view.findViewById(R.id.btn_log_for_date);

        // Set initial selected date to today
        Calendar today = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault());
        tvSelectedDate.setText(sdf.format(today.getTime()));

        calendarView.setOnDateChangeListener((view1, year, month, dayOfMonth) -> {
            Calendar sel = Calendar.getInstance();
            sel.set(year, month, dayOfMonth);
            String formattedDate = sdf.format(sel.getTime());
            tvSelectedDate.setText(formattedDate);

            // Dynamic phase calculation simulation based on day of month
            int dayMod = dayOfMonth % 28;
            if (dayMod >= 1 && dayMod <= 5) {
                tvPhaseBadge.setText("Period Phase");
                tvPhaseBadge.setBackgroundResource(R.drawable.bg_badge_period);
                tvPhaseBadge.setTextColor(getResources().getColor(R.color.colorPrimary));
                tvDateStatus.setText("Cycle Day " + dayMod + " • Heavy Flow Window");
                tvLoggedNotes.setText("Period active. Logged symptoms: Moderate cramps, low energy.");
            } else if (dayMod >= 12 && dayMod <= 16) {
                tvPhaseBadge.setText("Fertile Window");
                tvPhaseBadge.setBackgroundResource(R.drawable.bg_badge_ovulation);
                tvPhaseBadge.setTextColor(getResources().getColor(R.color.colorSkyBlueDark));
                tvDateStatus.setText("Cycle Day " + dayMod + " • Peak Ovulation Window");
                tvLoggedNotes.setText("High fertility window. Peak energy & calm mood.");
            } else {
                tvPhaseBadge.setText("Follicular / Luteal");
                tvPhaseBadge.setBackgroundResource(R.drawable.bg_badge_today);
                tvPhaseBadge.setTextColor(getResources().getColor(R.color.textSecondary));
                tvDateStatus.setText("Cycle Day " + (dayMod == 0 ? 28 : dayMod) + " • Regular Phase");
                tvLoggedNotes.setText("No severe symptoms recorded for this date.");
            }
        });

        btnLogForDate.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), PeriodLogActivity.class);
            startActivity(intent);
        });

        return view;
    }
}
