package kz.teamcargo.pult;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for building the screens in code. */
final class Ui {
    static final int BG = Color.parseColor("#15171C");
    static final int KEY = Color.parseColor("#2A2E37");
    static final int KEY_OFF = Color.parseColor("#1E2128");
    static final int TEXT = Color.parseColor("#F1F3F6");
    static final int MUTED = Color.parseColor("#8D93A0");
    static final int ACCENT = Color.parseColor("#2F7DF6");
    static final int RED = Color.parseColor("#D9342B");
    static final int GREEN = Color.parseColor("#2E9E4F");
    static final int YELLOW = Color.parseColor("#D7A71E");
    static final int BLUE = Color.parseColor("#2B62D9");

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static Button button(Context c, String text, int color) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setBackground(round(c, color, 14));
        b.setMinHeight(dp(c, 52));
        b.setMinimumHeight(dp(c, 52));
        b.setPadding(dp(c, 4), 0, dp(c, 4), 0);
        b.setStateListAnimator(null);
        return b;
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    static TextView title(Context c, String s) {
        TextView t = text(c, s, 18, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(c, 16), 0, dp(c, 6));
        return t;
    }

    static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    /** Adds a view with equal weight and a small margin. */
    static void add(LinearLayout row, android.view.View v, float weight) {
        Context c = row.getContext();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        int m = dp(c, 4);
        lp.setMargins(m, m, m, m);
        row.addView(v, lp);
    }

    static void spacer(LinearLayout row, float weight) {
        android.view.View v = new android.view.View(row.getContext());
        add(row, v, weight);
    }
}
