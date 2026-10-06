package com.cyclecare.profile;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.cyclecare.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class SettingsActivity extends AppCompatActivity {

    private Button btnShareApk, btnExportData, btnDeleteAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        btnShareApk = findViewById(R.id.btn_settings_share_apk);
        btnExportData = findViewById(R.id.btn_export_data);
        btnDeleteAccount = findViewById(R.id.btn_delete_account);

        btnShareApk.setOnClickListener(v -> shareApkFile());

        btnExportData.setOnClickListener(v -> 
            Toast.makeText(this, "Exporting personal cycle data JSON...", Toast.LENGTH_SHORT).show()
        );

        btnDeleteAccount.setOnClickListener(v -> 
            Toast.makeText(this, "Account deletion requested.", Toast.LENGTH_LONG).show()
        );
    }

    private void shareApkFile() {
        try {
            ApplicationInfo appInfo = getApplicationInfo();
            File originalApk = new File(appInfo.sourceDir);

            File tempApk = new File(getExternalCacheDir(), "CycleCare.apk");
            copyFile(originalApk, tempApk);

            Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", tempApk);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/vnd.android.package-archive");
            shareIntent.putExtra(Intent.EXTRA_STREAM, apkUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "CycleCare App APK");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Here is the CycleCare Android App APK file!");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share CycleCare APK File via"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Unable to share APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1024 * 4];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}
