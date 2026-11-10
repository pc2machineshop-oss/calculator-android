package com.example.calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.calculator.ConvertPolarPanels.AngleHost;
import com.example.calculator.ConvertPolarPanels.ConvertPanel;
import com.example.calculator.ConvertPolarPanels.PolarPanel;
import com.example.calculator.ShopMathPanels.MathPanel;
import com.example.calculator.ShopMathPanels.ShopPanel;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements AngleHost {

    private static final int T_DIGIT = 0, T_OP = 1, T_FUNC = 2, T_MEM = 3, T_CLEAR = 4, T_EQ = 5;
    private static final String[] TAB_NAMES = {"Calc", "Convert", "Polar", "Shop", "Machine", "Math", "History", "Settings"};
    private static final int TAB_CALC = 0, TAB_POLAR = 2, TAB_HISTORY = 6, TAB_SETTINGS = 7;

    private static class KeyDef {
        final String label, key, invLabel, invKey;
        final int type;
        KeyDef(String label, String key, String invLabel, String invKey, int type) {
            this.label = label; this.key = key; this.invLabel = invLabel; this.invKey = invKey; this.type = type;
        }
    }

    private static KeyDef k(String label, String key, int type) { return new KeyDef(label, key, null, null, type); }
    private static KeyDef ki(String label, String key, String il, String ik, int type) { return new KeyDef(label, key, il, ik, type); }

    private final Engine.Calc calc = new Engine.Calc();
    private final List<Button> keyButtons = new ArrayList<>();
    private final Button[] tabButtons = new Button[TAB_NAMES.length];
    private final View[] panels = new View[TAB_NAMES.length];

    private UiKit ui;
    private LinearLayout root;
    private int curTab = TAB_CALC;

    // settings (saved)
    private int themeIdx = 0;           // 0 dark, 1 light
    private boolean vibrate = true;
    private boolean largeKeys = false;
    private boolean clearAll = false;   // false: C removes the last entry, true: C clears everything

    // calculator screen
    private LinearLayout calcBox, displayBox, padBox;
    private TextView tvStatus, tvSmall, tvBig;
    private String previewText = null;

    // other screens
    private PolarPanel polarPanel;
    private History history = new History();
    private LinearLayout historyList;

    // =====================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loadState();
        buildUi();
        showTab(TAB_CALC);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientation();
    }

    // ---- AngleHost (used by the Polar screen)
    @Override public int angleMode() { return calc.mode; }

    @Override public void cycleAngleMode() {
        calc.press("DEG");
        saveState();
        refresh();
    }

    // =====================================================================
    //  Saved state
    // =====================================================================
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
        themeIdx = sp.getInt("theme", 0) == 1 ? 1 : 0;
        vibrate = sp.getBoolean("vibrate", true);
        largeKeys = sp.getBoolean("largeKeys", false);
        clearAll = sp.getBoolean("clearAll", false);
    }

    private void saveState() {
        getSharedPreferences("calc", MODE_PRIVATE).edit()
                .putInt("mode", calc.mode)
                .putBoolean("memSet", calc.memSet)
                .putString("mem", Double.toString(calc.mem))
                .putString("history", history.serialize())
                .putInt("theme", themeIdx)
                .putBoolean("vibrate", vibrate)
                .putBoolean("largeKeys", largeKeys)
                .putBoolean("clearAll", clearAll)
                .apply();
    }

    // =====================================================================
    //  Layout, tabs, theme
    // =====================================================================
    private int colorOf(int type) {
        UiKit.Pal p = ui.pal;
        switch (type) {
            case T_OP: return p.op;
            case T_FUNC: return p.func;
            case T_MEM: return p.mem;
            case T_CLEAR: return p.clear;
            case T_EQ: return p.eq;
            default: return p.digit;
        }
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private float keyTextSp() {
        float base = largeKeys ? 19f : 15f;
        return isLandscape() ? base * 0.8f : base;
    }

    private void buildUi() {
        ui = new UiKit(this, themeIdx == 1 ? UiKit.LIGHT : UiKit.DARK);
        keyButtons.clear();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.pal.bg);
        root.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            Button b = ui.smallButton(TAB_NAMES[i], ui.pal.tabOff);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showTab(idx); }
            });
            tabButtons[i] = b;
            tabRow.addView(b, ui.lpm(ui.dp(90), ui.dp(44), 0, 2));
        }
        hs.addView(tabRow, new FrameLayout.LayoutParams(UiKit.WRAP, UiKit.WRAP));
        root.addView(hs, ui.lp(UiKit.MATCH, UiKit.WRAP, 0));

        polarPanel = new PolarPanel(ui, this);
        panels[0] = buildCalcPanel();
        panels[1] = new ConvertPanel(ui).build();
        panels[2] = polarPanel.build();
        panels[3] = new ShopPanel(ui).build();
        panels[4] = new MachinePanel(ui).build();
        panels[5] = new MathPanel(ui).build();
        panels[6] = buildHistoryPanel();
        panels[7] = buildSettingsPanel();

        FrameLayout frame = new FrameLayout(this);
        for (View p : panels) {
            frame.addView(p, new FrameLayout.LayoutParams(UiKit.MATCH, UiKit.MATCH));
        }
        root.addView(frame, ui.lp(UiKit.MATCH, 0, 1));
        setContentView(root);
        applyOrientation();
    }

    private void showTab(int idx) {
        curTab = idx;
        for (int i = 0; i < panels.length; i++) {
            panels[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            int bg = i == idx ? ui.pal.active : ui.pal.tabOff;
            tabButtons[i].setBackground(ui.keyBg(bg));
            tabButtons[i].setTextColor(ui.textOn(bg));
        }
        if (idx == TAB_POLAR) polarPanel.refresh();
        if (idx == TAB_HISTORY) rebuildHistory();
        if (idx == TAB_CALC || idx == TAB_HISTORY || idx == TAB_SETTINGS) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && root != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        }
    }

    /** Portrait: display above keys. Landscape: display on the left, keys on the right. */
    private void applyOrientation() {
        if (calcBox == null) return;
        if (isLandscape()) {
            calcBox.setOrientation(LinearLayout.HORIZONTAL);
            displayBox.setLayoutParams(ui.lp(0, UiKit.MATCH, 4));
            padBox.setLayoutParams(ui.lp(0, UiKit.MATCH, 7));
        } else {
            calcBox.setOrientation(LinearLayout.VERTICAL);
            displayBox.setLayoutParams(ui.lp(UiKit.MATCH, UiKit.WRAP, 0));
            padBox.setLayoutParams(ui.lp(UiKit.MATCH, 0, 1));
        }
        for (Button b : keyButtons) b.setTextSize(TypedValue.COMPLEX_UNIT_SP, keyTextSp());
    }

    // =====================================================================
    //  Calculator screen
    // =====================================================================
    private View buildCalcPanel() {
        calcBox = new LinearLayout(this);
        calcBox.setOrientation(LinearLayout.VERTICAL);

        displayBox = new LinearLayout(this);
        displayBox.setOrientation(LinearLayout.VERTICAL);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(ui.pal.dim);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvStatus.setGravity(Gravity.END);
        tvStatus.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), 0);
        displayBox.addView(tvStatus, ui.lp(UiKit.MATCH, UiKit.WRAP, 0));

        tvSmall = new TextView(this);
        tvSmall.setTextColor(ui.pal.dim);
        tvSmall.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvSmall.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tvSmall.setSingleLine(true);
        tvSmall.setEllipsize(TextUtils.TruncateAt.START);
        tvSmall.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        displayBox.addView(tvSmall, ui.lp(UiKit.MATCH, ui.dp(30), 0));

        tvBig = new TextView(this);
        tvBig.setTextColor(ui.pal.text);
        tvBig.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        tvBig.setTypeface(Typeface.DEFAULT_BOLD);
        tvBig.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tvBig.setSingleLine(true);
        tvBig.setEllipsize(TextUtils.TruncateAt.START);
        tvBig.setPadding(ui.dp(8), 0, ui.dp(8), ui.dp(4));
        tvBig.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                copyDisplay();
                return true;
            }
        });
        displayBox.addView(tvBig, ui.lp(UiKit.MATCH, ui.dp(70), 0));

        KeyDef[][] rows = {
                {k("MC", "MC", T_MEM), k("MR", "MR", T_MEM), k("M+", "M+", T_MEM), k("M−", "M−", T_MEM), k("MS", "MS", T_MEM)},
                {k("DEG", "DEG", T_FUNC), k("INV", "INV", T_FUNC), k(",", ",", T_FUNC), k("(", "(", T_FUNC), k(")", ")", T_FUNC)},
                {ki("sin", "sin", "asin", "asin", T_FUNC), ki("cos", "cos", "acos", "acos", T_FUNC),
                        ki("tan", "tan", "atan", "atan", T_FUNC), ki("ln", "ln", "e^x", "e^", T_FUNC),
                        ki("log", "log", "10^x", "10^", T_FUNC)},
                {ki("sinh", "sinh", "asinh", "asinh", T_FUNC), ki("cosh", "cosh", "acosh", "acosh", T_FUNC),
                        ki("tanh", "tanh", "atanh", "atanh", T_FUNC), k("abs", "abs", T_FUNC), k("EXP", "E", T_FUNC)},
                {k("x²", "²", T_FUNC), k("x³", "³", T_FUNC), k("x^y", "^", T_FUNC), k("sqrt", "sqrt", T_FUNC), k("cbrt", "cbrt", T_FUNC)},
                {k("π", "π", T_FUNC), k("e", "e", T_FUNC), k("n!", "!", T_FUNC), k("nCr", "nCr", T_FUNC), k("nPr", "nPr", T_FUNC)},
                {k("7", "7", T_DIGIT), k("8", "8", T_DIGIT), k("9", "9", T_DIGIT), k("÷", "÷", T_OP), k("DEL", "DEL", T_CLEAR)},
                {k("4", "4", T_DIGIT), k("5", "5", T_DIGIT), k("6", "6", T_DIGIT), k("×", "×", T_OP), k("C", "C", T_CLEAR)},
                {k("1", "1", T_DIGIT), k("2", "2", T_DIGIT), k("3", "3", T_DIGIT), k("−", "−", T_OP), k("%", "%", T_FUNC)},
                {k("0", "0", T_DIGIT), k(".", ".", T_DIGIT), k("±", "±", T_DIGIT), k("+", "+", T_OP), k("=", "=", T_EQ)}
        };

        padBox = new LinearLayout(this);
        padBox.setOrientation(LinearLayout.VERTICAL);
        for (KeyDef[] row : rows) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (KeyDef d : row) {
                r.addView(makeKey(d), ui.lpm(0, UiKit.MATCH, 1, 2));
            }
            padBox.addView(r, ui.lp(UiKit.MATCH, 0, 1));
        }

        calcBox.addView(displayBox, ui.lp(UiKit.MATCH, UiKit.WRAP, 0));
        calcBox.addView(padBox, ui.lp(UiKit.MATCH, 0, 1));
        return calcBox;
    }

    private Button makeKey(KeyDef d) {
        int bg = colorOf(d.type);
        Button b = new Button(this);
        b.setTag(d);
        b.setAllCaps(false);
        b.setText(d.label);
        b.setTextColor(ui.textOn(bg));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, keyTextSp());
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setBackground(ui.keyBg(bg));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onKey((KeyDef) v.getTag(), v); }
        });
        if (d.key.equals("C")) {
            b.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    calc.press("C");          // hold C: clear everything
                    saveState();
                    refresh();
                    toast("All cleared");
                    return true;
                }
            });
        }
        keyButtons.add(b);
        return b;
    }

    private void onKey(KeyDef d, View v) {
        if (vibrate) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (d.key.equals("C")) {
            EditOps.clearKey(calc, clearAll);
            saveState();
            refresh();
            if (curTab == TAB_POLAR) polarPanel.refresh();
            return;
        }
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
        if (curTab == TAB_POLAR) polarPanel.refresh();
    }

    private void refresh() {
        for (Button b : keyButtons) {
            KeyDef d = (KeyDef) b.getTag();
            String lab = d.label;
            if (d.key.equals("DEG")) lab = Engine.MODE_NAMES[calc.mode];
            else if (calc.inv && d.invLabel != null) lab = d.invLabel;
            if (!lab.contentEquals(b.getText())) b.setText(lab);
            if (d.key.equals("INV")) {
                int bg = calc.inv ? ui.pal.active : colorOf(d.type);
                b.setBackground(ui.keyBg(bg));
                b.setTextColor(ui.textOn(bg));
            }
        }

        StringBuilder st = new StringBuilder(Engine.MODE_NAMES[calc.mode]);
        if (calc.memSet) st.append("    M = ").append(Engine.fmt(calc.mem));
        if (calc.inv) st.append("    INV");
        tvStatus.setText(st.toString());

        // live preview: what "=" would show right now (only when it succeeds)
        previewText = null;
        if (!calc.isResult() && !calc.hasError() && !calc.expr.isEmpty()) {
            try {
                double v = Engine.eval(calc.expr, calc.mode, calc.ans, calc.memSet ? calc.mem : 0);
                String p = Engine.fmt(v);
                if (!p.equals(calc.expr.replace('−', '-'))) previewText = p;
            } catch (Engine.CalcException ex) {
                previewText = null;
            }
        }

        String small, big;
        int bigColor = ui.pal.text;
        if (calc.hasError()) {
            small = calc.expr;
            big = calc.error;
            bigColor = ui.pal.error;
        } else if (calc.isResult()) {
            small = calc.expr + " =";
            big = calc.result;
        } else {
            small = previewText != null ? "= " + previewText : "";
            big = calc.expr.isEmpty() ? "0" : calc.expr;
        }
        tvSmall.setText(small);
        tvBig.setText(big);
        tvBig.setTextColor(bigColor);
        int n = big.length();
        int sp = calc.hasError() ? (n <= 22 ? 22 : 16) : (n <= 10 ? 44 : n <= 16 ? 32 : n <= 26 ? 24 : 18);
        tvBig.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** Long-press on the big number: copies the result (or the expression / preview while typing). */
    private void copyDisplay() {
        String text;
        if (calc.hasError()) text = null;
        else if (calc.isResult()) text = calc.result;
        else if (previewText != null) text = previewText;
        else text = calc.expr;
        if (text == null || text.isEmpty()) {
            toast("Nothing to copy");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            toast("Copy is not available");
            return;
        }
        cm.setPrimaryClip(ClipData.newPlainText("calculator", text));
        toast("Copied: " + text);
    }

    // =====================================================================
    //  History screen
    // =====================================================================
    private View buildHistoryPanel() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(ui.heading("History (newest first)"), ui.lp(0, UiKit.WRAP, 1));
        Button clear = ui.smallButton("Clear all", ui.pal.clear);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClear(); }
        });
        top.addView(clear, ui.lp(ui.dp(100), ui.dp(40), 0));
        col.addView(top, ui.lp(UiKit.MATCH, UiKit.WRAP, 0));

        ScrollView sv = new ScrollView(this);
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        sv.addView(historyList, new ViewGroup.LayoutParams(UiKit.MATCH, UiKit.WRAP));
        col.addView(sv, ui.lp(UiKit.MATCH, 0, 1));
        return col;
    }

    private void rebuildHistory() {
        historyList.removeAllViews();
        if (history.size() == 0) {
            TextView t = ui.note("No calculations yet.\nPress = on the Calc tab and they will appear here.");
            t.setPadding(0, ui.dp(16), 0, 0);
            historyList.addView(t);
            return;
        }
        for (final History.Entry e : history.items()) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(ui.shape(ui.pal.digit));
            card.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));

            TextView tvE = new TextView(this);
            tvE.setText(e.expr + "   [" + Engine.MODE_NAMES[e.mode] + "]");
            tvE.setTextColor(ui.pal.dim);
            tvE.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            card.addView(tvE);

            TextView tvR = new TextView(this);
            tvR.setText("= " + e.result);
            tvR.setTextColor(ui.pal.text);
            tvR.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            tvR.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(tvR);

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            Button b1 = ui.smallButton("Use expression", ui.pal.func);
            b1.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { recallExpression(e); }
            });
            Button b2 = ui.smallButton("Use result", ui.pal.op);
            b2.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { recallNumber(e); }
            });
            btns.addView(b1, ui.lpm(0, ui.dp(42), 1, 2));
            btns.addView(b2, ui.lpm(0, ui.dp(42), 1, 2));
            card.addView(btns);

            historyList.addView(card, ui.lpm(UiKit.MATCH, UiKit.WRAP, 0, 4));
        }
    }

    private void recordHistory(double ansBefore, double memBefore) {
        if (history.size() > 0) {
            History.Entry top = history.items().get(0);
            // pressing C then = again would repeat the same line: skip it
            if (top.expr.equals(calc.expr) && top.result.equals(calc.result) && top.mode == calc.mode) return;
        }
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

    /** Puts text into the calculator as the current expression (edit mode, nothing evaluated). */
    private void loadIntoCalc(String text) {
        calc.inv = false;
        EditOps.setExpression(calc, text);
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
        showTab(TAB_CALC);
    }

    // =====================================================================
    //  Settings screen
    // =====================================================================
    private String clearKeyLabel() {
        return "C key clears: " + (clearAll ? "Everything" : "Last entry") + "   (tap to switch)";
    }

    private View buildSettingsPanel() {
        LinearLayout col = ui.column();

        col.addView(ui.heading("Appearance"));
        final Button bTheme = ui.smallButton("", ui.pal.func);
        bTheme.setText("Theme: " + (themeIdx == 1 ? "Light" : "Dark") + "   (tap to switch)");
        bTheme.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                themeIdx = themeIdx == 1 ? 0 : 1;
                saveState();
                buildUi();            // rebuild every screen with the new colours
                showTab(TAB_SETTINGS);
                refresh();
            }
        });
        col.addView(bTheme, ui.lpm(UiKit.MATCH, ui.dp(46), 0, 0));

        final Button bLarge = ui.smallButton("", ui.pal.func);
        bLarge.setText("Calculator key text: " + (largeKeys ? "Large" : "Normal") + "   (tap to switch)");
        bLarge.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                largeKeys = !largeKeys;
                saveState();
                bLarge.setText("Calculator key text: " + (largeKeys ? "Large" : "Normal") + "   (tap to switch)");
                for (Button b : keyButtons) b.setTextSize(TypedValue.COMPLEX_UNIT_SP, keyTextSp());
            }
        });
        LinearLayout.LayoutParams lp2 = ui.lpm(UiKit.MATCH, ui.dp(46), 0, 0);
        lp2.topMargin = ui.dp(8);
        col.addView(bLarge, lp2);

        col.addView(ui.heading("Calculator keys"));
        final Button bClear = ui.smallButton("", ui.pal.func);
        bClear.setText(clearKeyLabel());
        bClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                clearAll = !clearAll;
                saveState();
                bClear.setText(clearKeyLabel());
            }
        });
        col.addView(bClear, ui.lpm(UiKit.MATCH, ui.dp(46), 0, 0));
        col.addView(ui.note("Hold the C key at any time to clear everything."));

        col.addView(ui.heading("Feedback"));
        final Button bVib = ui.smallButton("", ui.pal.func);
        bVib.setText("Key vibration: " + (vibrate ? "On" : "Off") + "   (tap to switch)");
        bVib.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                vibrate = !vibrate;
                saveState();
                bVib.setText("Key vibration: " + (vibrate ? "On" : "Off") + "   (tap to switch)");
            }
        });
        col.addView(bVib, ui.lpm(UiKit.MATCH, ui.dp(46), 0, 0));
        col.addView(ui.note("Key vibration also needs \"Touch feedback\" turned on in your phone's system sound / vibration settings."));

        col.addView(ui.heading("Tips"));
        col.addView(ui.note("• C clears the last entry; press it again to step back further. Hold C to clear everything.\n"
                + "• Long-press the big number on the Calc tab to copy it.\n"
                + "• While you type, the small line shows what = would give.\n"
                + "• INV turns sin, cos, tan, ln, log (and sinh, cosh, tanh) into their inverses.\n"
                + "• Check DEG / RAD / GRAD before using sin, cos or tan.\n"
                + "• History keeps your last 100 results; tap Use expression or Use result.\n"
                + "• Turn the phone sideways for a wide layout.\n"
                + "• Percent means divide by 100: 200 × 10% = 20, and 100 + 10% = 100.1."));
        return ui.scroll(col);
    }
}
