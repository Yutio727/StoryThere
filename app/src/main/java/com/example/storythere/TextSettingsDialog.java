package com.example.storythere;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class TextSettingsDialog extends DialogFragment {
    private static final String TAG = "TextSettingsDialog";
    private TextSettingsListener listener;
    private PDFParser.TextSettings currentSettings;
    private PDFParser.TextSettings defaultSettings;
    private SeekBar fontSizeSeekBar;
    private SeekBar letterSpacingSeekBar;
    private SeekBar lineHeightSeekBar;
    private SeekBar paragraphSpacingSeekBar;
    private RadioGroup alignmentGroup;
    private TextView fontSizeValue;
    private TextView letterSpacingValue;
    private TextView lineHeightValue;
    private TextView paragraphSpacingValue;

    public interface TextSettingsListener {
        void onSettingsChanged(PDFParser.TextSettings settings);
    }

    public static TextSettingsDialog newInstance(PDFParser.TextSettings settings) {
        TextSettingsDialog dialog = new TextSettingsDialog();
        dialog.currentSettings = settings;
        dialog.defaultSettings = new PDFParser.TextSettings(); // Create default settings
        return dialog;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (TextSettingsListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement TextSettingsListener");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.Widget_StoryThere_Dialog);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_text_settings, null);

        // Log theme information
        int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isNightMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        Log.d(TAG, "Current theme mode: " + (isNightMode ? "Night" : "Light"));
        
        // Get and log the title color
        int titleColor = getResources().getColor(isNightMode ? R.color.dialog_title_dark : R.color.dialog_title_light, null);
        Log.d(TAG, "Dialog title color: " + String.format("#%06X", (0xFFFFFF & titleColor)));

        // Initialize views
        fontSizeSeekBar = view.findViewById(R.id.fontSizeSeekBar);
        letterSpacingSeekBar = view.findViewById(R.id.letterSpacingSeekBar);
        lineHeightSeekBar = view.findViewById(R.id.lineHeightSeekBar);
        paragraphSpacingSeekBar = view.findViewById(R.id.paragraphSpacingSeekBar);
        alignmentGroup = view.findViewById(R.id.alignmentGroup);
        fontSizeValue = view.findViewById(R.id.fontSizeValue);
        letterSpacingValue = view.findViewById(R.id.letterSpacingValue);
        lineHeightValue = view.findViewById(R.id.lineHeightValue);
        paragraphSpacingValue = view.findViewById(R.id.paragraphSpacingValue);

        // Set up font size seekbar (32-64)
        fontSizeSeekBar.setMax(64);
        fontSizeSeekBar.setProgress((int) ((currentSettings.fontSize - 32) * 2));
        updateValueDisplay(fontSizeValue, currentSettings.fontSize);
        fontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSettings.fontSize = 32 + (progress / 2f);
                updateValueDisplay(fontSizeValue, currentSettings.fontSize);
                Log.d(TAG, "Font size changed to: " + currentSettings.fontSize);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Set up letter spacing seekbar
        letterSpacingSeekBar.setMax(20);
        letterSpacingSeekBar.setProgress((int) (currentSettings.letterSpacing * 10));
        updateValueDisplay(letterSpacingValue, currentSettings.letterSpacing);
        letterSpacingSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSettings.letterSpacing = progress / 10f;
                updateValueDisplay(letterSpacingValue, currentSettings.letterSpacing);
                Log.d(TAG, "Letter spacing changed to: " + currentSettings.letterSpacing);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Set up line height seekbar
        lineHeightSeekBar.setMax(20);
        lineHeightSeekBar.setProgress((int) ((currentSettings.lineHeight - 1) * 20));
        updateValueDisplay(lineHeightValue, currentSettings.lineHeight);
        lineHeightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSettings.lineHeight = 1 + (progress / 20f);
                updateValueDisplay(lineHeightValue, currentSettings.lineHeight);
                Log.d(TAG, "Line height changed to: " + currentSettings.lineHeight);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Set up paragraph spacing seekbar
        paragraphSpacingSeekBar.setMax(20);
        paragraphSpacingSeekBar.setProgress((int) ((currentSettings.paragraphSpacing - 1) * 20));
        updateValueDisplay(paragraphSpacingValue, currentSettings.paragraphSpacing);
        paragraphSpacingSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSettings.paragraphSpacing = 1 + (progress / 20f);
                updateValueDisplay(paragraphSpacingValue, currentSettings.paragraphSpacing);
                Log.d(TAG, "Paragraph spacing changed to: " + currentSettings.paragraphSpacing);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Set up alignment radio group
        int alignmentId;
        switch (currentSettings.textAlignment) {
            case CENTER:
                alignmentId = R.id.alignCenter;
                break;
            case RIGHT:
                alignmentId = R.id.alignRight;
                break;
            default:
                alignmentId = R.id.alignLeft;
                break;
        }
        alignmentGroup.check(alignmentId);
        alignmentGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.alignLeft) {
                currentSettings.textAlignment = Paint.Align.LEFT;
            } else if (checkedId == R.id.alignCenter) {
                currentSettings.textAlignment = Paint.Align.CENTER;
            } else if (checkedId == R.id.alignRight) {
                currentSettings.textAlignment = Paint.Align.RIGHT;
            }
            Log.d(TAG, "Text alignment changed to: " + currentSettings.textAlignment);
        });

        builder.setView(view)
               .setTitle(R.string.text_settings)
               .setPositiveButton(R.string.apply, (dialog, which) -> {
                   Log.d(TAG, "Applying settings - fontSize: " + currentSettings.fontSize + 
                             ", letterSpacing: " + currentSettings.letterSpacing + 
                             ", alignment: " + currentSettings.textAlignment);
                   listener.onSettingsChanged(currentSettings);
               })
               .setNeutralButton(R.string.default_settings, (dialog, which) -> {
                   resetToDefaults();
                   listener.onSettingsChanged(currentSettings);
               })
               .setNegativeButton(R.string.cancel, null);

        AlertDialog dialog = builder.create();
        
        // Make dialog full width and set background dim
        dialog.setOnShowListener(dialogInterface -> {
            int displayWidth = getResources().getDisplayMetrics().widthPixels;
            int dialogWidth = (int) (displayWidth * 0.95);
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            
            // Set background dim
            dialog.getWindow().setDimAmount(0.9f);
            
            // Enable elevation and shadow
            dialog.getWindow().setElevation(16f);

            // Log the actual title color being used
            TextView titleView = dialog.findViewById(android.R.id.title);
            if (titleView != null) {
                int actualTitleColor = titleView.getCurrentTextColor();
                Log.d(TAG, "Actual title color being used: " + String.format("#%06X", (0xFFFFFF & actualTitleColor)));
            }
        });

        return dialog;
    }

    private void updateValueDisplay(TextView textView, float value) {
        textView.setText(String.format("%.1f", value));
    }

    private void resetToDefaults() {
        // Reset current settings to defaults
        currentSettings.fontSize = 54.0f;
        currentSettings.letterSpacing = 0.0f;
        currentSettings.lineHeight = 1.2f;
        currentSettings.paragraphSpacing = 1.5f;
        currentSettings.textAlignment = Paint.Align.LEFT;

        // Update UI
        fontSizeSeekBar.setProgress((int) ((currentSettings.fontSize - 32) * 2));
        letterSpacingSeekBar.setProgress((int) (currentSettings.letterSpacing * 10));
        lineHeightSeekBar.setProgress((int) ((currentSettings.lineHeight - 1) * 20));
        paragraphSpacingSeekBar.setProgress((int) ((currentSettings.paragraphSpacing - 1) * 20));

        // Update value displays
        updateValueDisplay(fontSizeValue, currentSettings.fontSize);
        updateValueDisplay(letterSpacingValue, currentSettings.letterSpacing);
        updateValueDisplay(lineHeightValue, currentSettings.lineHeight);
        updateValueDisplay(paragraphSpacingValue, currentSettings.paragraphSpacing);

        // Update alignment
        alignmentGroup.check(R.id.alignLeft);

        Log.d(TAG, "Reset to defaults - fontSize: " + currentSettings.fontSize + 
                  ", letterSpacing: " + currentSettings.letterSpacing + 
                  ", alignment: " + currentSettings.textAlignment);
    }
} 