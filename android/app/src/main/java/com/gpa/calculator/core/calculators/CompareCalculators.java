package com.gpa.calculator.core.calculators;

import com.gpa.calculator.core.CalcException;
import com.gpa.calculator.core.CalcInput;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.Category;
import com.gpa.calculator.core.EmiMath;
import com.gpa.calculator.core.Fmt;
import com.gpa.calculator.core.Inputs;
import com.gpa.calculator.core.SimpleCalc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Side-by-side comparison calculators. */
public final class CompareCalculators {
    private CompareCalculators() {}

    // ============================================================================ Unit price
    /** Units grouped by dimension; factor converts to the base unit (kg, L, piece). */
    static final String[] UNITS = {"g", "kg", "oz", "lb", "ml", "L", "fl oz", "pcs"};
    private static final double[] FACTOR = {0.001, 1, 0.028349523125, 0.45359237, 0.001, 1, 0.0295735295625, 1};
    private static final int[] DIMENSION = {0, 0, 0, 0, 1, 1, 1, 2};
    private static final String[] BASE_LABEL = {"kg", "L", "piece"};
    private static final String[] SMALL_LABEL = {"100 g", "100 ml", "10 pieces"};
    private static final double[] SMALL_FACTOR = {0.1, 0.1, 10};

    static int unitIndex(String u) {
        for (int i = 0; i < UNITS.length; i++) if (UNITS[i].equals(u)) return i;
        return 1;
    }

    public static final class UnitPrice extends SimpleCalc {
        public UnitPrice() {
            super("unit_price", "Unit price", "Compare pack sizes and find the best value per kg, litre or piece",
                    Category.EVERYDAY, "ic_shopping", "unit price best value compare grocery shopping per kg per litre bulk");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(
                    CalcInput.number("a_price", "Price").positive().section("Item A"),
                    CalcInput.number("a_qty", "Quantity").positive(),
                    CalcInput.select("a_unit", "Unit", UNITS).def("g"),
                    CalcInput.number("b_price", "Price").positive().section("Item B"),
                    CalcInput.number("b_qty", "Quantity").positive(),
                    CalcInput.select("b_unit", "Unit", UNITS).def("g"),
                    CalcInput.number("c_price", "Price").positive().optional().section("Item C (optional)"),
                    CalcInput.number("c_qty", "Quantity").positive().optional(),
                    CalcInput.select("c_unit", "Unit", UNITS).def("g"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            String[] names = {"Item A", "Item B", "Item C"};
            String[] p = {"a", "b", "c"};
            List<Integer> idx = new ArrayList<>();
            double[] perBase = new double[3];
            int dim = -1;
            for (int i = 0; i < 3; i++) {
                boolean optional = i == 2;
                if (optional && !in.has("c_price") && !in.has("c_qty")) continue;
                if (optional && (!in.has("c_price") || !in.has("c_qty"))) throw new CalcException(in.has("c_price") ? "c_qty" : "c_price", "Enter both price and quantity for Item C");
                double price = in.num(p[i] + "_price"), qty = in.num(p[i] + "_qty");
                if (qty <= 0) throw new CalcException(p[i] + "_qty", "Quantity must be greater than 0");
                int u = unitIndex(in.select(p[i] + "_unit"));
                if (dim == -1) dim = DIMENSION[u];
                else if (DIMENSION[u] != dim) throw new CalcException(p[i] + "_unit", "Use the same kind of unit for every item (weight, volume or pieces)");
                perBase[i] = price / (qty * FACTOR[u]);
                idx.add(i);
            }
            int best = idx.get(0), worst = idx.get(0);
            for (int i : idx) {
                if (perBase[i] < perBase[best]) best = i;
                if (perBase[i] > perBase[worst]) worst = i;
            }
            List<CalcResult> r = new ArrayList<>();
            boolean allEqual = Math.abs(perBase[best] - perBase[worst]) < 1e-9;
            r.add(CalcResult.primaryText("Best value", allEqual ? "Same price per unit" : names[best]));
            for (int i : idx) {
                r.add(CalcResult.num(names[i] + " · per " + BASE_LABEL[dim], perBase[i], 2));
                r.add(CalcResult.num(names[i] + " · per " + SMALL_LABEL[dim], perBase[i] * SMALL_FACTOR[dim], 2));
            }
            if (!allEqual) {
                r.add(CalcResult.num("Saving vs " + names[worst], (perBase[worst] - perBase[best]) / perBase[worst] * 100, 1, "", "%"));
                r.add(CalcResult.num(names[worst] + " costs more by", (perBase[worst] - perBase[best]) / perBase[best] * 100, 1, "", "%"));
            }
            return r;
        }

        @Override public String formula() { return "Unit price = Price ÷ Quantity (converted to kg, L or pieces)"; }
    }

    // ============================================================================ Loan comparison
    public static final class LoanCompare extends SimpleCalc {
        public LoanCompare() {
            super("loan_compare", "Loan comparison", "Compare two loan offers by EMI, total interest and fees",
                    Category.FINANCE, "ic_compare", "compare loan offer bank emi interest processing fee cheaper");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(
                    CalcInput.number("a_amount", "Loan amount").positive().section("Loan A"),
                    CalcInput.number("a_rate", "Interest rate (per year)").suffix("%").range(0, 100),
                    CalcInput.number("a_years", "Tenure (years)").range(0.1, 50),
                    CalcInput.number("a_fee", "Processing fee").suffix("%").range(0, 100).optional(),
                    CalcInput.number("b_amount", "Loan amount (blank = same as A)").positive().optional().section("Loan B"),
                    CalcInput.number("b_rate", "Interest rate (per year)").suffix("%").range(0, 100),
                    CalcInput.number("b_years", "Tenure (years)").range(0.1, 50),
                    CalcInput.number("b_fee", "Processing fee").suffix("%").range(0, 100).optional());
        }

        static final class Offer {
            final double emi, interest, fee, total;
            Offer(double amount, double rate, double years, double feePct) {
                int months = (int) Math.round(years * 12);
                if (months < 1) throw new CalcException("Tenure must be at least 1 month");
                EmiMath.Result r = EmiMath.compute(amount, rate, months);
                emi = r.emi;
                interest = r.totalInterest;
                fee = amount * feePct / 100;
                total = interest + fee;
            }
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double aAmount = in.num("a_amount");
            double bAmount = in.has("b_amount") ? in.num("b_amount") : aAmount;
            if (aAmount <= 0) throw new CalcException("a_amount", "Loan amount must be greater than 0");
            if (bAmount <= 0) throw new CalcException("b_amount", "Loan amount must be greater than 0");
            Offer a = new Offer(aAmount, in.num("a_rate"), in.num("a_years"), in.num("a_fee"));
            Offer b = new Offer(bAmount, in.num("b_rate"), in.num("b_years"), in.num("b_fee"));
            double diff = Math.abs(a.total - b.total);
            List<CalcResult> r = new ArrayList<>();
            if (diff < 0.5) r.add(CalcResult.primaryText("Result", "Both loans cost the same"));
            else r.add(CalcResult.primary((a.total < b.total ? "Loan A" : "Loan B") + " is cheaper — you save", diff, 0, "", ""));
            r.add(CalcResult.num("Loan A · monthly EMI", a.emi, 2));
            r.add(CalcResult.num("Loan B · monthly EMI", b.emi, 2));
            r.add(CalcResult.num("Loan A · total interest", a.interest, 0));
            r.add(CalcResult.num("Loan B · total interest", b.interest, 0));
            if (a.fee > 0 || b.fee > 0) {
                r.add(CalcResult.num("Loan A · processing fee", a.fee, 0));
                r.add(CalcResult.num("Loan B · processing fee", b.fee, 0));
            }
            r.add(CalcResult.num("Loan A · total cost of borrowing", a.total, 0));
            r.add(CalcResult.num("Loan B · total cost of borrowing", b.total, 0));
            r.add(CalcResult.num("EMI difference per month", Math.abs(a.emi - b.emi), 2));
            if (aAmount != bAmount) r.add(CalcResult.text("Note", "Amounts differ (" + Fmt.fmt(aAmount, 0) + " vs " + Fmt.fmt(bAmount, 0) + "); compare cost per 1 lakh borrowed too"));
            return r;
        }

        @Override public String formula() { return "Total cost = total interest + processing fee; EMI uses the standard reducing-balance formula"; }
    }
}
