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
