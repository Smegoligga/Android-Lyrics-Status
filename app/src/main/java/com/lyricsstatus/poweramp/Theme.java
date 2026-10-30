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
 * Colour themes for the app. Orange, Blue, Pink and Green are the banner themes from the Museum ticket app
 * (same background and bubble colours); Classic is the original Discord blurple.
 */
public final class Theme {
    public final String id;
    public final String name;
    /** Banner / accent colour. */
    public final int bg;
    /** Translucent bubble colour drawn over the banner. */
    public final int bubble;
    /** Text colour on the banner and on accent buttons. */
    public final int onBg;

    private Theme(String id, String name, int bg, int bubble, int onBg) {
        this.id = id;
        this.name = name;
        this.bg = bg;
        this.bubble = bubble;
        this.onBg = onBg;
    }

    public static final Theme[] ALL = {
            new Theme("classic", "Classic", 0xFF5865F2, rgba(200, 205, 255, .45f), 0xFFFFFFFF),
            new Theme("orange", "Orange", 0xFFD85A30, rgba(250, 170, 120, .45f), 0xFF000000),
            new Theme("blue", "Blue", 0xFF3E9BE0, rgba(210, 235, 255, .50f), 0xFFFFFFFF),
            new Theme("pink", "Pink", 0xFFE0218A, rgba(255, 170, 210, .50f), 0xFFFFFFFF),
            new Theme("green", "Green", 0xFF2E9E5B, rgba(175, 230, 195, .55f), 0xFFFFFFFF),
    };

    public static Theme byId(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return ALL[0];
    }

    /** Accent at low opacity, for card backgrounds that work in light and dark mode. */
    public int tint(int alpha) {
        return (bg & 0x00FFFFFF) | (alpha << 24);
    }

    private static int rgba(int r, int g, int b, float a) {
        return (Math.round(a * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Rounded banner with translucent bubbles, like the ticket app's time banner (static, so it costs nothing).
     * A fixed seed keeps the bubbles in the same place every time the screen opens.
     */
    public Drawable banner(float radiusPx, float density) {
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
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }
}
