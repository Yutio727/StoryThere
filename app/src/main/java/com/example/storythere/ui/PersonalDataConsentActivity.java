package com.example.storythere.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.storythere.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

public class PersonalDataConsentActivity extends AppCompatActivity {
    public static final String CONSENT_PREFS = "PersonalDataConsentPrefs";
    public static final String KEY_ACCEPTED = "personal_data_consent_accepted";
    public static final String KEY_ACCEPTED_AT = "personal_data_consent_accepted_at";
    public static final String KEY_VERSION = "personal_data_consent_version";
    public static final int CURRENT_VERSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_personal_data_consent);
        getWindow().setStatusBarColor(getResources().getColor(R.color.progress_blue));

        MaterialCheckBox consentCheckbox = findViewById(R.id.checkboxConsent);
        MaterialButton acceptButton = findViewById(R.id.btnAcceptConsent);
        MaterialButton declineButton = findViewById(R.id.btnDeclineConsent);
        TextView appName = findViewById(R.id.tvConsentAppName);

        appName.setText(getString(R.string.app_name));
        acceptButton.setEnabled(false);

        consentCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> acceptButton.setEnabled(isChecked));
        acceptButton.setOnClickListener(v -> {
            markAccepted(this);
            Intent intent = new Intent(PersonalDataConsentActivity.this, Login.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
        declineButton.setOnClickListener(v -> finishAffinity());
    }

    @Override
    public void onBackPressed() {
        finishAffinity();
    }

    public static boolean isAccepted(AppCompatActivity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(CONSENT_PREFS, MODE_PRIVATE);
        return prefs.getBoolean(KEY_ACCEPTED, false);
    }

    public static void markAccepted(AppCompatActivity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(CONSENT_PREFS, MODE_PRIVATE);
        prefs.edit()
            .putBoolean(KEY_ACCEPTED, true)
            .putLong(KEY_ACCEPTED_AT, System.currentTimeMillis())
            .putInt(KEY_VERSION, CURRENT_VERSION)
            .apply();
    }
}
