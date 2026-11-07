package com.example.calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    // ---- colours
    private static final int BG = 0xFF15171B;
    private static final int C_DIGIT = 0xFF2F3640;
    private static final int C_OP = 0xFFFF9800;
    private static final int C_FUNC = 0xFF455A64;
    private static final int C_MEM = 0xFF37474F;
    private static final int C_CLEAR = 0xFFC62828;
    private static final int C_EQ = 0xFF2E7D32;
    private static final int C_ACTIVE = 0xFF1E88E5;
    private static final int C_TAB_OFF = 0xFF2A2F37;

    private static class KeyDef {
        final String label, key, invLabel, invKey;
        final int color;
        KeyDef(String label, String key, String invLabel, String invKey, int color) {
            this.label = label; this.key = key; this.invLabel = invLabel; this.invKey = invKey; this.color = color;
        }
    }

    private static KeyDef k(String label, String key, int color) { return new KeyDef(label, key, null, null, color); }
    private static KeyDef ki(String label, String key, String il, String ik, int color) { return new KeyDef(label, key, il, ik, color); }

    private final Engine.Calc calc = new Engine.Calc();
    private final List<Button> keyButtons = new ArrayList<>();
    private final Button[] tabButtons = new Button[4];
    private final View[] panels = new View[4];
    private LinearLayout root;
    private float density;

    // calculator display
    private TextView tvStatus, tvSmall, tvBig;

    // converter
    private Spinner spCat, spFrom, spTo;
    private EditText etConv;
    private TextView tvConvOut, tvConvAll;
    private boolean suppress = false;

    // history
    private History history = new History();
    private LinearLayout historyList;

    // polar / rectangular
    private Button btnAngle;
    private EditText etX, etY, etR, etTh;
    private TextView tvPR, tvPTh, tvPTh360, tvPX, tvPY, tvPNote;

    // =====================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loadState();
        buildUi();
        showTab(0);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveState();
    }

    private void loadState() {
        SharedPreferences sp = getSharedPreferences("calc", MODE_PRIVATE);
        int m = sp.getInt("mode", 0);
        calc.mode = (m >= 0 && m <= 2) ? m : 0;
        calc.memSet = sp.getBoolean("memSet", false);
        try {
            calc.mem = Double.parseDouble(sp.getString("mem", "0"));
        } catch (Exception e) {
            calc.mem = 0;
            calc.memSet = false;
        }
        if (Double.isNaN(calc.mem) || Double.isInfinite(calc.mem)) {
            calc.mem = 0;
            calc.memSet = false;
        }
        history = History.parse(sp.getString("history", ""));
    }

    private void saveState() {
        getSharedPreferences("calc", MODE_PRIVATE).edit()
                .putInt("mode", calc.mode)
                .putBoolean("memSet", calc.memSet)
                .putString("mem", Double.toString(calc.mem))
                .putString("history", history.serialize())
                .apply();
    }

    // =====================================================================
    //  UI helpers
    // =====================================================================
    private int dp(float v) { return (int) (v * density + 0.5f); }

    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    private LinearLayout.LayoutParams lpm(int w, int h, float weight, int marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        int m = dp(marginDp);
        p.setMargins(m, m, m, m);
        return p;
    }

    private GradientDrawable shape(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        return g;
    }

    private RippleDrawable keyBg(int color) {
        return new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), shape(color), null);
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFF9AA5B1);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setPadding(0, dp(10), 0, dp(2));
        return t;
    }

    private TextView heading(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFB74D);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private TextView valueText(int sp) {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private EditText numberField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF6B7683);
        e.setTextColor(Color.WHITE);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        return e;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        setItems(s, items);
        return s;
    }

    private void setItems(Spinner s, String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
    }

    private Button smallButton(String text, int color) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setStateListAnimator(null);
        b.setBackground(keyBg(color));
        return b;
    }

    private TextWatcher watcher(final Runnable r) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { r.run(); }
            @Override public void afterTextChanged(Editable s) { }
        };
    }

    /** Reads a number from a field. Accepts comma as decimal separator. Returns null if not a valid number yet. */
    private Double parseField(EditText e) {
        String t = e.getText().toString().trim().replace(',', '.');
        if (t.isEmpty() || t.equals("-") || t.equals(".") || t.equals("-.")) return null;
        try {
            double v = Double.parseDouble(t);
            if (Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // =====================================================================
    //  Main layout and tabs
    // =====================================================================
    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(6), dp(6), dp(6), dp(6));

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Calc", "Convert", "Polar", "History"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Button b = smallButton(names[i], C_TAB_OFF);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showTab(idx); }
            });
            tabButtons[i] = b;
            tabRow.addView(b, lpm(0, dp(44), 1, 2));
        }
        root.addView(tabRow, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));

        FrameLayout frame = new FrameLayout(this);
        panels[0] = buildCalcPanel();
        panels[1] = buildConverterPanel();
        panels[2] = buildPolarPanel();
        panels[3] = buildHistoryPanel();
        for (View p : panels) {
            frame.addView(p, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        root.addView(frame, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void showTab(int idx) {
        for (int i = 0; i < panels.length; i++) {
            panels[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            tabButtons[i].setBackground(keyBg(i == idx ? C_ACTIVE : C_TAB_OFF));
        }
        if (idx == 2) updatePolarAll();
        if (idx == 3) rebuildHistory();
        if (idx == 0 || idx == 3) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && root != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        }
    }

    // =====================================================================
    //  Calculator panel
    // =====================================================================
    private View buildCalcPanel() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(0xFF9AA5B1);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvStatus.setGravity(Gravity.END);
        tvStatus.setPadding(dp(8), dp(4), dp(8), 0);
        col.addView(tvStatus, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));

        tvSmall = new TextView(this);
        tvSmall.setTextColor(0xFF9AA5B1);
        tvSmall.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvSmall.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tvSmall.setSingleLine(true);
        tvSmall.setEllipsize(TextUtils.TruncateAt.START);
        tvSmall.setPadding(dp(8), 0, dp(8), 0);
        col.addView(tvSmall, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(30), 0));

        tvBig = new TextView(this);
        tvBig.setTextColor(Color.WHITE);
        tvBig.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        tvBig.setTypeface(Typeface.DEFAULT_BOLD);
        tvBig.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tvBig.setSingleLine(true);
        tvBig.setEllipsize(TextUtils.TruncateAt.START);
        tvBig.setPadding(dp(8), 0, dp(8), dp(4));
        col.addView(tvBig, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(70), 0));

        KeyDef[][] rows = {
                {k("MC", "MC", C_MEM), k("MR", "MR", C_MEM), k("M+", "M+", C_MEM), k("M−", "M−", C_MEM), k("MS", "MS", C_MEM)},
                {k("DEG", "DEG", C_FUNC), k("INV", "INV", C_FUNC), k(",", ",", C_FUNC), k("(", "(", C_FUNC), k(")", ")", C_FUNC)},
                {ki("sin", "sin", "asin", "asin", C_FUNC), ki("cos", "cos", "acos", "acos", C_FUNC),
                        ki("tan", "tan", "atan", "atan", C_FUNC), ki("ln", "ln", "e^x", "e^", C_FUNC),
                        ki("log", "log", "10^x", "10^", C_FUNC)},
                {ki("sinh", "sinh", "asinh", "asinh", C_FUNC), ki("cosh", "cosh", "acosh", "acosh", C_FUNC),
                        ki("tanh", "tanh", "atanh", "atanh", C_FUNC), k("abs", "abs", C_FUNC), k("EXP", "E", C_FUNC)},
                {k("x²", "²", C_FUNC), k("x³", "³", C_FUNC), k("x^y", "^", C_FUNC), k("sqrt", "sqrt", C_FUNC), k("cbrt", "cbrt", C_FUNC)},
                {k("π", "π", C_FUNC), k("e", "e", C_FUNC), k("n!", "!", C_FUNC), k("nCr", "nCr", C_FUNC), k("nPr", "nPr", C_FUNC)},
                {k("7", "7", C_DIGIT), k("8", "8", C_DIGIT), k("9", "9", C_DIGIT), k("÷", "÷", C_OP), k("DEL", "DEL", C_CLEAR)},
                {k("4", "4", C_DIGIT), k("5", "5", C_DIGIT), k("6", "6", C_DIGIT), k("×", "×", C_OP), k("C", "C", C_CLEAR)},
                {k("1", "1", C_DIGIT), k("2", "2", C_DIGIT), k("3", "3", C_DIGIT), k("−", "−", C_OP), k("%", "%", C_FUNC)},
                {k("0", "0", C_DIGIT), k(".", ".", C_DIGIT), k("±", "±", C_DIGIT), k("+", "+", C_OP), k("=", "=", C_EQ)}
        };

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        for (KeyDef[] row : rows) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (KeyDef d : row) {
                r.addView(makeKey(d), lpm(0, ViewGroup.LayoutParams.MATCH_PARENT, 1, 2));
            }
            pad.addView(r, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }
        col.addView(pad, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return col;
    }

    private Button makeKey(KeyDef d) {
        Button b = new Button(this);
        b.setTag(d);
        b.setAllCaps(false);
        b.setText(d.label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setBackground(keyBg(d.color));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onKey((KeyDef) v.getTag()); }
        });
        keyButtons.add(b);
        return b;
    }

    private void onKey(KeyDef d) {
        String key = d.key;
        if (calc.inv && d.invKey != null) {
            key = d.invKey;
            calc.inv = false;
        }
        boolean wasResult = calc.isResult();
        double ansBefore = calc.ans;
        double memBefore = calc.memSet ? calc.mem : 0;
        calc.press(key);
        if (!wasResult && calc.isResult()) recordHistory(ansBefore, memBefore);
        saveState();
        refresh();
        if (panels[2].getVisibility() == View.VISIBLE) updatePolarAll();
    }

    private void refresh() {
        for (Button b : keyButtons) {
            KeyDef d = (KeyDef) b.getTag();
            String lab = d.label;
            if (d.key.equals("DEG")) lab = Engine.MODE_NAMES[calc.mode];
            else if (calc.inv && d.invLabel != null) lab = d.invLabel;
            if (!lab.contentEquals(b.getText())) b.setText(lab);
            if (d.key.equals("INV")) b.setBackground(keyBg(calc.inv ? C_ACTIVE : d.color));
        }

        StringBuilder st = new StringBuilder(Engine.MODE_NAMES[calc.mode]);
        if (calc.memSet) st.append("    M = ").append(Engine.fmt(calc.mem));
        if (calc.inv) st.append("    INV");
        tvStatus.setText(st.toString());

        String small, big;
        int bigColor = Color.WHITE;
        if (calc.hasError()) {
            small = calc.expr;
            big = calc.error;
            bigColor = 0xFFEF5350;
        } else if (calc.isResult()) {
            small = calc.expr + " =";
            big = calc.result;
        } else {
            small = "";
            big = calc.expr.isEmpty() ? "0" : calc.expr;
        }
        tvSmall.setText(small);
        tvBig.setText(big);
        tvBig.setTextColor(bigColor);
        int n = big.length();
        int sp = calc.hasError() ? (n <= 22 ? 22 : 16) : (n <= 10 ? 44 : n <= 16 ? 32 : n <= 26 ? 24 : 18);
        tvBig.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    // =====================================================================
    //  History panel
    // =====================================================================
    private View buildHistoryPanel() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(4), dp(10), dp(4));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(heading("History (newest first)"), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button clear = smallButton("Clear all", C_CLEAR);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClear(); }
        });
        top.addView(clear, lp(dp(100), dp(40), 0));
        col.addView(top, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));

        ScrollView sv = new ScrollView(this);
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        sv.addView(historyList, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(sv, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return col;
    }

    private void rebuildHistory() {
        historyList.removeAllViews();
        if (history.size() == 0) {
            TextView t = valueText(15);
            t.setTextColor(0xFF9AA5B1);
            t.setText("No calculations yet.\nPress = on the Calc tab and they will appear here.");
            t.setPadding(0, dp(16), 0, 0);
            historyList.addView(t);
            return;
        }
        for (final History.Entry e : history.items()) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(shape(C_DIGIT));
            card.setPadding(dp(12), dp(8), dp(12), dp(8));

            TextView tvE = new TextView(this);
            tvE.setText(e.expr + "   [" + Engine.MODE_NAMES[e.mode] + "]");
            tvE.setTextColor(0xFF9AA5B1);
            tvE.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            card.addView(tvE);

            TextView tvR = new TextView(this);
            tvR.setText("= " + e.result);
            tvR.setTextColor(Color.WHITE);
            tvR.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            tvR.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(tvR);

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            Button b1 = smallButton("Use expression", C_FUNC);
            b1.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { recallExpression(e); }
            });
            Button b2 = smallButton("Use result", C_OP);
            b2.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { recallNumber(e); }
            });
            btns.addView(b1, lpm(0, dp(42), 1, 2));
            btns.addView(b2, lpm(0, dp(42), 1, 2));
            card.addView(btns);

            historyList.addView(card, lpm(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4));
        }
    }

    private void recordHistory(double ansBefore, double memBefore) {
        history.add(new History.Entry(calc.mode, calc.ans, calc.expr, calc.result,
                History.recallText(calc.expr, ansBefore, memBefore)));
    }

    private void confirmClear() {
        if (history.size() == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Clear history?")
                .setMessage("All " + history.size() + " saved calculations will be deleted.")
                .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        history.clear();
                        saveState();
                        rebuildHistory();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** Puts text into the calculator as the current expression (edit mode, nothing evaluated). */
    private void loadIntoCalc(String text) {
        calc.inv = false;
        calc.expr = text + "0";   // sentinel digit ...
        calc.press("DEL");        // ... removed again by DEL, which also switches the calculator to edit mode
    }

    private void recallExpression(History.Entry e) {
        if (e.recall.length() >= Engine.Calc.MAX_LEN) {
            toast("Expression is too long to reuse");
            return;
        }
        if (calc.mode != e.mode) {
            calc.mode = e.mode;   // sin, cos, tan depend on the angle unit used for this answer
            toast("Angle unit set to " + Engine.MODE_NAMES[e.mode] + " (as used in this calculation)");
        }
        loadIntoCalc(e.recall);
        finishRecall();
    }

    private void recallNumber(History.Entry e) {
        loadIntoCalc(History.recallNumberText(e.value));
        finishRecall();
    }

    private void finishRecall() {
        saveState();
        refresh();
        showTab(0);
    }

    // =====================================================================
    //  Converter panel
    // =====================================================================
    private View buildConverterPanel() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(4), dp(10), dp(20));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(label("Category"));
        spCat = spinner(Engine.CATEGORIES);
        col.addView(spCat);

        col.addView(label("Value"));
        etConv = numberField("Enter a number");
        etConv.setText("1");
        col.addView(etConv);

        col.addView(label("From"));
        spFrom = spinner(Engine.unitNames(0));
        col.addView(spFrom);

        Button swap = smallButton("Swap From / To", C_FUNC);
        LinearLayout.LayoutParams sp = lpm(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), 0, 0);
        sp.topMargin = dp(8);
        col.addView(swap, sp);

        col.addView(label("To"));
        spTo = spinner(Engine.unitNames(0));
        col.addView(spTo);

        tvConvOut = valueText(30);
        tvConvOut.setTypeface(Typeface.DEFAULT_BOLD);
        tvConvOut.setPadding(0, dp(18), 0, dp(4));
        col.addView(tvConvOut);

        col.addView(heading("All units"));
        tvConvAll = valueText(15);
        tvConvAll.setTypeface(Typeface.MONOSPACE);
        col.addView(tvConvAll);

        spCat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!suppress) fillUnits(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        AdapterView.OnItemSelectedListener upd = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!suppress) updateConversion();
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
                updateConversion();
            }
        });
        etConv.addTextChangedListener(watcher(new Runnable() {
            @Override public void run() { updateConversion(); }
        }));

        fillUnits(0);
        return sv;
    }

    private void fillUnits(int cat) {
        suppress = true;
        String[] names = Engine.unitNames(cat);
        setItems(spFrom, names);
        setItems(spTo, names);
        spFrom.setSelection(0);
        spTo.setSelection(names.length > 1 ? 1 : 0);
        suppress = false;
        updateConversion();
    }

    private void updateConversion() {
        int cat = spCat.getSelectedItemPosition();
        int from = spFrom.getSelectedItemPosition();
        int to = spTo.getSelectedItemPosition();
        if (cat < 0 || cat >= Engine.CATEGORIES.length || from < 0 || to < 0) return;
        String[] names = Engine.unitNames(cat);
        if (from >= names.length || to >= names.length) return;
        Double v = parseField(etConv);
        if (v == null) {
            tvConvOut.setText("—");
            tvConvOut.setTextColor(Color.WHITE);
            tvConvAll.setText("");
            return;
        }
        try {
            double r = Engine.convert(cat, from, to, v);
            tvConvOut.setTextColor(Color.WHITE);
            tvConvOut.setText(Engine.fmt(r) + " " + names[to]);
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
            tvConvAll.setText(sb.toString().trim());
        } catch (Engine.CalcException e) {
            tvConvOut.setTextColor(0xFFEF5350);
            tvConvOut.setText(e.getMessage());
            tvConvAll.setText("");
        }
    }

    // =====================================================================
    //  Polar / rectangular panel
    // =====================================================================
    private View buildPolarPanel() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(4), dp(10), dp(20));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        btnAngle = smallButton("", C_FUNC);
        btnAngle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                calc.press("DEG");
                saveState();
                refresh();
                updatePolarAll();
            }
        });
        LinearLayout.LayoutParams bp = lpm(ViewGroup.LayoutParams.MATCH_PARENT, dp(46), 0, 0);
        bp.topMargin = dp(6);
        col.addView(btnAngle, bp);

        Runnable r2p = new Runnable() { @Override public void run() { updateRectToPolar(); } };
        Runnable p2r = new Runnable() { @Override public void run() { updatePolarToRect(); } };

        col.addView(heading("Rectangular  →  Polar"));
        col.addView(label("x"));
        etX = numberField("x");
        col.addView(etX);
        col.addView(label("y"));
        etY = numberField("y");
        col.addView(etY);
        tvPR = valueText(20);
        tvPTh = valueText(20);
        tvPTh360 = valueText(20);
        tvPNote = valueText(14);
        tvPNote.setTextColor(0xFFFFB74D);
        col.addView(tvPR);
        col.addView(tvPTh);
        col.addView(tvPTh360);
        col.addView(tvPNote);

        col.addView(heading("Polar  →  Rectangular"));
        col.addView(label("r (radius)"));
        etR = numberField("r");
        col.addView(etR);
        col.addView(label("θ (angle, in the unit chosen above)"));
        etTh = numberField("θ");
        col.addView(etTh);
        tvPX = valueText(20);
        tvPY = valueText(20);
        col.addView(tvPX);
        col.addView(tvPY);

        etX.addTextChangedListener(watcher(r2p));
        etY.addTextChangedListener(watcher(r2p));
        etR.addTextChangedListener(watcher(p2r));
        etTh.addTextChangedListener(watcher(p2r));
        return sv;
    }

    private void updatePolarAll() {
        btnAngle.setText("Angle unit: " + Engine.MODE_NAMES[calc.mode] + "   (tap to change)");
        updateRectToPolar();
        updatePolarToRect();
    }

    private void updateRectToPolar() {
        Double x = parseField(etX), y = parseField(etY);
        tvPNote.setText("");
        if (x == null || y == null) {
            tvPR.setText("r = —");
            tvPTh.setText("θ = —");
            tvPTh360.setText("");
            return;
        }
        String unit = Engine.MODE_NAMES[calc.mode];
        String[] half = {"180", "π", "200"};
        String[] full = {"360", "2π", "400"};
        try {
            double[] a = Engine.rectToPolar(x, y, calc.mode);
            tvPR.setText("r = " + Engine.fmt(a[0]));
            tvPTh.setText("θ (−" + half[calc.mode] + " to " + half[calc.mode] + ") = " + Engine.fmt(a[1]) + " " + unit);
            tvPTh360.setText("θ (0 to " + full[calc.mode] + ") = " + Engine.fmt(a[2]) + " " + unit);
            if (x == 0 && y == 0) tvPNote.setText("At the origin the angle is undefined (shown as 0).");
        } catch (Engine.CalcException e) {
            tvPR.setText(e.getMessage());
            tvPTh.setText("");
            tvPTh360.setText("");
        }
    }

    private void updatePolarToRect() {
        Double r = parseField(etR), t = parseField(etTh);
        if (r == null || t == null) {
            tvPX.setText("x = —");
            tvPY.setText("y = —");
            return;
        }
        try {
            double[] a = Engine.polarToRect(r, t, calc.mode);
            tvPX.setText("x = " + Engine.fmt(a[0]));
            tvPY.setText("y = " + Engine.fmt(a[1]));
        } catch (Engine.CalcException e) {
            tvPX.setText(e.getMessage());
            tvPY.setText("");
        }
    }
}
