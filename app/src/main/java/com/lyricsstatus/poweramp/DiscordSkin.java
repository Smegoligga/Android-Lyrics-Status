package com.lyricsstatus.poweramp;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Classic theme, built like Discord's own settings screens: Discord's dark/light palettes (following the
 * phone), grey uppercase section labels over rounded panels, blurple 3 px-corner buttons with grey secondary
 * buttons, green toggles with a check/cross knob, and dark borderless inputs.
 */
public final class DiscordSkin extends Skin {

    private static final int BLURPLE = 0xFF5865F2, BLURPLE_PRESSED = 0xFF4752C4;
    private static final int GREEN = 0xFF23A55A, SWITCH_OFF = 0xFF80848E;

    private final boolean dark;
    private final int bg, panel, input, text, header, headerSecondary, muted, divider, secondaryBtn, secondaryBtnPressed;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    public DiscordSkin(Activity a) {
        super(a);
        dark = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            bg = 0xFF313338; panel = 0xFF2B2D31; input = 0xFF1E1F22;
            text = 0xFFDBDEE1; header = 0xFFF2F3F5; headerSecondary = 0xFFB5BAC1; muted = 0xFF949BA4;
            divider = 0xFF3F4147; secondaryBtn = 0xFF4E5058; secondaryBtnPressed = 0xFF6D6F78;
        } else {
            bg = 0xFFFFFFFF; panel = 0xFFF2F3F5; input = 0xFFE3E5E8;
            text = 0xFF313338; header = 0xFF060607; headerSecondary = 0xFF4E5058; muted = 0xFF5C5E66;
            divider = 0xFFE1E2E4; secondaryBtn = 0xFF6D6F78; secondaryBtnPressed = 0xFF4E5058;
        }
    }

    @Override
    public String id() {
        return "classic";
    }

    @Override
    public void applyWindow(ScrollView scroll, LinearLayout root) {
        scroll.setBackgroundColor(bg);
        systemBars(bg, dark);
    }

    @Override
    public View header(String title, String subtitle) {
        // Discord profile-card style: blurple banner strip on top of a panel
        LinearLayout card = vertical();
        card.setBackground(rounded(panel, 8));
        card.setClipToOutline(true);
        View banner = new View(a);
        GradientDrawable b = new GradientDrawable();
        b.setColor(BLURPLE);
        float r = dp(8);
        b.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        banner.setBackground(b);
        card.addView(banner, new LinearLayout.LayoutParams(-1, dp(56)));
        LinearLayout body = vertical();
        body.setPadding(dp(16), dp(12), dp(16), dp(16));
        TextView t = text(title, 20, true);
        t.setTextColor(header);
        body.addView(t);
        TextView s = text(subtitle, 14, false);
        s.setTextColor(muted);
        body.addView(s);
        card.addView(body);
        return card;
    }

    @Override
    public LinearLayout group(LinearLayout root, String title) {
        TextView label = text(title.toUpperCase(), 12, true);
        label.setTextColor(headerSecondary);
        label.setLetterSpacing(0.02f);
        label.setPadding(dp(4), dp(24), 0, dp(8));
        root.addView(label);
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(8), dp(16), dp(12));
        card.setBackground(rounded(panel, 8));
        root.addView(card, fill());
        return card;
    }

    @Override
    public LinearLayout box() {
        // Already inside a panel, so no second background
        LinearLayout c = vertical();
        c.setPadding(0, dp(6), 0, dp(6));
        return c;
    }

    @Override
    public TextView fieldLabel(String s) {
        // Discord form labels: 12px bold uppercase, secondary header colour
        TextView t = text(s.toUpperCase(), 12, true);
        t.setTextColor(headerSecondary);
        t.setLetterSpacing(0.02f);
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    @Override
    public Button button(String label, boolean primary, View.OnClickListener click) {
        Button btn = new Button(a);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTypeface(medium);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTextColor(0xFFFFFFFF);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, rounded(primary ? BLURPLE_PRESSED : secondaryBtnPressed, 3));
        sl.addState(new int[]{}, rounded(primary ? BLURPLE : secondaryBtn, 3));
        btn.setBackground(sl);
        btn.setStateListAnimator(null);
        btn.setMinHeight(0);
        btn.setMinimumHeight(dp(38));
        btn.setMinWidth(dp(60));
        btn.setMinimumWidth(dp(60));
        btn.setPadding(dp(16), dp(2), dp(16), dp(2));
        btn.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.rightMargin = dp(8);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        btn.setLayoutParams(lp);
        return btn;
    }

    @Override
    public View toggle(String title, String desc, boolean checked, Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout texts = vertical();
        TextView t = text(title, 16, false);
        t.setTypeface(medium);
        t.setTextColor(header);
        texts.addView(t);
        if (desc != null) {
            TextView d = text(desc, 14, false);
            d.setTextColor(muted);
            texts.addView(d);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(a);
        sw.setThumbDrawable(knob());
        sw.setTrackDrawable(track());
        sw.setThumbTintList(null);
        sw.setTrackTintList(null);
        sw.setSwitchMinWidth(dp(40));
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, v) -> onChange.accept(v));
        LinearLayout.LayoutParams lp = wrap();
        lp.leftMargin = dp(12);
        row.addView(sw, lp);
        row.setOnClickListener(v -> sw.toggle());

        // thin divider under each row, like Discord's settings lists
        LinearLayout wrapper = vertical();
        wrapper.addView(row);
        View line = new View(a);
        line.setBackgroundColor(divider);
        wrapper.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2 + 1)));
        return wrapper;
    }

    @Override
    public EditText edit() {
        EditText e = new EditText(a);
        e.setBackground(rounded(input, 3));
        e.setPadding(dp(10), dp(10), dp(10), dp(10));
        e.setTextColor(text);
        e.setHintTextColor(muted);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setHighlightColor((BLURPLE & 0x00FFFFFF) | 0x66000000);
        LinearLayout.LayoutParams lp = fill();
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(4);
        e.setLayoutParams(lp);
        return e;
    }

    @Override
    public View slider(String title, int value, int max, int mark, String markLabel, String unit,
                       IntConsumer onChange, Runnable onRelease) {
        // Discord settings slider: title + value on one line, blurple fill, white handle
        LinearLayout l = vertical();
        l.setPadding(0, dp(10), 0, dp(10));
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = text(title, 16, false);
        t.setTypeface(medium);
        t.setTextColor(header);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView out = text(fmt(value, unit), 14, false);
        out.setTypeface(medium);
        out.setTextColor(headerSecondary);
        top.addView(out, wrap());
        l.addView(top);
        SeekBar sb = new MarkedSeekBar(a, mark, GREEN);
        sb.setMax(max);
        sb.setProgress(value);
        sb.setProgressTintList(ColorStateList.valueOf(BLURPLE));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(dark ? 0xFF4E5058 : 0xFFC4C9CE));
        sb.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                out.setText(fmt(p, unit));
                if (u) onChange.accept(p);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { if (onRelease != null) onRelease.run(); }
        });
        l.addView(sb, fill());
        if (mark >= 0) {
            TextView cap = text("▲ " + markLabel, 13, false);
            cap.setTypeface(medium);
            cap.setTextColor(GREEN);
            cap.setOnClickListener(v -> {
                sb.setProgress(mark);
                onChange.accept(mark);
            });
            l.addView(cap);
        }
        return l;
    }

    @Override
    public View choice(String title, String[] options, int selected, IntConsumer onPick) {
        // Discord settings radio list: title, then rows with a blurple radio
        LinearLayout l = vertical();
        l.setPadding(0, dp(12), 0, dp(8));
        TextView t = text(title, 16, false);
        t.setTypeface(medium);
        t.setTextColor(header);
        l.addView(t);
        android.widget.RadioGroup g = new android.widget.RadioGroup(a);
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            android.widget.RadioButton r = new android.widget.RadioButton(a);
            r.setId(View.generateViewId());
            r.setText(options[i]);
            r.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            r.setTextColor(text);
            r.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{BLURPLE, muted}));
            r.setPadding(dp(8), dp(6), 0, dp(6));
            r.setChecked(i == selected);
            r.setOnClickListener(v -> onPick.accept(idx));
            g.addView(r);
        }
        l.addView(g);
        return l;
    }

    @Override
    public View themePicker(String current, Consumer<String> onPick) {
        // Discord's Appearance picker: round swatches, selected one ringed in blurple with a check badge
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));
        int[][] swatch = {{BLURPLE, 0xFF404EED}, {0xFF4C5844, 0xFFC4B550}};
        for (int i = 0; i < THEMES.length; i++) {
            String id = THEMES[i][0];
            boolean sel = id.equals(current);
            LinearLayout chip = vertical();
            chip.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout holder = new FrameLayout(a);
            View dot = new View(a);
            GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, swatch[i]);
            d.setShape(GradientDrawable.OVAL);
            if (sel) d.setStroke(dp(2), BLURPLE);
            dot.setBackground(d);
            holder.addView(dot, new FrameLayout.LayoutParams(dp(56), dp(56)));
            if (sel) {
                View badge = new View(a);
                badge.setBackground(checkBadge());
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP | Gravity.END);
                holder.addView(badge, bl);
            }
            chip.addView(holder);
            TextView name = text(THEMES[i][1], 13, sel);
            name.setTextColor(sel ? header : muted);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(6), 0, 0);
            chip.addView(name);
            chip.setContentDescription(THEMES[i][1] + " theme" + (sel ? ", selected" : ""));
            chip.setOnClickListener(v -> onPick.accept(id));
            row.addView(chip, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return row;
    }

    @Override
    public TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(text);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    @Override
    public TextView label(String s) {
        TextView t = text(s, 16, false);
        t.setTypeface(medium);
        t.setTextColor(header);
        t.setPadding(0, dp(14), 0, dp(2));
        return t;
    }

    @Override
    public TextView hint(String s) {
        TextView t = text(s, 14, false);
        t.setTextColor(muted);
        return t;
    }

    @Override
    public int highlight() {
        return dark ? 0xFF949CF7 : BLURPLE;
    }

    @Override
    public int errorColor() {
        return dark ? 0xFFF23F43 : 0xFFDA373C;
    }

    @Override
    public int linkColor() {
        return dark ? 0xFF00A8FC : 0xFF006CE7;
    }

    // ------------------------------------------------------------------ drawables

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private Drawable track() {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_checked}, pill(GREEN));
        sl.addState(new int[]{}, pill(SWITCH_OFF));
        return sl;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable d = rounded(color, 12);
        d.setSize(dp(40), dp(24));
        return d;
    }

    /** White knob with a check (on) or cross (off), like Discord's toggle. */
    private Drawable knob() {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_checked}, knobIcon(true));
        sl.addState(new int[]{}, knobIcon(false));
        return sl;
    }

    private Drawable knobIcon(boolean on) {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas c) {
                android.graphics.Rect b = getBounds();
                float cx = b.exactCenterX(), cy = b.exactCenterY(), r = dp(9);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, r, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(on ? GREEN : SWITCH_OFF);
                float s = dp(4);
                if (on) {
                    c.drawLine(cx - s, cy, cx - s / 3, cy + s * 0.7f, p);
                    c.drawLine(cx - s / 3, cy + s * 0.7f, cx + s, cy - s * 0.6f, p);
                } else {
                    c.drawLine(cx - s * 0.75f, cy - s * 0.75f, cx + s * 0.75f, cy + s * 0.75f, p);
                    c.drawLine(cx + s * 0.75f, cy - s * 0.75f, cx - s * 0.75f, cy + s * 0.75f, p);
                }
            }

            @Override
            public int getIntrinsicWidth() {
                return dp(24);
            }

            @Override
            public int getIntrinsicHeight() {
                return dp(24);
            }

            @Override
            public void setAlpha(int x) {}

            @Override
            public void setColorFilter(ColorFilter f) {}

            @Override
            @SuppressWarnings("deprecation")
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }

    private Drawable checkBadge() {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas c) {
                android.graphics.Rect b = getBounds();
                float cx = b.exactCenterX(), cy = b.exactCenterY(), r = b.width() / 2f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(BLURPLE);
                c.drawCircle(cx, cy, r, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(0xFFFFFFFF);
                float s = r * 0.45f;
                c.drawLine(cx - s, cy, cx - s / 3, cy + s * 0.7f, p);
                c.drawLine(cx - s / 3, cy + s * 0.7f, cx + s, cy - s * 0.6f, p);
            }

            @Override
            public void setAlpha(int x) {}

            @Override
            public void setColorFilter(ColorFilter f) {}

            @Override
            @SuppressWarnings("deprecation")
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }
}
