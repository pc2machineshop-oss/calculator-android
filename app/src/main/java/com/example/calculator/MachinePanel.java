package com.example.calculator;

import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Machine tab: taper, sine bar, thread data, G-code arc centre. */
public final class MachinePanel {
    private final UiKit ui;
    private boolean metric = false;
    private boolean ccw = true;

    private Button btnUnits, btnDir;
    private TextView lblD1, lblD2, lblLen, lblLen2, lblBar, lblHt, lblMajor, lblPitch, lblWire;
    private TextView lblX1, lblY1, lblX2, lblY2, lblR;
    private EditText etD1, etD2, etLen, etInc, etLen2, etBar, etAng, etHt;
    private EditText etMajor, etPitch, etPct, etWire;
    private EditText etX1, etY1, etX2, etY2, etR;
    private TextView outTaper, outDelta, outSineH, outSineA, outThread, outArc;

    public MachinePanel(UiKit ui) { this.ui = ui; }

    private Runnable updater() {
        return new Runnable() { @Override public void run() { update(); } };
    }

    private EditText field(LinearLayout col, TextView[] labelOut, String label, String hint) {
        TextView l = ui.label(label);
        col.addView(l);
        if (labelOut != null) labelOut[0] = l;
        EditText e = ui.numberField(hint);
        e.addTextChangedListener(UiKit.watcher(updater()));
        col.addView(e);
        return e;
    }

    public View build() {
        LinearLayout col = ui.column();
        TextView[] tmp = new TextView[1];

        btnUnits = ui.smallButton("", ui.pal.func);
        btnUnits.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { metric = !metric; applyUnits(); update(); }
        });
        LinearLayout.LayoutParams bp = ui.lpm(UiKit.MATCH, ui.dp(46), 0, 0);
        bp.topMargin = ui.dp(6);
        col.addView(btnUnits, bp);
        col.addView(ui.note("Standard formulas for ideal shapes. Always check against your machine data, tap chart and drawing."));

        // ---- taper
        col.addView(ui.heading("Taper"));
        etD1 = field(col, tmp, "Large diameter", "large diameter"); lblD1 = tmp[0];
        etD2 = field(col, tmp, "Small diameter", "small diameter"); lblD2 = tmp[0];
        etLen = field(col, tmp, "Length of taper", "length"); lblLen = tmp[0];
        outTaper = ui.valueText(16);
        col.addView(outTaper);
        col.addView(ui.label("Or: included angle (degrees, both sides) and length to get the diameter change"));
        etInc = field(col, null, "Included angle (degrees)", "angle");
        etLen2 = field(col, tmp, "Length", "length"); lblLen2 = tmp[0];
        outDelta = ui.valueText(16);
        col.addView(outDelta);

        // ---- sine bar
        col.addView(ui.heading("Sine bar"));
        etBar = field(col, tmp, "Sine bar length (centre to centre)", "bar length"); lblBar = tmp[0];
        etAng = field(col, null, "Angle wanted (degrees)", "angle");
        outSineH = ui.valueText(16);
        col.addView(outSineH);
        etHt = field(col, tmp, "Or: gauge block stack height", "stack height"); lblHt = tmp[0];
        outSineA = ui.valueText(16);
        col.addView(outSineA);

        // ---- thread data
        col.addView(ui.heading("Thread data (60° thread)"));
        etMajor = field(col, tmp, "Major (nominal) diameter", "major diameter"); lblMajor = tmp[0];
        etPitch = field(col, tmp, "Pitch", "pitch"); lblPitch = tmp[0];
        etPct = field(col, null, "Percent of thread for the tap drill (blank = 75)", "75");
        etWire = field(col, tmp, "Wire diameter for 3-wire measuring (blank = best wire size)", "wire"); lblWire = tmp[0];
        outThread = ui.valueText(15);
        outThread.setTypeface(Typeface.MONOSPACE);
        col.addView(outThread);

        // ---- G-code arc
        col.addView(ui.heading("G-code arc: I and J from a radius"));
        etX1 = field(col, tmp, "Start X", "X start"); lblX1 = tmp[0];
        etY1 = field(col, tmp, "Start Y", "Y start"); lblY1 = tmp[0];
        etX2 = field(col, tmp, "End X", "X end"); lblX2 = tmp[0];
        etY2 = field(col, tmp, "End Y", "Y end"); lblY2 = tmp[0];
        etR = field(col, tmp, "Radius (positive: arc up to 180°, negative: arc over 180°)", "radius"); lblR = tmp[0];
        btnDir = ui.smallButton("", ui.pal.func);
        btnDir.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ccw = !ccw; applyUnits(); update(); }
        });
        LinearLayout.LayoutParams dp = ui.lpm(UiKit.MATCH, ui.dp(44), 0, 0);
        dp.topMargin = ui.dp(8);
        col.addView(btnDir, dp);
        outArc = ui.valueText(15);
        outArc.setTypeface(Typeface.MONOSPACE);
        col.addView(outArc);
        col.addView(ui.note("The arc runs in the XY plane (G17) from the start point to the end point."));

        applyUnits();
        update();
        return ui.scroll(col);
    }

    private void applyUnits() {
        String len = metric ? "mm" : "inch";
        btnUnits.setText("Units: " + (metric ? "Metric (mm)" : "Inch") + "   (tap to change)");
        lblD1.setText("Large diameter (" + len + ")");
        lblD2.setText("Small diameter (" + len + ")");
        lblLen.setText("Length of taper (" + len + ")");
        lblLen2.setText("Length (" + len + ")");
        lblBar.setText("Sine bar length, centre to centre (" + len + ")");
        lblHt.setText("Or: gauge block stack height (" + len + ")");
        lblMajor.setText("Major (nominal) diameter (" + len + ")");
        lblPitch.setText(metric ? "Pitch (mm)" : "Threads per inch (TPI)");
        lblWire.setText("Wire diameter (" + len + ", blank = best wire size)");
        lblX1.setText("Start X (" + len + ")");
        lblY1.setText("Start Y (" + len + ")");
        lblX2.setText("End X (" + len + ")");
        lblY2.setText("End Y (" + len + ")");
        lblR.setText("Radius (" + len + "; positive: arc up to 180°, negative: arc over 180°)");
        btnDir.setText("Direction: " + (ccw ? "counter-clockwise (G3)" : "clockwise (G2)") + "   (tap to change)");
    }

    private void showError(TextView out, String msg) {
        out.setTextColor(ui.pal.error);
        out.setText(msg);
    }

    private void showOk(TextView out, String msg) {
        out.setTextColor(ui.pal.text);
        out.setText(msg);
    }

    private void update() {
        String len = metric ? "mm" : "in";
        int dec = metric ? 3 : 4;

        // taper
        Double d1 = UiKit.parse(etD1), d2 = UiKit.parse(etD2), tl = UiKit.parse(etLen);
        if (d1 == null || d2 == null || tl == null) {
            showOk(outTaper, "Enter both diameters and the length.");
        } else {
            try {
                double[] t = MachineTools.taper(d1, d2, tl);
                StringBuilder sb = new StringBuilder();
                sb.append("Taper = ").append(Tools.fixed(t[0], 5)).append(' ').append(len).append(" per ").append(len).append('\n');
                sb.append("Ratio = 1 : ").append(Engine.fmt(Engine.round15(1.0 / t[0]))).append('\n');
                if (!metric) sb.append("Taper per foot = ").append(Tools.fixed(t[0] * 12, 4)).append(" in\n");
                sb.append("Half angle (each side) = ").append(Tools.fixed(t[1], 4)).append("°\n");
                sb.append("Included angle = ").append(Tools.fixed(t[2], 4)).append('°');
                showOk(outTaper, sb.toString());
            } catch (Engine.CalcException e) {
                showError(outTaper, e.getMessage());
            }
        }
        Double inc = UiKit.parse(etInc), l2 = UiKit.parse(etLen2);
        if (inc == null || l2 == null) {
            showOk(outDelta, "");
        } else {
            try {
                double dd = MachineTools.diameterChange(inc, l2);
                showOk(outDelta, "Diameter change = " + Tools.fixed(dd, dec + 1) + " " + len + " over " + Engine.fmt(l2) + " " + len);
            } catch (Engine.CalcException e) {
                showError(outDelta, e.getMessage());
            }
        }

        // sine bar
        Double bar = UiKit.parse(etBar), ang = UiKit.parse(etAng), ht = UiKit.parse(etHt);
        if (bar == null || ang == null) {
            showOk(outSineH, "Stack height = —");
        } else {
            try {
                double h = MachineTools.sineBarHeight(bar, ang);
                String warn = ang > 45 ? "\nNote: a sine bar is less accurate above 45°." : "";
                showOk(outSineH, "Stack height = " + Tools.fixed(h, dec + 1) + " " + len + warn);
            } catch (Engine.CalcException e) {
                showError(outSineH, e.getMessage());
            }
        }
        if (bar == null || ht == null) {
            showOk(outSineA, "Angle = —");
        } else {
            try {
                double a = MachineTools.sineBarAngle(bar, ht);
                showOk(outSineA, "Angle = " + Tools.fixed(a, 4) + "°");
            } catch (Engine.CalcException e) {
                showError(outSineA, e.getMessage());
            }
        }

        // thread data
        Double major = UiKit.parse(etMajor), pitchIn = UiKit.parse(etPitch);
        Double pct = etPct.getText().toString().trim().isEmpty() ? Double.valueOf(75) : UiKit.parse(etPct);
        Double wire = etWire.getText().toString().trim().isEmpty() ? Double.valueOf(Double.NaN) : UiKit.parse(etWire);
        if (major == null || pitchIn == null || pct == null || wire == null) {
            showOk(outThread, "Enter the diameter and the " + (metric ? "pitch." : "threads per inch."));
        } else if (pitchIn <= 0) {
            showError(outThread, (metric ? "Pitch" : "Threads per inch") + " must be greater than 0");
        } else {
            try {
                double pitch = metric ? pitchIn : 1.0 / pitchIn;
                double[] t = MachineTools.thread(major, pitch, pct, wire);
                int td = metric ? 3 : 4;
                StringBuilder sb = new StringBuilder();
                sb.append(String.format(Locale.US, "Pitch diameter      %s%n", Tools.fixed(t[0], td)));
                sb.append(String.format(Locale.US, "Minor dia, external %s%n", Tools.fixed(t[1], td)));
                sb.append(String.format(Locale.US, "Minor dia, internal %s%n", Tools.fixed(t[2], td)));
                sb.append(String.format(Locale.US, "Thread depth        %s%n", Tools.fixed(t[3], td + 1)));
                sb.append(String.format(Locale.US, "Tap drill (%s%%)    %s%n", Engine.fmt(pct), Tools.fixed(t[4], td)));
                sb.append(String.format(Locale.US, "Best wire size      %s%n", Tools.fixed(t[5], td + 1)));
                sb.append(String.format(Locale.US, "Over 3 wires (%s)  %s", Tools.fixed(t[6], td + 1), Tools.fixed(t[7], td + 1)));
                showOk(outThread, sb.toString());
            } catch (Engine.CalcException e) {
                showError(outThread, e.getMessage());
            }
        }

        // G-code arc
        Double x1 = UiKit.parse(etX1), y1 = UiKit.parse(etY1), x2 = UiKit.parse(etX2), y2 = UiKit.parse(etY2), r = UiKit.parse(etR);
        if (x1 == null || y1 == null || x2 == null || y2 == null || r == null) {
            showOk(outArc, "Enter start, end and radius.");
        } else {
            try {
                double[] g = MachineTools.arc(x1, y1, x2, y2, r, ccw);
                String code = String.format(Locale.US, "%s X%s Y%s I%s J%s", ccw ? "G3" : "G2",
                        Tools.fixed(x2, dec), Tools.fixed(y2, dec), Tools.fixed(g[2], dec), Tools.fixed(g[3], dec));
                String codeR = String.format(Locale.US, "%s X%s Y%s R%s", ccw ? "G3" : "G2",
                        Tools.fixed(x2, dec), Tools.fixed(y2, dec), Tools.fixed(r, dec));
                StringBuilder sb = new StringBuilder();
                sb.append(code).append('\n').append(codeR).append("\n\n");
                sb.append("Centre  X").append(Tools.fixed(g[0], dec)).append("  Y").append(Tools.fixed(g[1], dec)).append('\n');
                sb.append("Sweep   ").append(Tools.fixed(g[4], 3)).append("°\n");
                sb.append("Length  ").append(Tools.fixed(g[5], dec));
                showOk(outArc, sb.toString());
            } catch (Engine.CalcException e) {
                showError(outArc, e.getMessage());
            }
        }
    }
}
