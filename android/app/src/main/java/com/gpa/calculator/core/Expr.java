package com.gpa.calculator.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Arithmetic expression evaluator for the standard calculator screen.
 * Supports + − × ÷ ^ %, parentheses, decimals and unary minus (shunting-yard).
 * Percent is a postfix operator: "50%" = 0.5, "200+10%" = 220 (percent of the left operand).
 */
public final class Expr {
    private Expr() {}

    private static final class Tok {
        final char op;      // 0 for numbers
        final double num;
        Tok(char op, double num) { this.op = op; this.num = num; }
        boolean isNum() { return op == 0; }
    }

    public static String normalise(String s) {
        return s.replace('×', '*').replace('÷', '/').replace('−', '-').replace(",", "");
    }

    public static double eval(String input) {
        String s = normalise(input == null ? "" : input).replace(" ", "");
        if (s.isEmpty()) throw new CalcException("Enter an expression");
        List<Tok> toks = tokenize(s);
        return rpn(toRpn(toks));
    }

    private static List<Tok> tokenize(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                int j = i;
                while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                String n = s.substring(i, j);
                if (n.equals(".") || n.indexOf('.') != n.lastIndexOf('.')) throw new CalcException("Invalid number " + n);
                out.add(new Tok((char) 0, Double.parseDouble(n)));
                i = j;
                continue;
            }
            if ("+-*/^%()".indexOf(c) < 0) throw new CalcException("Unexpected '" + c + "'");
            boolean unaryPos = out.isEmpty() || (!out.get(out.size() - 1).isNum() && out.get(out.size() - 1).op != ')' && out.get(out.size() - 1).op != '%');
            if (c == '-' && unaryPos) out.add(new Tok('n', 0));       // unary minus
            else if (c == '+' && unaryPos) { /* unary plus: skip */ }
            else out.add(new Tok(c, 0));
            i++;
        }
        return out;
    }

    private static int prec(char op) {
        switch (op) {
            case '+': case '-': return 2;
            case '*': case '/': return 3;
            case '^': return 4;
            case 'n': return 3; // below ^ so that -2^2 = -4 (maths convention)
            case '%': return 6;
            default: return 0;
        }
    }

    private static boolean rightAssoc(char op) { return op == '^' || op == 'n'; }

    private static List<Tok> toRpn(List<Tok> toks) {
        List<Tok> out = new ArrayList<>();
        Deque<Tok> ops = new ArrayDeque<>();
        for (Tok t : toks) {
            if (t.isNum()) { out.add(t); continue; }
            if (t.op == '(') { ops.push(t); continue; }
            if (t.op == ')') {
                while (!ops.isEmpty() && ops.peek().op != '(') out.add(ops.pop());
                if (ops.isEmpty()) throw new CalcException("Unbalanced parentheses");
                ops.pop();
                continue;
            }
            while (!ops.isEmpty() && ops.peek().op != '(' &&
                    (prec(ops.peek().op) > prec(t.op) || (prec(ops.peek().op) == prec(t.op) && !rightAssoc(t.op)))) {
                out.add(ops.pop());
            }
            ops.push(t);
        }
        while (!ops.isEmpty()) {
            Tok t = ops.pop();
            if (t.op == '(') throw new CalcException("Unbalanced parentheses");
            out.add(t);
        }
        return out;
    }

    private static double rpn(List<Tok> rpn) {
        Deque<Double> st = new ArrayDeque<>();
        Deque<Character> lastOp = new ArrayDeque<>();
        for (Tok t : rpn) {
            if (t.isNum()) { st.push(t.num); continue; }
            if (t.op == 'n') {
                if (st.isEmpty()) throw new CalcException("Incomplete expression");
                st.push(-st.pop());
                continue;
            }
            if (t.op == '%') {
                if (st.isEmpty()) throw new CalcException("Incomplete expression");
                st.push(st.pop() / 100.0);
                continue;
            }
            if (st.size() < 2) throw new CalcException("Incomplete expression");
            double b = st.pop(), a = st.pop();
            switch (t.op) {
                case '+': st.push(a + b); break;
                case '-': st.push(a - b); break;
                case '*': st.push(a * b); break;
                case '/':
                    if (b == 0) throw new CalcException("Cannot divide by zero");
                    st.push(a / b);
                    break;
                case '^': st.push(Math.pow(a, b)); break;
                default: throw new CalcException("Bad operator");
            }
        }
        if (st.size() != 1) throw new CalcException("Incomplete expression");
        double v = st.pop();
        if (Double.isNaN(v) || Double.isInfinite(v)) throw new CalcException("Result is undefined");
        return v;
    }

    /**
     * Calculator-style percent: in "a + b%" or "a − b%" the percent applies to a.
     * Implemented by rewriting "X op Y%" into "X op (X*Y/100)" for + and − before evaluation.
     */
    public static double evalCalculatorStyle(String input) {
        String s = normalise(input == null ? "" : input).replace(" ", "");
        // find patterns NUMBER (+|-) NUMBER %  (only when the left side is a plain number)
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)([+\\-])(\\d+(?:\\.\\d+)?)%").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String a = m.group(1), op = m.group(2), b = m.group(3);
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(a + op + "(" + a + "*" + b + "/100)"));
        }
        m.appendTail(sb);
        return eval(sb.toString());
    }
}
