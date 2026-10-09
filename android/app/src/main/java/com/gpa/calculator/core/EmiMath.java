package com.gpa.calculator.core;

import java.util.ArrayList;
import java.util.List;

/** Loan maths with amortisation schedule. */
public final class EmiMath {
    private EmiMath() {}

    public static final class Row {
        public final int month;
        public final double principal;
        public final double interest;
        public final double balance;

        Row(int month, double principal, double interest, double balance) {
            this.month = month;
            this.principal = principal;
            this.interest = interest;
            this.balance = balance;
        }
    }

    public static final class Result {
        public final double emi;
        public final double totalInterest;
        public final double totalPayment;
        public final List<Row> schedule;

        Result(double emi, double totalInterest, double totalPayment, List<Row> schedule) {
            this.emi = emi;
            this.totalInterest = totalInterest;
            this.totalPayment = totalPayment;
            this.schedule = schedule;
        }
    }

    public static double emi(double principal, double annualRatePct, int months) {
        if (months <= 0) throw new CalcException("Tenure must be at least 1 month");
        if (principal <= 0) throw new CalcException("Loan amount must be greater than 0");
        double r = annualRatePct / 12.0 / 100.0;
        if (r == 0) return principal / months;
        double f = Math.pow(1 + r, months);
        return principal * r * f / (f - 1);
    }

    public static Result compute(double principal, double annualRatePct, int months) {
        double emi = emi(principal, annualRatePct, months);
        double r = annualRatePct / 12.0 / 100.0;
        double bal = principal;
        double totalInterest = 0;
        List<Row> rows = new ArrayList<>(months);
        for (int m = 1; m <= months; m++) {
            double interest = bal * r;
            double princ = emi - interest;
            if (m == months) princ = bal; // absorb rounding on the final instalment
            bal -= princ;
            if (bal < 0.005) bal = 0;
            totalInterest += interest;
            rows.add(new Row(m, princ, interest, bal));
        }
        return new Result(emi, totalInterest, emi * months, rows);
    }
}
