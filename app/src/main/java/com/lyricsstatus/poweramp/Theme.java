package com.lyricsstatus.poweramp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * App colour themes.
 *
 * Classic: Discord blurple accents on the system light/dark background.
 * CS 1.6: the Counter-Strike 1.6 / classic Steam VGUI look: olive-green panels, mustard-yellow headings,
 * square corners and bevelled 3D borders. It always uses its own dark surfaces, whatever the system mode.
 */
public final class Theme {
    public final String id;
    public final String name;
    /** Accent colour (switches, buttons, highlights). */
    public final int bg;
    /** Text colour on the accent / banner. */
    public final int onBg;
    /** Translucent bubble colour for the Classic banner. */
    public final int bubble;

    /** Full-surface theme: 0 means "use the system colours". */
    public final int window, panel, text, heading, field;
    /** Bevel edges for retro panels and buttons; 0 = flat with rounded corners. */
    public final int bevelLight, bevelDark;

    private Theme(String id, String name, int bg, int onBg, int bubble,
                  int window, int panel, int text, int heading, int field, int bevelLight, int bevelDark) {
        this.id = id;
        this.name = name;
        this.bg = bg;
        this.onBg = onBg;
        this.bubble = bubble;
        this.window = window;
        this.panel = panel;
        this.text = text;
        this.heading = heading;
        this.field = field;
        this.bevelLight = bevelLight;
        this.bevelDark = bevelDark;
    }

    public static final Theme CLASSIC = new Theme("classic", "Classic",
            0xFF5865F2, 0xFFFFFFFF, rgba(200, 205, 255, .45f),
            0, 0, 0, 0, 0, 0, 0);

    /** Colours from the CS 1.6 / 2003 Steam VGUI skin. */
    public static final Theme CS16 = new Theme("cs16", "CS 1.6",
            0xFF958831, 0xFF1E2118, 0,
            0xFF4C5844,   // window: olive green
            0xFF3E4637,   // panels: darker olive
            0xFFD8DED3,   // text: pale grey-green
            0xFFC4B550,   // headings: mustard yellow
            0xFF3E4637,   // text fields
            0xFF8C9284,   // bevel light edge
            0xFF292C21);  // bevel dark edge

    public static final Theme[] ALL = {CLASSIC, CS16};

    public static Theme byId(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return CLASSIC;
    }

    public boolean retro() {
        return bevelLight != 0;
    }

    /** Accent at low opacity, for card backgrounds that work in light and dark mode. */
    public int tint(int alpha) {
        return (bg & 0x00FFFFFF) | (alpha << 24);
    }

    private static int rgba(int r, int g, int b, float a) {
        return (Math.round(a * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * VGUI-style bevelled rectangle: light top/left edge and dark bottom/right edge (raised), or the reverse
     * (sunken, used for text fields and pressed buttons).
     */
    public Drawable bevel(int fill, boolean sunken, float edgePx) {
        final int tl = sunken ? bevelDark : bevelLight;
        final int br = sunken ? bevelLight : bevelDark;
        return new Drawable() {
            private final Paint p = new Paint();

            @Override
            public void draw(Canvas c) {
                android.graphics.Rect b = getBounds();
                float e = Math.max(1, edgePx);
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
            public void setAlpha(int a) {}

            @Override
            public void setColorFilter(ColorFilter f) {}

            @Override
            @SuppressWarnings("deprecation")
            public int getOpacity() {
                return PixelFormat.OPAQUE;
            }
        };
    }

    /** Header banner: Classic gets the blurple banner with bubbles; CS 1.6 a raised VGUI title panel. */
    public Drawable banner(float radiusPx, float density) {
        if (retro()) return bevel(panel, false, density);
        return new Drawable() {
            private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path clip = new Path();

            @Override
            public void draw(Canvas c) {
                RectF r = new RectF(getBounds());
                clip.reset();
                clip.addRoundRect(r, radiusPx, radiusPx, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                fill.setColor(bg);
                c.drawRect(r, fill);
                Random rnd = new Random(42);
                int[] sizes = {14, 20, 26, 34, 44, 54};
                for (int i = 0; i < 11; i++) {
                    float d = sizes[i < 3 ? 4 + rnd.nextInt(2) : rnd.nextInt(4)] * density;
                    float x = r.left + rnd.nextFloat() * r.width();
                    float y = r.top + rnd.nextFloat() * r.height();
                    fill.setColor(i % 4 == 3 ? 0x52FFFFFF : bubble);
                    c.drawCircle(x, y, d / 2, fill);
                }
                c.restore();
            }

            @Override
            public void setAlpha(int a) {}

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
