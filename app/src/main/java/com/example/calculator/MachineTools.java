package com.example.calculator;

import com.example.calculator.Engine.CalcException;

/** Machining helpers: taper, sine bar, thread data and G-code arc centres (pure Java, no Android classes). */
public final class MachineTools {

    private MachineTools() {}

    private static boolean isFinite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    private static void positive(double v, String name) throws CalcException {
        if (!isFinite(v) || v <= 0) throw new CalcException(name + " must be greater than 0");
    }

    private static double fin(double v) throws CalcException {
        if (Double.isNaN(v)) throw new CalcException("Math Error");
        if (Double.isInfinite(v)) throw new CalcException("Overflow");
        return Engine.round15(v);
    }

    // =====================================================================
    //  Taper
    // =====================================================================

    /** Returns {diameter change per unit length, half angle in degrees, included angle in degrees}. */
    public static double[] taper(double d1, double d2, double length) throws CalcException {
        if (!isFinite(d1) || !isFinite(d2) || d1 < 0 || d2 < 0) throw new CalcException("Diameters must be 0 or more");
        if (d1 <= d2) throw new CalcException("The large diameter must be bigger than the small diameter");
        positive(length, "Length");
        double diff = d1 - d2;
        double half = Math.toDegrees(Math.atan(diff / (2 * length)));
        return new double[]{fin(diff / length), fin(half), fin(2 * half)};
    }

    /** Diameter change over a length for a given included angle (full angle, both sides). */
    public static double diameterChange(double includedDeg, double length) throws CalcException {
        if (!isFinite(includedDeg) || includedDeg <= 0 || includedDeg >= 180)
            throw new CalcException("Included angle must be between 0 and 180 degrees");
        positive(length, "Length");
        return fin(2 * length * Math.tan(Math.toRadians(includedDeg / 2)));
    }

    // =====================================================================
    //  Sine bar
    // =====================================================================

    /** Gauge-block stack height for an angle: bar length x sin(angle). */
    public static double sineBarHeight(double bar, double angleDeg) throws CalcException {
        positive(bar, "Bar length");
        if (!isFinite(angleDeg) || angleDeg <= 0 || angleDeg >= 90)
            throw new CalcException("Angle must be between 0 and 90 degrees");
        return fin(bar * Engine.trig(0, angleDeg, Engine.DEG));
    }

    /** Angle in degrees for a gauge-block stack height: asin(height / bar length). */
    public static double sineBarAngle(double bar, double height) throws CalcException {
        positive(bar, "Bar length");
        positive(height, "Stack height");
        if (height > bar) throw new CalcException("Stack height cannot be more than the bar length");
        return fin(Math.toDegrees(Math.asin(Math.min(1.0, height / bar))));
    }

    // =====================================================================
    //  Threads (theoretical 60 degree form)
    // =====================================================================

    /**
     * Returns {pitch diameter, minor diameter (external thread), minor diameter (internal thread, basic),
     * thread depth (external), tap drill for the given percent of thread, best wire size,
     * wire size used, measurement over three wires}.
     * Pass wire = NaN to use the best wire size.
     */
    public static double[] thread(double major, double pitch, double percent, double wire) throws CalcException {
        positive(major, "Major diameter");
        positive(pitch, "Pitch");
        if (!isFinite(percent) || percent < 50 || percent > 100)
            throw new CalcException("Percent of thread must be between 50 and 100");
        if (major <= 1.3 * pitch) throw new CalcException("Diameter is too small for this pitch");
        double h = Math.sqrt(3.0) / 2.0 * pitch;              // sharp-V height
        double pitchDia = major - 0.75 * h;
        double minorExt = major - 2 * (17.0 / 24.0) * h;
        double minorInt = major - 1.25 * h;
        double depth = (17.0 / 24.0) * h;
        double tap = major - 1.5 * (percent / 100.0) * h;
        double best = pitch / Math.sqrt(3.0);
        double w;
        if (Double.isNaN(wire)) {
            w = best;
        } else {
            positive(wire, "Wire diameter");
            w = wire;
        }
        double over = pitchDia + 3 * w - h;
        if (tap <= 0 || minorExt <= 0) throw new CalcException("Diameter is too small for this pitch");
        return new double[]{fin(pitchDia), fin(minorExt), fin(minorInt), fin(depth), fin(tap), fin(best), fin(w), fin(over)};
    }

    // =====================================================================
    //  G-code arc (G2 / G3) centre from start, end and radius
    // =====================================================================

    /**
     * Returns {centre X, centre Y, I, J, sweep angle in degrees, arc length}. I and J are measured
     * from the start point to the centre (the usual G2 / G3 incremental form).
     * ccw = true is G3, false is G2. A positive radius means an arc of 180 degrees or less,
     * a negative radius an arc of more than 180 degrees (the Fanuc R convention).
     */
    public static double[] arc(double x1, double y1, double x2, double y2, double r, boolean ccw) throws CalcException {
        if (!isFinite(x1) || !isFinite(y1) || !isFinite(x2) || !isFinite(y2) || !isFinite(r))
            throw new CalcException("Invalid number");
        if (r == 0) throw new CalcException("Radius cannot be 0");
        double dx = x2 - x1, dy = y2 - y1;
        double chord = Math.hypot(dx, dy);
        if (chord == 0) throw new CalcException("Start and end are the same point (a full circle needs I and J)");
        double rad = Math.abs(r);
        if (chord > 2 * rad * (1 + 1e-12))
            throw new CalcException("Radius is too small: it must be at least " + Engine.fmt(Engine.round15(chord / 2)));
        double h = Math.sqrt(Math.max(0.0, rad * rad - chord * chord / 4.0));
        double nx = -dy / chord, ny = dx / chord;               // left of the direction start -> end
        double side = (ccw ? 1.0 : -1.0) * (r > 0 ? 1.0 : -1.0);
        double cx = (x1 + x2) / 2.0 + side * h * nx;
        double cy = (y1 + y2) / 2.0 + side * h * ny;
        double a1 = Math.atan2(y1 - cy, x1 - cx);
        double a2 = Math.atan2(y2 - cy, x2 - cx);
        double sweep = ccw ? a2 - a1 : a1 - a2;
        double full = 2 * Math.PI;
        while (sweep <= 1e-12) sweep += full;
        while (sweep > full + 1e-12) sweep -= full;
        return new double[]{fin(cx), fin(cy), fin(cx - x1), fin(cy - y1), fin(Math.toDegrees(sweep)), fin(rad * sweep)};
    }
}
