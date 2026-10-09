package com.gpa.calculator.core.calculators;

import com.gpa.calculator.core.CalcInput;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.Category;
import com.gpa.calculator.core.Inputs;
import com.gpa.calculator.core.SimpleCalc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Unit converters: each is a table of factors relative to a base unit (temperature is special-cased). */
public final class ConverterCalculators {
    private ConverterCalculators() {}

    public static List<Calculator> all() {
        return Arrays.asList(
                new Factor("unit_length", "Length", "Metres, kilometres, miles, feet, inches …", "length metre km mile feet inch",
                        new String[]{"Metre (m)", "Kilometre (km)", "Centimetre (cm)", "Millimetre (mm)", "Mile", "Yard", "Foot", "Inch"},
                        new double[]{1, 1000, 0.01, 0.001, 1609.344, 0.9144, 0.3048, 0.0254}),
                new Factor("unit_weight", "Weight", "Kilograms, grams, pounds, ounces …", "weight mass kg gram pound ounce",
                        new String[]{"Kilogram (kg)", "Gram (g)", "Milligram (mg)", "Tonne", "Pound (lb)", "Ounce (oz)"},
                        new double[]{1, 0.001, 0.000001, 1000, 0.45359237, 0.028349523125}),
                new Factor("unit_area", "Area", "Square metres, acres, hectares, square feet …", "area acre hectare sqft",
                        new String[]{"Square metre", "Square kilometre", "Square foot", "Square yard", "Acre", "Hectare", "Cent (India)"},
                        new double[]{1, 1_000_000, 0.09290304, 0.83612736, 4046.8564224, 10000, 40.468564224}),
                new Factor("unit_volume", "Volume", "Litres, millilitres, gallons, cups …", "volume litre gallon ml cup",
                        new String[]{"Litre", "Millilitre", "Cubic metre", "US gallon", "US cup", "Fluid ounce (US)"},
                        new double[]{1, 0.001, 1000, 3.785411784, 0.2365882365, 0.0295735295625}),
                new Factor("unit_speed", "Speed", "km/h, mph, m/s, knots", "speed kmh mph",
                        new String[]{"km/h", "mph", "m/s", "Knot"},
                        new double[]{1, 1.609344, 3.6, 1.852}),
                new Factor("unit_data", "Data size", "Bytes, KB, MB, GB, TB", "data storage kb mb gb",
                        new String[]{"Byte", "Kilobyte (KB)", "Megabyte (MB)", "Gigabyte (GB)", "Terabyte (TB)"},
                        new double[]{1, 1024, 1048576, 1073741824d, 1099511627776d}),
                new Factor("unit_time", "Time", "Seconds, minutes, hours, days, weeks", "time seconds minutes hours days",
                        new String[]{"Second", "Minute", "Hour", "Day", "Week", "Year (365 d)"},
                        new double[]{1, 60, 3600, 86400, 604800, 31536000}),
                new Temperature());
    }

    public static final class Factor extends SimpleCalc {
        private final String[] units;
        private final double[] factors;

        Factor(String id, String name, String desc, String keywords, String[] units, double[] factors) {
            super(id, name, desc, Category.CONVERTERS, "ic_swap", keywords);
            this.units = units;
            this.factors = factors;
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("value", "Value"),
                    CalcInput.select("from", "From", units),
                    CalcInput.select("to", "To", units).def(units[Math.min(1, units.length - 1)]));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double v = in.num("value");
            int f = in.selectIndex("from"), t = in.selectIndex("to");
            double base = v * factors[f];
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary(units[t], base / factors[t], 6, "", ""));
            for (int i = 0; i < units.length; i++) {
                if (i == t || i == f) continue;
                r.add(CalcResult.num(units[i], base / factors[i], 6));
            }
            return r;
        }
    }

    public static final class Temperature extends SimpleCalc {
        public Temperature() {
            super("unit_temperature", "Temperature", "Celsius, Fahrenheit and Kelvin", Category.CONVERTERS, "ic_swap", "temperature celsius fahrenheit kelvin");
        }

        @Override public List<CalcInput> inputs() {
            return Arrays.asList(CalcInput.number("value", "Value"),
                    CalcInput.select("from", "From", "Celsius", "Fahrenheit", "Kelvin"));
        }

        @Override public List<CalcResult> compute(Inputs in) {
            double v = in.num("value");
            double c;
            switch (in.selectIndex("from")) {
                case 1: c = (v - 32) * 5 / 9; break;
                case 2: c = v - 273.15; break;
                default: c = v;
            }
            List<CalcResult> r = new ArrayList<>();
            r.add(CalcResult.primary("Celsius", c, 2, "", " °C"));
            r.add(CalcResult.num("Fahrenheit", c * 9 / 5 + 32, 2, "", " °F"));
            r.add(CalcResult.num("Kelvin", c + 273.15, 2, "", " K"));
            return r;
        }
    }
}
