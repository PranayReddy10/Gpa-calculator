package com.gpa.calculator.ui.calc;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gpa.calculator.R;
import com.gpa.calculator.core.CalcException;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.core.Fmt;
import com.gpa.calculator.core.GradeMath;
import com.gpa.calculator.core.GradeScale;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.data.Semesters;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.gpa.calculator.databinding.ActivityGradesBinding;
import com.gpa.calculator.databinding.ItemGradeRowBinding;
import com.gpa.calculator.ui.common.Anim;
import com.gpa.calculator.ui.common.Haptics;
import com.gpa.calculator.ui.common.ShareUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dynamic-row screen for SGPA (points), SGPA with letter grades, and CGPA (semesters). */
public class GradeCalculatorActivity extends CalcActivityBase {
    private enum Mode { SGPA, SGPA_GRADE, CGPA }

    static final class Row {
        String name = "";
        String credits = "";
        String value = "";  // grade points or SGPA
        String grade = "";  // letter grade (SGPA_GRADE)
    }

    private ActivityGradesBinding b;
    private Mode mode;
    private final List<Row> rows = new ArrayList<>();
    private RowAdapter adapter;
    private GradeScale scale = GradeScale.TEN_POINT;
    private List<CalcResult> lastResults = new ArrayList<>();
    private double lastGpa = -1, lastCredits;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (calc == null) return;
        b = ActivityGradesBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        b.toolbar.setTitle(calc.name());
        b.formula.setText(calc.formula());

        mode = "cgpa".equals(calc.id()) ? Mode.CGPA : "sgpa_grade".equals(calc.id()) ? Mode.SGPA_GRADE : Mode.SGPA;
        b.rowsTitle.setText(mode == Mode.CGPA ? "Semesters" : "Subjects");
        b.btnAdd.setText(mode == Mode.CGPA ? R.string.add_semester : R.string.add_subject);
        b.result.primaryLabel.setText(mode == Mode.CGPA ? R.string.cgpa : R.string.sgpa);
        b.result.secondary.setText(R.string.tagline);

        if (mode == Mode.SGPA_GRADE) {
            b.scaleLayout.setVisibility(View.VISIBLE);
            b.scale.setSimpleItems(GradeScale.names());
            b.scale.setText(scale.name, false);
            b.scale.setOnItemClickListener((p, v, pos, id) -> {
                scale = GradeScale.ALL.get(pos);
                for (Row r : rows) r.grade = "";
                adapter.notifyDataSetChanged();
            });
        }

        adapter = new RowAdapter();
        b.rows.setLayoutManager(new LinearLayoutManager(this));
        b.rows.setAdapter(adapter);
        b.rows.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());

        String prefill = getIntent().getStringExtra(CalcLauncher.EXTRA_INPUTS);
        if (prefill != null && restore(prefill)) {
            b.btnCalculate.postDelayed(this::calculate, 450);
        } else if (mode == Mode.CGPA && loadSavedSemesters()) {
            int n = rows.size();
            b.btnCalculate.postDelayed(() -> {
                calculate();
                Snackbar.make(b.getRoot(), getString(R.string.semesters_loaded, n), Snackbar.LENGTH_SHORT).show();
            }, 450);
        } else {
            int n = mode == Mode.CGPA ? 2 : 5;
            for (int i = 0; i < n; i++) rows.add(new Row());
        }
        setupSemesterButtons();
        adapter.notifyDataSetChanged();
        updateTotalCredits();

        b.btnAdd.setOnClickListener(v -> {
            rows.add(new Row());
            adapter.notifyItemInserted(rows.size() - 1);
            Haptics.tick(v);
            b.scroll.postDelayed(() -> b.scroll.smoothScrollBy(0, 400), 100);
        });
        b.btnCalculate.setOnClickListener(v -> calculate());
        b.result.btnCopy.setOnClickListener(v -> ShareUtil.copy(b.getRoot(), ShareUtil.resultsAsText(calc.name(), lastResults)));
        b.result.btnShare.setOnClickListener(v -> ShareUtil.shareViewAsImage(this, b.result.getRoot(), ShareUtil.resultsAsText(calc.name(), lastResults)));
        attachBanner(b.adContainer);
        attachNative(b.nativeContainer, false);
    }

    private void updateTotalCredits() {
        double total = 0;
        for (Row r : rows) {
            try { total += Double.parseDouble(r.credits); } catch (Exception ignored) {}
        }
        b.totalCredits.setText(getString(R.string.total_credits) + ": " + Fmt.fmt(total, 2));
    }

    private void calculate() {
        View f = getCurrentFocus();
        if (f != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.getWindowToken(), 0);
        List<Double> credits = new ArrayList<>(), values = new ArrayList<>();
        try {
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                boolean empty = r.credits.isEmpty() && r.value.isEmpty() && r.grade.isEmpty();
                if (empty) continue;
                if (r.credits.isEmpty()) throw new CalcException("Enter credits for row " + (i + 1));
                double c = Double.parseDouble(r.credits);
                double v;
                if (mode == Mode.SGPA_GRADE) {
                    if (r.grade.isEmpty()) throw new CalcException("Choose a grade for row " + (i + 1));
                    v = scale.pointsFor(r.grade);
                } else {
                    if (r.value.isEmpty()) throw new CalcException((mode == Mode.CGPA ? "Enter SGPA for semester " : "Enter grade points for row ") + (i + 1));
                    v = Double.parseDouble(r.value);
                    double max = mode == Mode.CGPA ? 10 : 10;
                    if (v > max) throw new CalcException((mode == Mode.CGPA ? "SGPA" : "Grade points") + " cannot exceed " + Fmt.fmt(max, 0) + " (row " + (i + 1) + ")");
                }
                credits.add(c);
                values.add(v);
            }
            double gpa = GradeMath.sgpa(credits, values);
            double max = mode == Mode.SGPA_GRADE ? scale.maxPoints : 10;
            double totalCredits = 0;
            for (double c : credits) totalCredits += c;
            double pct = gpa / max * 100;

            lastResults = new ArrayList<>();
            lastResults.add(CalcResult.primary(mode == Mode.CGPA ? "CGPA" : "SGPA", gpa, 2, "", ""));
            lastResults.add(CalcResult.num("Total credits", totalCredits, 2));
            lastResults.add(CalcResult.num("Equivalent percentage", pct, 2, "", "%"));
            lastResults.add(CalcResult.text("Grade", GradeScale.letterForPercent(pct)));

            b.result.primaryValue.countTo(gpa, 2, "", "");
            b.result.secondary.setText(Fmt.fmt(totalCredits, 2) + " credits · " + Fmt.fmt(pct, 1) + "% · " + GradeScale.letterForPercent(pct));
            b.result.donut.animateTo((float) (gpa / max), Fmt.fmt(pct, 0) + "%");
            Anim.pop(b.result.donut);
            Haptics.confirm(b.btnCalculate);
            b.scroll.smoothScrollTo(0, 0);

            lastGpa = gpa;
            lastCredits = totalCredits;
            if (mode != Mode.CGPA && b.result.btnSaveSemester.getVisibility() != View.VISIBLE) {
                b.result.btnSaveSemester.setVisibility(View.VISIBLE);
                Anim.pop(b.result.btnSaveSemester);
            }
            Repository.get(this).saveHistory(calc, serialise(), lastResults, null);
            afterCalculation();
        } catch (CalcException e) {
            Haptics.reject(b.btnCalculate);
            Anim.shake(b.rows);
            Snackbar.make(b.getRoot(), e.getMessage(), Snackbar.LENGTH_LONG).show();
        } catch (NumberFormatException e) {
            Haptics.reject(b.btnCalculate);
            Snackbar.make(b.getRoot(), "Enter valid numbers", Snackbar.LENGTH_LONG).show();
        }
    }

    private Map<String, String> serialise() {
        JsonArray arr = new JsonArray();
        for (Row r : rows) {
            if (r.credits.isEmpty() && r.value.isEmpty() && r.grade.isEmpty()) continue;
            JsonObject o = new JsonObject();
            o.addProperty("name", r.name);
            o.addProperty("credits", r.credits);
            o.addProperty("value", r.value);
            o.addProperty("grade", r.grade);
            arr.add(o);
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rows", arr.toString());
        m.put("scale", scale.name);
        return m;
    }

    private boolean restore(String json) {
        try {
            JsonObject o = new Gson().fromJson(json, JsonObject.class);
            if (o.has("scale")) {
                scale = GradeScale.byName(o.get("scale").getAsString());
                if (mode == Mode.SGPA_GRADE) b.scale.setText(scale.name, false);
            }
            JsonArray arr = new Gson().fromJson(o.get("rows").getAsString(), JsonArray.class);
            rows.clear();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject r = arr.get(i).getAsJsonObject();
                Row row = new Row();
                row.name = r.get("name").getAsString();
                row.credits = r.get("credits").getAsString();
                row.value = r.get("value").getAsString();
                row.grade = r.get("grade").getAsString();
                rows.add(row);
            }
            return !rows.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ my semesters (SGPA -> CGPA)
    private List<Semesters.Semester> savedSemesters() {
        return Semesters.parse(Prefs.get(this).getString(Semesters.PREF_KEY, null));
    }

    private void storeSemesters(List<Semesters.Semester> list) {
        Prefs.get(this).putString(Semesters.PREF_KEY, Semesters.toJson(list));
    }

    /** CGPA mode: replaces the rows with the saved semesters. */
    private boolean loadSavedSemesters() {
        List<Semesters.Semester> list = savedSemesters();
        if (list.isEmpty()) return false;
        rows.clear();
        for (Semesters.Semester s : list) {
            Row r = new Row();
            r.name = s.name;
            r.credits = Fmt.fmt(s.credits, 2).replace(",", "");
            r.value = Fmt.fmt(s.sgpa, 2).replace(",", "");
            rows.add(r);
        }
        return true;
    }

    private void setupSemesterButtons() {
        b.result.btnSaveSemester.setOnClickListener(v -> promptSaveSemester());
        refreshSavedButton();
        b.btnSavedSemesters.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.semesters_button, savedSemesters().size()))
                .setItems(new CharSequence[]{getString(R.string.semesters_reload), getString(R.string.semesters_clear)}, (d, which) -> {
                    if (which == 0) {
                        if (loadSavedSemesters()) {
                            adapter.notifyDataSetChanged();
                            updateTotalCredits();
                            calculate();
                        }
                    } else {
                        storeSemesters(new ArrayList<>());
                        refreshSavedButton();
                        Snackbar.make(b.getRoot(), R.string.semesters_cleared, Snackbar.LENGTH_SHORT).show();
                    }
                })
                .show());
    }

    private void refreshSavedButton() {
        int n = savedSemesters().size();
        b.btnSavedSemesters.setVisibility(mode == Mode.CGPA && n > 0 ? View.VISIBLE : View.GONE);
        b.btnSavedSemesters.setText(getString(R.string.semesters_button, n));
    }

    private void promptSaveSemester() {
        if (lastGpa < 0) return;
        List<Semesters.Semester> list = savedSemesters();
        com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(this);
        com.google.android.material.textfield.TextInputEditText et = new com.google.android.material.textfield.TextInputEditText(til.getContext());
        et.setText(Semesters.nextName(list));
        et.setSelectAllOnFocus(true);
        til.setHint(getString(R.string.semester_save_hint));
        til.addView(et);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(til);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.semester_save_title)
                .setMessage("SGPA " + Fmt.fmt(lastGpa, 2) + " · " + Fmt.fmt(lastCredits, 2) + " credits")
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String name = et.getText() == null ? "" : et.getText().toString().trim();
                    if (name.isEmpty()) name = Semesters.nextName(list);
                    storeSemesters(Semesters.upsert(list, new Semesters.Semester(name, lastGpa, lastCredits)));
                    Haptics.confirm(b.result.btnSaveSemester);
                    com.gpa.calculator.data.remote.Analytics.get(this).log("semester_saved");
                    com.gpa.calculator.core.Calculator cgpa = com.gpa.calculator.core.CalculatorRegistry.byId("cgpa");
                    Snackbar.make(b.getRoot(), getString(R.string.semester_saved, name, Fmt.fmt(lastGpa, 2)), Snackbar.LENGTH_LONG)
                            .setAction(R.string.semester_open_cgpa, x -> { if (cgpa != null) CalcLauncher.open(this, null, cgpa, null, false); })
                            .show();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (b != null) refreshSavedButton();
    }

    @Override
    protected void onReset() {
        rows.clear();
        int n = mode == Mode.CGPA ? 2 : 5;
        for (int i = 0; i < n; i++) rows.add(new Row());
        adapter.notifyDataSetChanged();
        b.result.primaryValue.setPlain("0.00");
        b.result.secondary.setText(R.string.tagline);
        b.result.donut.animateTo(0f, "");
        updateTotalCredits();
    }

    // ------------------------------------------------------------------ rows
    private class RowAdapter extends RecyclerView.Adapter<RowHolder> {
        @NonNull @Override public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RowHolder(ItemGradeRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull RowHolder h, int position) { h.bind(rows.get(position)); }

        @Override public int getItemCount() { return rows.size(); }
    }

    private class RowHolder extends RecyclerView.ViewHolder {
        final ItemGradeRowBinding v;
        Row bound;

        RowHolder(ItemGradeRowBinding v) {
            super(v.getRoot());
            this.v = v;
            v.name.addTextChangedListener(watch(s -> { if (bound != null) bound.name = s; }));
            v.credits.addTextChangedListener(watch(s -> { if (bound != null) { bound.credits = s; updateTotalCredits(); } }));
            v.points.addTextChangedListener(watch(s -> { if (bound != null) bound.value = s; }));
            v.grade.setOnItemClickListener((p, view, pos, id) -> { if (bound != null) bound.grade = scale.letters[pos]; Haptics.tick(view); });
            v.remove.setOnClickListener(view -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                rows.remove(pos);
                adapter.notifyItemRemoved(pos);
                adapter.notifyItemRangeChanged(pos, rows.size() - pos);
                updateTotalCredits();
                Haptics.tick(view);
            });
        }

        void bind(Row r) {
            bound = null;
            int n = getBindingAdapterPosition() + 1;
            v.nameLayout.setHint(mode == Mode.CGPA ? getString(R.string.semester) + " " + n : getString(R.string.subject) + " " + n);
            v.name.setText(r.name);
            v.credits.setText(r.credits);
            if (mode == Mode.SGPA_GRADE) {
                v.pointsLayout.setVisibility(View.GONE);
                v.gradeLayout.setVisibility(View.VISIBLE);
                v.grade.setSimpleItems(scale.letters);
                v.grade.setText(r.grade, false);
            } else {
                v.gradeLayout.setVisibility(View.GONE);
                v.pointsLayout.setVisibility(View.VISIBLE);
                v.pointsLayout.setHint(mode == Mode.CGPA ? getString(R.string.sgpa) : getString(R.string.grade_points));
                v.points.setText(r.value);
            }
            bound = r;
        }
    }

    private interface OnText { void on(String s); }

    private static TextWatcher watch(OnText cb) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void afterTextChanged(Editable s) { cb.on(s.toString().trim()); }
        };
    }
}
