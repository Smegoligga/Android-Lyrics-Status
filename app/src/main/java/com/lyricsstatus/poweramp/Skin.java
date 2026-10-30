package com.lyricsstatus.poweramp;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * A complete look for the settings screen: each theme builds its own controls so layouts can be faithful
 * to the original (Discord's settings UI, or CS 1.6's VGUI windows), not just recoloured.
 */
public abstract class Skin {

    /** Theme ids and display names, in picker order. Classic (Discord) is the default. */
    public static final String[][] THEMES = {{"classic", "Classic (Discord)"}, {"cs16", "CS 1.6"}};

    public static Skin create(String id, Activity a, Sfx sfx) {
        if ("cs16".equals(id)) return new CsSkin(a, sfx);
        return new DiscordSkin(a);
    }

    protected final Activity a;
    protected final float density;

    protected Skin(Activity a) {
        this.a = a;
        this.density = a.getResources().getDisplayMetrics().density;
    }

    public abstract String id();

    /** Window background, system bar colours and icon contrast. */
    public abstract void applyWindow(ScrollView scroll, LinearLayout root);

    /** App title block at the top. */
    public abstract View header(String title, String subtitle);

    /** Optional navigation (CS 1.6 main menu). {@code onPick} gets the index of a group, or -1 for Quit. */
    public View menu(String[] items, IntConsumer onPick) {
        return null;
    }

    /** A titled settings group added to {@code root}; returns the container to put rows in. */
    public abstract LinearLayout group(LinearLayout root, String title);

    /** Called when a group is shown from the menu (CS 1.6 brings that window to the front). */
    public void focusGroup(int index) {}

    /** An inset box inside a group (live status). */
    public abstract LinearLayout box();

    public abstract Button button(String label, boolean primary, View.OnClickListener click);

    /** On/off setting row (Discord switch / VGUI checkbox). */
    public abstract View toggle(String title, String desc, boolean checked, Consumer<Boolean> onChange);

    public abstract EditText edit();

    /**
     * Slider with a value readout. {@code mark} >= 0 draws a "recommended" marker at that value and adds a
     * caption ({@code markLabel}) that snaps the slider to it when tapped; -1 for none.
     */
    public abstract View slider(String title, int value, int max, int mark, String markLabel, String unit,
                                IntConsumer onChange, Runnable onRelease);

    /** SeekBar that draws a small marker (triangle above, tick below the track) at one value. */
    public static class MarkedSeekBar extends android.widget.SeekBar {
        private final int mark;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float d;

        public MarkedSeekBar(android.content.Context c, int mark, int color) {
            super(c);
            this.mark = mark;
            this.d = c.getResources().getDisplayMetrics().density;
            p.setColor(color);
            p.setStrokeWidth(Math.max(1.5f, 2 * d));
            setMinimumHeight(Math.round(30 * d));
        }

        @Override
        protected synchronized void onDraw(android.graphics.Canvas c) {
            if (mark >= 0 && getMax() > 0) {
                float w = getWidth() - getPaddingLeft() - getPaddingRight();
                float x = getPaddingLeft() + w * mark / getMax();
                float cy = getHeight() / 2f;
                android.graphics.Path t = new android.graphics.Path();
                t.moveTo(x - 5 * d, cy - 13 * d);
                t.lineTo(x + 5 * d, cy - 13 * d);
                t.lineTo(x, cy - 7 * d);
                t.close();
                p.setStyle(android.graphics.Paint.Style.FILL);
                c.drawPath(t, p);
                c.drawLine(x, cy + 7 * d, x, cy + 13 * d, p);
            }
            super.onDraw(c);
        }
    }

    /** Readout text like "3 s" or "70". */
    protected static String fmt(int v, String unit) {
        return unit == null || unit.isEmpty() ? String.valueOf(v) : v + " " + unit;
    }

    /** Single choice from a few options (radio buttons); calls {@code onPick} with the chosen index. */
    public abstract View choice(String title, String[] options, int selected, java.util.function.IntConsumer onPick);

    /** Theme chooser in this skin's style; calls {@code onPick} with a theme id. */
    public abstract View themePicker(String current, Consumer<String> onPick);

    public abstract TextView text(String s, float sp, boolean bold);

    /** Label above a text field. */
    public TextView fieldLabel(String s) {
        return text(s, 13, false);
    }

    /** Status line label (✓ Discord token …). */
    public abstract TextView label(String s);

    public abstract TextView hint(String s);

    /** Colour for the current lyric line. */
    public abstract int highlight();

    public abstract int errorColor();

    public abstract int linkColor();

    /** Whether this skin offers menu sounds. */
    public boolean hasSounds() {
        return false;
    }

    // ------------------------------------------------------------------ shared helpers

    public int dp(float v) {
        return Math.round(v * density);
    }

    protected LinearLayout vertical() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public LinearLayout row() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(0, dp(4), 0, dp(4));
        return l;
    }

    protected static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    protected static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Paint the window and pick light or dark system-bar icons. */
    @SuppressWarnings("deprecation")
    protected void systemBars(int color, boolean darkBackground) {
        a.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(color));
        a.getWindow().setStatusBarColor(color);
        a.getWindow().setNavigationBarColor(color);
        View decor = a.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(darkBackground ? flags & ~light : flags | light);
        if (Build.VERSION.SDK_INT >= 30 && a.getWindow().getInsetsController() != null) {
            int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            a.getWindow().getInsetsController().setSystemBarsAppearance(darkBackground ? 0 : mask, mask);
        }
    }
}
