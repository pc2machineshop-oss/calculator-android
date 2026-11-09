package com.example.calculator;

import android.graphics.Typeface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.math.BigDecimal;
import java.util.Locale;

public final class ShopMathPanels {

    private ShopMathPanels() {}

    /** Shop tools: spindle speed, feed rate, bolt circle, inch fractions. */
    static final class ShopPanel {
        private final UiKit ui;
        private boolean metric = false;
        private boolean clockwise = false;
        private boolean fracMm = false;

        private Button btnUnits, btnDir, btnFracUnit;
        private TextView lblDia, lblSpeed, lblChip, lblFeed, lblPcd, lblCx, lblCy;
        private EditText etDia, etSpeed, etRpm, etFeedRpm, etFlutes, etChip, etFeed;
        private EditText etN, etPcd, etStart, etCx, etCy, etFrac;
        private TextView outRpm, outSpeed, outFeed, outChip, outBolt, outFrac;

        ShopPanel(UiKit ui) { this.ui = ui; }

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

        View build() {
            LinearLayout col = ui.column();
            TextView[] tmp = new TextView[1];

            btnUnits = ui.smallButton("", ui.pal.func);
            btnUnits.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { metric = !metric; applyUnits(); update(); }
            });
            LinearLayout.LayoutParams bp = ui.lpm(UiKit.MATCH, ui.dp(46), 0, 0);
            bp.topMargin = ui.dp(6);
            col.addView(btnUnits, bp);

            // ---- spindle speed
            col.addView(ui.heading("Spindle speed"));
            etDia = field(col, tmp, "Tool diameter", "diameter"); lblDia = tmp[0];
            etSpeed = field(col, tmp, "Cutting speed", "speed"); lblSpeed = tmp[0];
            outRpm = ui.valueText(18);
            col.addView(outRpm);
            etRpm = field(col, null, "Spindle speed (RPM)", "rpm");
            outSpeed = ui.valueText(18);
            col.addView(outSpeed);

            // ---- feed rate
            col.addView(ui.heading("Feed rate"));
            etFeedRpm = field(col, null, "Spindle speed (RPM)", "rpm");
            etFlutes = field(col, null, "Number of flutes / teeth", "flutes");
            etChip = field(col, tmp, "Chip load per tooth", "chip load"); lblChip = tmp[0];
            outFeed = ui.valueText(18);
            col.addView(outFeed);
            etFeed = field(col, tmp, "Or enter a feed rate to get the chip load", "feed"); lblFeed = tmp[0];
            outChip = ui.valueText(18);
            col.addView(outChip);

            // ---- bolt circle
            col.addView(ui.heading("Bolt circle"));
            etN = field(col, null, "Number of holes", "holes");
            etPcd = field(col, tmp, "Circle diameter", "diameter"); lblPcd = tmp[0];
            etStart = field(col, null, "Angle of hole 1 in degrees (0 = on the +X axis; blank = 0)", "start angle");
            etCx = field(col, tmp, "Centre X (blank = 0)", "X"); lblCx = tmp[0];
            etCy = field(col, tmp, "Centre Y (blank = 0)", "Y"); lblCy = tmp[0];
            btnDir = ui.smallButton("", ui.pal.func);
            btnDir.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { clockwise = !clockwise; applyUnits(); update(); }
            });
            LinearLayout.LayoutParams dp = ui.lpm(UiKit.MATCH, ui.dp(44), 0, 0);
            dp.topMargin = ui.dp(8);
            col.addView(btnDir, dp);
            outBolt = ui.valueText(15);
            outBolt.setTypeface(Typeface.MONOSPACE);
            col.addView(outBolt);

            // ---- fractions
            col.addView(ui.heading("Fractions ↔ decimal ↔ mm"));
            btnFracUnit = ui.smallButton("", ui.pal.func);
            btnFracUnit.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { fracMm = !fracMm; applyUnits(); update(); }
            });
            col.addView(btnFracUnit, ui.lpm(UiKit.MATCH, ui.dp(44), 0, 0));
            col.addView(ui.label("Value (examples: 5/16   1 3/8   0.3125   12.7)"));
            etFrac = ui.textField("fraction or decimal", false, false);
            etFrac.addTextChangedListener(UiKit.watcher(updater()));
            col.addView(etFrac);
            outFrac = ui.valueText(15);
            outFrac.setTypeface(Typeface.MONOSPACE);
            col.addView(outFrac);

            applyUnits();
            update();
            return ui.scroll(col);
        }

        private void applyUnits() {
            String len = metric ? "mm" : "inch";
            btnUnits.setText("Units: " + (metric ? "Metric (mm, m/min)" : "Inch (in, SFM)") + "   (tap to change)");
            lblDia.setText("Tool diameter (" + len + ")");
            lblSpeed.setText("Cutting speed (" + (metric ? "m/min" : "SFM") + ")");
            lblChip.setText("Chip load per tooth (" + len + ")");
            lblFeed.setText("Or enter a feed rate (" + (metric ? "mm/min" : "in/min") + ") to get the chip load");
            lblPcd.setText("Circle diameter (" + len + ")");
            lblCx.setText("Centre X (" + len + ", blank = 0)");
            lblCy.setText("Centre Y (" + len + ", blank = 0)");
            btnDir.setText("Direction: " + (clockwise ? "clockwise" : "counter-clockwise") + "   (tap to change)");
            btnFracUnit.setText("Value is in: " + (fracMm ? "millimetres" : "inches") + "   (tap to change)");
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
            String lenU = metric ? "mm" : "in";
            int posDec = metric ? 3 : 4;

            // spindle speed
            Double dia = UiKit.parse(etDia), spd = UiKit.parse(etSpeed), rpm = UiKit.parse(etRpm);
            if (dia == null || spd == null) showOk(outRpm, "RPM = —");
            else try { showOk(outRpm, "RPM = " + Tools.fixed(Tools.rpmFor(spd, dia, metric), 1)); }
            catch (Engine.CalcException e) { showError(outRpm, e.getMessage()); }
            if (dia == null || rpm == null) showOk(outSpeed, "Cutting speed = —");
            else try { showOk(outSpeed, "Cutting speed = " + Tools.fixed(Tools.speedFor(rpm, dia, metric), 1) + (metric ? " m/min" : " SFM")); }
            catch (Engine.CalcException e) { showError(outSpeed, e.getMessage()); }

            // feed rate and chip load
            Double frpm = UiKit.parse(etFeedRpm), fl = UiKit.parse(etFlutes), chip = UiKit.parse(etChip), feed = UiKit.parse(etFeed);
            if (frpm == null || fl == null || chip == null) showOk(outFeed, "Feed rate = —");
            else try { showOk(outFeed, "Feed rate = " + Tools.fixed(Tools.feedFor(frpm, chip, fl), metric ? 1 : 3) + (metric ? " mm/min" : " in/min")); }
            catch (Engine.CalcException e) { showError(outFeed, e.getMessage()); }
            if (frpm == null || fl == null || feed == null) showOk(outChip, "Chip load = —");
            else try { showOk(outChip, "Chip load = " + Tools.fixed(Tools.chipLoadFor(feed, frpm, fl), 4) + " " + lenU + "/tooth"); }
            catch (Engine.CalcException e) { showError(outChip, e.getMessage()); }

            // bolt circle
            Double n = UiKit.parse(etN), pcd = UiKit.parse(etPcd);
            Double start = UiKit.parseOrZero(etStart), cx = UiKit.parseOrZero(etCx), cy = UiKit.parseOrZero(etCy);
            if (n == null || pcd == null || start == null || cx == null || cy == null) {
                showOk(outBolt, "Enter the number of holes and the circle diameter.");
            } else if (n != Math.rint(n)) {
                showError(outBolt, "Number of holes must be a whole number");
            } else {
                try {
                    double[][] h = Tools.boltCircle((int) Math.max(-1, Math.min(1000, n)), pcd, start, cx, cy, clockwise);
                    StringBuilder sb = new StringBuilder();
                    sb.append("Hole   X").append(metric ? "         " : "          ").append("Y\n");
                    for (int i = 0; i < h.length; i++) {
                        sb.append(String.format(Locale.US, "%3d    %s   %s%n", i + 1,
                                Tools.fixed(h[i][0], posDec), Tools.fixed(h[i][1], posDec)));
                    }
                    showOk(outBolt, sb.toString().trim());
                } catch (Engine.CalcException e) {
                    showError(outBolt, e.getMessage());
                }
            }

            // fractions
            String text = etFrac.getText().toString();
            if (text.trim().isEmpty()) {
                showOk(outFrac, "");
            } else {
                try {
                    double v = Tools.parseFraction(text);
                    double inches = fracMm ? v / 25.4 : v;
                    double mm = fracMm ? v : v * 25.4;
                    StringBuilder sb = new StringBuilder();
                    sb.append("Inches      = ").append(Engine.fmt(Engine.round15(inches))).append('\n');
                    sb.append("Millimetres = ").append(Engine.fmt(Engine.round15(mm))).append('\n');
                    sb.append("Nearest fraction of an inch:\n");
                    int[] dens = {16, 32, 64, 128};
                    for (int d : dens) {
                        double err = Tools.nearestFractionError(inches, d);
                        String errText = Math.abs(err) < 1e-9 ? "exact"
                                : "error " + (err > 0 ? "+" : "") + Tools.fixed(err, 5) + " in";
                        sb.append(String.format(Locale.US, "  1/%-3d  %-9s %s%n", d, Tools.nearestFraction(inches, d), errText));
                    }
                    showOk(outFrac, sb.toString().trim());
                } catch (Engine.CalcException e) {
                    showError(outFrac, e.getMessage());
                }
            }
        }
    }

    /** Math tools: quadratic equation, statistics, number bases. */
    static final class MathPanel {
        private static final String[] BASE_NAMES = {"Decimal (10)", "Hexadecimal (16)", "Binary (2)", "Octal (8)"};
        private static final int[] BASE_RADIX = {10, 16, 2, 8};

        private final UiKit ui;
        private EditText etA, etB, etC, etList, etBase;
        private Spinner spBase;
        private TextView outQuad, outStats, outBase;

        MathPanel(UiKit ui) { this.ui = ui; }

        private Runnable updater() {
            return new Runnable() { @Override public void run() { update(); } };
        }

        private EditText num(LinearLayout col, String label, String hint) {
            col.addView(ui.label(label));
            EditText e = ui.numberField(hint);
            e.addTextChangedListener(UiKit.watcher(updater()));
            col.addView(e);
            return e;
        }

        View build() {
            LinearLayout col = ui.column();

            col.addView(ui.heading("Quadratic equation   a·x² + b·x + c = 0"));
            etA = num(col, "a", "a");
            etB = num(col, "b", "b");
            etC = num(col, "c", "c");
            outQuad = ui.valueText(16);
            col.addView(outQuad);

            col.addView(ui.heading("Statistics"));
            col.addView(ui.note("Separate numbers with spaces, commas, semicolons or new lines. Use . for decimals."));
            etList = ui.textField("e.g. 2 4 4 4 5 5 7 9", true, false);
            etList.addTextChangedListener(UiKit.watcher(updater()));
            col.addView(etList);
            outStats = ui.valueText(15);
            col.addView(outStats);

            col.addView(ui.heading("Number bases"));
            col.addView(ui.label("Type a whole number in the base chosen below"));
            etBase = ui.textField("number", false, true);
            etBase.addTextChangedListener(UiKit.watcher(updater()));
            col.addView(etBase);
            spBase = ui.spinner(BASE_NAMES);
            spBase.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { update(); }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
            col.addView(ui.boxed(spBase));
            outBase = ui.valueText(15);
            outBase.setTypeface(Typeface.MONOSPACE);
            col.addView(outBase);

            update();
            return ui.scroll(col);
        }

        private void show(TextView out, String msg, boolean error) {
            out.setTextColor(error ? ui.pal.error : ui.pal.text);
            out.setText(msg);
        }

        private static String join(String[] lines) {
            StringBuilder sb = new StringBuilder();
            for (String l : lines) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(l);
            }
            return sb.toString();
        }

        private void update() {
            // quadratic
            Double a = UiKit.parse(etA), b = UiKit.parse(etB), c = UiKit.parse(etC);
            if (a == null || b == null || c == null) {
                show(outQuad, "Enter a, b and c", false);
            } else {
                try { show(outQuad, join(Tools.quadratic(a, b, c)), false); }
                catch (Engine.CalcException e) { show(outQuad, e.getMessage(), true); }
            }

            // statistics
            String list = etList.getText().toString();
            if (list.trim().isEmpty()) {
                show(outStats, "", false);
            } else {
                try {
                    BigDecimal[] v = Tools.parseList(list);
                    show(outStats, join(Tools.stats(v)), false);
                } catch (Engine.CalcException e) { show(outStats, e.getMessage(), true); }
            }

            // bases
            String text = etBase.getText().toString();
            int pos = spBase.getSelectedItemPosition();
            if (text.trim().isEmpty() || pos < 0 || pos >= BASE_RADIX.length) {
                show(outBase, "", false);
            } else {
                try {
                    String[] r = Tools.bases(text, BASE_RADIX[pos]);
                    show(outBase, "Decimal = " + r[0] + "\nHex     = " + r[1] + "\nBinary  = " + r[2] + "\nOctal   = " + r[3], false);
                } catch (Engine.CalcException e) { show(outBase, e.getMessage(), true); }
            }
        }
    }
}
