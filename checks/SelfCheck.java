import com.example.calculator.Engine;
import com.example.calculator.EditOps;
import com.example.calculator.History;
import com.example.calculator.MachineTools;
import com.example.calculator.Tools;

/** Quick self-check run by GitHub on every upload. If a line fails, the build stops and shows which one. */
public class SelfCheck {
    static int failures = 0;
    static int checks = 0;

    static void check(boolean ok, String name, String detail) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("SELF-CHECK FAILED: " + name + "  (" + detail + ")");
        }
    }

    static void val(String expr, String expected) {
        try {
            String got = Engine.fmt(Engine.eval(expr, Engine.DEG, 0, 0));
            check(got.equals(expected), expr, "got " + got + ", expected " + expected);
        } catch (Engine.CalcException e) {
            check(false, expr, "error " + e.getMessage() + ", expected " + expected);
        }
    }

    static void fails(String expr) {
        try {
            String got = Engine.fmt(Engine.eval(expr, Engine.DEG, 0, 0));
            check(false, expr, "should be an error but gave " + got);
        } catch (Engine.CalcException e) {
            checks++;
        }
    }

    static Engine.Calc keys(String... ks) {
        Engine.Calc c = new Engine.Calc();
        for (String k : ks) c.press(k);
        return c;
    }

    public static void main(String[] args) throws Exception {
        // arithmetic and noise
        val("0.1+0.2", "0.3"); val("0.1+0.2−0.3", "0"); val("2+3×4", "14"); val("(2+3)×4", "20");
        val("−2^2", "-4"); val("2^3^2", "512"); val("1÷3×3", "1"); val("5×−3", "-15");
        // trigonometry (degrees)
        val("sin(30)", "0.5"); val("cos(90)", "0"); val("sin(180)", "0"); val("tan(45)", "1"); val("asin(0.5)", "30");
        // errors that must stay errors
        fails("1÷0"); fails("sqrt(−4)"); fails("tan(90)"); fails("171!"); fails("ln(0)"); fails("0^0"); fails("2+"); fails("1E");
        // functions
        val("5!", "120"); val("nCr(5,2)", "10"); val("log(1000)", "3"); val("cbrt(−27)", "-3");

        // keypad behaviour
        Engine.Calc c = keys("1", "2", "+", "3", "=");
        check(c.isResult() && c.result.equals("15"), "12+3=", c.result);
        c = keys("7", "÷", "0", "=");
        check(c.hasError(), "7÷0 shows an error", c.result);
        c = keys("0", "0", "7"); check(c.expr.equals("7"), "leading zeros", c.expr);
        c = keys("5", "±"); check(c.expr.equals("(−5)"), "plus-minus", c.expr);
        c = keys("5", "M+", "3", "M+"); check(c.mem == 8 && c.memSet, "memory add", "" + c.mem);

        // C key: removes the last entry
        c = keys("1", "2", "+", "3", "4", "5");
        EditOps.clearKey(c, false); check(c.expr.equals("12+"), "C removes last number", c.expr);
        EditOps.clearKey(c, false); check(c.expr.equals("12"), "C steps back", c.expr);
        c = keys("1", "2", "+", "3", "=");
        EditOps.clearKey(c, false); check(c.expr.equals("12+3") && !c.isResult(), "C after = returns to the expression", c.expr);
        c = keys("1", "2", "+", "3");
        EditOps.clearKey(c, true); check(c.expr.isEmpty(), "clear-all mode", c.expr);
        check(EditOps.clearLastEntry("sin(30").equals("sin("), "clear last entry: function", EditOps.clearLastEntry("sin(30"));

        // history
        check(History.recallText("Ans+1", 5, 0).equals("(5)+1"), "history recall Ans", History.recallText("Ans+1", 5, 0));
        check(History.numberText(1e13).equals("1E13"), "history number text 1E13", History.numberText(1e13));
        check(History.recallNumberText(-0.25).equals("(−0.25)"), "history negative number in brackets", History.recallNumberText(-0.25));
        History h = new History();
        h.add(new History.Entry(0, 15, "12+3", "15", "12+3"));
        check(History.parse(h.serialize()).size() == 1, "history save/load", "");

        // converter
        check(Engine.fmt(Engine.convert(0, 1, 0, 1)).equals("25.4"), "1 inch = 25.4 mm", "");
        check(Engine.fmt(Engine.convert(4, 0, 1, 100)).equals("212"), "100 C = 212 F", "");
        check(Engine.fmt(Engine.convert(4, 0, 1, -40)).equals("-40"), "-40 C = -40 F", "");

        // polar
        double[] p = Engine.rectToPolar(1, 1, Engine.DEG);
        check(Engine.fmt(p[1]).equals("45"), "polar 45 degrees", Engine.fmt(p[1]));
        double[] r = Engine.polarToRect(10, 90, Engine.DEG);
        check(Engine.fmt(r[0]).equals("0") && Engine.fmt(r[1]).equals("10"), "polar 10 at 90 degrees", Engine.fmt(r[0]) + "," + Engine.fmt(r[1]));

        // shop tools
        check(Tools.fixed(Tools.rpmFor(300, 4, false), 2).equals("286.48"), "300 SFM 4 in = 286.48 RPM", "");
        check(Tools.fixed(Tools.feedFor(3000, 0.004, 4), 3).equals("48.000"), "feed rate", "");
        double[][] holes = Tools.boltCircle(6, 100, 0, 0, 0, false);
        check(Tools.fixed(holes[3][0], 4).equals("-50.0000") && Tools.fixed(holes[3][1], 4).equals("0.0000"), "bolt circle hole 4", "");
        check(Engine.fmt(Tools.parseFraction("1 3/8")).equals("1.375"), "fraction 1 3/8", "");
        check(Tools.nearestFraction(0.3125, 16).equals("5/16"), "nearest fraction", "");
        check(String.join("|", Tools.quadratic(1, -3, 2)).equals("Discriminant = 1|Two real roots|x1 = 1|x2 = 2"), "quadratic", "");
        check(Tools.stats(Tools.parseList("0.1 0.2 0.3"))[2].equals("Mean = 0.2"), "statistics mean", "");
        check(String.join("|", Tools.bases("255", 10)).equals("255|FF|1111 1111|377"), "number bases", "");

        // machine tools
        double[] taper = MachineTools.taper(1.05, 1.0, 1.0);
        check(Tools.fixed(taper[1], 4).equals("1.4321"), "taper half angle", Tools.fixed(taper[1], 4));
        check(Tools.fixed(MachineTools.sineBarHeight(10, 15), 4).equals("2.5882"), "sine bar 10 in at 15 degrees", "");
        check(Tools.fixed(MachineTools.sineBarAngle(5, 2.5), 4).equals("30.0000"), "sine bar angle", "");
        double[] thread = MachineTools.thread(8, 1.25, 75, Double.NaN);
        check(Tools.fixed(thread[0], 3).equals("7.188"), "M8x1.25 pitch diameter", Tools.fixed(thread[0], 3));
        check(Tools.fixed(thread[4], 3).equals("6.782"), "M8x1.25 tap drill at 75 percent", Tools.fixed(thread[4], 3));
        double[] inchThread = MachineTools.thread(0.25, 1.0 / 20, 75, Double.NaN);
        check(Tools.fixed(inchThread[4], 4).equals("0.2013"), "1/4-20 tap drill at 75 percent", Tools.fixed(inchThread[4], 4));
        double[] arc = MachineTools.arc(10, 0, 0, 10, 10, true);
        check(Tools.fixed(arc[2], 4).equals("-10.0000") && Tools.fixed(arc[3], 4).equals("0.0000"), "G3 arc I and J", Tools.fixed(arc[2], 4) + "," + Tools.fixed(arc[3], 4));
        double[] arcCw = MachineTools.arc(10, 0, 0, 10, 10, false);
        check(Tools.fixed(arcCw[2], 4).equals("0.0000") && Tools.fixed(arcCw[3], 4).equals("10.0000"), "G2 arc I and J", Tools.fixed(arcCw[2], 4) + "," + Tools.fixed(arcCw[3], 4));

        System.out.println("Self-check: " + checks + " checks, " + failures + " failed.");
        if (failures > 0) System.exit(1);
    }
}
