package com.lyricsstatus.poweramp;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * CS 1.6 theme, ported from smegolas.com's style.css ("Half-Life Tracker VGUI scheme: olive panels,
 * bevelled 1px borders, square corners, Tahoma"). Same tokens, and the same components:
 * main-menu text list (.mi), bevelled windows with a title bar and close box (.win/.win-title/.win-x),
 * sunken boxes with yellow headings (.box h3), VGUI buttons (.vbtn, .vbtn-def), checkboxes (.vcheck),
 * radio buttons (.vradio), sunken inputs (.vin) and sliders (.vslider). Plus the game's menu sounds.
 */
public final class CsSkin extends Skin {

    // Design tokens from :root[data-theme="cs16"]
    static final int WIN = 0xFF4C5844, WIN2 = 0xFF3E4637, FIELD = 0xFF3E4637;
    static final int HI = 0xFF889180, LO = 0xFF282E22;
    static final int TEXT = 0xFFD8DED3, MUTED = 0xFFA0AA95, TITLE = 0xFFFFFFFF;
    static final int ACCENT = 0xFFC4B550, SEL = 0xFF958831;
    static final int MENU = 0xFFD8DED3, MENU_HOVER = 0xFFFFFFFF;
    private static final float FS = 13; // --fs: 13px

    private final Sfx sfx;
    /** Tahoma isn't on Android; DejaVu Sans / sans-serif are the stylesheet's own fallbacks. */
    private final Typeface font = Typeface.create("sans-serif", Typeface.NORMAL);
    private final List<TextView> winTitles = new ArrayList<>();
    private final List<View> windows = new ArrayList<>();
    private final List<TextView> menuItems = new ArrayList<>();

    public CsSkin(Activity a, Sfx sfx) {
        super(a);
        this.sfx = sfx;
    }

    @Override
    public String id() {
        return "cs16";
    }

    @Override
    public boolean hasSounds() {
        return true;
    }

    @Override
    public void applyWindow(ScrollView scroll, LinearLayout root) {
        // .bg: linear-gradient(160deg, #4a5440, #23281e) under a radial darkening toward the edges
        GradientDrawable lin = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF4A5440, 0xFF23281E});
        GradientDrawable vignette = new GradientDrawable();
        vignette.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        vignette.setColors(new int[]{0x00000000, 0x73000000});
        vignette.setGradientCenter(0.7f, 0.3f);
        vignette.setGradientRadius(a.getResources().getDisplayMetrics().heightPixels * 0.9f);
        scroll.setBackground(new LayerDrawable(new Drawable[]{lin, vignette}));
        systemBars(0xFF4A5440, true);
    }

    @Override
    public View header(String title, String subtitle) {
        // Game-menu title: plain light text with the menu's 1px black text shadow
        LinearLayout l = vertical();
        l.setPadding(dp(4), dp(8), 0, dp(4));
        TextView t = text(title.replace("🎶 ", "").toUpperCase(), 24, true);
        t.setTextColor(TITLE);
        t.setLetterSpacing(0.04f);
        t.setShadowLayer(1, dp(1), dp(1), 0x99000000);
        l.addView(t);
        TextView s = text(subtitle, FS, false);
        s.setTextColor(MUTED);
        s.setShadowLayer(1, dp(1), dp(1), 0x99000000);
        l.addView(s);
        return l;
    }

    @Override
    public View menu(String[] items, IntConsumer onPick) {
        // .mi: plain light text list, 15px, text-shadow 1px 1px 0 rgba(0,0,0,.6); .mi.on = accent
        LinearLayout l = vertical();
        l.setPadding(dp(4), dp(10), 0, dp(6));
        for (int i = 0; i < items.length; i++) {
            final int idx = i == items.length - 1 ? -1 : i; // last item is Quit
            TextView mi = new TextView(a);
            mi.setText(items[i]);
            mi.setTypeface(font);
            mi.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            mi.setTextColor(MENU);
            mi.setShadowLayer(1, dp(1), dp(1), 0x99000000);
            mi.setPadding(0, dp(3), dp(24), dp(3));
            mi.setOnClickListener(v -> onPick.accept(idx));
            sfx.wire(mi);
            if (idx >= 0) menuItems.add(mi);
            l.addView(mi, wrap());
        }
        return l;
    }

    @Override
    public LinearLayout group(LinearLayout root, String title) {
        // .win: --win background, raised 1px bevel, box-shadow 1px 1px 0 rgba(0,0,0,.35), square corners
        final int index = windows.size();
        LinearLayout win = vertical();
        win.setBackground(new LayerDrawable(new Drawable[]{shadow(), bevel(WIN, false, false)}));
        win.setPadding(dp(1), dp(1), dp(2), dp(2));
        LinearLayout.LayoutParams lp = fill();
        lp.topMargin = dp(12);
        win.setLayoutParams(lp);

        // .win-title: padding 7px 8px 6px 12px, h2 weight 400, white when in front, muted behind
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(7), dp(8), dp(6));
        TextView h2 = text(title, FS, false);
        h2.setTextColor(MUTED);
        h2.setLetterSpacing(0.01f);
        bar.addView(h2, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout body = vertical();
        body.setPadding(dp(12), dp(2), dp(12), dp(12));

        // .win-x: 18x16 raised box with an x, pressed bevel on touch. Here it rolls the window up/down.
        View x = new View(a);
        StateListDrawable xs = new StateListDrawable();
        xs.addState(new int[]{android.R.attr.state_pressed}, layer(bevel(WIN, true, false), cross()));
        xs.addState(new int[]{}, layer(bevel(WIN, false, false), cross()));
        x.setBackground(xs);
        x.setContentDescription("Close " + title);
        x.setOnClickListener(v -> {
            body.setVisibility(body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            front(index);
        });
        sfx.wire(x);
        bar.addView(x, new LinearLayout.LayoutParams(dp(18), dp(16)));
        bar.setOnClickListener(v -> front(index));

        win.addView(bar, fill());
        win.addView(body, fill());
        root.addView(win);
        windows.add(win);
        winTitles.add(h2);
        if (index == 0) front(0);
        return body;
    }

    @Override
    public void focusGroup(int index) {
        if (index < 0 || index >= windows.size()) return;
        View win = windows.get(index);
        View body = ((LinearLayout) win).getChildAt(1);
        body.setVisibility(View.VISIBLE);
        front(index);
    }

    /** Bring a window to the front: white title for it, muted titles for the rest; menu item highlighted. */
    private void front(int index) {
        for (int i = 0; i < winTitles.size(); i++) winTitles.get(i).setTextColor(i == index ? TITLE : MUTED);
        for (int i = 0; i < menuItems.size(); i++) menuItems.get(i).setTextColor(i == index ? ACCENT : MENU);
    }

    @Override
    public LinearLayout box() {
        // .box: sunken bevel (lo/hi), --win2 background, padding 10px 12px 6px
        LinearLayout c = vertical();
        c.setBackground(bevel(WIN2, true, false));
        c.setPadding(dp(12), dp(10), dp(12), dp(6));
        LinearLayout.LayoutParams lp = fill();
        lp.topMargin = dp(4);
        c.setLayoutParams(lp);
        return c;
    }

    @Override
    public Button button(String label, boolean primary, View.OnClickListener click) {
        // .vbtn: --win background, raised bevel, padding 6px 14px, min-height 26px; :active reverses the bevel.
        // .vbtn-def (the default button) also gets a 1px dark outline.
        Button b = new Button(a);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(font);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, FS);
        b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_pressed}, {}}, new int[]{TITLE, TEXT}));
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, bevel(WIN, true, primary));
        sl.addState(new int[]{}, bevel(WIN, false, primary));
        b.setBackground(sl);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(dp(26));
        b.setMinWidth(0);
        b.setMinimumWidth(dp(48));
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        b.setOnClickListener(click);
        sfx.wire(b);
        LinearLayout.LayoutParams lp = wrap();
        lp.rightMargin = dp(6);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    public View toggle(String title, String desc, boolean checked, Consumer<Boolean> onChange) {
        // .vcheck: flex row, gap 8px, margin 6px 0, checkbox in the accent colour
        LinearLayout l = vertical();
        l.setPadding(0, dp(6), 0, dp(6));
        CheckBox cb = new CheckBox(a);
        cb.setButtonDrawable(checkDrawable());
        cb.setText(title);
        cb.setTypeface(font);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, FS);
        cb.setTextColor(TEXT);
        cb.setPadding(dp(8), 0, 0, 0);
        cb.setMinHeight(dp(24));
        cb.setChecked(checked);
        cb.setOnCheckedChangeListener((v, on) -> onChange.accept(on));
        sfx.wire(cb);
        l.addView(cb, wrap());
        if (desc != null) {
            TextView d = text(desc, 12, false);
            d.setTextColor(MUTED);
            d.setPadding(dp(21), 0, 0, 0);
            l.addView(d);
        }
        return l;
    }

    @Override
    public EditText edit() {
        // .vin: --field background, sunken bevel; :focus turns the border accent
        EditText e = new EditText(a);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, edged(FIELD, ACCENT, ACCENT));
        sl.addState(new int[]{}, bevel(FIELD, true, false));
        e.setBackground(sl);
        e.setPadding(dp(6), dp(5), dp(6), dp(5));
        e.setTypeface(font);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, FS);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setHighlightColor((SEL & 0x00FFFFFF) | 0xCC000000);
        if (android.os.Build.VERSION.SDK_INT >= 29) e.setTextCursorDrawable(cursor());
        LinearLayout.LayoutParams lp = fill();
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        e.setLayoutParams(lp);
        return e;
    }

    @Override
    public View slider(String title, int value, int max, IntConsumer onChange, Runnable onRelease) {
        // .vslider: label, range in the accent colour, value readout
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(6), 0, dp(6));
        TextView t = text(title, FS, false);
        l.addView(t, wrap());
        SeekBar sb = new SeekBar(a);
        sb.setMax(max);
        sb.setProgress(value);
        sb.setProgressTintList(ColorStateList.valueOf(ACCENT));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(LO));
        sb.setThumb(sliderThumb());
        sb.setSplitTrack(false);
        TextView out = text(String.valueOf(value), FS, false);
        out.setMinWidth(dp(28));
        out.setGravity(Gravity.END);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                out.setText(String.valueOf(p));
                if (u) onChange.accept(p);
            }

            public void onStartTrackingTouch(SeekBar s) {}

            public void onStopTrackingTouch(SeekBar s) {
                if (onRelease != null) onRelease.run();
            }
        });
        l.addView(sb, new LinearLayout.LayoutParams(0, -2, 1f));
        l.addView(out, wrap());
        return l;
    }

    @Override
    public View themePicker(String current, Consumer<String> onPick) {
        // Options > Appearance on the site: .vradio list
        RadioGroup g = new RadioGroup(a);
        g.setOrientation(RadioGroup.VERTICAL);
        for (String[] t : THEMES) {
            RadioButton r = new RadioButton(a);
            r.setId(View.generateViewId());
            r.setButtonDrawable(radioDrawable());
            r.setText(t[1]);
            r.setTypeface(font);
            r.setTextSize(TypedValue.COMPLEX_UNIT_SP, FS);
            r.setTextColor(TEXT);
            r.setPadding(dp(8), 0, 0, 0);
            r.setMinHeight(dp(24));
            r.setChecked(t[0].equals(current));
            r.setOnClickListener(v -> onPick.accept(t[0]));
            sfx.wire(r);
            RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(-2, -2);
            lp.topMargin = dp(6);
            lp.bottomMargin = dp(6);
            g.addView(r, lp);
        }
        return g;
    }

    @Override
    public TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(bold ? Typeface.create(font, Typeface.BOLD) : font);
        t.setTextColor(TEXT);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, dp(1), 0, dp(1));
        return t;
    }

    @Override
    public TextView label(String s) {
        // .box h3: bold, accent colour, letter-spacing .02em
        TextView t = text(s, FS, true);
        t.setTextColor(ACCENT);
        t.setLetterSpacing(0.02f);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    @Override
    public TextView hint(String s) {
        TextView t = text(s, 12, false);
        t.setTextColor(MUTED);
        return t;
    }

    @Override
    public int highlight() {
        return ACCENT;
    }

    @Override
    public int errorColor() {
        return 0xFFE0745A;
    }

    @Override
    public int linkColor() {
        return ACCENT;
    }

    // ------------------------------------------------------------------ VGUI drawables

    /**
     * 1px bevel: raised = light top/left (--hi), dark bottom/right (--lo); sunken = the reverse.
     * {@code defOutline} adds the 1px --lo outline of a default button (.vbtn-def).
     */
    Drawable bevel(int fill, boolean sunken, boolean defOutline) {
        return new Drawable() {
            private final Paint p = new Paint();

            @Override
            public void draw(Canvas c) {
                Rect b = new Rect(getBounds());
                int e = Math.max(1, dp(1));
                if (defOutline) {
                    p.setColor(LO);
                    c.drawRect(b, p);
                    b.inset(e, e);
                }
                p.setColor(fill);
                c.drawRect(b, p);
                p.setColor(sunken ? LO : HI);
                c.drawRect(b.left, b.top, b.right, b.top + e, p);
                c.drawRect(b.left, b.top, b.left + e, b.bottom, p);
                p.setColor(sunken ? HI : LO);
                c.drawRect(b.left, b.bottom - e, b.right, b.bottom, p);
                c.drawRect(b.right - e, b.top, b.right, b.bottom, p);
            }

            @Override
            public void setAlpha(int x) {}

            @Override
            public void setColorFilter(ColorFilter f) {}

            @Override
            @SuppressWarnings("deprecation")
            public int getOpacity() {
                return PixelFormat.OPAQUE;
            }
        };
    }

    private Drawable edged(int fill, int tl, int br) {
        return new Drawable() {
            private final Paint p = new Paint();

            @Override
            public void draw(Canvas c) {
                Rect b = getBounds();
                int e = Math.max(1, dp(1));
                p.setColor(fill);
                c.drawRect(b, p);
                p.setColor(tl);
                c.drawRect(b.left, b.top, b.right, b.top + e, p);
                c.drawRect(b.left, b.top, b.left + e, b.bottom, p);
                p.setColor(br);
                c.drawRect(b.left, b.bottom - e, b.right, b.bottom, p);
                c.drawRect(b.right - e, b.top, b.right, b.bottom, p);
            }

            @Override
            public void setAlpha(int x) {}

            @Override
            public void setColorFilter(ColorFilter f) {}

            @Override
            @SuppressWarnings("deprecation")
            public int getOpacity() {
                return PixelFormat.OPAQUE;
            }
        };
    }

    /** box-shadow: 1px 1px 0 rgba(0,0,0,.35) */
    private Drawable shadow() {
        return new Drawable() {
            private final Paint p = new Paint();

            @Override
            public void draw(Canvas c) {
                Rect b = getBounds();
                int e = Math.max(1, dp(1));
                p.setColor(0x59000000);
                c.drawRect(b.left + e, b.top + e, b.right, b.bottom, p);
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

    private LayerDrawable layer(Drawable a1, Drawable a2) {
        return new LayerDrawable(new Drawable[]{a1, a2});
    }

    private Drawable cross() {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas c) {
                Rect b = getBounds();
                p.setColor(TEXT);
                p.setStrokeWidth(Math.max(1.5f, dp(1.5f)));
                float cx = b.exactCenterX(), cy = b.exactCenterY(), s = dp(3.5f);
                c.drawLine(cx - s, cy - s, cx + s, cy + s, p);
                c.drawLine(cx + s, cy - s, cx - s, cy + s, p);
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

    /** VGUI checkbox: 13px sunken field square, accent check when on. */
    private Drawable checkDrawable() {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_checked}, checkBox(true));
        sl.addState(new int[]{}, checkBox(false));
        return sl;
    }

    private Drawable checkBox(boolean on) {
        Drawable box = bevel(FIELD, true, false);
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas c) {
                Rect b = getBounds();
                int s = dp(13);
                Rect r = new Rect(b.left, b.centerY() - s / 2, b.left + s, b.centerY() - s / 2 + s);
                box.setBounds(r);
                box.draw(c);
                if (on) {
                    p.setColor(ACCENT);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(dp(2));
                    float l = r.left + s * 0.22f, m = r.left + s * 0.42f, rr = r.left + s * 0.78f;
                    c.drawLine(l, r.top + s * 0.52f, m, r.top + s * 0.72f, p);
                    c.drawLine(m, r.top + s * 0.72f, rr, r.top + s * 0.28f, p);
                }
            }

            @Override
            public int getIntrinsicWidth() {
                return dp(13);
            }

            @Override
            public int getIntrinsicHeight() {
                return dp(13);
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

    /** VGUI radio: sunken round field with an accent dot. */
    private Drawable radioDrawable() {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_checked}, radio(true));
        sl.addState(new int[]{}, radio(false));
        return sl;
    }

    private Drawable radio(boolean on) {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas c) {
                Rect b = getBounds();
                float s = dp(13), cx = b.left + s / 2, cy = b.exactCenterY(), r = s / 2;
                p.setStyle(Paint.Style.FILL);
                p.setColor(FIELD);
                c.drawCircle(cx, cy, r, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1, dp(1)));
                p.setColor(LO); // dark upper-left arc, light lower-right: sunken
                c.drawArc(cx - r, cy - r, cx + r, cy + r, 135, 180, false, p);
                p.setColor(HI);
                c.drawArc(cx - r, cy - r, cx + r, cy + r, 315, 180, false, p);
                if (on) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(ACCENT);
                    c.drawCircle(cx, cy, r * 0.42f, p);
                }
            }

            @Override
            public int getIntrinsicWidth() {
                return dp(13);
            }

            @Override
            public int getIntrinsicHeight() {
                return dp(13);
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

    /** Raised VGUI slider handle. */
    private Drawable sliderThumb() {
        Drawable d = bevel(WIN, false, false);
        return new Drawable() {
            @Override
            public void draw(Canvas c) {
                d.setBounds(getBounds());
                d.draw(c);
            }

            @Override
            public int getIntrinsicWidth() {
                return dp(10);
            }

            @Override
            public int getIntrinsicHeight() {
                return dp(18);
            }

            @Override
            public void setAlpha(int x) {}

            @Override
            public void setColorFilter(ColorFilter f) {}

            @Override
            @SuppressWarnings("deprecation")
            public int getOpacity() {
                return PixelFormat.OPAQUE;
            }
        };
    }

    private Drawable cursor() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(TEXT);
        g.setSize(Math.max(1, dp(1)), dp(16));
        return g;
    }
}
