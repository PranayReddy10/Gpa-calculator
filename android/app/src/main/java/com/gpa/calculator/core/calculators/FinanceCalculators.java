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

public final class FinanceCalculators {
    private FinanceCalculators() {}

    public static List<Calculator> all() {
        return Arrays.asList(new Emi(), new SimpleInterest(), new CompoundInterest(), new Sip(), new Lumpsum(), new Fd(), new Rd(),
                new GstTax(), new Tip(), new ProfitLoss(), new Inflation(), new Roi(), new Salary(), new LoanAffordability());
    }

    // ---------------------------------------------------------------------------------------------
    public static final class Emi extends SimpleCalc {
        public Emi() {
            super("emi", "EMI / Loan", "Monthly instalment, total interest and full repayment schedule",
                    Category.FINANCE, "ic_money", "loan emi home car personal interest schedule amortization");
        }

        @Override public Screen screen() { return Screen.EMI; }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("principal", "Loan amount").positive(),
                    CalcInput.number("rate", "Interest rate (per year)").suffix("%").range(0, 100),
                    CalcInput.number("tenure", "Tenure").positive(),
                    CalcInput.select("unit", "Tenure unit", "Years", "Months"));
        }

        public static int months(Inputs in) {
            double t = in.num("tenure");
            int months = in.selectIndex("unit") == 0 ? (int) Math.round(t * 12) : (int) Math.round(t);
            if (months <= 0) throw new CalcException("tenure", "Tenure must be at least 1 month");
            if (months > 600) throw new CalcException("tenure", "Tenure cannot exceed 50 years");
            return months;
        }

        public EmiMath.Result full(Inputs in) {
            return EmiMath.compute(in.num("principal"), in.num("rate"), months(in));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            EmiMath.Result res = full(in);
            double p = in.num("principal");
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Monthly EMI", res.emi, 2, "", ""));
            r.add(CalcResult.num("Principal", p, 2));
            r.add(CalcResult.num("Total interest", res.totalInterest, 2));
            r.add(CalcResult.num("Total payment", res.totalPayment, 2));
            r.add(CalcResult.num("Interest share", res.totalPayment == 0 ? 0 : res.totalInterest / res.totalPayment * 100, 2, "", "%"));
            return r;
        }

        @Override public String formula() { return "EMI = P × r × (1 + r)ⁿ ÷ ((1 + r)ⁿ − 1), r = rate ÷ 12 ÷ 100"; }
    }

    // ---------------------------------------------------------------------------------------------
    public static final class SimpleInterest extends SimpleCalc {
        public SimpleInterest() {
            super("simple_interest", "Simple interest", "Interest and total amount at a flat rate", Category.FINANCE, "ic_money", "si interest flat");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("p", "Principal").positive(),
                    CalcInput.number("r", "Rate (per year)").suffix("%").positive(),
                    CalcInput.number("t", "Time").positive(),
                    CalcInput.select("unit", "Time unit", "Years", "Months", "Days"));
        }

        static double years(Inputs in, String key, String unitKey) {
            double t = in.num(key);
            switch (in.selectIndex(unitKey)) {
                case 1: return t / 12;
                case 2: return t / 365;
                default: return t;
            }
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double p = in.num("p"), r = in.num("r"), t = years(in, "t", "unit");
            double si = p * r * t / 100;
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary("Interest", si, 2, "", ""));
            out.add(CalcResult.num("Total amount", p + si, 2));
            out.add(CalcResult.num("Monthly interest", t == 0 ? 0 : si / (t * 12), 2));
            return out;
        }

        @Override public String formula() { return "SI = P × R × T ÷ 100"; }
    }

    // ---------------------------------------------------------------------------------------------
    static final String[] COMPOUNDING = {"Yearly", "Half-yearly", "Quarterly", "Monthly", "Daily"};

    static int periodsPerYear(int idx) {
        switch (idx) {
            case 1: return 2;
            case 2: return 4;
            case 3: return 12;
            case 4: return 365;
            default: return 1;
        }
    }

    public static final class CompoundInterest extends SimpleCalc {
        public CompoundInterest() {
            super("compound_interest", "Compound interest", "Growth with yearly, quarterly or monthly compounding", Category.FINANCE, "ic_trending", "ci compound growth");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("p", "Principal").positive(),
                    CalcInput.number("r", "Rate (per year)").suffix("%").positive(),
                    CalcInput.number("t", "Time").positive(),
                    CalcInput.select("unit", "Time unit", "Years", "Months"),
                    CalcInput.select("n", "Compounding", COMPOUNDING));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double p = in.num("p"), r = in.num("r") / 100;
            double t = in.selectIndex("unit") == 0 ? in.num("t") : in.num("t") / 12;
            int n = periodsPerYear(in.selectIndex("n"));
            double amount = p * Math.pow(1 + r / n, n * t);
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary("Maturity amount", amount, 2, "", ""));
            out.add(CalcResult.num("Interest earned", amount - p, 2));
            out.add(CalcResult.num("Effective annual rate", (Math.pow(1 + r / n, n) - 1) * 100, 3, "", "%"));
            return out;
        }

        @Override public String formula() { return "A = P × (1 + r/n)^(n × t)"; }
    }

    // ---------------------------------------------------------------------------------------------
    public static final class Sip extends SimpleCalc {
        public Sip() {
            super("sip", "SIP", "Future value of a monthly investment plan", Category.FINANCE, "ic_trending", "mutual fund sip monthly invest");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("amount", "Monthly investment").positive(),
                    CalcInput.number("rate", "Expected return (per year)").suffix("%").positive().def("12"),
                    CalcInput.number("years", "Duration (years)").range(0.1, 60));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double a = in.num("amount"), i = in.num("rate") / 12 / 100;
            int n = (int) Math.round(in.num("years") * 12);
            double fv = i == 0 ? a * n : a * ((Math.pow(1 + i, n) - 1) / i) * (1 + i);
            double invested = a * n;
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary("Expected value", fv, 0, "", ""));
            out.add(CalcResult.num("Total invested", invested, 0));
            out.add(CalcResult.num("Estimated returns", fv - invested, 0));
            out.add(CalcResult.num("Wealth gain", invested == 0 ? 0 : (fv - invested) / invested * 100, 2, "", "%"));
            return out;
        }

        @Override public String formula() { return "FV = A × ((1 + i)ⁿ − 1) ÷ i × (1 + i)"; }
    }

    public static final class Lumpsum extends SimpleCalc {
        public Lumpsum() {
            super("lumpsum", "Lumpsum investment", "Future value of a one-time investment", Category.FINANCE, "ic_trending", "one time invest growth");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("amount", "Investment amount").positive(),
                    CalcInput.number("rate", "Expected return (per year)").suffix("%").positive().def("12"),
                    CalcInput.number("years", "Duration (years)").range(0.1, 60));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double a = in.num("amount"), r = in.num("rate") / 100, y = in.num("years");
            double fv = a * Math.pow(1 + r, y);
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary("Expected value", fv, 0, "", ""));
            out.add(CalcResult.num("Estimated returns", fv - a, 0));
            out.add(CalcResult.num("Growth", a == 0 ? 0 : fv / a, 2, "", "×"));
            return out;
        }
    }

    public static final class Fd extends SimpleCalc {
        public Fd() {
            super("fd", "Fixed deposit", "FD maturity value and interest", Category.FINANCE, "ic_money", "fixed deposit bank fd maturity");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("p", "Deposit amount").positive(),
                    CalcInput.number("r", "Interest rate (per year)").suffix("%").positive(),
                    CalcInput.number("t", "Tenure").positive(),
                    CalcInput.select("unit", "Tenure unit", "Years", "Months", "Days"),
                    CalcInput.select("n", "Compounding", "Quarterly", "Monthly", "Half-yearly", "Yearly", "Simple interest"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double p = in.num("p"), r = in.num("r") / 100, t = SimpleInterest.years(in, "t", "unit");
            int idx = in.selectIndex("n");
            double amount;
            if (idx == 4) amount = p * (1 + r * t);
            else {
                int n = idx == 0 ? 4 : idx == 1 ? 12 : idx == 2 ? 2 : 1;
                amount = p * Math.pow(1 + r / n, n * t);
            }
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary("Maturity amount", amount, 2, "", ""));
            out.add(CalcResult.num("Interest earned", amount - p, 2));
            out.add(CalcResult.num("Deposit", p, 2));
            return out;
        }
    }

    public static final class Rd extends SimpleCalc {
        public Rd() {
            super("rd", "Recurring deposit", "RD maturity with quarterly compounding", Category.FINANCE, "ic_money", "recurring deposit rd monthly");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("p", "Monthly deposit").positive(),
                    CalcInput.number("r", "Interest rate (per year)").suffix("%").positive(),
                    CalcInput.integer("months", "Tenure (months)").range(1, 600));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double p = in.num("p"), r = in.num("r") / 100;
            int n = in.integer("months");
            // Standard Indian bank RD: each instalment compounds quarterly for the months it stays invested.
            double maturity = 0;
            for (int k = 1; k <= n; k++) {
                double monthsInvested = n - k + 1;
                maturity += p * Math.pow(1 + r / 4, 4 * monthsInvested / 12.0);
            }
            double invested = p * n;
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary("Maturity amount", maturity, 2, "", ""));
            out.add(CalcResult.num("Total deposited", invested, 2));
            out.add(CalcResult.num("Interest earned", maturity - invested, 2));
            return out;
        }
    }

    // ---------------------------------------------------------------------------------------------
    public static final class GstTax extends SimpleCalc {
        public GstTax() {
            super("tax", "Tax / GST", "Add or remove GST, VAT or sales tax from an amount", Category.FINANCE, "ic_receipt", "gst vat tax inclusive exclusive cgst sgst");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("amount", "Amount").positive(),
                    CalcInput.select("rate", "Tax rate", "18%", "12%", "5%", "28%", "3%", "0.25%", "Custom"),
                    CalcInput.number("custom", "Custom rate").suffix("%").range(0, 100).optional(),
                    CalcInput.select("mode", "Mode", "Add tax (amount is before tax)", "Remove tax (amount includes tax)"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double amount = in.num("amount");
            String sel = in.select("rate");
            double rate = sel.equals("Custom") ? in.num("custom") : Double.parseDouble(sel.replace("%", ""));
            double net, tax, gross;
            if (in.selectIndex("mode") == 0) {
                net = amount;
                tax = amount * rate / 100;
                gross = net + tax;
            } else {
                gross = amount;
                net = amount / (1 + rate / 100);
                tax = gross - net;
            }
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary(in.selectIndex("mode") == 0 ? "Total with tax" : "Amount before tax", in.selectIndex("mode") == 0 ? gross : net, 2, "", ""));
            out.add(CalcResult.num("Tax (" + Fmt.fmt(rate, 2) + "%)", tax, 2));
            out.add(CalcResult.num("CGST / SGST (each)", tax / 2, 2));
            out.add(CalcResult.num(in.selectIndex("mode") == 0 ? "Net amount" : "Gross amount", in.selectIndex("mode") == 0 ? net : gross, 2));
            return out;
        }
    }

    public static final class Tip extends SimpleCalc {
        public Tip() {
            super("tip", "Tip & split", "Tip amount, total bill and per-person share", Category.FINANCE, "ic_restaurant", "tip bill split restaurant");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("bill", "Bill amount").positive(),
                    CalcInput.number("tip", "Tip").suffix("%").range(0, 100).def("10"),
                    CalcInput.integer("people", "Split between").range(1, 1000).def("1"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double bill = in.num("bill"), tipPct = in.num("tip");
            int people = in.integer("people");
            double tip = bill * tipPct / 100, total = bill + tip;
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary("Total bill", total, 2, "", ""));
            out.add(CalcResult.num("Tip amount", tip, 2));
            if (people > 1) {
                out.add(CalcResult.num("Each person pays", total / people, 2));
                out.add(CalcResult.num("Tip per person", tip / people, 2));
            }
            return out;
        }
    }

    public static final class ProfitLoss extends SimpleCalc {
        public ProfitLoss() {
            super("profit_loss", "Profit / Loss", "Profit or loss and its percentage from cost and selling price", Category.FINANCE, "ic_trending", "profit loss margin markup cost selling");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("cp", "Cost price").positive(),
                    CalcInput.number("sp", "Selling price").positive(),
                    CalcInput.integer("qty", "Quantity").range(1, 1_000_000).def("1"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double cp = in.num("cp"), sp = in.num("sp");
            int q = in.integer("qty");
            if (cp <= 0) throw new CalcException("cp", "Cost price must be greater than 0");
            double diff = (sp - cp) * q;
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary(diff >= 0 ? "Profit" : "Loss", Math.abs(diff), 2, "", ""));
            out.add(CalcResult.num((diff >= 0 ? "Profit" : "Loss") + " percentage", Math.abs(sp - cp) / cp * 100, 2, "", "%"));
            out.add(CalcResult.num("Margin on selling price", sp == 0 ? 0 : (sp - cp) / sp * 100, 2, "", "%"));
            out.add(CalcResult.num("Total revenue", sp * q, 2));
            return out;
        }
    }

    public static final class Inflation extends SimpleCalc {
        public Inflation() {
            super("inflation", "Inflation", "Future cost of today's money and its purchasing power", Category.FINANCE, "ic_trending", "inflation purchasing power future cost");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("amount", "Amount today").positive(),
                    CalcInput.number("rate", "Inflation rate (per year)").suffix("%").range(0, 100).def("6"),
                    CalcInput.number("years", "Years").range(0, 100));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double a = in.num("amount"), r = in.num("rate") / 100, y = in.num("years");
            double future = a * Math.pow(1 + r, y);
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary("Cost after " + Fmt.fmt(y, 1) + " years", future, 2, "", ""));
            out.add(CalcResult.num("Purchasing power of amount then", a / Math.pow(1 + r, y), 2));
            out.add(CalcResult.num("Total price rise", a == 0 ? 0 : (future - a) / a * 100, 2, "", "%"));
            return out;
        }
    }

    public static final class Roi extends SimpleCalc {
        public Roi() {
            super("roi", "ROI / CAGR", "Return on investment and annualised growth rate", Category.FINANCE, "ic_trending", "roi cagr return annualised");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("invested", "Amount invested").positive(),
                    CalcInput.number("returned", "Final value").positive(),
                    CalcInput.number("years", "Holding period (years)").range(0, 100).optional());
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double i = in.num("invested"), f = in.num("returned"), y = in.num("years");
            if (i <= 0) throw new CalcException("invested", "Amount invested must be greater than 0");
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary("ROI", (f - i) / i * 100, 2, "", "%"));
            out.add(CalcResult.num("Net gain", f - i, 2));
            if (y > 0) out.add(CalcResult.num("CAGR (per year)", (Math.pow(f / i, 1 / y) - 1) * 100, 2, "", "%"));
            return out;
        }
    }

    public static final class Salary extends SimpleCalc {
        public Salary() {
            super("salary", "Salary breakdown", "Annual pay broken into monthly, weekly, daily and hourly", Category.FINANCE, "ic_money", "salary ctc hourly monthly wage");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("amount", "Salary amount").positive(),
                    CalcInput.select("period", "Period", "Per year", "Per month", "Per week", "Per day", "Per hour"),
                    CalcInput.number("hours", "Working hours per day").range(1, 24).def("8"),
                    CalcInput.number("days", "Working days per week").range(1, 7).def("5"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double a = in.num("amount"), h = in.num("hours"), d = in.num("days");
            double weeksPerYear = 52;
            double yearly;
            switch (in.selectIndex("period")) {
                case 1: yearly = a * 12; break;
                case 2: yearly = a * weeksPerYear; break;
                case 3: yearly = a * d * weeksPerYear; break;
                case 4: yearly = a * h * d * weeksPerYear; break;
                default: yearly = a;
            }
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary("Per month", yearly / 12, 2, "", ""));
            out.add(CalcResult.num("Per year", yearly, 2));
            out.add(CalcResult.num("Per week", yearly / weeksPerYear, 2));
            out.add(CalcResult.num("Per day", yearly / weeksPerYear / d, 2));
            out.add(CalcResult.num("Per hour", yearly / weeksPerYear / d / h, 2));
            return out;
        }
    }

    public static final class LoanAffordability extends SimpleCalc {
        public LoanAffordability() {
            super("loan_affordability", "Loan eligibility", "How much you can borrow for a given monthly EMI budget", Category.FINANCE, "ic_money", "eligibility afford borrow budget");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("emi", "EMI you can pay per month").positive(),
                    CalcInput.number("rate", "Interest rate (per year)").suffix("%").range(0, 100),
                    CalcInput.number("years", "Tenure (years)").range(0.1, 50));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double emi = in.num("emi"), r = in.num("rate") / 12 / 100;
            int n = (int) Math.round(in.num("years") * 12);
            double principal = r == 0 ? emi * n : emi * (Math.pow(1 + r, n) - 1) / (r * Math.pow(1 + r, n));
            List<CalcResult> out = new ArrayList<>();
            out.add(CalcResult.primary("Maximum loan amount", principal, 0, "", ""));
            out.add(CalcResult.num("Total payment", emi * n, 0));
            out.add(CalcResult.num("Total interest", emi * n - principal, 0));
            return out;
        }
    }
}
