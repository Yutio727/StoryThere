package com.example.storythere.ui;

import android.app.Activity;
import android.view.Window;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.storythere.R;

public final class SystemBarUtils {
    private SystemBarUtils() {
    }

    public static void applyStatusBar(Activity activity) {
        if (activity == null) {
            return;
        }

        Window window = activity.getWindow();
        int statusBarColor = ContextCompat.getColor(activity, R.color.status_bar_background);
        WindowCompat.setDecorFitsSystemWindows(window, true);
        window.setStatusBarColor(statusBarColor);

        WindowInsetsControllerCompat controller =
            WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false);
    }
}
