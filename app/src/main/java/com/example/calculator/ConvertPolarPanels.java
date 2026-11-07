package com.example.calculator;

import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.graphics.Typeface;

public final class ConvertPolarPanels {

    private ConvertPolarPanels() {}

    /** Gives the Polar panel access to the calculator's angle unit (DEG / RAD / GRAD). */
    interface AngleHost {
        int angleMode();
        void cycleAngleMode();
    }

    /** Unit converter screen. */
    static final class ConvertPanel {
        private final UiKit ui;
        private Spinner spCat, spFrom, spTo;
        private EditText etConv;
        private TextView tvOut, tvAll;
        private boolean suppress = false;

        ConvertPanel(UiKit ui) { this.ui = ui; }

        View build() {
            LinearLayout col = ui.column();

            col.addView(ui.label("Category"));
            spCat = ui.spinner(Engine.CATEGORIES);
            col.addView(ui.boxed(spCat));

            col.addView(ui.label("Value"));
            etConv = ui.numberField("Enter a number");
            etConv.setText("1");
            col.addView(etConv);

            col.addView(ui.label("From"));
            spFrom = ui.spinner(Engine.unitNames(0));
            col.addView(ui.boxed(spFrom));

            Button swap = ui.smallButton("Swap From / To", ui.pal.func);
            LinearLayout.LayoutParams sp = ui.lpm(UiKit.MATCH, ui.dp(44), 0, 0);
            sp.topMargin = ui.dp(8);
            col.addView(swap, sp);

            col.addView(ui.label("To"));
            spTo = ui.spinner(Engine.unitNames(0));
            col.addView(ui.boxed(spTo));

            tvOut = ui.valueText(30);
            tvOut.setTypeface(Typeface.DEFAULT_BOLD);
            tvOut.setPadding(0, ui.dp(18), 0, ui.dp(4));
            col.addView(tvOut);

            col.addView(ui.heading("All units"));
            tvAll = ui.valueText(15);
            tvAll.setTypeface(Typeface.MONOSPACE);
            col.addView(tvAll);

            spCat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (!suppress) fillUnits(position);
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
            AdapterView.OnItemSelectedListener upd = new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (!suppress) update();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            };
            spFrom.setOnItemSelectedListener(upd);
            spTo.setOnItemSelectedListener(upd);
            swap.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    int a = spFrom.getSelectedItemPosition();
                    int b = spTo.getSelectedItemPosition();
                    suppress = true;
                    spFrom.setSelection(b);
                    spTo.setSelection(a);
                    suppress = false;
                    update();
                }
            });
            etConv.addTextChangedListener(UiKit.watcher(new Runnable() {
                @Override public void run() { update(); }
            }));

            fillUnits(0);
            return ui.scroll(col);
        }

        private void fillUnits(int cat) {
            suppress = true;
            String[] names = Engine.unitNames(cat);
            ui.setItems(spFrom, names);
            ui.setItems(spTo, names);
            spFrom.setSelection(0);
            spTo.setSelection(names.length > 1 ? 1 : 0);
            suppress = false;
            update();
        }

        private void update() {
            int cat = spCat.getSelectedItemPosition();
            int from = spFrom.getSelectedItemPosition();
            int to = spTo.getSelectedItemPosition();
            if (cat < 0 || cat >= Engine.CATEGORIES.length || from < 0 || to < 0) return;
            String[] names = Engine.unitNames(cat);
            if (from >= names.length || to >= names.length) return;
            Double v = UiKit.parse(etConv);
            tvOut.setTextColor(ui.pal.text);
            if (v == null) {
                tvOut.setText("—");
                tvAll.setText("");
                return;
            }
            try {
                double r = Engine.convert(cat, from, to, v);
                tvOut.setText(Engine.fmt(r) + " " + names[to]);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < names.length; i++) {
                    String val;
                    try {
                        val = Engine.fmt(Engine.convert(cat, from, i, v));
                    } catch (Engine.CalcException e) {
                        val = "—";
                    }
                    sb.append(val).append("  ").append(names[i]).append('\n');
                }
                tvAll.setText(sb.toString().trim());
            } catch (Engine.CalcException e) {
                tvOut.setTextColor(ui.pal.error);
                tvOut.setText(e.getMessage());
                tvAll.setText("");
            }
        }
    }

    /** Polar <-> rectangular screen. */
    static final class PolarPanel {
        private final UiKit ui;
        private final AngleHost host;
        private Button btnAngle;
        private EditText etX, etY, etR, etTh;
        private TextView tvR, tvTh, tvTh360, tvX, tvY, tvNote;

        PolarPanel(UiKit ui, AngleHost host) {
            this.ui = ui;
            this.host = host;
        }

        View build() {
            LinearLayout col = ui.column();

            btnAngle = ui.smallButton("", ui.pal.func);
            btnAngle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    host.cycleAngleMode();
                    refresh();
                }
            });
            LinearLayout.LayoutParams bp = ui.lpm(UiKit.MATCH, ui.dp(46), 0, 0);
            bp.topMargin = ui.dp(6);
            col.addView(btnAngle, bp);

            Runnable r2p = new Runnable() { @Override public void run() { updateRectToPolar(); } };
            Runnable p2r = new Runnable() { @Override public void run() { updatePolarToRect(); } };

            col.addView(ui.heading("Rectangular  →  Polar"));
            col.addView(ui.label("x"));
            etX = ui.numberField("x");
            col.addView(etX);
            col.addView(ui.label("y"));
            etY = ui.numberField("y");
            col.addView(etY);
            tvR = ui.valueText(20);
            tvTh = ui.valueText(20);
            tvTh360 = ui.valueText(20);
            tvNote = ui.valueText(14);
            tvNote.setTextColor(ui.pal.accent);
            col.addView(tvR);
            col.addView(tvTh);
            col.addView(tvTh360);
            col.addView(tvNote);

            col.addView(ui.heading("Polar  →  Rectangular"));
            col.addView(ui.label("r (radius)"));
            etR = ui.numberField("r");
            col.addView(etR);
            col.addView(ui.label("θ (angle, in the unit chosen above)"));
            etTh = ui.numberField("θ");
            col.addView(etTh);
            tvX = ui.valueText(20);
            tvY = ui.valueText(20);
            col.addView(tvX);
            col.addView(tvY);

            etX.addTextChangedListener(UiKit.watcher(r2p));
            etY.addTextChangedListener(UiKit.watcher(r2p));
            etR.addTextChangedListener(UiKit.watcher(p2r));
            etTh.addTextChangedListener(UiKit.watcher(p2r));
            refresh();
            return ui.scroll(col);
        }

        void refresh() {
            btnAngle.setText("Angle unit: " + Engine.MODE_NAMES[host.angleMode()] + "   (tap to change)");
            updateRectToPolar();
            updatePolarToRect();
        }

        private void updateRectToPolar() {
            int mode = host.angleMode();
            Double x = UiKit.parse(etX), y = UiKit.parse(etY);
            tvNote.setText("");
            if (x == null || y == null) {
                tvR.setText("r = —");
                tvTh.setText("θ = —");
                tvTh360.setText("");
                return;
            }
            String unit = Engine.MODE_NAMES[mode];
            String[] half = {"180", "π", "200"};
            String[] full = {"360", "2π", "400"};
            try {
                double[] a = Engine.rectToPolar(x, y, mode);
                tvR.setText("r = " + Engine.fmt(a[0]));
                tvTh.setText("θ (−" + half[mode] + " to " + half[mode] + ") = " + Engine.fmt(a[1]) + " " + unit);
                tvTh360.setText("θ (0 to " + full[mode] + ") = " + Engine.fmt(a[2]) + " " + unit);
                if (x == 0 && y == 0) tvNote.setText("At the origin the angle is undefined (shown as 0).");
            } catch (Engine.CalcException e) {
                tvR.setText(e.getMessage());
                tvTh.setText("");
                tvTh360.setText("");
            }
        }

        private void updatePolarToRect() {
            Double r = UiKit.parse(etR), t = UiKit.parse(etTh);
            if (r == null || t == null) {
                tvX.setText("x = —");
                tvY.setText("y = —");
                return;
            }
            try {
                double[] a = Engine.polarToRect(r, t, host.angleMode());
                tvX.setText("x = " + Engine.fmt(a[0]));
                tvY.setText("y = " + Engine.fmt(a[1]));
            } catch (Engine.CalcException e) {
                tvX.setText(e.getMessage());
                tvY.setText("");
            }
        }
    }
}
