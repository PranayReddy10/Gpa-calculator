package com.gpa.calculator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EngineTest {

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static CalcResult first(List<CalcResult> r) {
        return r.get(0);
    }

    @Test
    public void registryHasUniqueIdsAndIcons() {
        assertTrue(CalculatorRegistry.count() >= 40);
        for (Calculator c : CalculatorRegistry.all()) {
            assertNotNull(c.id());
            assertNotNull(c.icon());
            assertTrue(c.icon().startsWith("ic_"));
        }
    }

    @Test
    public void marksPercent() {
        List<CalcResult> r = CalculatorRegistry.byId("marks_percent").compute(map("obtained", "450", "total", "500"));
        assertEquals(90.0, first(r).numeric, 1e-9);
        assertEquals("O", r.get(1).display);
    }

    @Test
    public void marksPercentRejectsOverflow() {
        try {
            CalculatorRegistry.byId("marks_percent").compute(map("obtained", "600", "total", "500"));
            fail();
        } catch (CalcException e) {
            assertEquals("obtained", e.inputKey);
        }
    }

    @Test
    public void generalPercentModes() {
        Calculator c = CalculatorRegistry.byId("general_percent");
        assertEquals(50.0, first(c.compute(map("mode", "What is X% of Y?", "x", "25", "y", "200"))).numeric, 1e-9);
        assertEquals(12.5, first(c.compute(map("mode", "X is what % of Y?", "x", "25", "y", "200"))).numeric, 1e-9);
        assertEquals(500.0, first(c.compute(map("mode", "X is Y% of what?", "x", "25", "y", "5"))).numeric, 1e-9);
    }

    @Test
    public void percentChange() {
        List<CalcResult> r = CalculatorRegistry.byId("percent_change").compute(map("old", "80", "new", "100"));
        assertEquals("Increase", first(r).label);
        assertEquals(25.0, first(r).numeric, 1e-9);
    }

    @Test
    public void sgpaAndCgpa() {
        double s = GradeMath.sgpa(Arrays.asList(4.0, 3.0, 3.0), Arrays.asList(10.0, 9.0, 8.0));
        assertEquals((40 + 27 + 24) / 10.0, s, 1e-9);
        double c = GradeMath.cgpa(Arrays.asList(9.1, 8.5), Arrays.asList(20.0, 22.0));
        assertEquals((9.1 * 20 + 8.5 * 22) / 42, c, 1e-9);
        assertEquals(10.0, GradeScale.TEN_POINT.pointsFor("O"), 0);
        assertEquals(3.7, GradeScale.FOUR_POINT.pointsFor("A-"), 0);
    }

    @Test
    public void cgpaToPercentCbse() {
        List<CalcResult> r = CalculatorRegistry.byId("cgpa_to_percent").compute(map("cgpa", "8", "scale", "CGPA × 9.5 (CBSE)"));
        assertEquals(76.0, first(r).numeric, 1e-9);
    }

    @Test
    public void attendance() {
        List<CalcResult> r = CalculatorRegistry.byId("attendance").compute(map("attended", "30", "total", "40", "required", "75"));
        assertEquals(75.0, first(r).numeric, 1e-9);
        assertEquals("Classes you can skip", r.get(1).label);
        assertEquals(0.0, r.get(1).numeric, 0);
        r = CalculatorRegistry.byId("attendance").compute(map("attended", "20", "total", "40", "required", "75"));
        assertEquals("Classes to attend in a row", r.get(1).label);
        assertEquals(40.0, r.get(1).numeric, 0); // (0.75*40-20)/(0.25) = 40
    }

    @Test
    public void emi() {
        EmiMath.Result res = EmiMath.compute(1_000_000, 8.5, 240);
        assertEquals(8678.23, res.emi, 0.01);
        assertEquals(240, res.schedule.size());
        assertEquals(0, res.schedule.get(239).balance, 0.01);
        assertEquals(res.totalPayment, res.totalInterest + 1_000_000, 1.0);
        // zero-rate loan
        assertEquals(1000.0, EmiMath.emi(12000, 0, 12), 1e-9);
    }

    @Test
    public void emiCalculatorYearsToMonths() {
        List<CalcResult> r = CalculatorRegistry.byId("emi").compute(map("principal", "500000", "rate", "10", "tenure", "5", "unit", "Years"));
        assertEquals(10623.52, first(r).numeric, 0.01);
    }

    @Test
    public void simpleAndCompoundInterest() {
        List<CalcResult> si = CalculatorRegistry.byId("simple_interest").compute(map("p", "10000", "r", "5", "t", "2", "unit", "Years"));
        assertEquals(1000.0, first(si).numeric, 1e-9);
        List<CalcResult> ci = CalculatorRegistry.byId("compound_interest").compute(map("p", "10000", "r", "10", "t", "2", "unit", "Years", "n", "Yearly"));
        assertEquals(12100.0, first(ci).numeric, 1e-6);
    }

    @Test
    public void sip() {
        List<CalcResult> r = CalculatorRegistry.byId("sip").compute(map("amount", "5000", "rate", "12", "years", "10"));
        assertEquals(1161695, first(r).numeric, 1.0);
        assertEquals(600000.0, r.get(1).numeric, 1e-6);
    }

    @Test
    public void gstAddAndRemove() {
        Calculator c = CalculatorRegistry.byId("tax");
        List<CalcResult> add = c.compute(map("amount", "1000", "rate", "18%", "mode", "Add tax (amount is before tax)"));
        assertEquals(1180.0, first(add).numeric, 1e-9);
        List<CalcResult> rem = c.compute(map("amount", "1180", "rate", "18%", "mode", "Remove tax (amount includes tax)"));
        assertEquals(1000.0, first(rem).numeric, 1e-9);
        List<CalcResult> custom = c.compute(map("amount", "200", "rate", "Custom", "custom", "7.5", "mode", "Add tax (amount is before tax)"));
        assertEquals(215.0, first(custom).numeric, 1e-9);
    }

    @Test
    public void tipSplit() {
        List<CalcResult> r = CalculatorRegistry.byId("tip").compute(map("bill", "1000", "tip", "10", "people", "4"));
        assertEquals(1100.0, first(r).numeric, 1e-9);
        assertEquals(275.0, r.get(2).numeric, 1e-9);
    }

    @Test
    public void age() {
        List<CalcResult> r = CalculatorRegistry.byId("age").compute(map("dob", "2000-02-29", "asof", "2024-03-01"));
        assertEquals("24 years, 0 months, 1 days", first(r).display);
    }

    @Test
    public void dateDifference() {
        List<CalcResult> r = CalculatorRegistry.byId("date_diff").compute(map("from", "2024-01-01", "to", "2024-01-31", "inclusive", "Exclude end date"));
        assertEquals(30.0, first(r).numeric, 0);
    }

    @Test
    public void bmi() {
        List<CalcResult> r = CalculatorRegistry.byId("bmi").compute(map("units", "Metric (kg, cm)", "weight", "70", "height", "175"));
        assertEquals(22.9, first(r).numeric, 0.05);
        assertEquals("Normal", r.get(1).display);
    }

    @Test
    public void converters() {
        List<CalcResult> r = CalculatorRegistry.byId("unit_length").compute(map("value", "1", "from", "Kilometre (km)", "to", "Mile"));
        assertEquals(0.621371, first(r).numeric, 1e-5);
        r = CalculatorRegistry.byId("unit_temperature").compute(map("value", "100", "from", "Celsius"));
        assertEquals(212.0, r.get(1).numeric, 1e-9);
    }

    @Test
    public void averageList() {
        List<CalcResult> r = CalculatorRegistry.byId("average").compute(map("values", "10, 20 30\n40"));
        assertEquals(25.0, first(r).numeric, 1e-9);
        assertEquals(25.0, r.get(1).numeric, 1e-9);
    }

    @Test
    public void search() {
        assertTrue(CalculatorRegistry.search("loan", null).size() >= 2);
        assertEquals(CalculatorRegistry.byCategory(Category.PERCENTAGE).size(), CalculatorRegistry.search("", Category.PERCENTAGE).size());
    }

    @Test
    public void formatting() {
        java.util.Locale.setDefault(java.util.Locale.US);
        assertEquals("1,234.5", Fmt.fmt(1234.5, 2).replace(' ', ' '));
        assertEquals("0", Fmt.fmt(-0.0001, 2));
    }

    @Test
    public void expressionEvaluator() {
        assertEquals(14.0, Expr.eval("2+3×4"), 1e-9);
        assertEquals(20.0, Expr.eval("(2+3)×4"), 1e-9);
        assertEquals(-4.0, Expr.eval("-2^2"), 1e-9);
        assertEquals(8.0, Expr.eval("2^3"), 1e-9);
        assertEquals(0.5, Expr.eval("50%"), 1e-9);
        assertEquals(220.0, Expr.evalCalculatorStyle("200+10%"), 1e-9);
        assertEquals(180.0, Expr.evalCalculatorStyle("200-10%"), 1e-9);
        assertEquals(2.5, Expr.eval("10÷4"), 1e-9);
        assertEquals(1234.5, Expr.eval("1,234.5"), 1e-9);
        try { Expr.eval("1÷0"); fail(); } catch (CalcException e) { assertTrue(e.getMessage().contains("zero")); }
        try { Expr.eval("(1+2"); fail(); } catch (CalcException e) { /* ok */ }
        try { Expr.eval("1+"); fail(); } catch (CalcException e) { /* ok */ }
    }

    @Test
    public void currencyConvert() {
        java.util.Map<String, Double> rates = new java.util.HashMap<>();
        rates.put("USD", 1.0); rates.put("INR", 80.0); rates.put("EUR", 0.5);
        assertEquals(8000.0, Currencies.convert(100, "USD", "INR", rates), 1e-9);
        assertEquals(160.0, Currencies.convert(1, "EUR", "INR", rates), 1e-9);
        assertEquals("USD", Currencies.codeFromLabel(Currencies.label("USD")));
        assertNotNull(CalculatorRegistry.byId("basic"));
        assertEquals("basic", CalculatorRegistry.all().get(0).id());
        assertNotNull(CalculatorRegistry.byId("currency"));
    }

    @Test
    public void unitPrice() {
        Calculator c = CalculatorRegistry.byId("unit_price");
        // A: 500 g for 120 -> 240/kg ; B: 1 kg for 220 -> 220/kg (best)
        List<CalcResult> r = c.compute(map("a_price", "120", "a_qty", "500", "a_unit", "g", "b_price", "220", "b_qty", "1", "b_unit", "kg"));
        assertEquals("Item B", first(r).display);
        assertEquals(240.0, r.get(1).numeric, 1e-9);
        assertEquals(24.0, r.get(2).numeric, 1e-9);   // per 100 g
        assertEquals(220.0, r.get(3).numeric, 1e-9);
        // three items in volume, C best
        r = c.compute(map("a_price", "100", "a_qty", "1", "a_unit", "L", "b_price", "55", "b_qty", "500", "b_unit", "ml", "c_price", "180", "c_qty", "2", "c_unit", "L"));
        assertEquals("Item C", first(r).display);
        // mixing weight and volume is rejected
        try {
            c.compute(map("a_price", "1", "a_qty", "1", "a_unit", "kg", "b_price", "1", "b_qty", "1", "b_unit", "L"));
            fail();
        } catch (CalcException e) { assertEquals("b_unit", e.inputKey); }
        // half-filled item C is rejected
        try {
            c.compute(map("a_price", "1", "a_qty", "1", "a_unit", "kg", "b_price", "1", "b_qty", "1", "b_unit", "kg", "c_price", "5"));
            fail();
        } catch (CalcException e) { assertEquals("c_qty", e.inputKey); }
    }

    @Test
    public void loanCompare() {
        Calculator c = CalculatorRegistry.byId("loan_compare");
        // Same amount; A cheaper rate but 2% fee, B higher rate no fee
        List<CalcResult> r = c.compute(map("a_amount", "1000000", "a_rate", "8.5", "a_years", "20", "a_fee", "2",
                "b_rate", "9", "b_years", "20"));
        double aInterest = EmiMath.compute(1_000_000, 8.5, 240).totalInterest;
        double bInterest = EmiMath.compute(1_000_000, 9, 240).totalInterest;
        double aTotal = aInterest + 20000, bTotal = bInterest;
        assertTrue(first(r).label.startsWith(aTotal < bTotal ? "Loan A" : "Loan B"));
        assertEquals(Math.abs(aTotal - bTotal), first(r).numeric, 1e-6);
        assertEquals(8678.23, r.get(1).numeric, 0.01);
        // identical offers
        r = c.compute(map("a_amount", "500000", "a_rate", "10", "a_years", "5", "b_rate", "10", "b_years", "5"));
        assertEquals("Both loans cost the same", first(r).display);
    }
}
