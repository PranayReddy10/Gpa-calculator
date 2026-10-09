package com.gpa.calculator.core.calculators;

import com.gpa.calculator.core.CalcException;
import com.gpa.calculator.core.CalcInput;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.Category;
import com.gpa.calculator.core.Fmt;
import com.gpa.calculator.core.GradeMath;
import com.gpa.calculator.core.GradeScale;
import com.gpa.calculator.core.Inputs;
import com.gpa.calculator.core.SimpleCalc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AcademicCalculators {
    private AcademicCalculators() {}

    public static List<Calculator> all() {
        return Arrays.asList(new Sgpa(), new SgpaWithGrade(), new Cgpa(), new GpaToPercent(), new PercentToGpa(),
                new CgpaToPercent(), new PercentToCgpa(), new GpaScaleConvert(), new FinalGradeNeeded(),
                new Attendance(), new GradeFromMarks());
    }

    /** Base for the three dynamic-row grade calculators rendered by GradeCalculatorActivity. */
    public abstract static class GradeCalc extends SimpleCalc {
        protected GradeCalc(String id, String name, String desc, String keywords) {
            super(id, name, desc, Category.ACADEMIC, "ic_school", keywords);
        }

        @Override public Screen screen() { return Screen.GRADES; }
        @Override public List<CalcInput> inputs() { return Collections.emptyList(); }

        /** Row-based input is handled by the grades screen; the generic path is unused. */
        @Override public List<CalcResult> compute(Inputs in) {
            throw new CalcException("Use the grades screen for this calculator");
        }
    }

    public static final class Sgpa extends GradeCalc {
        public Sgpa() { super("sgpa", "SGPA", "Semester GPA from credits and grade points of each subject", "semester gpa credits grade points"); }
        @Override public String formula() { return "SGPA = Σ(Credits × Grade points) ÷ Σ(Credits)"; }
    }

    public static final class SgpaWithGrade extends GradeCalc {
        public SgpaWithGrade() { super("sgpa_grade", "SGPA with Grade", "Semester GPA using letter grades (O, A+, A …)", "letter grade semester gpa"); }
        @Override public String formula() { return "SGPA = Σ(Credits × Points of grade) ÷ Σ(Credits)"; }
    }

    public static final class Cgpa extends GradeCalc {
        public Cgpa() { super("cgpa", "CGPA", "Cumulative GPA from SGPA and credits of each semester", "cumulative gpa overall"); }
        @Override public String formula() { return "CGPA = Σ(SGPA × Semester credits) ÷ Σ(Semester credits)"; }
    }

    // ---------------------------------------------------------------------------------------------
    static final String[] SCALES = {"10-point × 9.5 (CBSE)", "10-point × 10", "4-point × 25", "5-point × 20"};

    static double[] scaleFor(int idx) {
        switch (idx) {
            case 0: return new double[]{10, 9.5};
            case 1: return new double[]{10, 10};
            case 2: return new double[]{4, 25};
            default: return new double[]{5, 20};
        }
    }

    public static final class GpaToPercent extends SimpleCalc {
        public GpaToPercent() {
            super("gpa_to_percent", "GPA to %", "Convert GPA to percentage on common scales", Category.ACADEMIC, "ic_school", "gpa percentage convert");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("gpa", "GPA").positive(), CalcInput.select("scale", "Scale", SCALES));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double[] s = scaleFor(in.selectIndex("scale"));
            double pct = GradeMath.gpaToPercent(in.num("gpa"), s[0], s[1]);
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Percentage", pct, 2, "", "%"));
            r.add(CalcResult.text("Grade", GradeScale.letterForPercent(pct)));
            r.add(CalcResult.text("Remark", GradeScale.remarkForPercent(pct)));
            return r;
        }

        @Override public String formula() { return "Percentage = GPA × multiplier of the selected scale"; }
    }

    public static final class PercentToGpa extends SimpleCalc {
        public PercentToGpa() {
            super("percent_to_gpa", "% to GPA", "Convert percentage to GPA on common scales", Category.ACADEMIC, "ic_school", "percentage gpa convert");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("pct", "Percentage").suffix("%").range(0, 100), CalcInput.select("scale", "Scale", SCALES));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double[] s = scaleFor(in.selectIndex("scale"));
            double gpa = Math.min(s[0], in.num("pct") / s[1]);
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("GPA", gpa, 2, "", " / " + Fmt.fmt(s[0], 0)));
            r.add(CalcResult.text("Grade", GradeScale.letterForPercent(in.num("pct"))));
            return r;
        }
    }

    public static final class CgpaToPercent extends SimpleCalc {
        public CgpaToPercent() {
            super("cgpa_to_percent", "CGPA to %", "Convert CGPA to percentage (CBSE and university scales)", Category.ACADEMIC, "ic_school", "cgpa percentage");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("cgpa", "CGPA").range(0, 10),
                    CalcInput.select("scale", "Formula", "CGPA × 9.5 (CBSE)", "CGPA × 10", "(CGPA − 0.75) × 10 (VTU / JNTU)", "(CGPA − 0.5) × 10"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double c = in.num("cgpa");
            double pct;
            switch (in.selectIndex("scale")) {
                case 0: pct = c * 9.5; break;
                case 1: pct = c * 10; break;
                case 2: pct = (c - 0.75) * 10; break;
                default: pct = (c - 0.5) * 10;
            }
            pct = Math.max(0, Math.min(100, pct));
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Percentage", pct, 2, "", "%"));
            r.add(CalcResult.text("Grade", GradeScale.letterForPercent(pct)));
            r.add(CalcResult.text("Remark", GradeScale.remarkForPercent(pct)));
            return r;
        }
    }

    public static final class PercentToCgpa extends SimpleCalc {
        public PercentToCgpa() {
            super("percent_to_cgpa", "% to CGPA", "Convert percentage to CGPA (10-point)", Category.ACADEMIC, "ic_school", "percentage cgpa");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("pct", "Percentage").suffix("%").range(0, 100),
                    CalcInput.select("scale", "Formula", "Percentage ÷ 9.5 (CBSE)", "Percentage ÷ 10", "Percentage ÷ 10 + 0.75 (VTU / JNTU)"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double p = in.num("pct");
            double c;
            switch (in.selectIndex("scale")) {
                case 0: c = p / 9.5; break;
                case 1: c = p / 10; break;
                default: c = p / 10 + 0.75;
            }
            c = Math.min(10, c);
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("CGPA", c, 2, "", " / 10"));
            r.add(CalcResult.text("Grade", GradeScale.letterForPercent(p)));
            return r;
        }
    }

    public static final class GpaScaleConvert extends SimpleCalc {
        public GpaScaleConvert() {
            super("gpa_scale", "GPA scale converter", "Convert between 10-point, 5-point and 4-point GPA", Category.ACADEMIC, "ic_swap", "10 point 4 point convert gpa scale");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("gpa", "GPA").positive(),
                    CalcInput.select("from", "From scale", "10-point", "5-point", "4-point"),
                    CalcInput.select("to", "To scale", "4-point", "5-point", "10-point"));
        }

        private static double max(String s) { return s.startsWith("10") ? 10 : s.startsWith("5") ? 5 : 4; }

        @Override public List<CalcResult> compute(Inputs in) {
            double g = in.num("gpa");
            double from = max(in.select("from")), to = max(in.select("to"));
            if (g > from) throw new CalcException("gpa", "GPA cannot exceed " + Fmt.fmt(from, 0));
            double out = g / from * to;
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("GPA on " + Fmt.fmt(to, 0) + "-point scale", out, 2, "", ""));
            r.add(CalcResult.num("Equivalent percentage", g / from * 100, 2, "", "%"));
            return r;
        }
    }

    public static final class FinalGradeNeeded extends SimpleCalc {
        public FinalGradeNeeded() {
            super("final_grade", "Final exam grade needed", "Score needed on the final exam to reach your target grade", Category.ACADEMIC, "ic_checklist", "final exam needed target");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("current", "Current grade").suffix("%").range(0, 100),
                    CalcInput.number("target", "Target grade").suffix("%").range(0, 100),
                    CalcInput.number("weight", "Final exam weight").suffix("%").range(0.01, 100));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double c = in.num("current"), t = in.num("target"), w = in.num("weight") / 100;
            double need = (t - c * (1 - w)) / w;
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Needed on final", need, 2, "", "%"));
            r.add(CalcResult.text("Verdict", need <= 0 ? "Already achieved" : need > 100 ? "Not possible with this weight" : need > 90 ? "Very hard" : need > 70 ? "Hard" : "Achievable"));
            return r;
        }

        @Override public String formula() { return "Needed = (Target − Current × (1 − Weight)) ÷ Weight"; }
    }

    public static final class Attendance extends SimpleCalc {
        public Attendance() {
            super("attendance", "Attendance", "Current attendance %, classes you can skip or must attend", Category.ACADEMIC, "ic_checklist", "attendance bunk classes present absent");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.integer("attended", "Classes attended").positive(),
                    CalcInput.integer("total", "Total classes held").positive(),
                    CalcInput.number("required", "Required attendance").suffix("%").range(1, 100).def("75"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            int a = in.integer("attended"), t = in.integer("total");
            double req = in.num("required") / 100;
            if (t <= 0) throw new CalcException("total", "Total classes must be greater than 0");
            if (a > t) throw new CalcException("attended", "Attended cannot exceed total classes");
            double pct = (double) a / t * 100;
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Current attendance", pct, 2, "", "%"));
            if (pct / 100 >= req) {
                // how many classes can be skipped while staying >= req: (a) / (t + x) >= req
                int canSkip = (int) Math.floor(a / req - t);
                r.add(CalcResult.num("Classes you can skip", Math.max(0, canSkip), 0));
                r.add(CalcResult.text("Status", "Safe"));
            } else {
                // classes to attend consecutively: (a + x) / (t + x) >= req
                int need = (int) Math.ceil((req * t - a) / (1 - req));
                r.add(CalcResult.num("Classes to attend in a row", Math.max(0, need), 0));
                r.add(CalcResult.text("Status", "Below required"));
            }
            return r;
        }
    }

    public static final class GradeFromMarks extends SimpleCalc {
        public GradeFromMarks() {
            super("grade_from_marks", "Grade from marks", "Letter grade and grade points for a subject score", Category.ACADEMIC, "ic_school", "letter grade points subject");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("marks", "Marks obtained").positive(),
                    CalcInput.number("max", "Maximum marks").positive().def("100"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double m = in.num("marks"), mx = in.num("max");
            if (mx <= 0) throw new CalcException("max", "Maximum marks must be greater than 0");
            if (m > mx) throw new CalcException("marks", "Marks cannot exceed maximum");
            double pct = m / mx * 100;
            String letter = GradeScale.letterForPercent(pct);
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primaryText("Grade", letter));
            r.add(CalcResult.num("Grade points (10-point)", GradeScale.TEN_POINT.pointsFor(letter), 0));
            r.add(CalcResult.num("Percentage", pct, 2, "", "%"));
            r.add(CalcResult.text("Remark", GradeScale.remarkForPercent(pct)));
            return r;
        }
    }
}
