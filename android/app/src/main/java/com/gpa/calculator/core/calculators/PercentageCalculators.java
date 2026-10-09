package com.gpa.calculator.core.calculators;

import com.gpa.calculator.core.CalcException;
import com.gpa.calculator.core.CalcInput;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.Category;
import com.gpa.calculator.core.Fmt;
import com.gpa.calculator.core.GradeScale;
import com.gpa.calculator.core.Inputs;
import com.gpa.calculator.core.SimpleCalc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PercentageCalculators {
    private PercentageCalculators() {}

    public static List<Calculator> all() {
        return Arrays.asList(new MarksPercent(), new RatioToPercent(), new GeneralPercent(), new PercentChange(),
                new Discount(), new PercentDifference(), new MarksNeeded());
    }

    // ---------------------------------------------------------------------------------------------
    public static final class MarksPercent extends SimpleCalc {
        public MarksPercent() {
            super("marks_percent", "Marks %", "Percentage from marks obtained and total marks",
                    Category.PERCENTAGE, "ic_percent", "marks percentage exam score grade");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(
                    CalcInput.number("obtained", "Marks obtained").positive(),
                    CalcInput.number("total", "Total marks").positive());
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double o = in.num("obtained"), t = in.num("total");
            if (t <= 0) throw new CalcException("total", "Total marks must be greater than 0");
            if (o > t) throw new CalcException("obtained", "Marks obtained cannot exceed total marks");
            double pct = o / t * 100;
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Percentage", pct, 2, "", "%"));
            r.add(CalcResult.text("Grade", GradeScale.letterForPercent(pct)));
            r.add(CalcResult.text("Remark", GradeScale.remarkForPercent(pct)));
            r.add(CalcResult.num("Marks lost", t - o, 2));
            return r;
        }

        @Override public String formula() { return "Percentage = Obtained ÷ Total × 100"; }
    }

    // ---------------------------------------------------------------------------------------------
    public static final class RatioToPercent extends SimpleCalc {
        public RatioToPercent() {
            super("ratio_to_percent", "Ratio to %", "Convert a ratio a : b into a percentage",
                    Category.PERCENTAGE, "ic_percent", "ratio fraction proportion");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(
                    CalcInput.number("a", "First value (a)"),
                    CalcInput.number("b", "Second value (b)"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double a = in.num("a"), b = in.num("b");
            if (b == 0) throw new CalcException("b", "Second value cannot be 0");
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("a ÷ b as percentage", a / b * 100, 2, "", "%"));
            r.add(CalcResult.num("Share of a in (a + b)", (a + b) == 0 ? 0 : a / (a + b) * 100, 2, "", "%"));
            r.add(CalcResult.num("Share of b in (a + b)", (a + b) == 0 ? 0 : b / (a + b) * 100, 2, "", "%"));
            r.add(CalcResult.num("Decimal value", a / b, 4));
            if (a == Math.floor(a) && b == Math.floor(b) && a != 0) {
                long g = gcd((long) Math.abs(a), (long) Math.abs(b));
                r.add(CalcResult.text("Simplified ratio", (long) a / g + " : " + (long) b / g));
            }
            return r;
        }

        private static long gcd(long x, long y) { return y == 0 ? x : gcd(y, x % y); }

        @Override public String formula() { return "Percentage = a ÷ b × 100"; }
    }

    // ---------------------------------------------------------------------------------------------
    public static final class GeneralPercent extends SimpleCalc {
        public GeneralPercent() {
            super("general_percent", "General %", "What is X% of Y, X is what % of Y, and more",
                    Category.PERCENTAGE, "ic_percent", "percent of what is");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(
                    CalcInput.select("mode", "Question", "What is X% of Y?", "X is what % of Y?", "X is Y% of what?"),
                    CalcInput.number("x", "X"),
                    CalcInput.number("y", "Y"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double x = in.num("x"), y = in.num("y");
            List<CalcResult> r = new ArrayList<>();
            switch (in.selectIndex("mode")) {
                case 0:
                    r.add(CalcResult.primary(Fmt.fmt(x, 2) + "% of " + Fmt.fmt(y, 2), x / 100 * y, 4, "", ""));
                    break;
                case 1:
                    if (y == 0) throw new CalcException("y", "Y cannot be 0");
                    r.add(CalcResult.primary(Fmt.fmt(x, 2) + " is this % of " + Fmt.fmt(y, 2), x / y * 100, 4, "", "%"));
                    break;
                default:
                    if (y == 0) throw new CalcException("y", "Y cannot be 0");
                    r.add(CalcResult.primary(Fmt.fmt(x, 2) + " is " + Fmt.fmt(y, 2) + "% of", x * 100 / y, 4, "", ""));
            }
            return r;
        }
    }

    // ---------------------------------------------------------------------------------------------
    public static final class PercentChange extends SimpleCalc {
        public PercentChange() {
            super("percent_change", "Change in %", "Percentage increase or decrease between two values",
                    Category.PERCENTAGE, "ic_trending", "increase decrease growth change");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(
                    CalcInput.number("old", "Old value"),
                    CalcInput.number("new", "New value"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double o = in.num("old"), n = in.num("new");
            if (o == 0) throw new CalcException("old", "Old value cannot be 0");
            double change = (n - o) / Math.abs(o) * 100;
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary(change >= 0 ? "Increase" : "Decrease", Math.abs(change), 2, "", "%"));
            r.add(CalcResult.num("Absolute change", n - o, 2));
            r.add(CalcResult.num("Growth factor", o == 0 ? 0 : n / o, 4, "", "×"));
            return r;
        }

        @Override public String formula() { return "Change % = (New − Old) ÷ |Old| × 100"; }
    }

    // ---------------------------------------------------------------------------------------------
    public static final class Discount extends SimpleCalc {
        public Discount() {
            super("discount", "Discount %", "Final price after discount and the amount you save",
                    Category.PERCENTAGE, "ic_offer", "sale offer price save coupon");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(
                    CalcInput.number("price", "Original price").positive(),
                    CalcInput.number("discount", "Discount").suffix("%").range(0, 100),
                    CalcInput.number("extra", "Extra discount on discounted price").suffix("%").range(0, 100).optional(),
                    CalcInput.number("tax", "Tax on final price").suffix("%").range(0, 100).optional());
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double p = in.num("price"), d = in.num("discount"), e = in.num("extra"), t = in.num("tax");
            double afterFirst = p * (1 - d / 100);
            double afterExtra = afterFirst * (1 - e / 100);
            double tax = afterExtra * t / 100;
            double finalPrice = afterExtra + tax;
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Final price", finalPrice, 2, "", ""));
            r.add(CalcResult.num("You save", p - afterExtra, 2));
            r.add(CalcResult.num("Effective discount", p == 0 ? 0 : (p - afterExtra) / p * 100, 2, "", "%"));
            if (t > 0) r.add(CalcResult.num("Tax added", tax, 2));
            return r;
        }
    }

    // ---------------------------------------------------------------------------------------------
    public static final class PercentDifference extends SimpleCalc {
        public PercentDifference() {
            super("percent_difference", "% Difference", "Percentage difference between two values (relative to their average)",
                    Category.PERCENTAGE, "ic_percent", "difference compare");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("a", "Value A"), CalcInput.number("b", "Value B"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double a = in.num("a"), b = in.num("b");
            double avg = (Math.abs(a) + Math.abs(b)) / 2;
            if (avg == 0) throw new CalcException("Both values cannot be 0");
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Percentage difference", Math.abs(a - b) / avg * 100, 2, "", "%"));
            r.add(CalcResult.num("Absolute difference", Math.abs(a - b), 2));
            r.add(CalcResult.num("A relative to B", b == 0 ? 0 : (a - b) / Math.abs(b) * 100, 2, "", "%"));
            return r;
        }

        @Override public String formula() { return "|A − B| ÷ ((|A| + |B|) ÷ 2) × 100"; }
    }

    // ---------------------------------------------------------------------------------------------
    public static final class MarksNeeded extends SimpleCalc {
        public MarksNeeded() {
            super("marks_needed", "Marks needed", "Marks you must score to reach a target percentage",
                    Category.PERCENTAGE, "ic_checklist", "target required score");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(
                    CalcInput.number("target", "Target percentage").suffix("%").range(0, 100),
                    CalcInput.number("total", "Total marks of the exam").positive(),
                    CalcInput.number("scored", "Marks already scored (optional)").positive().optional(),
                    CalcInput.number("scored_of", "Out of (for marks already scored)").positive().optional());
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double target = in.num("target"), total = in.num("total");
            double scored = in.num("scored"), scoredOf = in.num("scored_of");
            if (total <= 0) throw new CalcException("total", "Total marks must be greater than 0");
            double needTotal = target / 100 * total;
            List<CalcResult> r = new ArrayList<>();
            if (scoredOf > 0) {
                if (scoredOf > total) throw new CalcException("scored_of", "Cannot exceed total marks");
                double remaining = total - scoredOf;
                double need = needTotal - scored;
                r.add(CalcResult.primary("Needed in remaining " + Fmt.fmt(remaining, 0) + " marks", Math.max(0, need), 2, "", ""));
                r.add(CalcResult.num("Required % in remaining", remaining == 0 ? 0 : Math.max(0, need) / remaining * 100, 2, "", "%"));
                r.add(CalcResult.text("Achievable", need <= remaining ? "Yes" : "No, target is out of reach"));
            } else {
                r.add(CalcResult.primary("Marks required", needTotal, 2, "", ""));
            }
            return r;
        }
    }
}
