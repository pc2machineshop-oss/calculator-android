package com.example.calculator;

import com.example.calculator.Engine.CalcException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Shop, fraction, equation, statistics and number-base tools (pure Java, no Android classes). */
public final class Tools {

    private Tools() {}

    // =====================================================================
    //  Helpers
    // =====================================================================

    /** Fixed decimals, rounded half-up the way a person would round the decimal shown. */
    public static String fixed(double v, int decimals) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "Error";
        return BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }

    private static void positive(double v, String name) throws CalcException {
        if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0)
            throw new CalcException(name + " must be greater than 0");
    }

    private static boolean isFinite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    private static double finite(double v) throws CalcException {
        if (Double.isNaN(v)) throw new CalcException("Math Error");
        if (Double.isInfinite(v)) throw new CalcException("Overflow");
        return Engine.round15(v);
    }

    private static double flutes(double f) throws CalcException {
        if (!isFinite(f) || f != Math.rint(f) || f < 1 || f > 1000)
            throw new CalcException("Flutes must be a whole number from 1 to 1000");
        return f;
    }

    // =====================================================================
    //  Machining
    // =====================================================================

    /** Spindle RPM from cutting speed (SFM with diameter in inches, or m/min with diameter in mm). */
    public static double rpmFor(double speed, double diameter, boolean metric) throws CalcException {
        positive(speed, "Cutting speed");
        positive(diameter, "Diameter");
        return finite(metric ? speed * 1000.0 / (Math.PI * diameter) : speed * 12.0 / (Math.PI * diameter));
    }

    /** Cutting speed from spindle RPM (SFM with diameter in inches, or m/min with diameter in mm). */
    public static double speedFor(double rpm, double diameter, boolean metric) throws CalcException {
        positive(rpm, "RPM");
        positive(diameter, "Diameter");
        return finite(metric ? rpm * Math.PI * diameter / 1000.0 : rpm * Math.PI * diameter / 12.0);
    }

    /** Feed rate = RPM x chip load per tooth x number of flutes. */
    public static double feedFor(double rpm, double chipLoad, double flutes) throws CalcException {
        positive(rpm, "RPM");
        positive(chipLoad, "Chip load");
        return finite(rpm * chipLoad * flutes(flutes));
    }

    /** Chip load per tooth = feed rate / (RPM x flutes). */
    public static double chipLoadFor(double feed, double rpm, double flutes) throws CalcException {
        positive(feed, "Feed rate");
        positive(rpm, "RPM");
        return finite(feed / (rpm * flutes(flutes)));
    }

    /**
     * Bolt-circle hole positions. Angles are measured from the +X axis, counter-clockwise
     * unless clockwise is true. Returns n rows of {x, y}.
     */
    public static double[][] boltCircle(int n, double pcd, double startDeg, double cx, double cy,
                                        boolean clockwise) throws CalcException {
        if (n < 1 || n > 360) throw new CalcException("Number of holes must be 1 to 360");
        positive(pcd, "Circle diameter");
        if (!isFinite(startDeg) || !isFinite(cx) || !isFinite(cy)) throw new CalcException("Invalid number");
        double r = pcd / 2.0;
        double[][] out = new double[n][2];
        for (int i = 0; i < n; i++) {
            double step = 360.0 * i / n;
            double ang = clockwise ? startDeg - step : startDeg + step;
            out[i][0] = finite(cx + r * Engine.trig(1, ang, Engine.DEG));
            out[i][1] = finite(cy + r * Engine.trig(0, ang, Engine.DEG));
        }
        return out;
    }

    // =====================================================================
    //  Fractions (inch fractions <-> decimal <-> mm)
    // =====================================================================

    private static final String BAD_FRACTION = "Not a number or fraction (examples: 5/16, 1 3/8, 0.3125)";

    /** Parses 5/16, 1 3/8, 1-3/8, -1 1/2, 0.3125, .5, 12 ... Returns the decimal value. */
    public static double parseFraction(String in) throws CalcException {
        if (in == null || in.trim().isEmpty()) throw new CalcException("Enter a value");
        String s = in.trim().replace(',', '.');
        boolean neg = false;
        if (s.startsWith("-") || s.startsWith("−")) { neg = true; s = s.substring(1).trim(); }
        else if (s.startsWith("+")) { s = s.substring(1).trim(); }
        if (s.isEmpty()) throw new CalcException(BAD_FRACTION);
        double v;
        int slash = s.indexOf('/');
        if (slash >= 0) {
            String left = s.substring(0, slash).trim();
            String right = s.substring(slash + 1).trim();
            if (!right.matches("[0-9]{1,9}")) throw new CalcException(BAD_FRACTION);
            String[] parts = left.split("[ \\-]+");
            if (parts.length < 1 || parts.length > 2) throw new CalcException(BAD_FRACTION);
            for (String p : parts) if (!p.matches("[0-9]{1,9}")) throw new CalcException(BAD_FRACTION);
            long den = Long.parseLong(right);
            if (den == 0) throw new CalcException("Denominator cannot be 0");
            long num = Long.parseLong(parts[parts.length - 1]);
            long whole = parts.length == 2 ? Long.parseLong(parts[0]) : 0;
            v = whole + (double) num / den;
        } else {
            if (!s.matches("[0-9]*\\.?[0-9]+|[0-9]+\\.")) throw new CalcException(BAD_FRACTION);
            v = Double.parseDouble(s);
        }
        if (v > 1e9) throw new CalcException("Value too large");
        return neg ? -v : v;
    }

    private static long gcd(long a, long b) { return b == 0 ? a : gcd(b, a % b); }

    /** Nearest fraction with the given denominator, reduced ("5/16", "1 3/8", "2", "0"). */
    public static String nearestFraction(double inches, int den) throws CalcException {
        if (den < 1 || Double.isNaN(inches) || Math.abs(inches) > 1e6) throw new CalcException("Value too large");
        long n = Math.round(Math.abs(inches) * den);
        if (n == 0) return "0";
        long whole = n / den, rem = n % den;
        String body;
        if (rem == 0) {
            body = Long.toString(whole);
        } else {
            long g = gcd(rem, den);
            String f = (rem / g) + "/" + (den / g);
            body = whole == 0 ? f : whole + " " + f;
        }
        return (inches < 0 ? "−" : "") + body;
    }

    /** (fraction value) minus (actual value): positive means the fraction is larger. */
    public static double nearestFractionError(double inches, int den) {
        long n = Math.round(Math.abs(inches) * den);
        double approx = (double) n / den;
        if (inches < 0) approx = -approx;
        return Engine.round15(approx - inches);
    }

    // =====================================================================
    //  Quadratic equation  a x^2 + b x + c = 0
    // =====================================================================

    public static String[] quadratic(double a, double b, double c) throws CalcException {
        if (!isFinite(a) || !isFinite(b) || !isFinite(c)) throw new CalcException("Invalid number");
        List<String> out = new ArrayList<>();
        if (a == 0) {
            if (b == 0) {
                out.add(c == 0 ? "Every x is a solution (0 = 0)" : "No solution (" + Engine.fmt(c) + " = 0 is false)");
            } else {
                out.add("Not quadratic (a = 0): linear equation");
                out.add("x = " + Engine.fmt(finite(-c / b)));
            }
            return out.toArray(new String[0]);
        }
        double bb = finite(b * b);
        double ac4 = finite(4 * a * c);
        double disc = finite(bb - ac4);
        out.add("Discriminant = " + Engine.fmt(disc));
        if (disc > 0) {
            double sq = Math.sqrt(disc);
            double q = -0.5 * (b + Math.copySign(sq, b));      // avoids cancellation
            double x1 = finite(q / a);
            double x2 = finite(c / q);
            double lo = Math.min(x1, x2), hi = Math.max(x1, x2);
            out.add("Two real roots");
            out.add("x1 = " + Engine.fmt(lo));
            out.add("x2 = " + Engine.fmt(hi));
        } else if (disc == 0) {
            out.add("One repeated real root");
            out.add("x = " + Engine.fmt(finite(-b / (2 * a))));
        } else {
            double re = finite(-b / (2 * a));
            double im = finite(Math.sqrt(-disc) / (2 * Math.abs(a)));
            out.add("Two complex roots");
            String r = re == 0 ? "" : Engine.fmt(re) + " ";
            out.add("x1 = " + r + (re == 0 ? "" : "+ ") + Engine.fmt(im) + " i");
            out.add("x2 = " + r + (re == 0 ? "−" : "− ") + Engine.fmt(im) + " i");
        }
        return out.toArray(new String[0]);
    }

    // =====================================================================
    //  Statistics
    // =====================================================================

    /** Numbers separated by spaces, commas, semicolons or new lines. Use "." for decimals. Exact decimal values. */
    public static BigDecimal[] parseList(String s) throws CalcException {
        if (s == null || s.trim().isEmpty()) throw new CalcException("Enter at least one number");
        String[] tok = s.trim().replace('−', '-').split("[\\s,;]+");
        List<BigDecimal> out = new ArrayList<>();
        for (String t : tok) {
            if (t.isEmpty()) continue;
            if (!t.matches("[+-]?([0-9]+\\.?[0-9]*|\\.[0-9]+)([eE][+-]?[0-9]{1,3})?"))
                throw new CalcException("Not a number: " + (t.length() > 20 ? t.substring(0, 20) + "…" : t));
            BigDecimal v = new BigDecimal(t.startsWith("+") ? t.substring(1) : t);
            double d = v.doubleValue();
            if (!isFinite(d)) throw new CalcException("Number too large: " + t);
            out.add(v);
            if (out.size() > 10000) throw new CalcException("Too many numbers (max 10000)");
        }
        if (out.isEmpty()) throw new CalcException("Enter at least one number");
        return out.toArray(new BigDecimal[0]);
    }

    private static String num(BigDecimal v) throws CalcException {
        double d = v.doubleValue();
        if (!isFinite(d)) throw new CalcException("Overflow");
        return Engine.fmt(Engine.round15(d));
    }

    /** Statistics computed with exact decimal arithmetic, so typed decimals give the expected answers. */
    public static String[] stats(BigDecimal[] v) throws CalcException {
        int n = v.length;
        if (n == 0) throw new CalcException("Enter at least one number");
        MathContext mc = MathContext.DECIMAL128;
        BigDecimal count = BigDecimal.valueOf(n);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal x : v) sum = sum.add(x);
        BigDecimal mean = sum.divide(count, mc);
        BigDecimal m2 = BigDecimal.ZERO;
        for (BigDecimal x : v) {
            BigDecimal d = x.subtract(mean);
            m2 = m2.add(d.multiply(d));
        }
        BigDecimal[] sorted = v.clone();
        Arrays.sort(sorted);
        BigDecimal median = n % 2 == 1 ? sorted[n / 2]
                : sorted[n / 2 - 1].add(sorted[n / 2]).divide(BigDecimal.valueOf(2));
        BigDecimal min = sorted[0], max = sorted[n - 1];
        BigDecimal popVar = m2.divide(count, mc);
        String sampVar = "— (needs 2 or more values)", sampSd = sampVar;
        if (n > 1) {
            BigDecimal sv = m2.divide(BigDecimal.valueOf(n - 1), mc);
            sampVar = num(sv);
            sampSd = Engine.fmt(Engine.round15(Math.sqrt(sv.doubleValue())));
        }
        double popSd = Math.sqrt(popVar.doubleValue());
        if (!isFinite(popSd)) throw new CalcException("Overflow");
        return new String[]{
                "Count = " + n,
                "Sum = " + num(sum),
                "Mean = " + num(mean),
                "Median = " + num(median),
                "Min = " + num(min),
                "Max = " + num(max),
                "Range = " + num(max.subtract(min)),
                "Std dev, sample (n−1) = " + sampSd,
                "Std dev, population (n) = " + Engine.fmt(Engine.round15(popSd)),
                "Variance, sample = " + sampVar,
                "Variance, population = " + num(popVar)
        };
    }

    // =====================================================================
    //  Number bases
    // =====================================================================

    /** Returns {decimal, hexadecimal, binary, octal} for an integer typed in the given base. */
    public static String[] bases(String input, int radix) throws CalcException {
        String s = input == null ? "" : input.trim().replace("_", "").replace(" ", "");
        if (s.isEmpty()) throw new CalcException("Enter a number");
        boolean neg = false;
        if (s.startsWith("-") || s.startsWith("−")) { neg = true; s = s.substring(1); }
        if (s.isEmpty()) throw new CalcException("Enter a number");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 128 || Character.digit(c, radix) < 0)
                throw new CalcException("'" + c + "' is not a valid digit in base " + radix);
        }
        BigInteger v = new BigInteger(s, radix);
        if (v.bitLength() > 63) throw new CalcException("Too large (maximum 63 bits)");
        if (neg) v = v.negate();
        return new String[]{
                v.toString(10),
                v.toString(16).toUpperCase(),
                groupBits(v.toString(2)),
                v.toString(8)
        };
    }

    private static String groupBits(String bin) {
        String sign = "";
        if (bin.startsWith("-")) { sign = "-"; bin = bin.substring(1); }
        StringBuilder sb = new StringBuilder();
        int first = bin.length() % 4;
        if (first > 0) sb.append(bin, 0, first);
        for (int i = first; i < bin.length(); i += 4) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(bin, i, i + 4);
        }
        return sign + sb;
    }
}
