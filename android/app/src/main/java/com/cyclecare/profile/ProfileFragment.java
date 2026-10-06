package com.cyclecare.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.cyclecare.R;
import com.cyclecare.auth.LoginActivity;
import com.cyclecare.partner.PartnerCareActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ProfileFragment extends Fragment {

    private Button btnShareApk, btnAppSettings, btnLogout;
    private TextView tvUserName, tvUserEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        tvUserName = view.findViewById(R.id.tv_user_name);
        tvUserEmail = view.findViewById(R.id.tv_user_email);
        btnShareApk = view.findViewById(R.id.btn_share_apk);
        btnAppSettings = view.findViewById(R.id.btn_app_settings);
        btnLogout = view.findViewById(R.id.btn_logout);

        if (getActivity() != null) {
            SharedPreferences prefs = getActivity().getSharedPreferences("cyclecare_prefs", Context.MODE_PRIVATE);
            String userName = prefs.getString("user_name", "Demo User");
            tvUserName.setText(userName);
            tvUserEmail.setText(userName.toLowerCase().replaceAll("\\s+", "") + "@cyclecare.com");
        }

        View cardPartnerCare = view.findViewById(R.id.card_partner_care);
        if (cardPartnerCare != null) {
            cardPartnerCare.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), PartnerCareActivity.class);
                startActivity(intent);
            });
        }

        View cardMyOrders = view.findViewById(R.id.card_my_orders);
        if (cardMyOrders != null) {
            cardMyOrders.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), com.cyclecare.store.OrdersActivity.class);
                startActivity(intent);
            });
        }

        View cardMyAddresses = view.findViewById(R.id.card_my_addresses);
        if (cardMyAddresses != null) {
            cardMyAddresses.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), com.cyclecare.store.AddressManagementActivity.class);
                startActivity(intent);
            });
        }

        btnShareApk.setOnClickListener(v -> shareApkFile());

        btnAppSettings.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> {
            if (getActivity() != null) {
                SharedPreferences prefs = getActivity().getSharedPreferences("cyclecare_prefs", Context.MODE_PRIVATE);
                prefs.edit().clear().apply();
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });

        return view;
    }

    private void shareApkFile() {
        try {
            Context context = getContext();
            if (context == null) return;

            ApplicationInfo appInfo = context.getApplicationInfo();
            File originalApk = new File(appInfo.sourceDir);

            File tempApk = new File(context.getExternalCacheDir(), "CycleCare.apk");
            copyFile(originalApk, tempApk);

            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", tempApk);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/vnd.android.package-archive");
            shareIntent.putExtra(Intent.EXTRA_STREAM, apkUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "CycleCare App APK");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Here is the CycleCare Android App APK file!");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share CycleCare APK File via"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Unable to share APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
