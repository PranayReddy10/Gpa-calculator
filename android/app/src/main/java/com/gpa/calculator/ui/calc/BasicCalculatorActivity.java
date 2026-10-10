package com.gpa.calculator.ui.calc;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.gridlayout.widget.GridLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.gpa.calculator.R;
import com.gpa.calculator.core.CalcException;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.core.Expr;
import com.gpa.calculator.core.Fmt;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.databinding.ActivityBasicCalcBinding;
import com.gpa.calculator.ui.common.Anim;
import com.gpa.calculator.ui.common.Haptics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standard keypad calculator with a live preview of the result. */
public class BasicCalculatorActivity extends CalcActivityBase {
    private ActivityBasicCalcBinding b;
    private final StringBuilder expr = new StringBuilder();
    private boolean justEvaluated;

    private static final String[][] KEYS = {
            {"C", "( )", "%", "÷"},
            {"7", "8", "9", "×"},
            {"4", "5", "6", "−"},
            {"1", "2", "3", "+"},
            {"0", ".", "⌫", "="}};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (calc == null) return;
        b = ActivityBasicCalcBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        b.toolbar.setTitle(calc.name());
        buildKeys();
        String prefill = getIntent().getStringExtra(CalcLauncher.EXTRA_INPUTS);
        if (prefill != null) {
            try {
                Map<String, String> m = new com.google.gson.Gson().fromJson(prefill, new com.google.gson.reflect.TypeToken<Map<String, String>>() {}.getType());
                if (m.get("expression") != null) expr.append(m.get("expression"));
            } catch (Exception ignored) {
            }
        }
        render();
        attachBanner(b.adContainer);
        // Compact native only when the display area has room for it next to the expression.
        b.display.post(() -> {
            float dp = b.display.getHeight() / getResources().getDisplayMetrics().density;
            if (dp >= 260) attachNative(b.nativeContainer, true);
        });
    }

    private void buildKeys() {
        int onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0);
        int primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0);
        int onPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, 0);
        int surfHigh = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0);
        int secCont = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, 0);
        int onSecCont = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer, 0);
        int row = 0;
        for (String[] line : KEYS) {
            int col = 0;
            for (String k : line) {
                MaterialButton btn = new MaterialButton(this);
                btn.setText(k);
                btn.setTextSize(k.equals("( )") ? 20 : 26);
                btn.setAllCaps(false);
                btn.setCornerRadius((int) (24 * getResources().getDisplayMetrics().density));
                btn.setInsetTop(0);
                btn.setInsetBottom(0);
                btn.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(this, R.animator.card_press));
                boolean op = "÷×−+".contains(k);
                boolean fn = k.equals("C") || k.equals("( )") || k.equals("%") || k.equals("⌫");
                if (k.equals("=")) { btn.setBackgroundColor(primary); btn.setTextColor(onPrimary); }
                else if (op) { btn.setBackgroundColor(secCont); btn.setTextColor(onSecCont); }
                else if (fn) { btn.setBackgroundColor(surfHigh); btn.setTextColor(primary); }
                else { btn.setBackgroundColor(surfHigh); btn.setTextColor(onSurface); }
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(row), GridLayout.spec(col, 1f));
                lp.width = 0;
                lp.height = (int) (68 * getResources().getDisplayMetrics().density);
                int m = (int) (5 * getResources().getDisplayMetrics().density);
                lp.setMargins(m, m, m, m);
                btn.setLayoutParams(lp);
                btn.setOnClickListener(v -> onKey(k, v));
                if (k.equals("⌫")) btn.setOnLongClickListener(v -> { clear(); Haptics.confirm(v); return true; });
                b.keys.addView(btn);
                col++;
            }
            row++;
        }
        Anim.staggerChildren(b.keys, 60);
    }

    private void onKey(String k, View v) {
        Haptics.tick(v);
        switch (k) {
            case "C": clear(); break;
            case "⌫":
                if (expr.length() > 0) expr.setLength(expr.length() - 1);
                justEvaluated = false;
                break;
            case "=": evaluate(); return;
            case "( )": {
                long open = expr.chars().filter(c -> c == '(').count(), close = expr.chars().filter(c -> c == ')').count();
                char last = expr.length() == 0 ? ' ' : expr.charAt(expr.length() - 1);
                boolean canClose = open > close && (Character.isDigit(last) || last == ')' || last == '%');
                expr.append(canClose ? ")" : (Character.isDigit(last) || last == ')' ? "×(" : "("));
                justEvaluated = false;
                break;
            }
            default: {
                if (justEvaluated && (Character.isDigit(k.charAt(0)) || k.equals("."))) expr.setLength(0);
                justEvaluated = false;
                char last = expr.length() == 0 ? ' ' : expr.charAt(expr.length() - 1);
                boolean isOp = "÷×−+".contains(k);
                if (isOp && "÷×−+".indexOf(last) >= 0) expr.setLength(expr.length() - 1); // replace operator
                else if (isOp && expr.length() == 0 && !k.equals("−")) return;
                if (k.equals(".")) {
                    int i = expr.length() - 1;
                    while (i >= 0 && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) { if (expr.charAt(i) == '.') return; i--; }
                    if (i == expr.length() - 1) expr.append('0');
                }
                expr.append(k);
            }
        }
        render();
    }

    private void clear() {
        expr.setLength(0);
        justEvaluated = false;
        render();
    }

    private void render() {
        b.expression.setText(expr.length() == 0 ? "0" : expr.toString());
        b.exprScroll.post(() -> b.exprScroll.fullScroll(View.FOCUS_RIGHT));
        if (justEvaluated) return;
        try {
            if (expr.length() > 0) b.preview.setText(Fmt.fmt(Expr.evalCalculatorStyle(expr.toString()), 8));
            else b.preview.setText("");
        } catch (Exception e) {
            b.preview.setText("");
        }
    }

    private void evaluate() {
        if (expr.length() == 0) return;
        try {
            double v = Expr.evalCalculatorStyle(expr.toString());
            String shown = Fmt.fmt(v, 10);
            List<CalcResult> res = new ArrayList<>();
            res.add(CalcResult.primary(expr.toString(), v, 10, "", ""));
            Map<String, String> inputs = new LinkedHashMap<>();
            inputs.put("expression", expr.toString());
            b.preview.setText("= " + shown);
            Anim.pop(b.preview);
            Haptics.confirm(b.keys);
            expr.setLength(0);
            expr.append(shown.replace(",", ""));
            justEvaluated = true;
            b.expression.setText(shown);
            Repository.get(this).saveHistory(calc, inputs, res, null);
            afterCalculation();
        } catch (CalcException e) {
            b.preview.setText(e.getMessage());
            Anim.shake(b.display);
            Haptics.reject(b.keys);
        }
    }

    @Override
    protected void onReset() { clear(); }
}
