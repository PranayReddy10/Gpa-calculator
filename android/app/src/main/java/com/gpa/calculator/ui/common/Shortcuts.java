package com.gpa.calculator.ui.common;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.gpa.calculator.R;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.push.PushManager;
import com.gpa.calculator.ui.MainActivity;

import java.util.Arrays;
import java.util.List;

/** Launcher shortcuts: recently used calculators (dynamic) and "Add to home screen" (pinned). */
public final class Shortcuts {
    private Shortcuts() {}

    /** Calculators already covered by the static shortcuts in res/xml/shortcuts.xml. */
    private static final List<String> STATIC = Arrays.asList("sgpa_grade", "cgpa", "emi", "basic");

    private static Intent intent(Context c, Calculator calc) {
        return new Intent(c, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(PushManager.EXTRA_OPEN_CALC, calc.id());
    }

    private static ShortcutInfoCompat info(Context c, Calculator calc) {
        String shortLabel = calc.name().length() > 24 ? calc.name().substring(0, 24) : calc.name();
        return new ShortcutInfoCompat.Builder(c, "calc_" + calc.id())
                .setShortLabel(shortLabel)
                .setLongLabel(calc.name())
                .setIcon(IconCompat.createWithResource(c, R.drawable.ic_sc_generic))
                .setIntent(intent(c, calc))
                .build();
    }

    /** Called after each calculation; the launcher keeps the most recent few. */
    public static void reportUsed(Context c, Calculator calc) {
        if (STATIC.contains(calc.id())) return;
        try {
            ShortcutManagerCompat.pushDynamicShortcut(c, info(c, calc));
        } catch (Exception ignored) {
            // rate-limited or unsupported launcher: shortcuts are a nicety, never crash for them
        }
    }

    /** @return false if the launcher can't pin shortcuts. */
    public static boolean pin(Context c, Calculator calc) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(c)) return false;
        return ShortcutManagerCompat.requestPinShortcut(c, info(c, calc), null);
    }
}
