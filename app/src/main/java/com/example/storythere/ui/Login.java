package com.example.storythere.ui;

import android.net.ConnectivityManager;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.storythere.NetworkUtils;
import com.example.storythere.R;
import com.google.android.material.textfield.TextInputLayout;
import android.widget.EditText;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.util.Log;
import android.os.Handler;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.google.firebase.auth.FirebaseAuth;
import android.widget.ProgressBar;
import com.google.firebase.auth.FirebaseUser;
import com.example.storythere.data.UserRepository;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import androidx.annotation.NonNull;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;

public class Login extends AppCompatActivity {

    private FrameLayout overlayView;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConstraintLayout overlayContent;
    private ImageView overlayAppIcon;
    private ProgressBar overlayProgressBar;
    private ImageView overlayResultIcon;
    private TextView overlayResultText;
    private Handler handler = new Handler();
    private UserRepository userRepository;
    private AlertDialog offlineModeDialog;
    private boolean isLoginInProgress = false;
    private boolean isAnimationInProgress = false;
    private int retryAttempts = 0;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 4000; // 4 seconds
    private boolean isActivityDestroyed = false; // Add flag to track activity state

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        // Removed insets padding code
        // Set status bar color to blue
        getWindow().setStatusBarColor(getResources().getColor(R.color.progress_blue));

        // Initialize UserRepository
        userRepository = new UserRepository();

        // Set ActionBar color to blue
        if (getSupportActionBar() != null) {
            getSupportActionBar().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(getResources().getColor(R.color.progress_blue)));
        }

        // Check internet connectivity immediately on startup
        if (!NetworkUtils.isInternetAvailable(Login.this)) {
            showOfflineModeDialogOnStartup();
            return;
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
                // Prevent multiple clicks during animation or login process
                if (isLoginInProgress || isAnimationInProgress || isActivityDestroyed) {
                    Log.d("Login", "Login attempt blocked - already in progress or activity destroyed");
                    return;
                }
                
                // Disable the button immediately to prevent multiple clicks
                v.setEnabled(false);
                
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
                    v.setEnabled(true); // Re-enable button
                    return;
                }
                
                // Check internet connectivity before attempting login
                if (!NetworkUtils.isInternetAvailable(Login.this)) {
                    showOfflineModeDialog(email, password);
                    v.setEnabled(true); // Re-enable button after showing dialog
                    return;
                }
                
                performLogin(email, password);
                // Button will be re-enabled in performLogin completion or error handling
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

    private void showSuccessAndNavigate() {
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
    }

    private void showErrorAndStay(Exception exception) {
        overlayAppIcon.setVisibility(View.GONE);
        overlayResultIcon.setImageResource(R.drawable.ic_cross_circle);
        overlayResultIcon.setVisibility(View.VISIBLE);
        String errorMsg = getString(R.string.login_failed);
        if (exception != null) {
            errorMsg += ":\n" + exception.getMessage();
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
                public void onAnimationStart(Animation animation) {
                    isAnimationInProgress = true;
                }
                @Override
                public void onAnimationEnd(Animation animation) {
                    overlayView.setVisibility(View.GONE);
                    isAnimationInProgress = false;
                    isLoginInProgress = false;
                }
                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            overlayView.startAnimation(fadeOut);
        }, 3000);
    }

    private void showOfflineModeDialog(String email, String password) {
        // Check if activity is destroyed or being destroyed
        if (isActivityDestroyed || isFinishing() || isDestroyed()) {
            Log.d("Login", "Activity is destroyed or finishing, skipping dialog");
            return;
        }
        
        if (offlineModeDialog != null && offlineModeDialog.isShowing()) {
            Log.d("Login", "Offline dialog already showing, ignoring new request");
            return;
        }
        
        // Prevent multiple login attempts
        isLoginInProgress = true;
        
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Widget_StoryThere_Dialog);
            LayoutInflater inflater = getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_offline_mode, null);
            builder.setView(dialogView);
            
            offlineModeDialog = builder.create();
            offlineModeDialog.setCancelable(false);
            offlineModeDialog.setCanceledOnTouchOutside(false);
            
            MaterialButton btnTryAgain = dialogView.findViewById(R.id.btnTryAgain);
            MaterialButton btnOfflineMode = dialogView.findViewById(R.id.btnOfflineMode);
            
            btnTryAgain.setOnClickListener(v -> {
                if (offlineModeDialog != null) {
                    offlineModeDialog.dismiss();
                    offlineModeDialog = null;
                }
                retryAttempts++;
                if (retryAttempts <= MAX_RETRY_ATTEMPTS) {
                    handler.postDelayed(() -> {
                        // Check activity state before proceeding
                        if (isActivityDestroyed || isFinishing() || isDestroyed()) {
                            Log.d("Login", "Activity destroyed during retry, aborting");
                            return;
                        }
                        
                        if (NetworkUtils.isInternetAvailable(Login.this)) {
                            isLoginInProgress = false; // Reset flag before attempting login
                            performLogin(email, password);
                        } else {
                            isLoginInProgress = false; // Reset flag before showing dialog again
                            showOfflineModeDialog(email, password);
                        }
                    }, RETRY_DELAY_MS);
                } else {
                    // Max retries reached, show error and allow offline mode
                    isLoginInProgress = false; // Reset flag
                    Toast.makeText(Login.this, getString(R.string.network_error_message), Toast.LENGTH_LONG).show();
                    continueInOfflineMode();
                }
            });
            
            btnOfflineMode.setOnClickListener(v -> {
                if (offlineModeDialog != null) {
                    offlineModeDialog.dismiss();
                    offlineModeDialog = null;
                }
                isLoginInProgress = false; // Reset flag
                continueInOfflineMode();
            });
            
            // Add dialog dismiss listener to reset state
            offlineModeDialog.setOnDismissListener(dialog -> {
                isLoginInProgress = false;
                offlineModeDialog = null;
            });
            
            offlineModeDialog.show();
        } catch (Exception e) {
            Log.e("Login", "Error showing offline dialog: " + e.getMessage());
            isLoginInProgress = false;
            // If dialog creation fails, just continue in offline mode
            continueInOfflineMode();
        }
    }

    private void performLogin(String email, String password) {
        isLoginInProgress = true;
        retryAttempts = 0; // Reset retry attempts on successful network check
        
        // Show overlay with fade-in animation
        showLoginAnimation();
        
        // Firebase login logic
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(Login.this, new com.google.android.gms.tasks.OnCompleteListener<com.google.firebase.auth.AuthResult>() {
                    @Override
                    public void onComplete(@androidx.annotation.NonNull com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult> task) {
                        overlayProgressBar.setVisibility(View.GONE);
                        isLoginInProgress = false;
                        
                        // Re-enable the login button
                        findViewById(R.id.btnLogin).setEnabled(true);
                        
                        if (task.isSuccessful()) {
                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                            if (user != null) {
                                // Update lastLoginAt in Firestore
                                userRepository.updateLastLogin(user.getUid(), new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> updateTask) {
                                        if (updateTask.isSuccessful()) {
                                            Log.d("Login", "Last login timestamp updated successfully");
                                        } else {
                                            Log.w("Login", "Failed to update last login timestamp", updateTask.getException());
                                        }
                                        
                                        // Continue with success flow
                                        user.getIdToken(false).addOnCompleteListener(tokenTask -> {
                                            if (tokenTask.isSuccessful() && tokenTask.getResult() != null) {
                                                String token = tokenTask.getResult().getToken();
                                                Log.d("Login", "Login successful. Token: " + token);
                                            } else {
                                                Log.w("Login", "Login successful, but failed to get token.");
                                            }
                                        });
                                        
                                        showSuccessAndNavigate();
                                    }
                                });
                            } else {
                                showSuccessAndNavigate();
                            }
                        } else {
                            // Check if it's a network-related error
                            Exception exception = task.getException();
                            if (exception != null && (exception.getMessage().contains("network") || 
                                exception.getMessage().contains("Network") ||
                                exception.getMessage().contains("timeout") ||
                                exception.getMessage().contains("connection"))) {
                                showOfflineModeDialog(email, password);
                            } else {
                                showErrorAndStay(exception);
                            }
                        }
                    }
                });
    }

    private void showLoginAnimation() {
        isAnimationInProgress = true;
        overlayAppIcon.setVisibility(View.VISIBLE);
        overlayProgressBar.setVisibility(View.VISIBLE);
        overlayResultIcon.setVisibility(View.GONE);
        overlayResultText.setVisibility(View.GONE);
        overlayView.setVisibility(View.VISIBLE);
        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setDuration(300);
        fadeIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                isAnimationInProgress = false;
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        overlayView.startAnimation(fadeIn);
    }
    
    private void continueInOfflineMode() {
        try {
            // Set activity as destroyed to prevent any further operations
            isActivityDestroyed = true;
            
            // Reset all flags to prevent any issues
            isLoginInProgress = false;
            isAnimationInProgress = false;
            retryAttempts = 0;
            
            // Clean up any pending operations
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }
            
            // Dismiss any existing dialogs
            if (offlineModeDialog != null && offlineModeDialog.isShowing()) {
                try {
                    offlineModeDialog.dismiss();
                } catch (Exception e) {
                    Log.w("Login", "Error dismissing dialog: " + e.getMessage());
                }
                offlineModeDialog = null;
            }
            
            // Hide any overlay if visible
            if (overlayView != null && overlayView.getVisibility() == View.VISIBLE) {
                overlayView.setVisibility(View.GONE);
            }
            
            Log.d("Login", "Navigating to MainActivity in offline mode");
            
            // Navigate to MainActivity (My Books) in offline mode
            Intent intent = new Intent(Login.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("offline_mode", true); // Add flag to indicate offline mode
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e("Login", "Error navigating to offline mode: " + e.getMessage());
            // Fallback: try to restart the app
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("offline_mode", true);
                    startActivity(intent);
                    finish();
                }
            } catch (Exception fallbackException) {
                Log.e("Login", "Fallback navigation also failed: " + fallbackException.getMessage());
                Toast.makeText(this, "Error starting offline mode. Please restart the app.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showOfflineModeDialogOnStartup() {
        // Check if activity is destroyed or being destroyed
        if (isActivityDestroyed || isFinishing() || isDestroyed()) {
            Log.d("Login", "Activity is destroyed or finishing, skipping startup dialog");
            return;
        }
        
        if (offlineModeDialog != null && offlineModeDialog.isShowing()) {
            return;
        }
        
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Widget_StoryThere_Dialog);
            LayoutInflater inflater = getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_offline_mode, null);
            builder.setView(dialogView);
            
            offlineModeDialog = builder.create();
            offlineModeDialog.setCancelable(false);
            offlineModeDialog.setCanceledOnTouchOutside(false);
            
            MaterialButton btnTryAgain = dialogView.findViewById(R.id.btnTryAgain);
            MaterialButton btnOfflineMode = dialogView.findViewById(R.id.btnOfflineMode);
            
            btnTryAgain.setOnClickListener(v -> {
                if (offlineModeDialog != null) {
                    offlineModeDialog.dismiss();
                    offlineModeDialog = null;
                }
                // Check if internet is now available
                if (NetworkUtils.isInternetAvailable(Login.this)) {
                    // Recreate the activity to show normal login flow
                    recreate();
                } else {
                    // Still no internet, show dialog again
                    showOfflineModeDialogOnStartup();
                }
            });
            
            btnOfflineMode.setOnClickListener(v -> {
                if (offlineModeDialog != null) {
                    offlineModeDialog.dismiss();
                    offlineModeDialog = null;
                }
                continueInOfflineMode();
            });
            
            offlineModeDialog.show();
        } catch (Exception e) {
            Log.e("Login", "Error showing startup offline dialog: " + e.getMessage());
            // If dialog creation fails, just continue in offline mode
            continueInOfflineMode();
        }
    }

    @Override
    public void onBackPressed() {
        // If offline dialog is showing, handle back press properly
        if (offlineModeDialog != null && offlineModeDialog.isShowing()) {
            // Don't allow back press to close the dialog, force user to make a choice
            return;
        }
        
        // If login is in progress, don't allow back press
        if (isLoginInProgress || isAnimationInProgress) {
            return;
        }
        
        super.onBackPressed();
    }
}