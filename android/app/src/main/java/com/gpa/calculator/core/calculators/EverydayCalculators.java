package com.gpa.calculator.core.calculators;

import com.gpa.calculator.core.CalcException;
import com.gpa.calculator.core.CalcInput;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.Category;
import com.gpa.calculator.core.Fmt;
import com.gpa.calculator.core.Inputs;
import com.gpa.calculator.core.SimpleCalc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class EverydayCalculators {
    private EverydayCalculators() {}

    public static List<Calculator> all() {
        return Arrays.asList(new Age(), new DateDifference(), new Bmi(), new Bmr(), new FuelCost(), new ElectricityBill(),
                new SpeedDistanceTime(), new Average(), new DaysUntil());
    }

    // ---------------------------------------------------------------------------------------------
    public static final class Age extends SimpleCalc {
        public Age() {
            super("age", "Age", "Exact age in years, months and days, plus next birthday", Category.EVERYDAY, "ic_cake", "age birthday dob born");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.date("dob", "Date of birth"), CalcInput.date("asof", "Age on date").optional());
        }

        @Override public List<CalcResult> compute(Inputs in) {
            LocalDate dob = in.date("dob");
            LocalDate asOf = in.date("asof");
            if (asOf == null) asOf = LocalDate.now();
            if (dob.isAfter(asOf)) throw new CalcException("dob", "Date of birth must be before the other date");
            Period p = Period.between(dob, asOf);
            long days = ChronoUnit.DAYS.between(dob, asOf);
            LocalDate next = dob.withYear(asOf.getYear());
            if (!next.isAfter(asOf)) next = next.plusYears(1);
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primaryText("Age", p.getYears() + " years, " + p.getMonths() + " months, " + p.getDays() + " days"));
            r.add(CalcResult.num("Total months", p.getYears() * 12L + p.getMonths(), 0));
            r.add(CalcResult.num("Total weeks", days / 7, 0));
            r.add(CalcResult.num("Total days", days, 0));
            r.add(CalcResult.num("Total hours", days * 24, 0));
            r.add(CalcResult.num("Next birthday in", ChronoUnit.DAYS.between(asOf, next), 0, "", " days"));
            r.add(CalcResult.text("Born on", dob.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())));
            return r;
        }
    }

    public static final class DateDifference extends SimpleCalc {
        public DateDifference() {
            super("date_diff", "Date difference", "Days, weeks and months between two dates", Category.EVERYDAY, "ic_calendar", "days between dates duration");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.date("from", "From date"), CalcInput.date("to", "To date"),
                    CalcInput.select("inclusive", "Count", "Exclude end date", "Include end date"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            LocalDate a = in.date("from"), b = in.date("to");
            if (b.isBefore(a)) { LocalDate t = a; a = b; b = t; }
            long days = ChronoUnit.DAYS.between(a, b) + (in.selectIndex("inclusive") == 1 ? 1 : 0);
            Period p = Period.between(a, b);
            long weekdays = 0;
            for (LocalDate d = a; d.isBefore(b); d = d.plusDays(1)) {
                DayOfWeek w = d.getDayOfWeek();
                if (w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY) weekdays++;
            }
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Days", days, 0, "", ""));
            r.add(CalcResult.text("Breakdown", p.getYears() + " years, " + p.getMonths() + " months, " + p.getDays() + " days"));
            r.add(CalcResult.num("Weeks", days / 7.0, 2));
            r.add(CalcResult.num("Working days (Mon–Fri)", weekdays, 0));
            r.add(CalcResult.num("Hours", days * 24, 0));
            return r;
        }
    }

    public static final class DaysUntil extends SimpleCalc {
        public DaysUntil() {
            super("days_until", "Days until", "Countdown to any date from today", Category.EVERYDAY, "ic_timer", "countdown until exam event");
        }

        @Override public List<CalcInput> inputs() {
            return Collections.singletonList(CalcInput.date("date", "Target date"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            LocalDate t = in.date("date");
            long days = ChronoUnit.DAYS.between(LocalDate.now(), t);
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary(days >= 0 ? "Days remaining" : "Days ago", Math.abs(days), 0, "", ""));
            r.add(CalcResult.num("Weeks", Math.abs(days) / 7.0, 1));
            r.add(CalcResult.text("Falls on", t.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())));
            return r;
        }
    }

    public static final class Bmi extends SimpleCalc {
        public Bmi() {
            super("bmi", "BMI", "Body mass index with category and healthy weight range", Category.EVERYDAY, "ic_fitness", "bmi weight height health");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.select("units", "Units", "Metric (kg, cm)", "Imperial (lb, in)"),
                    CalcInput.number("weight", "Weight").positive(),
                    CalcInput.number("height", "Height").positive());
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double w = in.num("weight"), h = in.num("height");
            boolean imperial = in.selectIndex("units") == 1;
            double kg = imperial ? w * 0.45359237 : w;
            double m = imperial ? h * 0.0254 : h / 100;
            if (m <= 0 || kg <= 0) throw new CalcException("Weight and height must be greater than 0");
            double bmi = kg / (m * m);
            String cat = bmi < 18.5 ? "Underweight" : bmi < 25 ? "Normal" : bmi < 30 ? "Overweight" : "Obese";
            double lo = 18.5 * m * m, hi = 24.9 * m * m;
            if (imperial) { lo /= 0.45359237; hi /= 0.45359237; }
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("BMI", bmi, 1, "", ""));
            r.add(CalcResult.text("Category", cat));
            r.add(CalcResult.text("Healthy weight range", Fmt.fmt(lo, 1) + " – " + Fmt.fmt(hi, 1) + (imperial ? " lb" : " kg")));
            return r;
        }

        @Override public String formula() { return "BMI = weight (kg) ÷ height² (m)"; }
    }

    public static final class Bmr extends SimpleCalc {
        public Bmr() {
            super("bmr", "Calories (BMR)", "Daily calorie needs from BMR and activity level", Category.EVERYDAY, "ic_fitness", "calories bmr tdee diet");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.select("gender", "Gender", "Male", "Female"),
                    CalcInput.integer("age", "Age (years)").range(1, 120),
                    CalcInput.number("weight", "Weight (kg)").positive(),
                    CalcInput.number("height", "Height (cm)").positive(),
                    CalcInput.select("activity", "Activity", "Sedentary", "Lightly active", "Moderately active", "Very active", "Extra active"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double w = in.num("weight"), h = in.num("height");
            int age = in.integer("age");
            double bmr = 10 * w + 6.25 * h - 5 * age + (in.selectIndex("gender") == 0 ? 5 : -161);
            double[] f = {1.2, 1.375, 1.55, 1.725, 1.9};
            double tdee = bmr * f[in.selectIndex("activity")];
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Daily calories to maintain", tdee, 0, "", " kcal"));
            r.add(CalcResult.num("BMR (at rest)", bmr, 0, "", " kcal"));
            r.add(CalcResult.num("To lose ~0.5 kg / week", tdee - 500, 0, "", " kcal"));
            r.add(CalcResult.num("To gain ~0.5 kg / week", tdee + 500, 0, "", " kcal"));
            return r;
        }

        @Override public String formula() { return "Mifflin–St Jeor equation"; }
    }

    public static final class FuelCost extends SimpleCalc {
        public FuelCost() {
            super("fuel_cost", "Fuel cost", "Fuel needed and trip cost from distance and mileage", Category.EVERYDAY, "ic_fuel", "fuel petrol diesel trip mileage km");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("distance", "Distance (km)").positive(),
                    CalcInput.number("mileage", "Mileage (km per litre)").positive(),
                    CalcInput.number("price", "Fuel price per litre").positive(),
                    CalcInput.integer("people", "Share between people").range(1, 100).def("1"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double d = in.num("distance"), m = in.num("mileage"), p = in.num("price");
            int people = in.integer("people");
            if (m <= 0) throw new CalcException("mileage", "Mileage must be greater than 0");
            double litres = d / m, cost = litres * p;
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Trip cost", cost, 2, "", ""));
            r.add(CalcResult.num("Fuel needed", litres, 2, "", " L"));
            r.add(CalcResult.num("Cost per km", d == 0 ? 0 : cost / d, 2));
            if (people > 1) r.add(CalcResult.num("Per person", cost / people, 2));
            return r;
        }
    }

    public static final class ElectricityBill extends SimpleCalc {
        public ElectricityBill() {
            super("electricity", "Electricity cost", "Energy used and cost of running an appliance", Category.EVERYDAY, "ic_bolt", "electricity power watt unit kwh bill");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("watts", "Power rating (watts)").positive(),
                    CalcInput.number("hours", "Hours used per day").range(0, 24),
                    CalcInput.integer("days", "Number of days").range(1, 3660).def("30"),
                    CalcInput.number("rate", "Price per unit (kWh)").positive());
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double kwh = in.num("watts") / 1000 * in.num("hours") * in.integer("days");
            double cost = kwh * in.num("rate");
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Cost", cost, 2, "", ""));
            r.add(CalcResult.num("Energy used", kwh, 2, "", " kWh"));
            r.add(CalcResult.num("Cost per day", cost / in.integer("days"), 2));
            return r;
        }
    }

    public static final class SpeedDistanceTime extends SimpleCalc {
        public SpeedDistanceTime() {
            super("speed", "Speed · Distance · Time", "Find any one of speed, distance or time", Category.EVERYDAY, "ic_speed", "speed distance time travel");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.select("find", "Find", "Time", "Speed", "Distance"),
                    CalcInput.number("speed", "Speed (km/h)").positive().optional(),
                    CalcInput.number("distance", "Distance (km)").positive().optional(),
                    CalcInput.number("time", "Time (hours)").positive().optional());
        }

        @Override public List<CalcResult> compute(Inputs in) {
            List<CalcResult> r = new ArrayList<>();
            switch (in.selectIndex("find")) {
                case 0: {
                    double s = req(in, "speed"), d = req(in, "distance");
                    double h = d / s;
                    r.add(CalcResult.primary("Time", h, 2, "", " h"));
                    r.add(CalcResult.text("That is", hm(h)));
                    break;
                }
                case 1: {
                    double d = req(in, "distance"), t = req(in, "time");
                    r.add(CalcResult.primary("Speed", d / t, 2, "", " km/h"));
                    r.add(CalcResult.num("In m/s", d / t / 3.6, 2));
                    break;
                }
                default: {
                    double s = req(in, "speed"), t = req(in, "time");
                    r.add(CalcResult.primary("Distance", s * t, 2, "", " km"));
                }
            }
            return r;
        }

        private static double req(Inputs in, String key) {
            if (!in.has(key)) throw new CalcException(key, key.substring(0, 1).toUpperCase() + key.substring(1) + " is required");
            double v = in.num(key);
            if (v <= 0) throw new CalcException(key, key.substring(0, 1).toUpperCase() + key.substring(1) + " must be greater than 0");
            return v;
        }

        private static String hm(double hours) {
            int h = (int) hours;
            int m = (int) Math.round((hours - h) * 60);
            if (m == 60) { h++; m = 0; }
            return h + " h " + m + " min";
        }
    }

    public static final class Average extends SimpleCalc {
        public Average() {
            super("average", "Average & statistics", "Mean, median, min, max and sum of a list of numbers", Category.EVERYDAY, "ic_functions", "average mean median sum statistics");
        }

        @Override public List<CalcInput> inputs() {
            return Collections.singletonList(CalcInput.text("values", "Numbers (comma separated)").hint("e.g. 78, 82, 91, 65"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            List<Double> v = new ArrayList<>(in.numberList("values"));
            Collections.sort(v);
            double sum = 0;
            for (double d : v) sum += d;
            int n = v.size();
            double mean = sum / n;
            double median = n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2;
            double var = 0;
            for (double d : v) var += (d - mean) * (d - mean);
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Average", mean, 4, "", ""));
            r.add(CalcResult.num("Median", median, 4));
            r.add(CalcResult.num("Sum", sum, 4));
            r.add(CalcResult.num("Count", n, 0));
            r.add(CalcResult.num("Minimum", v.get(0), 4));
            r.add(CalcResult.num("Maximum", v.get(n - 1), 4));
            r.add(CalcResult.num("Std. deviation", Math.sqrt(var / n), 4));
            return r;
        }
    }
}
