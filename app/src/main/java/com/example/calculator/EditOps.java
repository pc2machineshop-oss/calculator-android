package com.example.calculator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Editing helpers for the calculator expression (pure Java, no Android classes). */
public final class EditOps {

    private EditOps() {}

    private static final String NUM = "(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)";
    private static final Pattern WRAPPED_NEGATIVE = Pattern.compile("\\(−" + NUM + "(?:E[+−]?[0-9]*)?\\)$");
    private static final Pattern TRAILING_NUMBER = Pattern.compile(NUM + "(?:E[+−]?[0-9]*)?$");
    private static final String[] TOKENS = {
            "asinh(", "acosh(", "atanh(", "sinh(", "cosh(", "tanh(", "asin(", "acos(", "atan(",
            "sin(", "cos(", "tan(", "ln(", "log(", "sqrt(", "cbrt(", "abs(", "nCr(", "nPr(", "Ans"};

    /**
     * Removes the last entry from an expression: a whole number (12, 3.5, 1E-5), a negative number
     * in brackets, a function such as sin(, or a single symbol (+, ×, ), π ...).
     */
    public static String clearLastEntry(String expr) {
        if (expr == null || expr.isEmpty()) return "";
        for (String t : TOKENS) {
            if (expr.endsWith(t)) return expr.substring(0, expr.length() - t.length());
        }
        Matcher m = WRAPPED_NEGATIVE.matcher(expr);
        if (m.find()) return expr.substring(0, m.start());
        m = TRAILING_NUMBER.matcher(expr);
        if (m.find()) return expr.substring(0, m.start());
        return expr.substring(0, expr.length() - 1);
    }

    /** Puts text into the calculator as the current expression, in edit mode, without evaluating it. */
    public static void setExpression(Engine.Calc calc, String text) {
        calc.expr = text + "0";   // sentinel digit ...
        calc.press("DEL");        // ... is removed again by DEL, which also switches to edit mode
    }

    /**
     * What the C key does.
     * clearAll = false: undo-style. After "=", it goes back to the expression; otherwise it removes
     * the last entry, and each further press steps back one more entry.
     * clearAll = true: clears everything.
     */
    public static void clearKey(Engine.Calc calc, boolean clearAll) {
        if (clearAll) {
            calc.press("C");
            return;
        }
        boolean inv = calc.inv;
        String target = calc.isResult() ? calc.expr : clearLastEntry(calc.expr);
        setExpression(calc, target);
        calc.inv = inv;
    }
}
