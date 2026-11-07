package com.example.calculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Calculation history (pure Java, no Android classes). Newest entry first. */
public final class History {

    public static final int MAX = 100;

    public static final class Entry {
        public final int mode;        // angle unit that was active (matters for sin, cos, tan...)
        public final double value;    // full-precision result
        public final String expr;     // expression as the user saw it
        public final String result;   // result as displayed
        public final String recall;   // expression with Ans and M replaced by the numbers actually used

        public Entry(int mode, double value, String expr, String result, String recall) {
            this.mode = mode;
            this.value = value;
            this.expr = clean(expr);
            this.result = clean(result);
            this.recall = clean(recall);
        }
    }

    private final List<Entry> items = new ArrayList<>();

    public List<Entry> items() { return Collections.unmodifiableList(items); }

    public int size() { return items.size(); }

    public void add(Entry e) {
        items.add(0, e);
        while (items.size() > MAX) items.remove(items.size() - 1);
    }

    public void clear() { items.clear(); }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** Number as text the calculator keypad/parser understands (full precision, "−" for minus). */
    public static String numberText(double v) {
        String s = Double.toString(v);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        s = s.replace(".0E", "E");
        return s.replace('-', '−');
    }

    /** Number to load into the keypad when reusing a result. Negatives get brackets so -0.25 squared is 0.0625, not -0.0625. */
    public static String recallNumberText(double v) {
        String s = numberText(v);
        return v < 0 ? "(" + s + ")" : s;
    }

    /** Makes an expression safe to re-run later: "Ans" and "M" become the values they had. */
    public static String recallText(String expr, double ansBefore, double memBefore) {
        String s = expr.replace("Ans", "(" + numberText(ansBefore) + ")");
        return s.replace("M", "(" + numberText(memBefore) + ")");
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : items) {
            sb.append(e.mode).append('\t').append(Double.toString(e.value)).append('\t')
                    .append(e.expr).append('\t').append(e.result).append('\t')
                    .append(e.recall).append('\n');
        }
        return sb.toString();
    }

    public static History parse(String data) {
        History h = new History();
        if (data == null || data.isEmpty()) return h;
        for (String line : data.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length != 5) continue;
            try {
                int mode = Integer.parseInt(f[0]);
                double v = Double.parseDouble(f[1]);
                if (mode < 0 || mode > 2 || Double.isNaN(v) || Double.isInfinite(v) || f[2].isEmpty()) continue;
                h.items.add(new Entry(mode, v, f[2], f[3], f[4]));
            } catch (NumberFormatException ex) {
                // corrupt line: skip it
            }
            if (h.items.size() >= MAX) break;
        }
        return h;
    }
}
