package com.gpa.calculator.ui.calc;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.gpa.calculator.R;
import com.gpa.calculator.core.CalcException;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.core.Currencies;
import com.gpa.calculator.core.Fmt;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.data.remote.RatesRepo;
import com.gpa.calculator.databinding.ActivityCurrencyBinding;
import com.gpa.calculator.databinding.ViewResultRowBinding;
import com.gpa.calculator.ui.common.Anim;
import com.gpa.calculator.ui.common.Haptics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Live currency converter (USD, INR, EUR, GBP, AED … ) using cached ECB reference rates. */
public class CurrencyActivity extends CalcActivityBase {
    private static final String[] POPULAR = {"USD", "INR", "EUR", "GBP", "AED", "SAR", "JPY", "AUD"};
    private ActivityCurrencyBinding b;
    private Map<String, Double> rates = new LinkedHashMap<>();
    private String from = "USD", to = "INR";
    private boolean savedThisSession;
    private final Runnable recalc = this::convert;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (calc == null) return;
        b = ActivityCurrencyBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        b.toolbar.setTitle(calc.name());

        from = Prefs.get(this).getString("cur_from", "USD");
        to = Prefs.get(this).getString("cur_to", "INR");
        String prefill = getIntent().getStringExtra(CalcLauncher.EXTRA_INPUTS);
        if (prefill != null) {
            try {
                Map<String, String> m = new com.google.gson.Gson().fromJson(prefill, new com.google.gson.reflect.TypeToken<Map<String, String>>() {}.getType());
                if (m.get("from") != null) from = m.get("from");
                if (m.get("to") != null) to = m.get("to");
                if (m.get("amount") != null) b.amount.setText(m.get("amount"));
            } catch (Exception ignored) {
            }
        }

        String[] labels = new String[Currencies.NAMES.size()];
        int i = 0;
        for (String code : Currencies.NAMES.keySet()) labels[i++] = Currencies.label(code);
        b.from.setSimpleItems(labels);
        b.to.setSimpleItems(labels);
        b.from.setText(Currencies.label(from), false);
        b.to.setText(Currencies.label(to), false);
        b.from.setOnItemClickListener((p, v, pos, id) -> { from = Currencies.codeFromLabel(labels[pos]); Haptics.tick(v); schedule(); });
        b.to.setOnItemClickListener((p, v, pos, id) -> { to = Currencies.codeFromLabel(labels[pos]); Haptics.tick(v); schedule(); });
        b.amount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { schedule(); }
        });
        b.swap.setOnClickListener(v -> {
            String t = from; from = to; to = t;
            b.from.setText(Currencies.label(from), false);
            b.to.setText(Currencies.label(to), false);
            v.animate().rotationBy(180f).setDuration(350).setInterpolator(new android.view.animation.OvershootInterpolator()).start();
            Haptics.tick(v);
            convert();
        });
        b.btnRefresh.setOnClickListener(v -> load(true));

        rates = RatesRepo.get(this).cached();
        convert();
        load(false);
        attachBanner(b.adContainer);
        attachNative(b.nativeContainer, false);
    }

    private void load(boolean force) {
        b.btnRefresh.setEnabled(false);
        RatesRepo.get(this).refresh(force, (r, updatedAt, fromNetwork, error) -> {
            if (b == null) return;
            rates = r;
            b.btnRefresh.setEnabled(true);
            if (error != null && force) Snackbar.make(b.getRoot(), getString(R.string.currency_offline), Snackbar.LENGTH_LONG).show();
            if (fromNetwork) Anim.pop(b.updated);
            convert();
        });
    }

    private void schedule() {
        b.getRoot().removeCallbacks(recalc);
        b.getRoot().postDelayed(recalc, 150);
    }

    private double amount() {
        try {
            return Double.parseDouble(b.amount.getText().toString().replace(",", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private void convert() {
        savedThisSession = false;
        double amt = amount();
        Prefs.get(this).putString("cur_from", from);
        Prefs.get(this).putString("cur_to", to);
        try {
            double out = Currencies.convert(amt, from, to, rates);
            double unit = Currencies.convert(1, from, to, rates);
            b.resultLabel.setText(Fmt.fmt(amt, 2) + " " + from + " =");
            b.resultValue.countTo(out, out >= 100 ? 2 : 4, "", " " + to, 600);
            b.rateLine.setText("1 " + from + " = " + Fmt.fmt(unit, unit >= 100 ? 2 : 4) + " " + to + "   ·   1 " + to + " = " + Fmt.fmt(1 / unit, 1 / unit >= 100 ? 2 : 4) + " " + from);
            long ts = RatesRepo.get(this).updatedAt();
            b.updated.setText(ts == 0 ? getString(R.string.currency_offline)
                    : getString(R.string.currency_updated, DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)));
            b.popular.removeAllViews();
            for (String code : POPULAR) {
                if (code.equals(from)) continue;
                ViewResultRowBinding row = ViewResultRowBinding.inflate(getLayoutInflater(), b.popular, false);
                double v = Currencies.convert(amt, from, code, rates);
                row.rowLabel.setText(Currencies.label(code));
                row.rowValue.setPlain(Fmt.fmt(v, v >= 100 ? 2 : 4));
                row.getRoot().setOnClickListener(x -> { to = code; b.to.setText(Currencies.label(to), false); convert(); });
                b.popular.addView(row.getRoot());
            }
        } catch (CalcException e) {
            b.resultValue.setPlain("—");
            b.rateLine.setText(e.getMessage());
        }
    }

    /** Written to history when the user leaves the screen with a non-trivial amount. */
    @Override
    protected void onPause() {
        super.onPause();
        double amt = amount();
        if (amt > 0 && amt != 1 && !savedThisSession && b != null) {
            try {
                double out = Currencies.convert(amt, from, to, rates);
                List<CalcResult> res = new ArrayList<>();
                res.add(CalcResult.primary(Fmt.fmt(amt, 2) + " " + from, out, 2, "", " " + to));
                Map<String, String> in = new LinkedHashMap<>();
                in.put("amount", Fmt.fmt(amt, 2).replace(",", ""));
                in.put("from", from);
                in.put("to", to);
                Repository.get(this).saveHistory(calc, in, res, null);
                savedThisSession = true;
                afterCalculation();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onReset() {
        b.amount.setText("1");
        from = "USD";
        to = "INR";
        b.from.setText(Currencies.label(from), false);
        b.to.setText(Currencies.label(to), false);
        convert();
    }
}
