package com.example.storythere.ui;

import android.app.Activity;
import com.example.storythere.R;

public final class ActivityTransitions {
    private ActivityTransitions() {}

    public static void applySlideRightOpen(Activity activity) {
        applyOpen(activity, R.anim.slide_in_right, R.anim.slide_out_left);
    }

    public static void applySlideLeftOpen(Activity activity) {
        applyOpen(activity, R.anim.slide_in_left, R.anim.slide_out_right);
    }

    public static void applySlideBottomOpen(Activity activity) {
        applyOpen(activity, R.anim.slide_in_bottom, R.anim.stay);
    }

    public static void applySlideTopClose(Activity activity) {
        applyClose(activity, R.anim.stay, R.anim.slide_out_top);
    }

    public static void applyFadeOpen(Activity activity) {
        applyOpen(activity, R.anim.fade_in, R.anim.fade_out);
    }

    public static void applyFadeClose(Activity activity) {
        applyClose(activity, R.anim.stay, R.anim.fade_out);
    }

    public static void applyTabOpen(Activity activity, int currentIndex, int targetIndex) {
        if (targetIndex > currentIndex) {
            applySlideRightOpen(activity);
        } else if (targetIndex < currentIndex) {
            applySlideLeftOpen(activity);
        }
    }

    private static void applyOpen(Activity activity, int enterAnim, int exitAnim) {
        apply(activity, enterAnim, exitAnim);
    }

    private static void applyClose(Activity activity, int enterAnim, int exitAnim) {
        apply(activity, enterAnim, exitAnim);
    }

    @SuppressWarnings("deprecation")
    private static void apply(Activity activity, int enterAnim, int exitAnim) {
        activity.overridePendingTransition(enterAnim, exitAnim);
    }
}
