package com.example.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java calculation engine (no Android classes) so it can be tested on any computer.
 * Contains: expression parser, calculator key-state machine, unit converter, polar/rectangular.
 */
public final class Engine {

    public static final int DEG = 0, RAD = 1, GRAD = 2;
    public static final String[] MODE_NAMES = {"DEG", "RAD", "GRAD"};

    public static class CalcException extends Exception {
        public CalcException(String m) { super(m); }
    }

    private Engine() {}

    // =====================================================================
    //  Number helpers
    // =====================================================================

    /** Removes binary floating-point noise (0.30000000000000004 -> 0.3). */
    static double round15(double v) {
        if (v == 0 || Double.isNaN(v) || Double.isInfinite(v)) return v;
        return new BigDecimal(v).round(new MathContext(15)).doubleValue();
    }

    /** Display format: 12 significant digits, scientific form for very big/small numbers. */
    public static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "Error";
        if (v == 0) return "0";
        BigDecimal bd = new BigDecimal(v).round(new MathContext(12)).stripTrailingZeros();
        double a = Math.abs(bd.doubleValue());
        if (a >= 1e12 || a < 1e-9) {
            String digits = bd.unscaledValue().abs().toString();
            int exp = digits.length() - bd.scale() - 1;
            StringBuilder sb = new StringBuilder();
            if (bd.signum() < 0) sb.append('-');
            sb.append(digits.charAt(0));
            if (digits.length() > 1) sb.append('.').append(digits, 1, digits.length());
            sb.append('E').append(exp);
            return sb.toString();
        }
        return bd.toPlainString();
    }

    private static double chk(double v) throws CalcException {
        if (Double.isNaN(v)) throw new CalcException("Math Error");
        if (Double.isInfinite(v)) throw new CalcException("Overflow");
        return v;
    }

    // =====================================================================
    //  Trigonometry with exact values at quadrant angles
    // =====================================================================

    /** fn: 0 = sin, 1 = cos, 2 = tan. Angle in the given mode. */
    static double trig(int fn, double x, int mode) throws CalcException {
        double q;
        double rad;
        if (mode == DEG) {
            double r = x % 360.0; if (r < 0) r += 360.0;
            q = r / 90.0;
            rad = Math.toRadians(r);
        } else if (mode == GRAD) {
            double r = x % 400.0; if (r < 0) r += 400.0;
            q = r / 100.0;
            rad = r * Math.PI / 200.0;
        } else {
            rad = x;
            q = Math.abs(x) > 1e9 ? Double.NaN : x / (Math.PI / 2.0);
        }
        if (!Double.isNaN(q) && Math.abs(q - Math.rint(q)) < 1e-12 * Math.max(1.0, Math.abs(q))) {
            int n = (int) (((long) Math.rint(q)) % 4);
            if (n < 0) n += 4;
            switch (fn) {
                case 0: return new double[]{0, 1, 0, -1}[n];
                case 1: return new double[]{1, 0, -1, 0}[n];
                default:
                    if (n % 2 == 1) throw new CalcException("Math Error (tan undefined)");
                    return 0;
            }
        }
        switch (fn) {
            case 0: return Math.sin(rad);
            case 1: return Math.cos(rad);
            default:
                if (Math.abs(Math.cos(rad)) < 1e-14) throw new CalcException("Math Error (tan undefined)");
                return Math.tan(rad);
        }
    }

    static double fromRad(double r, int mode) {
        if (mode == DEG) return Math.toDegrees(r);
        if (mode == GRAD) return r * 200.0 / Math.PI;
        return r;
    }

    // =====================================================================
    //  Expression evaluator
    // =====================================================================

    static String normalize(String src) {
        return src.replace(" ", "")
                .replace("×", "*").replace("÷", "/")
                .replace("−", "-").replace("–", "-")
                .replace("π", "pi").replace("√", "sqrt").replace("∛", "cbrt");
    }

    public static double eval(String src, int mode, double ans, double mem) throws CalcException {
        String t = normalize(src);
        if (t.isEmpty()) throw new CalcException("Syntax Error");
        int open = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '(') open++;
            else if (c == ')') { open--; if (open < 0) throw new CalcException("Syntax Error (extra \")\")"); }
        }
        StringBuilder sb = new StringBuilder(t);
        while (open-- > 0) sb.append(')');
        Parser p = new Parser(sb.toString(), mode, ans, mem);
        double v = p.expr();
        if (p.pos != p.s.length()) throw new CalcException("Syntax Error");
        return round15(chk(v));
    }

    private static final class Parser {
        private static final String[] NAMES = {
                "asinh", "acosh", "atanh", "sinh", "cosh", "tanh", "asin", "acos", "atan",
                "sqrt", "cbrt", "sin", "cos", "tan", "log", "abs", "exp", "nCr", "nPr",
                "Ans", "ln", "pi", "e", "M"};

        final String s;
        int pos = 0;
        final int mode;
        final double ans, mem;

        Parser(String s, int mode, double ans, double mem) {
            this.s = s; this.mode = mode; this.ans = ans; this.mem = mem;
        }

        char peek() { return pos < s.length() ? s.charAt(pos) : '\0'; }

        boolean isDigit(char c) { return c >= '0' && c <= '9'; }
        boolean isLetter(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }

        boolean startsPrimary() {
            char c = peek();
            return isDigit(c) || c == '.' || c == '(' || isLetter(c);
        }

        void expect(char c) throws CalcException {
            if (peek() != c) throw new CalcException("Syntax Error");
            pos++;
        }

        double expr() throws CalcException {
            double v = term();
            while (true) {
                char c = peek();
                if (c == '+') { pos++; double r = term(); v = chk(round15(v + r)); }
                else if (c == '-') { pos++; double r = term(); v = chk(round15(v - r)); }
                else break;
            }
            return v;
        }

        double term() throws CalcException {
            double v = unary();
            while (true) {
                char c = peek();
                if (c == '*') { pos++; double r = unary(); v = chk(round15(v * r)); }
                else if (c == '/') {
                    pos++;
                    double d = unary();
                    if (d == 0) throw new CalcException("Divide by 0");
                    v = chk(round15(v / d));
                }
                else if (startsPrimary()) { double r = power(); v = chk(round15(v * r)); } // implicit multiplication
                else break;
            }
            return v;
        }

        double unary() throws CalcException {
            char c = peek();
            if (c == '-') { pos++; return -unary(); }
            if (c == '+') { pos++; return unary(); }
            return power();
        }

        double power() throws CalcException {
            double base = postfix();
            if (peek() == '^') {
                pos++;
                double ex = unary();            // right-associative, allows 2^-3
                return pow(base, ex);
            }
            return base;
        }

        double pow(double b, double e) throws CalcException {
            if (b == 0 && e == 0) throw new CalcException("0^0 is undefined");
            if (b == 0 && e < 0) throw new CalcException("Divide by 0");
            if (b < 0 && e != Math.rint(e)) throw new CalcException("Math Error (negative base, fractional power)");
            return chk(round15(Math.pow(b, e)));
        }

        double postfix() throws CalcException {
            double v = primary();
            while (true) {
                char c = peek();
                if (c == '!') { pos++; v = fact(v); }
                else if (c == '%') { pos++; v = round15(v / 100.0); }
                else if (c == '²') { pos++; v = pow(v, 2); }
                else if (c == '³') { pos++; v = pow(v, 3); }
                else break;
            }
            return v;
        }

        double fact(double v) throws CalcException {
            if (v < 0 || v != Math.rint(v)) throw new CalcException("Math Error (n! needs whole number >= 0)");
            if (v > 170) throw new CalcException("Overflow (n! too large)");
            double r = 1;
            for (int i = 2; i <= (int) v; i++) r *= i;
            return chk(round15(r));
        }

        double number() throws CalcException {
            int st = pos;
            boolean dot = false;
            int digits = 0;
            while (true) {
                char c = peek();
                if (isDigit(c)) { digits++; pos++; }
                else if (c == '.') {
                    if (dot) throw new CalcException("Syntax Error");
                    dot = true; pos++;
                } else break;
            }
            if (digits == 0) throw new CalcException("Syntax Error");
            if (peek() == 'E') {
                pos++;
                if (peek() == '+' || peek() == '-') pos++;
                int ed = 0;
                while (isDigit(peek())) { pos++; ed++; }
                if (ed == 0) throw new CalcException("Syntax Error (incomplete exponent)");
            }
            try {
                return chk(Double.parseDouble(s.substring(st, pos)));
            } catch (NumberFormatException ex) {
                throw new CalcException("Syntax Error");
            }
        }

        String matchName() {
            for (String n : NAMES) if (s.startsWith(n, pos)) return n;
            return null;
        }

        double primary() throws CalcException {
            char c = peek();
            if (isDigit(c) || c == '.') return number();
            if (c == '(') {
                pos++;
                double v = expr();
                expect(')');
                return v;
            }
            if (isLetter(c)) {
                String name = matchName();
                if (name == null) throw new CalcException("Syntax Error");
                pos += name.length();
                switch (name) {
                    case "pi": return Math.PI;
                    case "e": return Math.E;
                    case "Ans": return ans;
                    case "M": return mem;
                    default: return func(name);
                }
            }
            throw new CalcException("Syntax Error");
        }

        double func(String name) throws CalcException {
            expect('(');
            double a = expr();
            double b = 0;
            boolean two = false;
            if (peek() == ',') { pos++; b = expr(); two = true; }
            expect(')');
            boolean needTwo = name.equals("nCr") || name.equals("nPr");
            if (needTwo != two) throw new CalcException("Syntax Error");
            return chk(round15(apply(name, a, b)));
        }

        double apply(String f, double a, double b) throws CalcException {
            switch (f) {
                case "sin": return trig(0, a, mode);
                case "cos": return trig(1, a, mode);
                case "tan": return trig(2, a, mode);
                case "asin":
                case "acos": {
                    if (Math.abs(a) > 1 + 1e-12) throw new CalcException("Math Error (input must be -1 to 1)");
                    double c = Math.max(-1, Math.min(1, a));
                    return fromRad(f.equals("asin") ? Math.asin(c) : Math.acos(c), mode);
                }
                case "atan": return fromRad(Math.atan(a), mode);
                case "sinh": return Math.sinh(a);
                case "cosh": return Math.cosh(a);
                case "tanh": return Math.tanh(a);
                case "asinh": return Math.signum(a) * Math.log(Math.abs(a) + Math.sqrt(a * a + 1));
                case "acosh":
                    if (a < 1) throw new CalcException("Math Error (acosh needs x >= 1)");
                    return Math.log(a + Math.sqrt(a * a - 1));
                case "atanh":
                    if (Math.abs(a) >= 1) throw new CalcException("Math Error (atanh needs -1 < x < 1)");
                    return 0.5 * Math.log((1 + a) / (1 - a));
                case "ln":
                    if (a <= 0) throw new CalcException("Math Error (ln needs x > 0)");
                    return Math.log(a);
                case "log":
                    if (a <= 0) throw new CalcException("Math Error (log needs x > 0)");
                    return Math.log10(a);
                case "sqrt":
                    if (a < 0) throw new CalcException("Math Error (sqrt of negative)");
                    return Math.sqrt(a);
                case "cbrt": return Math.cbrt(a);
                case "abs": return Math.abs(a);
                case "exp": return Math.exp(a);
                case "nCr": return comb(a, b, false);
                case "nPr": return comb(a, b, true);
                default: throw new CalcException("Syntax Error");
            }
        }

        double comb(double n, double r, boolean perm) throws CalcException {
            if (n != Math.rint(n) || r != Math.rint(r) || n < 0 || r < 0 || r > n)
                throw new CalcException("Math Error (nCr/nPr need whole numbers, 0 <= r <= n)");
            if (n > 100000) throw new CalcException("Overflow");
            double res = 1;
            if (perm) {
                for (long i = 0; i < (long) r; i++) { res *= (n - i); if (Double.isInfinite(res)) break; }
            } else {
                double k = Math.min(r, n - r);
                for (long i = 1; i <= (long) k; i++) {
                    res = res * (n - k + i) / i;
                    if (Double.isInfinite(res)) break;
                }
            }
            return res;
        }
    }

    // =====================================================================
    //  Calculator key state machine (what the buttons do)
    // =====================================================================

    public static final class Calc {
        public static final int MAX_LEN = 100;
        private static final int EDIT = 0, RESULT = 1, ERROR = 2;
        private static final String NUM = "(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:E[+−]?[0-9]+)?";
        private static final Pattern NUM_TAIL = Pattern.compile(NUM + "$");
        private static final String[] FUNC_TOKENS = {
                "asinh(", "acosh(", "atanh(", "sinh(", "cosh(", "tanh(", "asin(", "acos(", "atan(",
                "sin(", "cos(", "tan(", "ln(", "log(", "sqrt(", "cbrt(", "abs(", "nCr(", "nPr("};
        private static final String FUNC_KEYS =
                ",sin,cos,tan,asin,acos,atan,sinh,cosh,tanh,asinh,acosh,atanh,ln,log,sqrt,cbrt,abs,nCr,nPr,";

        public String expr = "";
        public String result = "";
        public String error = "";
        public int mode = DEG;
        public boolean inv = false;
        public double ans = 0;
        public double mem = 0;
        public boolean memSet = false;
        private int state = EDIT;

        public boolean isResult() { return state == RESULT; }
        public boolean hasError() { return state == ERROR; }

        public void press(String k) {
            switch (k) {
                case "C": expr = ""; result = ""; error = ""; state = EDIT; return;
                case "DEL": backspace(); return;
                case "=": equalsKey(); return;
                case "DEG": mode = (mode + 1) % 3; return;
                case "INV": inv = !inv; return;
                case "MC": memSet = false; mem = 0; return;
                case "MR": if (memSet) insertToken("M"); return;
                case "M+": case "M−": case "MS": memoryKey(k); return;
                case "±": negate(); return;
                case ".": dot(); return;
                case "+": case "−": case "×": case "÷": operator(k); return;
                case "(": openParen(); return;
                case ")": closeParen(); return;
                case ",": comma(); return;
                case "!": case "%": case "²": case "³": case "^": postfixOp(k); return;
                case "E": expKey(); return;
                case "π": case "e": insertToken(k); return;
                case "e^": case "10^": insertToken(k); return;
                default:
            }
            if (k.length() == 1 && isDig(k.charAt(0))) { digit(k.charAt(0)); return; }
            if (FUNC_KEYS.contains("," + k + ",")) insertToken(k + "(");
        }

        // ---------- helpers ----------
        private static boolean isDig(char c) { return c >= '0' && c <= '9'; }

        private void prepare(boolean continuing) {
            if (state == RESULT) { expr = continuing ? "Ans" : ""; result = ""; }
            state = EDIT;
            error = "";
        }

        private String trailingNumber() {
            int i = expr.length();
            while (i > 0) {
                char c = expr.charAt(i - 1);
                if (isDig(c) || c == '.') i--; else break;
            }
            return expr.substring(i);
        }

        private boolean inExponent() {
            int i = expr.length();
            while (i > 0 && isDig(expr.charAt(i - 1))) i--;
            if (i > 1 && "+−".indexOf(expr.charAt(i - 1)) >= 0 && expr.charAt(i - 2) == 'E') return true;
            return i > 0 && expr.charAt(i - 1) == 'E';
        }

        private boolean pendingExp() {
            int n = expr.length();
            if (n >= 1 && expr.charAt(n - 1) == 'E') return true;
            return n >= 2 && "+−".indexOf(expr.charAt(n - 1)) >= 0 && expr.charAt(n - 2) == 'E';
        }

        private boolean endsOperand() {
            if (expr.isEmpty()) return false;
            char c = expr.charAt(expr.length() - 1);
            if (isDig(c)) return true;
            if (c == '.') return trailingNumber().length() > 1;
            return ")!%²³πeM".indexOf(c) >= 0 || expr.endsWith("Ans");
        }

        private int openCount() {
            int n = 0;
            for (int i = 0; i < expr.length(); i++) {
                char c = expr.charAt(i);
                if (c == '(') n++; else if (c == ')') n--;
            }
            return n;
        }

        // ---------- key handlers ----------
        private void digit(char d) {
            prepare(false);
            if (expr.length() >= MAX_LEN) return;
            if (insertIntoWrappedNegative(String.valueOf(d))) return;
            String tail = trailingNumber();
            if (tail.equals("0")) {
                int i = expr.length() - 1;
                boolean standalone = i == 0 || "+−×÷^(,".indexOf(expr.charAt(i - 1)) >= 0;
                if (standalone) {
                    if (d != '0') expr = expr.substring(0, i) + d;
                    return;
                }
            }
            expr += d;
        }

        private void dot() {
            prepare(false);
            if (expr.length() >= MAX_LEN - 1) return;
            if (insertIntoWrappedNegative(".")) return;
            if (inExponent()) return;
            String tail = trailingNumber();
            if (tail.contains(".")) return;
            expr += tail.isEmpty() ? "0." : ".";
        }

        /** After the +/- key, typing more digits edits the number inside "(-5)". */
        private boolean insertIntoWrappedNegative(String ch) {
            if (!expr.endsWith(")")) return false;
            int open = expr.lastIndexOf("(−");
            if (open < 0) return false;
            String inner = expr.substring(open + 2, expr.length() - 1);
            if (!inner.matches(NUM)) return false;
            if (ch.equals(".") && (inner.contains(".") || inner.contains("E"))) return true;
            if (inner.equals("0") && ch.equals("0")) return true;
            inner = (inner.equals("0") && !ch.equals(".")) ? ch : inner + ch;
            expr = expr.substring(0, open + 2) + inner + ")";
            return true;
        }

        private void operator(String k) {
            prepare(true);
            if (expr.length() >= MAX_LEN) return;
            if (expr.isEmpty()) { if (k.equals("−")) expr = "−"; return; }
            if (trailingNumber().equals(".")) expr = expr.substring(0, expr.length() - 1);
            if (expr.isEmpty()) { if (k.equals("−")) expr = "−"; return; }
            if (pendingExp()) {
                if (expr.endsWith("E") && (k.equals("+") || k.equals("−"))) expr += k;
                return;
            }
            char last = expr.charAt(expr.length() - 1);
            if ("(,^".indexOf(last) >= 0) { if (k.equals("−")) expr += "−"; return; }
            if ("+−×÷".indexOf(last) >= 0) {
                if (k.equals("−") && (last == '×' || last == '÷')) { expr += "−"; return; }
                String stripped = expr;
                while (!stripped.isEmpty() && "+−×÷".indexOf(stripped.charAt(stripped.length() - 1)) >= 0)
                    stripped = stripped.substring(0, stripped.length() - 1);
                if (stripped.isEmpty() || "(,^".indexOf(stripped.charAt(stripped.length() - 1)) >= 0) {
                    if (k.equals("−")) expr = stripped + "−";
                    return;
                }
                expr = stripped + k;
                return;
            }
            expr += k;
        }

        private void openParen() {
            prepare(false);
            if (expr.length() >= MAX_LEN || pendingExp()) return;
            expr += "(";
        }

        private void closeParen() {
            if (state == RESULT) return;
            prepare(false);
            if (expr.length() >= MAX_LEN) return;
            if (openCount() > 0 && endsOperand()) expr += ")";
        }

        private void comma() {
            if (state == RESULT) return;
            prepare(false);
            if (expr.length() >= MAX_LEN) return;
            if (openCount() > 0 && endsOperand()) expr += ",";
        }

        private void postfixOp(String k) {
            prepare(true);
            if (expr.length() >= MAX_LEN) return;
            if (!endsOperand()) return;
            expr += k;
        }

        private void expKey() {
            if (state == RESULT) return;
            prepare(false);
            if (expr.length() >= MAX_LEN - 2) return;
            if (expr.isEmpty() || !isDig(expr.charAt(expr.length() - 1))) return;
            if (inExponent()) return;
            if (trailingNumber().isEmpty()) return;
            expr += "E";
        }

        private void insertToken(String tok) {
            prepare(false);
            if (expr.length() + tok.length() > MAX_LEN || pendingExp()) return;
            expr += tok;
        }

        private void negate() {
            if (state == RESULT) { expr = "−Ans"; result = ""; error = ""; state = EDIT; return; }
            prepare(false);
            if (expr.length() >= MAX_LEN - 4) { return; }
            if (expr.isEmpty()) { expr = "−"; return; }
            if (expr.endsWith(")")) {
                int open = expr.lastIndexOf("(−");
                if (open >= 0) {
                    String inner = expr.substring(open + 2, expr.length() - 1);
                    if (inner.matches(NUM)) expr = expr.substring(0, open) + inner;
                }
                return;
            }
            char last = expr.charAt(expr.length() - 1);
            if (last == '−') {
                boolean unary = expr.length() == 1 || "×÷^(,E".indexOf(expr.charAt(expr.length() - 2)) >= 0;
                if (unary) expr = expr.substring(0, expr.length() - 1);
                return;
            }
            if ("+×÷^(,".indexOf(last) >= 0) { expr += "−"; return; }
            Matcher m = NUM_TAIL.matcher(expr);
            if (m.find()) {
                expr = expr.substring(0, m.start()) + "(−" + m.group() + ")";
            }
        }

        private void backspace() {
            if (state == RESULT) { result = ""; }
            state = EDIT;
            error = "";
            if (expr.isEmpty()) return;
            for (String f : FUNC_TOKENS) {
                if (expr.endsWith(f)) { expr = expr.substring(0, expr.length() - f.length()); return; }
            }
            if (expr.endsWith("Ans")) { expr = expr.substring(0, expr.length() - 3); return; }
            expr = expr.substring(0, expr.length() - 1);
        }

        private void equalsKey() {
            if (state == RESULT || expr.isEmpty()) return;
            evaluate();
        }

        private boolean evaluate() {
            try {
                double v = eval(expr, mode, ans, memSet ? mem : 0);
                ans = v;
                result = fmt(v);
                error = "";
                state = RESULT;
                return true;
            } catch (CalcException ex) {
                error = ex.getMessage();
                result = "";
                state = ERROR;
                return false;
            }
        }

        private void memoryKey(String k) {
            if (state != RESULT) {
                if (expr.isEmpty()) return;
                if (!evaluate()) return;
            }
            double v = ans;
            double base = memSet ? mem : 0;
            switch (k) {
                case "M+": mem = round15(base + v); break;
                case "M−": mem = round15(base - v); break;
                default: mem = v;
            }
            if (Double.isInfinite(mem) || Double.isNaN(mem)) { mem = base; return; }
            memSet = true;
        }
    }

    // =====================================================================
    //  Unit converter
    // =====================================================================

    public static final class Unit {
        public final String name;
        final double f, off;
        final boolean inv;
        Unit(String name, double f, double off, boolean inv) {
            this.name = name; this.f = f; this.off = off; this.inv = inv;
        }
    }

    private static Unit u(String n, double f) { return new Unit(n, f, 0, false); }

    public static final String[] CATEGORIES = {
            "Length", "Area", "Volume", "Mass", "Temperature", "Time", "Speed", "Pressure",
            "Energy", "Power", "Force", "Torque", "Angle", "Frequency / RPM", "Data", "Density",
            "Fuel economy"};
    private static final int TEMPERATURE = 4;

    private static final Unit[][] UNITS = {
            // Length (base metre)
            {u("millimetre (mm)", 1e-3), u("inch (in)", 0.0254), u("centimetre (cm)", 1e-2), u("metre (m)", 1),
                    u("kilometre (km)", 1000), u("foot (ft)", 0.3048), u("yard (yd)", 0.9144),
                    u("mile (mi)", 1609.344), u("nautical mile", 1852), u("thou / mil", 2.54e-5), u("micrometre (µm)", 1e-6)},
            // Area (base m²)
            {u("m²", 1), u("ft²", 0.09290304), u("mm²", 1e-6), u("cm²", 1e-4), u("km²", 1e6), u("hectare", 1e4),
                    u("in²", 6.4516e-4), u("yd²", 0.83612736), u("acre", 4046.8564224), u("mi²", 2589988.110336)},
            // Volume (base m³)
            {u("litre (L)", 1e-3), u("US gallon", 0.003785411784), u("millilitre (mL)", 1e-6), u("m³", 1),
                    u("in³", 1.6387064e-5), u("ft³", 0.028316846592), u("US quart", 9.46352946e-4),
                    u("US pint", 4.73176473e-4), u("US fl oz", 2.95735295625e-5), u("US cup", 2.365882365e-4),
                    u("US tbsp", 1.478676478125e-5), u("US tsp", 4.92892159375e-6), u("UK gallon", 0.00454609)},
            // Mass (base kg)
            {u("kilogram (kg)", 1), u("pound (lb)", 0.45359237), u("gram (g)", 1e-3), u("milligram (mg)", 1e-6),
                    u("tonne (t)", 1000), u("ounce (oz)", 0.028349523125), u("stone", 6.35029318),
                    u("US ton", 907.18474), u("UK ton", 1016.0469088)},
            // Temperature (base kelvin)
            {new Unit("°C", 1, 273.15, false), new Unit("°F", 5.0 / 9.0, 273.15 - 160.0 / 9.0, false),
                    u("K", 1), u("°R (Rankine)", 5.0 / 9.0)},
            // Time (base s)
            {u("second (s)", 1), u("minute (min)", 60), u("hour (h)", 3600), u("day", 86400), u("week", 604800),
                    u("year (365.25 d)", 31557600), u("millisecond (ms)", 1e-3), u("microsecond (µs)", 1e-6),
                    u("nanosecond (ns)", 1e-9)},
            // Speed (base m/s)
            {u("km/h", 1000.0 / 3600.0), u("mph", 0.44704), u("m/s", 1), u("knot", 1852.0 / 3600.0),
                    u("ft/s", 0.3048), u("ft/min (SFM)", 0.00508), u("m/min", 1.0 / 60.0)},
            // Pressure (base Pa)
            {u("bar", 1e5), u("psi", 6894.757293168), u("Pa", 1), u("kPa", 1e3), u("MPa", 1e6), u("mbar", 100),
                    u("atm", 101325), u("mmHg", 133.322387415), u("inHg", 3386.389), u("kgf/cm²", 98066.5),
                    u("ksi", 6894757.293168)},
            // Energy (base J)
            {u("joule (J)", 1), u("kilojoule (kJ)", 1e3), u("calorie (cal)", 4.184), u("kilocalorie (kcal)", 4184),
                    u("watt-hour (Wh)", 3600), u("kilowatt-hour (kWh)", 3.6e6), u("BTU", 1055.05585262),
                    u("ft·lbf", 1.3558179483314), u("electronvolt (eV)", 1.602176634e-19)},
            // Power (base W)
            {u("kilowatt (kW)", 1e3), u("horsepower (hp)", 745.6998715822702), u("watt (W)", 1), u("megawatt (MW)", 1e6),
                    u("metric hp (PS)", 735.49875), u("BTU/h", 0.29307107017222), u("kcal/h", 4184.0 / 3600.0),
                    u("ft·lbf/s", 1.3558179483314)},
            // Force (base N)
            {u("newton (N)", 1), u("pound-force (lbf)", 4.4482216152605), u("kilonewton (kN)", 1e3),
                    u("kilogram-force (kgf)", 9.80665), u("ounce-force (ozf)", 0.2780138509537812), u("dyne", 1e-5)},
            // Torque (base N·m)
            {u("N·m", 1), u("lbf·ft", 1.3558179483314), u("N·cm", 0.01), u("N·mm", 0.001), u("kgf·m", 9.80665),
                    u("kgf·cm", 0.0980665), u("lbf·in", 0.1129848290276), u("ozf·in", 0.007061551814226)},
            // Angle (base radian)
            {u("degree (°)", Math.PI / 180.0), u("radian (rad)", 1), u("gradian (grad)", Math.PI / 200.0),
                    u("arcminute (')", Math.PI / 10800.0), u("arcsecond (\")", Math.PI / 648000.0),
                    u("turn", 2 * Math.PI), u("milliradian (mrad)", 1e-3)},
            // Frequency (base Hz)
            {u("hertz (Hz)", 1), u("RPM", 1.0 / 60.0), u("kilohertz (kHz)", 1e3), u("megahertz (MHz)", 1e6),
                    u("rad/s", 1.0 / (2 * Math.PI))},
            // Data (base byte)
            {u("megabyte (MB)", 1e6), u("gigabyte (GB)", 1e9), u("byte (B)", 1), u("kilobyte (KB)", 1e3),
                    u("terabyte (TB)", 1e12), u("bit", 0.125), u("kibibyte (KiB)", 1024.0),
                    u("mebibyte (MiB)", 1048576.0), u("gibibyte (GiB)", 1073741824.0),
                    u("tebibyte (TiB)", 1099511627776.0)},
            // Density (base kg/m³)
            {u("kg/m³", 1), u("g/cm³", 1000), u("lb/ft³", 16.01846337396), u("lb/in³", 27679.9047102)},
            // Fuel economy (base km/L)
            {u("km/L", 1), u("mpg (US)", 1.609344 / 3.785411784), u("mpg (UK)", 1.609344 / 4.54609),
                    new Unit("L/100 km", 100, 0, true)}
    };

    public static String[] unitNames(int cat) {
        Unit[] us = UNITS[cat];
        String[] n = new String[us.length];
        for (int i = 0; i < us.length; i++) n[i] = us[i].name;
        return n;
    }

    public static double convert(int cat, int from, int to, double v) throws CalcException {
        if (Double.isNaN(v) || Double.isInfinite(v)) throw new CalcException("Invalid number");
        Unit a = UNITS[cat][from], b = UNITS[cat][to];
        double base;
        if (a.inv) {
            if (v <= 0) throw new CalcException("Value must be greater than 0");
            base = a.f / v;
        } else {
            base = v * a.f + a.off;
        }
        if (cat == TEMPERATURE && base < -1e-9) throw new CalcException("Below absolute zero");
        double out;
        if (b.inv) {
            if (base <= 0) throw new CalcException("Divide by 0 (zero fuel economy)");
            out = b.f / base;
        } else {
            out = (base - b.off) / b.f;
        }
        return chk(round15(out));
    }

    // =====================================================================
    //  Polar <-> Rectangular
    // =====================================================================

    private static double fullTurn(int mode) { return mode == DEG ? 360.0 : mode == GRAD ? 400.0 : 2 * Math.PI; }

    /** returns {r, theta in (-half..+half], theta in [0..full)} using the angle mode. */
    public static double[] rectToPolar(double x, double y, int mode) throws CalcException {
        if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y))
            throw new CalcException("Invalid number");
        x += 0.0; y += 0.0;                       // turns -0.0 into 0.0
        double r = chk(Math.hypot(x, y));
        if (r == 0) return new double[]{0, 0, 0};
        double th = fromRad(Math.atan2(y, x), mode);
        double th360 = th < 0 ? th + fullTurn(mode) : th;
        return new double[]{round15(r), round15(th), round15(th360)};
    }

    public static double[] polarToRect(double r, double theta, int mode) throws CalcException {
        if (Double.isNaN(r) || Double.isInfinite(r) || Double.isNaN(theta) || Double.isInfinite(theta))
            throw new CalcException("Invalid number");
        double x = chk(r * trig(1, theta, mode));
        double y = chk(r * trig(0, theta, mode));
        return new double[]{round15(x), round15(y)};
    }
}
