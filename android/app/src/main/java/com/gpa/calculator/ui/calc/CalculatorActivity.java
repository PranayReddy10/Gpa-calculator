package com.gpa.calculator.ui.calc;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gpa.calculator.R;
import com.gpa.calculator.core.CalcException;
import com.gpa.calculator.core.CalcInput;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.data.local.HistoryEntity;
import com.gpa.calculator.databinding.ActivityCalculatorBinding;
import com.gpa.calculator.databinding.ViewRecentRowBinding;
import com.gpa.calculator.databinding.ViewResultRowBinding;
import com.gpa.calculator.ui.common.Anim;
import com.gpa.calculator.ui.common.Haptics;
import com.gpa.calculator.ui.common.Icons;
import com.gpa.calculator.ui.common.ShareUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic form-driven calculator screen: renders CalcInput declarations and animates CalcResults. */
public class CalculatorActivity extends CalcActivityBase {
    private ActivityCalculatorBinding b;
    private final Map<String, View> fields = new LinkedHashMap<>();
    private final Map<String, TextInputLayout> layouts = new LinkedHashMap<>();
    private final Map<String, String> dateValues = new LinkedHashMap<>();
    private List<CalcResult> lastResults = new ArrayList<>();
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (calc == null) return;
        b = ActivityCalculatorBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        b.collapsing.setTitle(calc.name());
        b.heroDesc.setText(calc.description());
        b.heroIcon.setImageResource(Icons.resolve(this, calc.icon()));
        if (!calc.formula().isEmpty()) {
            b.formula.setText(calc.formula());
            b.formula.setVisibility(View.VISIBLE);
        }

        buildForm();
        Anim.staggerChildren(b.inputs, 120);

        String prefill = getIntent().getStringExtra(CalcLauncher.EXTRA_INPUTS);
        if (prefill != null) {
            applyInputs(prefill);
            b.btnCalculate.postDelayed(this::calculate, 450);
        }

        b.btnCalculate.setOnClickListener(v -> calculate());
        b.result.btnCopy.setOnClickListener(v -> ShareUtil.copy(b.getRoot(), ShareUtil.resultsAsText(calc.name(), lastResults)));
        b.result.btnShare.setOnClickListener(v -> ShareUtil.shareViewAsImage(this, b.result.getRoot(), ShareUtil.resultsAsText(calc.name(), lastResults)));

        Repository.get(this).historyFor(calc.id()).observe(this, this::showRecent);
        attachBanner(b.adContainer);
        attachNative(b.nativeContainer, false);
    }

    // ------------------------------------------------------------------ form
    private void buildForm() {
        LayoutInflater li = getLayoutInflater();
        for (CalcInput in : calc.inputs()) {
            if (!in.section.isEmpty()) {
                android.widget.TextView h = (android.widget.TextView) li.inflate(R.layout.view_input_section, b.inputs, false);
                h.setText(in.section);
                b.inputs.addView(h);
            }
            switch (in.type) {
                case SELECT: {
                    TextInputLayout til = (TextInputLayout) li.inflate(R.layout.view_input_select, b.inputs, false);
                    MaterialAutoCompleteTextView ac = til.findViewById(R.id.edit);
                    til.setHint(in.label);
                    ac.setSimpleItems(in.options);
                    ac.setText(in.defaultValue, false);
                    ac.setOnItemClickListener((p, v, pos, id) -> Haptics.tick(v));
                    b.inputs.addView(til);
                    fields.put(in.key, ac);
                    layouts.put(in.key, til);
                    break;
                }
                case DATE: {
                    TextInputLayout til = (TextInputLayout) li.inflate(R.layout.view_input_text, b.inputs, false);
                    TextInputEditText et = til.findViewById(R.id.edit);
                    til.setHint(in.label + (in.optional ? " (optional)" : ""));
                    til.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
                    til.setEndIconDrawable(R.drawable.ic_calendar);
                    et.setInputType(InputType.TYPE_NULL);
                    et.setFocusable(false);
                    et.setClickable(true);
                    View.OnClickListener open = v -> pickDate(in.key, et);
                    et.setOnClickListener(open);
                    til.setEndIconOnClickListener(open);
                    b.inputs.addView(til);
                    fields.put(in.key, et);
                    layouts.put(in.key, til);
                    break;
                }
                case TEXT: {
                    TextInputLayout til = (TextInputLayout) li.inflate(R.layout.view_input_text, b.inputs, false);
                    TextInputEditText et = til.findViewById(R.id.edit);
                    til.setHint(in.label);
                    til.setHelperText(in.hint.isEmpty() ? null : in.hint);
                    et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                    et.setMaxLines(4);
                    b.inputs.addView(til);
                    fields.put(in.key, et);
                    layouts.put(in.key, til);
                    break;
                }
                default: {
                    TextInputLayout til = (TextInputLayout) li.inflate(R.layout.view_input_text, b.inputs, false);
                    TextInputEditText et = til.findViewById(R.id.edit);
                    til.setHint(in.label + (in.optional ? " (optional)" : ""));
                    if (!in.suffix.isEmpty()) til.setSuffixText(in.suffix);
                    if (!in.hint.isEmpty()) til.setHelperText(in.hint);
                    int flags = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED;
                    if (in.type == CalcInput.Type.NUMBER) flags |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
                    et.setInputType(flags);
                    if (!in.defaultValue.isEmpty()) et.setText(in.defaultValue);
                    b.inputs.addView(til);
                    fields.put(in.key, et);
                    layouts.put(in.key, til);
                }
            }
        }
        // last field: IME "done" triggers calculation
        View last = null;
        for (View v : fields.values()) if (v instanceof TextInputEditText) last = v;
        if (last != null) {
            ((TextInputEditText) last).setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            ((TextInputEditText) last).setOnEditorActionListener((v, a, e) -> { calculate(); return true; });
        }
    }

    private void pickDate(String key, TextInputEditText et) {
        String current = dateValues.get(key);
        long sel = current == null ? MaterialDatePicker.todayInUtcMilliseconds()
                : LocalDate.parse(current).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker().setSelection(sel).build();
        picker.addOnPositiveButtonClickListener(ms -> {
            LocalDate d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate();
            dateValues.put(key, d.toString());
            et.setText(d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)));
            Haptics.tick(et);
        });
        picker.show(getSupportFragmentManager(), "date_" + key);
    }

    private Map<String, String> collect() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, View> e : fields.entrySet()) {
            if (dateValues.containsKey(e.getKey())) m.put(e.getKey(), dateValues.get(e.getKey()));
            else if (e.getValue() instanceof android.widget.TextView) m.put(e.getKey(), ((android.widget.TextView) e.getValue()).getText().toString());
        }
        return m;
    }

    private void applyInputs(String json) {
        try {
            Map<String, String> m = gson.fromJson(json, new TypeToken<Map<String, String>>() {}.getType());
            for (CalcInput in : calc.inputs()) {
                String v = m.get(in.key);
                View f = fields.get(in.key);
                if (v == null || f == null) continue;
                if (in.type == CalcInput.Type.DATE) {
                    dateValues.put(in.key, v);
                    ((TextInputEditText) f).setText(LocalDate.parse(v).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)));
                } else if (f instanceof MaterialAutoCompleteTextView) {
                    ((MaterialAutoCompleteTextView) f).setText(v, false);
                } else {
                    ((android.widget.TextView) f).setText(v);
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ compute
    private void calculate() {
        for (TextInputLayout l : layouts.values()) l.setError(null);
        hideKeyboard();
        Map<String, String> inputs = collect();
        List<CalcResult> results;
        try {
            results = calc.compute(inputs);
        } catch (CalcException e) {
            TextInputLayout l = e.inputKey == null ? null : layouts.get(e.inputKey);
            if (l != null) {
                l.setError(e.getMessage());
                Anim.shake(l);
                l.requestFocus();
            } else {
                Snackbar.make(b.getRoot(), e.getMessage(), Snackbar.LENGTH_LONG).show();
            }
            Haptics.reject(b.btnCalculate);
            return;
        } catch (Exception e) {
            Snackbar.make(b.getRoot(), R.string.error_generic, Snackbar.LENGTH_LONG).show();
            return;
        }
        Haptics.confirm(b.btnCalculate);
        lastResults = results;
        showResults(results);
        Repository.get(this).saveHistory(calc, inputs, results, null);
        afterCalculation();
    }

    private void showResults(List<CalcResult> results) {
        CalcResult primary = results.get(0);
        for (CalcResult r : results) if (r.primary) { primary = r; break; }
        b.result.primaryLabel.setText(primary.label);
        if (primary.numeric != null) b.result.primaryValue.countTo(primary.numeric, primary.decimals, primary.prefix, primary.suffix);
        else b.result.primaryValue.setPlain(primary.display);

        b.result.rows.removeAllViews();
        int i = 0;
        for (CalcResult r : results) {
            if (r == primary) continue;
            ViewResultRowBinding row = ViewResultRowBinding.inflate(getLayoutInflater(), b.result.rows, false);
            row.rowLabel.setText(r.label);
            if (r.numeric != null) row.rowValue.countTo(r.numeric, r.decimals, r.prefix, r.suffix, 700 + i * 80L);
            else row.rowValue.setPlain(r.display);
            b.result.rows.addView(row.getRoot());
            i++;
        }
        boolean wasVisible = b.result.getRoot().getVisibility() == View.VISIBLE && b.result.getRoot().getAlpha() == 1f;
        if (!wasVisible) Anim.springIn(b.result.getRoot());
        else Anim.staggerChildren(b.result.rows, 0);
        b.scroll.postDelayed(() -> b.scroll.smoothScrollTo(0, b.result.getRoot().getTop() - 24), 120);
    }

    private void showRecent(List<HistoryEntity> items) {
        b.recent.removeAllViews();
        int shown = 0;
        for (HistoryEntity e : items) {
            if (shown++ >= 5) break;
            ViewRecentRowBinding row = ViewRecentRowBinding.inflate(getLayoutInflater(), b.recent, false);
            row.recentValue.setText(e.summary);
            row.recentInputs.setText(summarise(e.inputsJson));
            row.getRoot().setOnClickListener(v -> {
                applyInputs(e.inputsJson);
                calculate();
            });
            b.recent.addView(row.getRoot());
        }
        b.recentTitle.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
    }

    private String summarise(String json) {
        try {
            Map<String, String> m = gson.fromJson(json, new TypeToken<Map<String, String>>() {}.getType());
            StringBuilder sb = new StringBuilder();
            for (CalcInput in : calc.inputs()) {
                String v = m.get(in.key);
                if (v == null || v.isEmpty()) continue;
                if (sb.length() > 0) sb.append(" · ");
                sb.append(in.label.length() > 14 ? in.label.substring(0, 14) : in.label).append(": ").append(v);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
            f.clearFocus();
        }
    }

    @Override
    protected void onReset() {
        dateValues.clear();
        for (CalcInput in : calc.inputs()) {
            View f = fields.get(in.key);
            if (f instanceof MaterialAutoCompleteTextView) ((MaterialAutoCompleteTextView) f).setText(in.defaultValue, false);
            else if (f instanceof android.widget.TextView) ((android.widget.TextView) f).setText(in.type == CalcInput.Type.DATE ? "" : in.defaultValue);
        }
        for (TextInputLayout l : layouts.values()) l.setError(null);
        b.result.getRoot().animate().alpha(0f).translationY(40f).setDuration(200)
                .withEndAction(() -> b.result.getRoot().setVisibility(View.GONE)).start();
        Haptics.tick(b.btnCalculate);
    }
}
