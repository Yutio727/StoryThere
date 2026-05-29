package com.example.storythere.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.storythere.R;
import com.google.android.material.textfield.TextInputLayout;
import android.widget.EditText;
import android.util.Log;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import java.util.Calendar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import androidx.annotation.NonNull;
import android.widget.ProgressBar;
import android.os.Handler;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.content.SharedPreferences;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.content.res.ColorStateList;
import com.google.firebase.Timestamp;
import com.example.storythere.data.User;
import com.example.storythere.data.UserRepository;
import android.view.inputmethod.InputMethodManager;

public class Registration extends AppCompatActivity {
    private static final String USER_SYNC_PREFS = "UserSyncPrefs";
    private static final String KEY_DOB_PREFIX = "dob_";
    private static final String KEY_BUCKET_PREFIX = "bucket_";
    private static final String KEY_SEX_PREFIX = "sex_";
    private static final String SEX_MALE = "0.0";
    private static final String SEX_FEMALE = "1.0";

    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private UserRepository userRepository;
    private FrameLayout overlayView;
    private ConstraintLayout overlayContent;
    private ImageView overlayAppIcon;
    private ProgressBar overlayProgressBar;
    private ImageView overlayResultIcon;
    private TextView overlayResultText;
    private Handler handler = new Handler();
    private View registerButton;
    private boolean isRegistrationInProgress = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_registration);
        getWindow().setStatusBarColor(getResources().getColor(R.color.progress_blue));

        // Initialize UserRepository
        userRepository = new UserRepository();

        // Set ActionBar color to blue
        if (getSupportActionBar() != null) {
            getSupportActionBar().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(getResources().getColor(R.color.progress_blue)));
        }

        // Set up the "Already have account? - Login" text with bold and blue Login, and make it clickable
        TextView tvAlreadyHaveAccount = findViewById(R.id.tvAlreadyHaveAccount);
        String label = getString(R.string.already_have_account) + " - ";
        String login = getString(R.string.login_bold);
        SpannableString spannable = new SpannableString(label + login);
        int start = label.length();
        int end = start + login.length();
        spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.progress_blue)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(Registration.this, Login.class));
                finish();
                ActivityTransitions.applyFadeClose(Registration.this);
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvAlreadyHaveAccount.setText(spannable);
        tvAlreadyHaveAccount.setMovementMethod(LinkMovementMethod.getInstance());

        // --- Custom logic for textfield stroke color on focus ---
        final TextInputLayout usernameLayout = findViewById(R.id.etUsernameLayout);
        final TextInputLayout birthdayLayout = findViewById(R.id.etBirthdayLayout);
        final TextInputLayout genderLayout = findViewById(R.id.etGenderLayout);
        final TextInputLayout passwordLayout = findViewById(R.id.etPasswordLayout);
        final TextInputLayout confirmPasswordLayout = findViewById(R.id.etConfirmPasswordLayout);
        final EditText usernameEdit = findViewById(R.id.etUsername);
        final EditText birthdayEdit = findViewById(R.id.etBirthday);
        final AutoCompleteTextView genderEdit = findViewById(R.id.etGender);
        final EditText passwordEdit = findViewById(R.id.etPassword);
        final EditText confirmPasswordEdit = findViewById(R.id.etConfirmPassword);
        final int colorFocused = getResources().getColor(R.color.progress_blue);
        final int colorUnfocused = getResources().getColor(R.color.textfield_stroke);
        final int colorTextNormal = getResources().getColor(R.color.textfield_text);

        // Set all fields to red at start
        int colorRed = getResources().getColor(android.R.color.holo_red_dark);
        usernameLayout.setBoxStrokeColor(colorRed);
        birthdayLayout.setBoxStrokeColor(colorRed);
        genderLayout.setBoxStrokeColor(colorUnfocused);
        passwordLayout.setBoxStrokeColor(colorRed);
        confirmPasswordLayout.setBoxStrokeColor(colorRed);

        final String[] genderLabels = new String[]{
            getString(R.string.gender_not_specified),
            getString(R.string.gender_male),
            getString(R.string.gender_female)
        };
        final String[] genderValues = new String[]{null, SEX_MALE, SEX_FEMALE};
        final String[] selectedSex = new String[]{null};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            genderLabels
        );
        genderEdit.setAdapter(genderAdapter);
        genderEdit.setOnClickListener(v -> genderEdit.showDropDown());
        genderEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                genderLayout.setBoxStrokeColor(colorFocused);
                genderLayout.setEndIconTintList(ColorStateList.valueOf(colorFocused));
            } else {
                genderLayout.setBoxStrokeColor(colorUnfocused);
                genderLayout.setEndIconTintList(ColorStateList.valueOf(colorUnfocused));
            }
            genderLayout.invalidate();
        });
        genderEdit.setOnItemClickListener((parent, view, position, id) -> {
            selectedSex[0] = genderValues[position];
            genderEdit.setTextColor(colorTextNormal);
            genderLayout.setBoxStrokeColor(colorUnfocused);
            genderLayout.setEndIconTintList(ColorStateList.valueOf(colorUnfocused));
            genderLayout.invalidate();
        });
        genderLayout.setEndIconTintList(ColorStateList.valueOf(colorUnfocused));

        // Username real-time validation
        usernameEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String value = s.toString().trim();
                boolean valid = value.length() >= 3 && value.length() <= 20 && !value.contains("@") && !value.contains("<") && !value.contains(">") && !value.isEmpty();
                usernameEdit.setTextColor(valid ? colorTextNormal : colorRed);
                if (usernameEdit.hasFocus()) {
                    usernameLayout.setBoxStrokeColor(colorFocused);
                    usernameLayout.setEndIconTintList(ColorStateList.valueOf(colorFocused));
                } else {
                    usernameLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
                    usernameLayout.setEndIconTintList(ColorStateList.valueOf(valid ? colorUnfocused : colorRed));
                }
                usernameLayout.invalidate();
            }
        });
        usernameEdit.setOnFocusChangeListener((v, hasFocus) -> {
            String value = usernameEdit.getText() != null ? usernameEdit.getText().toString().trim() : "";
            boolean valid = value.length() >= 3 && value.length() <= 20 && !value.contains("@") && !value.contains("<") && !value.contains(">") && !value.isEmpty();
            if (hasFocus) {
                usernameLayout.setBoxStrokeColor(colorFocused);
                usernameLayout.setEndIconTintList(ColorStateList.valueOf(colorFocused));
            } else {
                usernameLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
                usernameLayout.setEndIconTintList(ColorStateList.valueOf(valid ? colorUnfocused : colorRed));
            }
            usernameLayout.invalidate();
        });

        // Birthday real-time validation
        birthdayEdit.addTextChangedListener(new TextWatcher() {
            private boolean isEditing = false;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (isEditing) return;
                isEditing = true;
                String clean = s.toString().replaceAll("[^\\d]", "");
                int sel = clean.length();
                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < clean.length() && i < 8; i++) {
                    formatted.append(clean.charAt(i));
                    if ((i == 1 || i == 3) && i != clean.length() - 1) {
                        formatted.append('.');
                        sel++;
                    }
                }
                String result = formatted.toString();
                if (!result.equals(s.toString())) {
                    birthdayEdit.setText(result);
                    birthdayEdit.setSelection(Math.min(sel, result.length()));
                }
                // Validate
                boolean valid = false;
                if (result.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                    String[] parts = result.split("\\.");
                    if (parts.length == 3) {
                        int day = Integer.parseInt(parts[0]);
                        int month = Integer.parseInt(parts[1]);
                        int year = Integer.parseInt(parts[2]);
                        valid = day >= 1 && day <= 31 && month >= 1 && month <= 12;
                        if (valid) {
                            Calendar entered = Calendar.getInstance();
                            entered.set(year, month - 1, day, 0, 0, 0);
                            entered.set(Calendar.MILLISECOND, 0);
                            Calendar now = Calendar.getInstance();
                            now.set(Calendar.HOUR_OF_DAY, 0);
                            now.set(Calendar.MINUTE, 0);
                            now.set(Calendar.SECOND, 0);
                            now.set(Calendar.MILLISECOND, 0);
                            if (entered.after(now)) valid = false;
                        }
                    }
                }
                birthdayEdit.setTextColor(valid ? colorTextNormal : colorRed);
                if (birthdayEdit.hasFocus()) {
                    birthdayLayout.setBoxStrokeColor(colorFocused);
                    birthdayLayout.setEndIconTintList(ColorStateList.valueOf(colorFocused));
                } else {
                    birthdayLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
                    birthdayLayout.setEndIconTintList(ColorStateList.valueOf(valid ? colorUnfocused : colorRed));
                }
                birthdayLayout.invalidate();
                isEditing = false;
            }
        });
        birthdayEdit.setOnFocusChangeListener((v, hasFocus) -> {
            String value = birthdayEdit.getText() != null ? birthdayEdit.getText().toString().trim() : "";
            boolean valid = false;
            if (value.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                String[] parts = value.split("\\.");
                if (parts.length == 3) {
                    int day = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]);
                    int year = Integer.parseInt(parts[2]);
                    valid = day >= 1 && day <= 31 && month >= 1 && month <= 12;
                    if (valid) {
                        Calendar entered = Calendar.getInstance();
                        entered.set(year, month - 1, day, 0, 0, 0);
                        entered.set(Calendar.MILLISECOND, 0);
                        Calendar now = Calendar.getInstance();
                        now.set(Calendar.HOUR_OF_DAY, 0);
                        now.set(Calendar.MINUTE, 0);
                        now.set(Calendar.SECOND, 0);
                        now.set(Calendar.MILLISECOND, 0);
                        if (entered.after(now)) valid = false;
                    }
                }
            }
            if (hasFocus) {
                birthdayLayout.setBoxStrokeColor(colorFocused);
                birthdayLayout.setEndIconTintList(ColorStateList.valueOf(colorFocused));
            } else {
                birthdayLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
                birthdayLayout.setEndIconTintList(ColorStateList.valueOf(valid ? colorUnfocused : colorRed));
            }
            birthdayLayout.invalidate();
        });

        final TextInputLayout emailLayout = findViewById(R.id.etEmailLayout);
        final EditText emailEdit = findViewById(R.id.etEmail);
        emailEdit.setOnFocusChangeListener((v, hasFocus) -> {
            String value = emailEdit.getText() != null ? emailEdit.getText().toString().trim() : "";
            boolean valid = value.length() >= 5 && value.length() <= 50 && value.contains("@") && android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches();
            
            if (hasFocus) {
                emailLayout.setBoxStrokeColor(colorFocused);
                emailLayout.setEndIconTintList(ColorStateList.valueOf(colorFocused));
            } else {
                emailLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
                emailLayout.setEndIconTintList(ColorStateList.valueOf(valid ? colorUnfocused : colorRed));
            }
            emailLayout.invalidate();
        });
        emailLayout.setBoxStrokeColor(emailEdit.hasFocus() ? colorFocused : colorUnfocused);
        emailLayout.setEndIconTintList(ColorStateList.valueOf(emailEdit.hasFocus() ? colorFocused : colorUnfocused));
        emailLayout.invalidate();

        // Email real-time validation
        emailEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String value = s.toString().trim();
                boolean valid = value.length() >= 5 && value.length() <= 50 && value.contains("@") && android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches();
                emailEdit.setTextColor(valid ? colorTextNormal : colorRed);
                if (emailEdit.hasFocus()) {
                    emailLayout.setBoxStrokeColor(colorFocused);
                    emailLayout.setEndIconTintList(ColorStateList.valueOf(colorFocused));
                } else {
                    emailLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
                    emailLayout.setEndIconTintList(ColorStateList.valueOf(valid ? colorUnfocused : colorRed));
                }
                emailLayout.invalidate();
            }
        });
        emailEdit.setOnFocusChangeListener((v, hasFocus) -> {
            String value = emailEdit.getText() != null ? emailEdit.getText().toString().trim() : "";
            boolean valid = value.length() >= 5 && value.length() <= 50 && value.contains("@") && android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches();
            if (hasFocus) {
                emailLayout.setBoxStrokeColor(colorFocused);
                emailLayout.setEndIconTintList(ColorStateList.valueOf(colorFocused));
            } else {
                emailLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
                emailLayout.setEndIconTintList(ColorStateList.valueOf(valid ? colorUnfocused : colorRed));
            }
            emailLayout.invalidate();
        });

        // Password real-time validation
        passwordEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
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

        // Confirm password real-time validation
        confirmPasswordEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String value = s.toString();
                String passwordValue = passwordEdit.getText() != null ? passwordEdit.getText().toString() : "";
                boolean valid = value.length() >= 6 && value.length() <= 32 && value.equals(passwordValue);
                confirmPasswordEdit.setTextColor(valid ? colorTextNormal : colorRed);
                if (confirmPasswordEdit.hasFocus()) {
                    confirmPasswordLayout.setBoxStrokeColor(colorFocused);
                } else {
                    confirmPasswordLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
                }
                confirmPasswordLayout.invalidate();
            }
        });
        confirmPasswordEdit.setOnFocusChangeListener((v, hasFocus) -> {
            String value = confirmPasswordEdit.getText() != null ? confirmPasswordEdit.getText().toString() : "";
            String passwordValue = passwordEdit.getText() != null ? passwordEdit.getText().toString() : "";
            boolean valid = value.length() >= 6 && value.length() <= 32 && value.equals(passwordValue);
            if (hasFocus) {
                confirmPasswordLayout.setBoxStrokeColor(colorFocused);
            } else {
                confirmPasswordLayout.setBoxStrokeColor(valid ? colorUnfocused : colorRed);
            }
            confirmPasswordLayout.invalidate();
        });

        overlayView = findViewById(R.id.overlayView);
        overlayContent = findViewById(R.id.overlayContent);
        overlayAppIcon = findViewById(R.id.overlayAppIcon);
        overlayProgressBar = findViewById(R.id.overlayProgressBar);
        overlayResultIcon = findViewById(R.id.overlayResultIcon);
        overlayResultText = findViewById(R.id.overlayResultText);

        registerButton = findViewById(R.id.btnRegister);
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRegistrationInProgress) {
                    return;
                }
                hideKeyboard();
                setRegistrationInProgress(true);

                String username = usernameEdit.getText() != null ? usernameEdit.getText().toString().trim() : "";
                String birthday = birthdayEdit.getText() != null ? birthdayEdit.getText().toString().trim() : "";
                String email = emailEdit.getText() != null ? emailEdit.getText().toString().trim() : "";
                String password = passwordEdit.getText() != null ? passwordEdit.getText().toString().trim() : "";
                String confirmPassword = confirmPasswordEdit.getText() != null ? confirmPasswordEdit.getText().toString().trim() : "";
                boolean valid = true;
                int colorRed = getResources().getColor(android.R.color.holo_red_dark);
                if (username.isEmpty() || username.contains("@") || username.contains("<") || username.contains(">")) {
                    usernameLayout.setBoxStrokeColor(colorRed);
                    usernameEdit.setTextColor(colorRed);
                    valid = false;
                }
                if (birthday.isEmpty() || !birthday.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                    birthdayLayout.setBoxStrokeColor(colorRed);
                    birthdayEdit.setTextColor(colorRed);
                    valid = false;
                } else {
                    // Additional birthday validation: dd <= 31, mm <= 12, date <= today
                    String[] parts = birthday.split("\\.");
                    if (parts.length == 3) {
                        int day = Integer.parseInt(parts[0]);
                        int month = Integer.parseInt(parts[1]);
                        int year = Integer.parseInt(parts[2]);
                        boolean dateValid = true;
                        if (day < 1 || day > 31) dateValid = false;
                        if (month < 1 || month > 12) dateValid = false;
                        Calendar entered = Calendar.getInstance();
                        entered.set(year, month - 1, day, 0, 0, 0);
                        entered.set(Calendar.MILLISECOND, 0);
                        Calendar now = Calendar.getInstance();
                        now.set(Calendar.HOUR_OF_DAY, 0);
                        now.set(Calendar.MINUTE, 0);
                        now.set(Calendar.SECOND, 0);
                        now.set(Calendar.MILLISECOND, 0);
                        if (entered.after(now)) dateValid = false;
                        if (!dateValid) {
                            birthdayLayout.setBoxStrokeColor(colorRed);
                            birthdayEdit.setTextColor(colorRed);
                            valid = false;
                        }
                    } else {
                        birthdayLayout.setBoxStrokeColor(colorRed);
                        birthdayEdit.setTextColor(colorRed);
                        valid = false;
                    }
                }
                if (email.isEmpty() || !email.contains("@") || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailLayout.setBoxStrokeColor(colorRed);
                    emailEdit.setTextColor(colorRed);
                    valid = false;
                }
                if (password.isEmpty()) {
                    passwordLayout.setBoxStrokeColor(colorRed);
                    passwordEdit.setTextColor(colorRed);
                    valid = false;
                }
                if (confirmPassword.isEmpty()) {
                    confirmPasswordLayout.setBoxStrokeColor(colorRed);
                    confirmPasswordEdit.setTextColor(colorRed);
                    valid = false;
                }
                if (!valid) {
                    Toast.makeText(Registration.this, getString(R.string.please_fill_all_fields_correctly), Toast.LENGTH_SHORT).show();
                    Log.d("Registration", "Unfilled/invalid fields: username=" + username + ", birthday=" + birthday + ", email=" + email + ", password=" + password + ", confirmPassword=" + confirmPassword);
                    setRegistrationInProgress(false);
                    return;
                }
                if (!password.equals(confirmPassword)) {
                    passwordLayout.setBoxStrokeColor(colorRed);
                    confirmPasswordLayout.setBoxStrokeColor(colorRed);
                    passwordEdit.setTextColor(colorRed);
                    confirmPasswordEdit.setTextColor(colorRed);
                    Toast.makeText(Registration.this, getString(R.string.passwords_do_not_match), Toast.LENGTH_SHORT).show();
                    Log.d("Registration", "Passwords do not match");
                    setRegistrationInProgress(false);
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
                // Firebase registration logic only
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(Registration.this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                overlayProgressBar.setVisibility(View.GONE);
                                if (task.isSuccessful()) {
                                    // Save user profile data
                                    FirebaseUser user = mAuth.getCurrentUser();
                                    if (user != null) {
                                        // Create user profile data
                                        User userProfile = new User(
                                            user.getUid(),
                                            email,
                                            username, // displayName from registration
                                            "", // photoURL - empty for now, will be implemented later
                                            birthday, // dateOfBirth from registration
                                            "standard", // default role
                                            Timestamp.now(), // createdAt
                                            Timestamp.now() // lastLoginAt
                                        );
                                        String recommendationAgeBucket = calculateAgeBucketFromBirthday(birthday);
                                        userProfile.setRecommendationAgeBucket(recommendationAgeBucket);
                                        userProfile.setSex(selectedSex[0]);
                                        cacheSyncProfile(user.getUid(), birthday, recommendationAgeBucket, selectedSex[0]);

                                        // Sync user profile in backend (MySQL via API)
                                        userRepository.createUser(userProfile, new UserRepository.UserCallback() {
                                            @Override
                                            public void onSuccess(com.example.storythere.api.model.ApiUser apiUser) {
                                                Log.d("Registration", "User profile synced with backend successfully");
                                                updateFirebaseAuthProfile(user, username);
                                            }

                                            @Override
                                            public void onError(Throwable throwable) {
                                                Log.w("Registration", "Failed to sync user profile with backend", throwable);
                                                updateFirebaseAuthProfile(user, username);
                                            }
                                        });
                                    } else {
                                        // Fallback if user is null
                                        showSuccessAndNavigate();
                                    }
                                } else {
                                    showErrorAndStay(task.getException());
                                }
                            }
                        });
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            reload();
        }
    }

    private void updateUI(FirebaseUser user) {
        // TODO: Update your UI after registration (e.g., go to main screen)
    }

    private void reload() {
        // TODO: Optionally reload user or refresh UI
    }

    private void updateFirebaseAuthProfile(FirebaseUser user, String username) {
        com.google.firebase.auth.UserProfileChangeRequest profileUpdates =
            new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build();

        user.updateProfile(profileUpdates)
            .addOnCompleteListener(authProfileTask -> {
                if (authProfileTask.isSuccessful()) {
                    Log.d("Registration", "Firebase Auth profile updated successfully");
                } else {
                    Log.w("Registration", "Failed to update Firebase Auth profile", authProfileTask.getException());
                }
                showSuccessAndNavigate();
            });
    }

    private void showSuccessAndNavigate() {
        overlayAppIcon.setVisibility(View.GONE);
        overlayResultIcon.setImageResource(R.drawable.ic_check_circle);
        overlayResultIcon.setVisibility(View.VISIBLE);
        overlayResultText.setText(getString(R.string.registration_success));
        overlayResultText.setTextColor(getResources().getColor(R.color.progress_blue));
        overlayResultText.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> {
            Intent intent = new Intent(Registration.this, OnboardingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            ActivityTransitions.applyFadeOpen(Registration.this);
        }, 700);
    }

    private void showErrorAndStay(Exception exception) {
        overlayAppIcon.setVisibility(View.GONE);
        overlayResultIcon.setImageResource(R.drawable.ic_cross_circle);
        overlayResultIcon.setVisibility(View.VISIBLE);
        String errorMsg = getString(R.string.registration_failed);
        if (exception != null) {
            errorMsg += "\n" + exception.getMessage();
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
                    setRegistrationInProgress(false);
                }
                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            overlayView.startAnimation(fadeOut);
        }, 3000);
    }

    private void setRegistrationInProgress(boolean inProgress) {
        isRegistrationInProgress = inProgress;
        if (registerButton != null) {
            registerButton.setEnabled(!inProgress);
        }
    }

    private void hideKeyboard() {
        View currentFocus = getCurrentFocus();
        if (currentFocus == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
        currentFocus.clearFocus();
    }

    private String calculateAgeBucketFromBirthday(String birthday) {
        if (birthday == null || !birthday.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
            return null;
        }

        String[] parts = birthday.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        int day = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int year = Integer.parseInt(parts[2]);

        Calendar birth = Calendar.getInstance();
        birth.setLenient(false);
        try {
            birth.set(year, month - 1, day, 0, 0, 0);
            birth.set(Calendar.MILLISECOND, 0);
            birth.getTime(); // triggers strict validation
        } catch (Exception e) {
            return null;
        }

        Calendar now = Calendar.getInstance();
        int age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
        if (now.get(Calendar.MONTH) < birth.get(Calendar.MONTH) ||
            (now.get(Calendar.MONTH) == birth.get(Calendar.MONTH) &&
                now.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH))) {
            age--;
        }

        if (age < 0) {
            return null;
        }
        if (age < 18) return "under_18";
        if (age <= 24) return "18_24";
        if (age <= 34) return "25_34";
        if (age <= 44) return "35_44";
        if (age <= 54) return "45_54";
        return "55_plus";
    }

    private void cacheSyncProfile(String uid, String birthday, String recommendationAgeBucket, String sex) {
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(USER_SYNC_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (birthday != null && !birthday.trim().isEmpty()) {
            editor.putString(KEY_DOB_PREFIX + uid, birthday);
        }
        if (recommendationAgeBucket != null && !recommendationAgeBucket.trim().isEmpty()) {
            editor.putString(KEY_BUCKET_PREFIX + uid, recommendationAgeBucket);
        }
        if (sex != null && !sex.trim().isEmpty()) {
            editor.putString(KEY_SEX_PREFIX + uid, sex);
        }
        editor.apply();
    }

    @Override
    public void onBackPressed() {
        finish();
        ActivityTransitions.applyFadeClose(this);
    }
}
