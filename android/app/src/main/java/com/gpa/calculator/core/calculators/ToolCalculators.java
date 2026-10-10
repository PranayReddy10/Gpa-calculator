package com.gpa.calculator.core.calculators;

import com.gpa.calculator.core.CalcException;
import com.gpa.calculator.core.CalcInput;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.Category;
import com.gpa.calculator.core.Inputs;
import com.gpa.calculator.core.SimpleCalc;

import java.util.Collections;
import java.util.List;

/** Calculators with their own dedicated screens (keypad calculator, currency converter). */
public final class ToolCalculators {
    private ToolCalculators() {}

    public static final class Basic extends SimpleCalc {
        public Basic() {
            super("basic", "Calculator", "Standard calculator with live result, brackets and percent",
                    Category.EVERYDAY, "ic_calculate", "normal basic standard arithmetic add subtract multiply divide");
        }

        @Override public Screen screen() { return Screen.BASIC; }
        @Override public List<CalcInput> inputs() { return Collections.emptyList(); }
        @Override public List<CalcResult> compute(Inputs in) { throw new CalcException("Use the calculator screen"); }
    }

    public static final class Currency extends SimpleCalc {
        public Currency() {
            super("currency", "Currency converter", "Dollar, rupee, euro and 30+ currencies with live exchange rates",
                    Category.CONVERTERS, "ic_money", "dollar usd inr rupee euro exchange rate forex money convert");
        }

        @Override public Screen screen() { return Screen.CURRENCY; }
        @Override public List<CalcInput> inputs() { return Collections.emptyList(); }
        @Override public List<CalcResult> compute(Inputs in) { throw new CalcException("Use the currency screen"); }
    }

    public static Calculator basic() { return new Basic(); }
    public static Calculator currency() { return new Currency(); }
}
