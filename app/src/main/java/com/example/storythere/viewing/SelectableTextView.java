package com.example.storythere.viewing;

import android.content.Context;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.MovementMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.widget.AppCompatTextView;

public class SelectableTextView extends AppCompatTextView {
    private static final String TAG = "SelectableTextView";
    
    public interface TextSelectionListener {
        void onTextSelected(String selectedText, int start, int end);
        void onSelectionCleared();
    }
    
    private TextSelectionListener selectionListener;
    private ActionMode.Callback actionModeCallback;
    
    public SelectableTextView(Context context) {
        super(context);
        init();
    }
    
    public SelectableTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public SelectableTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        setTextIsSelectable(true);
        setMovementMethod(ArrowKeyMovementMethod.getInstance());
        
        // Create custom action mode callback with error suppression
        actionModeCallback = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                try {
                    // Clear any existing selection callback
                    if (selectionListener != null) {
                        selectionListener.onSelectionCleared();
                    }
                    return true;
                } catch (Exception e) {
                    // Suppress MIUIX FloatingActionMode errors
                    Log.d(TAG, "ActionMode onCreate suppressed: " + e.getMessage());
                    return true;
                }
            }
            
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                try {
                    return false;
                } catch (Exception e) {
                    // Suppress MIUIX FloatingActionMode errors
                    Log.d(TAG, "ActionMode onPrepare suppressed: " + e.getMessage());
                    return false;
                }
            }
            
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                try {
                    return false;
                } catch (Exception e) {
                    // Suppress MIUIX FloatingActionMode errors
                    Log.d(TAG, "ActionMode onActionItemClicked suppressed: " + e.getMessage());
                    return false;
                }
            }
            
            @Override
            public void onDestroyActionMode(ActionMode mode) {
                try {
                    // Action mode is destroyed when selection is cleared
                    if (selectionListener != null) {
                        selectionListener.onSelectionCleared();
                    }
                } catch (Exception e) {
                    // Suppress MIUIX FloatingActionMode errors
                    Log.d(TAG, "ActionMode onDestroy suppressed: " + e.getMessage());
                }
            }
        };
        
        // Set the custom action mode callback with error handling
        try {
            setCustomSelectionActionModeCallback(actionModeCallback);
        } catch (Exception e) {
            // Suppress MIUIX FloatingActionMode errors
            Log.d(TAG, "Failed to set custom selection action mode callback: " + e.getMessage());
        }
    }
    
    public void setTextSelectionListener(TextSelectionListener listener) {
        this.selectionListener = listener;
    }
    
    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        try {
            super.onSelectionChanged(selStart, selEnd);
            
            if (selectionListener != null) {
                if (selStart != selEnd) {
                    // Text is selected
                    String selectedText = getText().toString().substring(selStart, selEnd);
                    Log.d(TAG, "Text selected: '" + selectedText + "' (start: " + selStart + ", end: " + selEnd + ")");
                    selectionListener.onTextSelected(selectedText, selStart, selEnd);
                } else {
                    // Selection cleared
                    Log.d(TAG, "Selection cleared");
                    selectionListener.onSelectionCleared();
                }
            }
        } catch (Exception e) {
            // Suppress any selection-related errors
            Log.d(TAG, "Selection change suppressed: " + e.getMessage());
        }
    }
    
    @Override
    protected MovementMethod getDefaultMovementMethod() {
        return ArrowKeyMovementMethod.getInstance();
    }
    
    @Override
    public boolean isTextSelectable() {
        return true;
    }
    
    @Override
    public void setTextIsSelectable(boolean selectable) {
        try {
            super.setTextIsSelectable(true); // Always keep it selectable
        } catch (Exception e) {
            // Suppress any text selection setup errors
            Log.d(TAG, "setTextIsSelectable suppressed: " + e.getMessage());
        }
    }
    
    /**
     * Programmatically set the selection range if the text is Spannable.
     */
    public void setSelection(int start, int end) {
        Log.d(TAG, "[SELECT_TEXT] setSelection called - start: " + start + ", end: " + end + ", text length: " + getText().length());
        
        // Ensure the text is Spannable
        CharSequence currentText = getText();
        if (!(currentText instanceof android.text.Spannable)) {
            Log.d(TAG, "[SELECT_TEXT] Converting text to Spannable");
            android.text.SpannableString spannableText = new android.text.SpannableString(currentText);
            setText(spannableText);
            currentText = spannableText;
        }
        
        if (currentText instanceof android.text.Spannable) {
            android.text.Spannable spannable = (android.text.Spannable) currentText;
            Log.d(TAG, "[SELECT_TEXT] Text is Spannable, setting selection");
            
            // Ensure bounds are valid
            int textLength = spannable.length();
            start = Math.max(0, Math.min(start, textLength));
            end = Math.max(start, Math.min(end, textLength));
            
            Log.d(TAG, "[SELECT_TEXT] Adjusted bounds - start: " + start + ", end: " + end + ", textLength: " + textLength);
            
            try {
                android.text.Selection.setSelection(spannable, start, end);
                Log.d(TAG, "[SELECT_TEXT] Selection set successfully");
                
                // Force the selection to be visible
                requestFocus();
                
                // Trigger the selection change callback manually
                if (selectionListener != null) {
                    String selectedText = spannable.toString().substring(start, end);
                    Log.d(TAG, "[SELECT_TEXT] Manually triggering selection callback: '" + selectedText + "'");
                    selectionListener.onTextSelected(selectedText, start, end);
                }
            } catch (Exception e) {
                Log.e(TAG, "[SELECT_TEXT] Error setting selection: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "[SELECT_TEXT] Text is not Spannable, cannot set selection. Text type: " + currentText.getClass().getSimpleName());
        }
    }
} 