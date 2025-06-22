package com.example.storythere;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.res.ColorStateList;
import com.google.android.material.textfield.TextInputLayout;
import android.widget.EditText;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;
import android.os.Handler;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.google.firebase.auth.FirebaseAuth;
import android.widget.ProgressBar;
import com.google.firebase.auth.FirebaseUser;

public class Login extends AppCompatActivity {

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
        setContentView(R.layout.activity_login);
        // Removed insets padding code
        // Set status bar color to blue
        getWindow().setStatusBarColor(getResources().getColor(R.color.progress_blue));

        // Set ActionBar color to blue
        if (getSupportActionBar() != null) {
            getSupportActionBar().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(getResources().getColor(R.color.progress_blue)));
        }

        // Check if user is already authenticated
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            user.getIdToken(false).addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    String token = task.getResult().getToken();
                    Log.d("Login", "User already authenticated. Token: " + token);
                } else {
                    Log.w("Login", "User already authenticated, but failed to get token.");
                }
                startActivity(new Intent(Login.this, HomeActivity.class));
                finish();
            });
            return;
        }

        final int colorRed = getResources().getColor(android.R.color.holo_red_dark);
        final int colorFocused = getResources().getColor(R.color.progress_blue);
        final int colorUnfocused = getResources().getColor(R.color.textfield_stroke);
        final int colorTextNormal = getResources().getColor(R.color.textfield_text);

        // --- Custom logic for textfield stroke color on focus ---
        final TextInputLayout emailLayout = findViewById(R.id.etEmailLayout);
        final TextInputLayout passwordLayout = findViewById(R.id.etPasswordLayout);
        final EditText emailEdit = findViewById(R.id.etEmail);
        final EditText passwordEdit = findViewById(R.id.etPassword);

        emailEdit.setOnFocusChangeListener((v, hasFocus) -> {
            String value = emailEdit.getText() != null ? emailEdit.getText().toString().trim() : "";
            boolean valid = value.length() >= 5 && value.length() <= 50 && value.contains("@") && android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches();
            
            if (hasFocus) {
                emailLayout.setBoxStrokeColor(colorFocused);
            } else {
                emailLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
            }
            emailLayout.invalidate();
        });
        passwordEdit.setOnFocusChangeListener((v, hasFocus) -> {
            String value = passwordEdit.getText() != null ? passwordEdit.getText().toString() : "";
            boolean valid = value.length() >= 6 && value.length() <= 32;
            
            if (hasFocus) {
                passwordLayout.setBoxStrokeColor(colorFocused);
            } else {
                passwordLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
            }
            passwordLayout.invalidate();
        });
        // Set initial state
        emailLayout.setBoxStrokeColor(emailEdit.hasFocus() ? colorFocused : colorUnfocused);
        passwordLayout.setBoxStrokeColor(passwordEdit.hasFocus() ? colorFocused : colorUnfocused);
        emailLayout.invalidate();
        passwordLayout.invalidate();

        // Registration click logic
        TextView tvRegistration = findViewById(R.id.tvRegistration);
        tvRegistration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Login.this, Registration.class));
            }
        });

        // Forgot password click logic
        TextView tvForgetPassword = findViewById(R.id.tvForgetPassword);
        tvForgetPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Login.this, ResetPassword.class));
            }
        });

        // Login button logic
        findViewById(R.id.btnLogin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailEdit.getText() != null ? emailEdit.getText().toString().trim() : "";
                String password = passwordEdit.getText() != null ? passwordEdit.getText().toString().trim() : "";
                boolean valid = true;
                boolean emailValid = email.length() >= 5 && email.length() <= 50 && email.contains("@") && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
                boolean passwordValid = password.length() >= 6 && password.length() <= 32;
                if (!emailValid) {
                    emailLayout.setBoxStrokeColor(colorRed);
                    emailEdit.setTextColor(colorRed);
                    emailLayout.invalidate();
                    valid = false;
                }
                if (!passwordValid) {
                    passwordLayout.setBoxStrokeColor(colorRed);
                    passwordEdit.setTextColor(colorRed);
                    passwordLayout.invalidate();
                    valid = false;
                }
                if (!valid) {
                    android.widget.Toast.makeText(Login.this, getString(R.string.please_fill_all_fields_correctly), android.widget.Toast.LENGTH_SHORT).show();
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
                // Firebase login logic
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(Login.this, new com.google.android.gms.tasks.OnCompleteListener<com.google.firebase.auth.AuthResult>() {
                            @Override
                            public void onComplete(@androidx.annotation.NonNull com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult> task) {
                                overlayProgressBar.setVisibility(View.GONE);
                                if (task.isSuccessful()) {
                                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                                    if (user != null) {
                                        user.getIdToken(false).addOnCompleteListener(tokenTask -> {
                                            if (tokenTask.isSuccessful() && tokenTask.getResult() != null) {
                                                String token = tokenTask.getResult().getToken();
                                                Log.d("Login", "Login successful. Token: " + token);
                                            } else {
                                                Log.w("Login", "Login successful, but failed to get token.");
                                            }
                                        });
                                    }
                                    overlayAppIcon.setVisibility(View.GONE);
                                    overlayResultIcon.setImageResource(R.drawable.ic_check_circle);
                                    overlayResultIcon.setVisibility(View.VISIBLE);
                                    overlayResultText.setText(getString(R.string.welcome_title) + "\n" + getString(R.string.welcome_message));
                                    overlayResultText.setTextColor(getResources().getColor(R.color.progress_blue));
                                    overlayResultText.setVisibility(View.VISIBLE);
                                    handler.postDelayed(() -> {
                                        Intent intent = new Intent(Login.this, HomeActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        finish();
                                    }, 700);
                                } else {
                                    overlayAppIcon.setVisibility(View.GONE);
                                    overlayResultIcon.setImageResource(R.drawable.ic_cross_circle);
                                    overlayResultIcon.setVisibility(View.VISIBLE);
                                    String errorMsg = getString(R.string.login_failed);
                                    if (task.getException() != null) {
                                        errorMsg += ":\n" + task.getException().getMessage();
                                    }
                                    overlayResultText.setText(errorMsg);
                                    overlayResultText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                                    overlayResultText.setVisibility(View.VISIBLE);
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
                            }
                        });
            }
        });

        overlayView = findViewById(R.id.overlayView);
        overlayContent = findViewById(R.id.overlayContent);
        overlayAppIcon = findViewById(R.id.overlayAppIcon);
        overlayProgressBar = findViewById(R.id.overlayProgressBar);
        overlayResultIcon = findViewById(R.id.overlayResultIcon);
        overlayResultText = findViewById(R.id.overlayResultText);

        // Real-time validation for email and password
        emailEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String value = s.toString().trim();
                boolean valid = value.length() >= 5 && value.length() <= 50 && value.contains("@") && android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches();
                emailEdit.setTextColor(valid ? colorTextNormal : colorRed);
                if (emailEdit.hasFocus()) {
                    emailLayout.setBoxStrokeColor(colorFocused);
                } else {
                    emailLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
                }
                emailLayout.invalidate();
            }
        });
        passwordEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String value = s.toString();
                boolean valid = value.length() >= 6 && value.length() <= 32;
                passwordEdit.setTextColor(valid ? colorTextNormal : colorRed);
                if (passwordEdit.hasFocus()) {
                    passwordLayout.setBoxStrokeColor(colorFocused);
                } else {
                    passwordLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
                }
                passwordLayout.invalidate();
            }
        });
    }
}