package com.example.calculator;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/** Colours (dark / light) and small view-building helpers shared by all screens. */
public final class UiKit {

    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static final class Pal {
        public final int bg, digit, op, func, mem, clear, eq, active, tabOff, text, dim, accent, error, hint;
        Pal(int bg, int digit, int op, int func, int mem, int clear, int eq, int active, int tabOff,
            int text, int dim, int accent, int error, int hint) {
            this.bg = bg; this.digit = digit; this.op = op; this.func = func; this.mem = mem;
            this.clear = clear; this.eq = eq; this.active = active; this.tabOff = tabOff;
            this.text = text; this.dim = dim; this.accent = accent; this.error = error; this.hint = hint;
        }
    }

    public static final Pal DARK = new Pal(0xFF15171B, 0xFF2F3640, 0xFFFF9800, 0xFF455A64, 0xFF37474F,
            0xFFC62828, 0xFF2E7D32, 0xFF1E88E5, 0xFF2A2F37, 0xFFFFFFFF, 0xFF9AA5B1, 0xFFFFB74D,
            0xFFEF5350, 0xFF6B7683);

    public static final Pal LIGHT = new Pal(0xFFF3F5F8, 0xFFDDE3EA, 0xFFFFB74D, 0xFFB0BEC5, 0xFFCFD8DC,
            0xFFEF9A9A, 0xFF81C784, 0xFF64B5F6, 0xFFCFD8DC, 0xFF111418, 0xFF55606B, 0xFFE65100,
            0xFFC62828, 0xFF8A949E);

    public final Context ctx;
    public final Pal pal;
    private final float density;

    public UiKit(Context ctx, Pal pal) {
        this.ctx = ctx;
        this.pal = pal;
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    // ---------- sizes, shapes, colours ----------
    public int dp(float v) { return (int) (v * density + 0.5f); }

    public LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public LinearLayout.LayoutParams lpm(int w, int h, float weight, int marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        int m = dp(marginDp);
        p.setMargins(m, m, m, m);
        return p;
    }

    public GradientDrawable shape(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        return g;
    }

    /** White text on dark backgrounds, near-black text on light ones. */
    public int textOn(int bg) {
        double lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0;
        return lum > 0.6 ? 0xFF111418 : Color.WHITE;
    }

    public RippleDrawable keyBg(int color) {
        int ripple = textOn(color) == Color.WHITE ? 0x55FFFFFF : 0x33000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple), shape(color), null);
    }

    // ---------- text ----------
    public TextView label(String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(pal.dim);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setPadding(0, dp(10), 0, dp(2));
        return t;
    }

    public TextView heading(String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(pal.accent);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(18), 0, dp(4));
        return t;
    }

    public TextView valueText(int sp) {
        TextView t = new TextView(ctx);
        t.setTextColor(pal.text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    public TextView note(String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(pal.dim);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    // ---------- inputs ----------
    private EditText baseField(String hint) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setHintTextColor(pal.hint);
        e.setTextColor(pal.text);
        e.setBackground(shape(pal.digit));
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        return e;
    }

    public EditText numberField(String hint) {
        EditText e = baseField(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        return e;
    }

    public EditText textField(String hint, boolean multiline, boolean caps) {
        EditText e = baseField(hint);
        int type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if (caps) type |= InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
        if (multiline) {
            type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            e.setSingleLine(false);
            e.setMinLines(3);
            e.setGravity(Gravity.TOP);
            e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5000)});
        } else {
            e.setSingleLine(true);
            e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        }
        e.setInputType(type);
        return e;
    }

    public Spinner spinner(String[] items) {
        Spinner s = new Spinner(ctx);
        setItems(s, items);
        return s;
    }

    public void setItems(Spinner s, String[] items) {
        final int color = pal.text;
        ArrayAdapter<String> a = new ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, items) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) ((TextView) v).setTextColor(color);
                return v;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
    }

    /** A rounded box around a spinner so it is visible on both themes. */
    public LinearLayout boxed(View inner) {
        LinearLayout b = new LinearLayout(ctx);
        b.setBackground(shape(pal.digit));
        b.setPadding(dp(6), dp(2), dp(6), dp(2));
        b.addView(inner, new LinearLayout.LayoutParams(MATCH, WRAP));
        return b;
    }

    public Button smallButton(String text, int bg) {
        Button b = new Button(ctx);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(textOn(bg));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setStateListAnimator(null);
        b.setBackground(keyBg(bg));
        return b;
    }

    // ---------- containers ----------
    public LinearLayout column() {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(10), dp(4), dp(10), dp(24));
        return c;
    }

    public ScrollView scroll(LinearLayout col) {
        ScrollView sv = new ScrollView(ctx);
        sv.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
        return sv;
    }

    // ---------- input helpers ----------
    public static TextWatcher watcher(final Runnable r) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { r.run(); }
            @Override public void afterTextChanged(Editable s) { }
        };
    }

    /** Number from a field (comma accepted as decimal point). Null if empty or not a valid number yet. */
    public static Double parse(EditText e) {
        String t = e.getText().toString().trim().replace(',', '.');
        if (t.isEmpty() || t.equals("-") || t.equals(".") || t.equals("-.") || t.equals("+")) return null;
        try {
            double v = Double.parseDouble(t);
            if (Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Like parse, but an empty field counts as 0 (for optional values such as centre X, Y). */
    public static Double parseOrZero(EditText e) {
        return e.getText().toString().trim().isEmpty() ? Double.valueOf(0) : parse(e);
    }
}
