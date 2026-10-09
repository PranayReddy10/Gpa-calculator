package com.gpa.calculator.ui.calc;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gpa.calculator.R;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.core.EmiMath;
import com.gpa.calculator.core.Fmt;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.databinding.ActivityEmiBinding;
import com.gpa.calculator.databinding.ItemScheduleRowBinding;
import com.gpa.calculator.databinding.SheetScheduleBinding;
import com.gpa.calculator.databinding.ViewResultRowBinding;
import com.gpa.calculator.ui.common.Anim;
import com.gpa.calculator.ui.common.Haptics;
import com.gpa.calculator.ui.common.ShareUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Live EMI calculator with sliders, animated donut, and an amortisation schedule bottom sheet. */
public class EmiActivity extends CalcActivityBase {
    private ActivityEmiBinding b;
    @Nullable private EmiMath.Result last;
    private List<CalcResult> lastResults = new ArrayList<>();
    private boolean savedThisSession;
    private final Runnable recalc = this::recalculate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (calc == null) return;
        b = ActivityEmiBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        b.toolbar.setTitle(calc.name());
        b.formula.setText(calc.formula());

        String prefill = getIntent().getStringExtra(CalcLauncher.EXTRA_INPUTS);
        if (prefill != null) restore(prefill);

        b.principal.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { schedule(); }
        });
        b.rate.addOnChangeListener((s, v, user) -> { updateLabels(); if (user) schedule(); });
        b.tenure.addOnChangeListener((s, v, user) -> { updateLabels(); if (user) schedule(); });
        b.tenureUnit.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            boolean years = id == R.id.unit_years;
            float value = b.tenure.getValue();
            b.tenure.setValueTo(years ? 30 : 360);
            b.tenure.setValue(Math.min(value, years ? 30 : 360));
            updateLabels();
            schedule();
        });
        b.btnSchedule.setOnClickListener(v -> showSchedule());
        b.btnShare.setOnClickListener(v -> {
            save();
            ShareUtil.shareViewAsImage(this, b.resultCard, ShareUtil.resultsAsText(calc.name(), lastResults));
        });
        updateLabels();
        recalculate();
        attachBanner(b.adContainer);
    }

    private void updateLabels() {
        b.rateLabel.setText("Interest rate: " + Fmt.fmt(b.rate.getValue(), 1) + "% per year");
        boolean years = b.tenureUnit.getCheckedButtonId() == R.id.unit_years;
        b.tenureLabel.setText("Tenure: " + Fmt.fmt(b.tenure.getValue(), 0) + (years ? " years" : " months"));
    }

    private void schedule() {
        b.getRoot().removeCallbacks(recalc);
        b.getRoot().postDelayed(recalc, 180);
    }

    private int months() {
        boolean years = b.tenureUnit.getCheckedButtonId() == R.id.unit_years;
        return Math.max(1, Math.round(b.tenure.getValue() * (years ? 12 : 1)));
    }

    private void recalculate() {
        double principal;
        try {
            principal = Double.parseDouble(b.principal.getText().toString().replace(",", ""));
        } catch (Exception e) {
            principal = 0;
        }
        if (principal <= 0) {
            b.principalLayout.setError("Enter the loan amount");
            return;
        }
        b.principalLayout.setError(null);
        last = EmiMath.compute(principal, b.rate.getValue(), months());
        savedThisSession = false;
        double interestShare = last.totalPayment == 0 ? 0 : last.totalInterest / last.totalPayment;

        b.emiValue.countTo(last.emi, 0, "", "", 700);
        b.emiSub.setText("for " + months() + " months · total " + Fmt.fmt(last.totalPayment, 0));
        b.donut.animateTo((float) interestShare, Fmt.fmt(interestShare * 100, 0) + "%");

        lastResults = new ArrayList<>();
        lastResults.add(CalcResult.primary("Monthly EMI", last.emi, 2, "", ""));
        lastResults.add(CalcResult.num("Principal", principal, 2));
        lastResults.add(CalcResult.num("Total interest", last.totalInterest, 2));
        lastResults.add(CalcResult.num("Total payment", last.totalPayment, 2));
        lastResults.add(CalcResult.num("Interest share", interestShare * 100, 1, "", "%"));

        b.rows.removeAllViews();
        for (int i = 1; i < lastResults.size(); i++) {
            CalcResult r = lastResults.get(i);
            ViewResultRowBinding row = ViewResultRowBinding.inflate(getLayoutInflater(), b.rows, false);
            row.rowLabel.setText(r.label);
            row.rowValue.countTo(r.numeric, r.decimals, r.prefix, r.suffix, 600 + i * 60L);
            b.rows.addView(row.getRoot());
        }
    }

    private Map<String, String> inputs() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("principal", b.principal.getText().toString());
        m.put("rate", Fmt.fmt(b.rate.getValue(), 2).replace(",", ""));
        m.put("tenure", Fmt.fmt(b.tenure.getValue(), 0).replace(",", ""));
        m.put("unit", b.tenureUnit.getCheckedButtonId() == R.id.unit_years ? "Years" : "Months");
        return m;
    }

    private void restore(String json) {
        try {
            Map<String, String> m = new Gson().fromJson(json, new TypeToken<Map<String, String>>() {}.getType());
            b.principal.setText(m.get("principal"));
            boolean years = !"Months".equals(m.get("unit"));
            b.tenureUnit.check(years ? R.id.unit_years : R.id.unit_months);
            b.tenure.setValueTo(years ? 30 : 360);
            b.tenure.setValue(Math.min(b.tenure.getValueTo(), Math.max(1, Float.parseFloat(m.get("tenure")))));
            b.rate.setValue(Math.min(30, Math.max(0, Float.parseFloat(m.get("rate")))));
        } catch (Exception ignored) {
        }
    }

    /** History is written once per distinct calculation (on schedule view / share) to avoid spam from live sliders. */
    private void save() {
        if (last == null || savedThisSession) return;
        savedThisSession = true;
        Repository.get(this).saveHistory(calc, inputs(), lastResults, null);
        afterCalculation();
    }

    private void showSchedule() {
        if (last == null) return;
        Haptics.tick(b.btnSchedule);
        save();
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        SheetScheduleBinding s = SheetScheduleBinding.inflate(getLayoutInflater());
        dialog.setContentView(s.getRoot());

        // yearly aggregation for the chart
        int years = (int) Math.ceil(last.schedule.size() / 12.0);
        double[] principal = new double[years], interest = new double[years];
        String[] labels = new String[years];
        for (EmiMath.Row r : last.schedule) {
            int y = (r.month - 1) / 12;
            principal[y] += r.principal;
            interest[y] += r.interest;
        }
        for (int i = 0; i < years; i++) labels[i] = "Y" + (i + 1);
        s.chart.setData(principal, interest, labels);

        s.list.setLayoutManager(new LinearLayoutManager(this));
        s.list.setAdapter(new RecyclerView.Adapter<RowHolder>() {
            @NonNull @Override public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RowHolder(ItemScheduleRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
            }

            @Override public void onBindViewHolder(@NonNull RowHolder h, int position) {
                EmiMath.Row r = last.schedule.get(position);
                h.v.cMonth.setText(String.valueOf(r.month));
                h.v.cPrincipal.setText(Fmt.fmt(r.principal, 0));
                h.v.cInterest.setText(Fmt.fmt(r.interest, 0));
                h.v.cBalance.setText(Fmt.fmt(r.balance, 0));
                h.v.getRoot().setAlpha(position % 12 == 0 ? 1f : 0.85f);
            }

            @Override public int getItemCount() { return last.schedule.size(); }
        });
        dialog.show();
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final ItemScheduleRowBinding v;
        RowHolder(ItemScheduleRowBinding v) { super(v.getRoot()); this.v = v; }
    }

    @Override
    protected void onReset() {
        b.principal.setText("500000");
        b.rate.setValue(9f);
        b.tenureUnit.check(R.id.unit_years);
        b.tenure.setValueTo(30);
        b.tenure.setValue(5f);
        updateLabels();
        recalculate();
        Anim.pop(b.resultCard);
    }
}
