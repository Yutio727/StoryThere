package com.example.storythere;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import android.content.res.ColorStateList;

public class ResetPassword extends AppCompatActivity {

    private FrameLayout overlayView;
    private ConstraintLayout overlayContent;
    private ImageView overlayAppIcon;
    private ProgressBar overlayProgressBar;
    private ImageView overlayResultIcon;
    private TextView overlayResultText;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reset_password);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Set status bar color to blue
        getWindow().setStatusBarColor(getResources().getColor(R.color.progress_blue));

        // Set ActionBar color to blue
        if (getSupportActionBar() != null) {
            getSupportActionBar().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(getResources().getColor(R.color.progress_blue)));
        }

        final int colorRed = getResources().getColor(android.R.color.holo_red_dark);
        final int colorFocused = getResources().getColor(R.color.progress_blue);
        final int colorUnfocused = getResources().getColor(R.color.textfield_stroke);
        final int colorTextNormal = getResources().getColor(R.color.textfield_text);

        // Initialize views
        final TextInputLayout emailLayout = findViewById(R.id.etEmailLayout);
        final EditText emailEdit = findViewById(R.id.etEmail);
        
        // Set up focus change listeners
        emailEdit.setOnFocusChangeListener((v, hasFocus) -> {
            String value = emailEdit.getText() != null ? emailEdit.getText().toString().trim() : "";
            boolean valid = value.length() >= 5 && value.length() <= 50 && value.contains("@") && android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches();
            
            if (hasFocus) {
                emailLayout.setBoxStrokeColor(colorFocused);
                emailLayout.setEndIconTintList(ColorStateList.valueOf(colorFocused));
            } else {
                if (valid) {
                    emailLayout.setBoxStrokeColor(colorUnfocused);
                    emailLayout.setEndIconTintList(ColorStateList.valueOf(colorUnfocused));
                } else {
                    emailLayout.setBoxStrokeColor(colorRed);
                    emailLayout.setEndIconTintList(ColorStateList.valueOf(colorRed));
                }
            }
            emailLayout.invalidate();
        });
        
        // Set initial state
        emailLayout.setBoxStrokeColor(emailEdit.hasFocus() ? colorFocused : colorUnfocused);
        emailLayout.setEndIconTintList(ColorStateList.valueOf(emailEdit.hasFocus() ? colorFocused : colorUnfocused));
        emailLayout.invalidate();

        // Real-time email validation
        emailEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String value = s.toString().trim();
                boolean valid = value.length() >= 5 && value.length() <= 50 && value.contains("@") && android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches();
                emailEdit.setTextColor(valid ? colorTextNormal : colorRed);
                if (emailEdit.hasFocus()) {
                    emailLayout.setBoxStrokeColor(colorFocused);
                    emailLayout.setEndIconTintList(ColorStateList.valueOf(colorFocused));
                } else {
                    if (valid) {
                        emailLayout.setBoxStrokeColor(colorUnfocused);
                        emailLayout.setEndIconTintList(ColorStateList.valueOf(colorUnfocused));
                    } else {
                        emailLayout.setBoxStrokeColor(colorRed);
                        emailLayout.setEndIconTintList(ColorStateList.valueOf(colorRed));
                    }
                }
                emailLayout.invalidate();
            }
        });

        // Initialize overlay views
        overlayView = findViewById(R.id.overlayView);
        overlayContent = findViewById(R.id.overlayContent);
        overlayAppIcon = findViewById(R.id.overlayAppIcon);
        overlayProgressBar = findViewById(R.id.overlayProgressBar);
        overlayResultIcon = findViewById(R.id.overlayResultIcon);
        overlayResultText = findViewById(R.id.overlayResultText);

        // Send button click handler
        findViewById(R.id.btnSend).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailEdit.getText() != null ? emailEdit.getText().toString().trim() : "";
                boolean emailValid = email.length() >= 5 && email.length() <= 50 && email.contains("@") && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
                
                if (!emailValid) {
                    emailLayout.setBoxStrokeColor(colorRed);
                    emailLayout.setEndIconTintList(ColorStateList.valueOf(colorRed));
                    emailEdit.setTextColor(colorRed);
                    emailLayout.invalidate();
                    Toast.makeText(ResetPassword.this, getString(R.string.please_fill_all_fields_correctly), Toast.LENGTH_SHORT).show();
                    return;
                }

                // Show overlay with fade-in animation
                overlayAppIcon.setVisibility(View.VISIBLE);
                overlayProgressBar.setVisibility(View.VISIBLE);
                overlayResultIcon.setVisibility(View.GONE);
                overlayResultText.setVisibility(View.GONE);
                overlayView.setVisibility(View.VISIBLE);
                Animation fadeIn = new AlphaAnimation(0, 1);
                fadeIn.setDuration(300);
                overlayView.startAnimation(fadeIn);

                // Firebase password reset logic
                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        .addOnCompleteListener(task -> {
                            overlayProgressBar.setVisibility(View.GONE);
                            if (task.isSuccessful()) {
                                overlayAppIcon.setVisibility(View.GONE);
                                overlayResultIcon.setImageResource(R.drawable.ic_check_circle);
                                overlayResultIcon.setVisibility(View.VISIBLE);
                                overlayResultText.setText(R.string.password_reset_email_sent_successfully_please_check_your_email);
                                overlayResultText.setTextColor(getResources().getColor(R.color.progress_blue));
                                overlayResultText.setVisibility(View.VISIBLE);
                                Log.d("ResetPassword", "Password reset email sent to: " + email);
                                
                                handler.postDelayed(() -> {
                                    finish(); // Go back to login screen
                                }, 2000);
                            } else {
                                overlayAppIcon.setVisibility(View.GONE);
                                overlayResultIcon.setImageResource(R.drawable.ic_cross_circle);
                                overlayResultIcon.setVisibility(View.VISIBLE);
                                String errorMsg = getString(R.string.failed_to_send_reset_email);
                                if (task.getException() != null) {
                                    errorMsg += ":\n" + task.getException().getMessage();
                                }
                                overlayResultText.setText(errorMsg);
                                overlayResultText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                                overlayResultText.setVisibility(View.VISIBLE);
                                Log.e("ResetPassword", "Failed to send reset email: " + task.getException());
                                
                                handler.postDelayed(() -> {
                                    // Fade out overlay
                                    Animation fadeOut = new AlphaAnimation(1, 0);
                                    fadeOut.setDuration(300);
                                    fadeOut.setAnimationListener(new Animation.AnimationListener() {
                                        @Override
                                        public void onAnimationStart(Animation animation) {}
                                        @Override
                                        public void onAnimationEnd(Animation animation) {
                                            overlayView.setVisibility(View.GONE);
                                        }
                                        @Override
                                        public void onAnimationRepeat(Animation animation) {}
                                    });
                                    overlayView.startAnimation(fadeOut);
                                }, 3000);
                            }
                        });
            }
        });
    }
}