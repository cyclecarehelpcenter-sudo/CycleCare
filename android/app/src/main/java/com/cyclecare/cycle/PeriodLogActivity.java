package com.cyclecare.cycle;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyclecare.R;
import com.cyclecare.repository.CycleRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PeriodLogActivity extends AppCompatActivity {

    private EditText etStartDate, etEndDate, etNotes;
    private RadioGroup rgFlow;
    private Button btnSaveLog;
    private CycleRepository cycleRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_period_log);

        etStartDate = findViewById(R.id.et_start_date);
        etEndDate = findViewById(R.id.et_end_date);
        etNotes = findViewById(R.id.et_notes);
        rgFlow = findViewById(R.id.rg_flow);
        btnSaveLog = findViewById(R.id.btn_save_log);

        cycleRepository = new CycleRepository(this);

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        etStartDate.setText(today);

        btnSaveLog.setOnClickListener(v -> {
            String start = etStartDate.getText().toString().trim();
            String end = etEndDate.getText().toString().trim();
            String notes = etNotes.getText().toString().trim();

            int selectedId = rgFlow.getCheckedRadioButtonId();
            String flow = "MEDIUM";
            if (selectedId == R.id.rb_flow_light) flow = "LIGHT";
            else if (selectedId == R.id.rb_flow_heavy) flow = "HEAVY";

            if (start.isEmpty()) {
                Toast.makeText(this, "Start date required", Toast.LENGTH_SHORT).show();
                return;
            }

            cycleRepository.addPeriodLog(start, end, flow, notes);
            Toast.makeText(this, "Period log saved successfully (Offline-First Ready)", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
