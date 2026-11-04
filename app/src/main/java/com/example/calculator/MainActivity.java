package com.example.calculator;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.DecimalFormat;

public class MainActivity extends Activity {

    private TextView display;
    private double acc = 0;
    private String op = null;
    private String cur = "0";
    private boolean fresh = true; // next digit replaces the current number

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        display = findViewById(R.id.display);
    }

    public void onButton(View v) {
        String t = ((Button) v).getText().toString();

        if (cur.equals("Error") && !t.equals("C")) {
            clearAll();
        }

        switch (t) {
            case "C":
                clearAll();
                break;
            case "⌫":
                if (!fresh && cur.length() > 1) {
                    cur = cur.substring(0, cur.length() - 1);
                    if (cur.equals("-")) cur = "0";
                } else if (!fresh) {
                    cur = "0";
                }
                break;
            case "±":
                if (!cur.equals("0")) {
                    cur = cur.startsWith("-") ? cur.substring(1) : "-" + cur;
                }
                break;
            case "+": case "−": case "×": case "÷":
                if (op != null && !fresh) {
                    if (!compute()) break;
                } else {
                    acc = parse(cur);
                }
                cur = format(acc);
                op = t;
                fresh = true;
                break;
            case "=":
                if (op != null) {
                    if (!compute()) break;
                    cur = format(acc);
                    op = null;
                    fresh = true;
                }
                break;
            default: // digits and "."
                if (fresh) {
                    cur = t.equals(".") ? "0." : t;
                    fresh = false;
                } else if (t.equals(".")) {
                    if (!cur.contains(".")) cur += ".";
                } else if (cur.equals("0")) {
                    cur = t;
                } else if (cur.length() < 15) {
                    cur += t;
                }
        }
        display.setText(cur);
    }

    private boolean compute() {
        double b = parse(cur);
        switch (op) {
            case "+": acc += b; break;
            case "−": acc -= b; break;
            case "×": acc *= b; break;
            case "÷":
                if (b == 0) {
                    clearAll();
                    cur = "Error";
                    return false;
                }
                acc /= b;
                break;
        }
        return true;
    }

    private void clearAll() {
        acc = 0;
        op = null;
        cur = "0";
        fresh = true;
    }

    private double parse(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String format(double d) {
        if (d == (long) d && Math.abs(d) < 1e15) return String.valueOf((long) d);
        return new DecimalFormat("#.##########").format(d);
    }
}
