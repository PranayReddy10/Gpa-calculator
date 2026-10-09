package com.gpa.calculator.ui.common;

import android.view.HapticFeedbackConstants;
import android.view.View;

import com.gpa.calculator.data.Prefs;

public final class Haptics {
    private Haptics() {}

    public static void tick(View v) {
        if (!Prefs.get(v.getContext()).hapticFeedback()) return;
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    public static void confirm(View v) {
        if (!Prefs.get(v.getContext()).hapticFeedback()) return;
        v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }

    public static void reject(View v) {
        if (!Prefs.get(v.getContext()).hapticFeedback()) return;
        v.performHapticFeedback(HapticFeedbackConstants.REJECT);
    }
}
