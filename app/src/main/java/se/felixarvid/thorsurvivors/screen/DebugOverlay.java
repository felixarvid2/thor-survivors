package se.felixarvid.thorsurvivors.screen;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import se.felixarvid.thorsurvivors.game.Sim;

/**
 * v1.5 B8 (item 80): a small panel in the top-left of the world screen with the frame time (last, p95, worst of 240
 * frames) and the entity counts, to catch regressions on the device. Hidden; toggled by holding L1 + R1 + Select.
 * Text goes through a reused char buffer, so drawing allocates nothing.
 */
final class DebugOverlay {
    private final Paint bg = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final char[] buf = new char[64];

    DebugOverlay() {
        bg.setColor(0xB0000000);
        text.setColor(0xFFE8F0E0);
        text.setTypeface(Typeface.MONOSPACE);
    }

    void draw(Canvas c, int w, int h, FrameStats st, Sim s, boolean held) {
        float u = Math.max(1f, h / 1080f), ts = 22f * u, x = 16f * u, y = 16f * u, lh = ts * 1.25f;
        text.setTextSize(ts);
        c.drawRect(x - 6f * u, y - 4f * u, x + 20f * ts * 0.62f, y + lh * 4 + 4f * u, bg);
        int n = 0;
        n = put(buf, n, "ms ");
        n = fixed1(buf, n, st.last());
        n = put(buf, n, "  p95 ");
        n = fixed1(buf, n, st.p95());
        n = put(buf, n, "  max ");
        n = fixed1(buf, n, st.worst());
        c.drawText(buf, 0, n, x, y + ts, text);
        if (s != null) {
            n = put(buf, 0, "enemies ");
            n = num(buf, n, s.enemyN);
            n = put(buf, n, "  proj ");
            n = num(buf, n, s.projN);
            c.drawText(buf, 0, n, x, y + ts + lh, text);
            n = put(buf, 0, "pickups ");
            n = num(buf, n, s.pickN);
            n = put(buf, n, "  eproj ");
            n = num(buf, n, s.eprojN);
            c.drawText(buf, 0, n, x, y + ts + 2 * lh, text);
        }
        n = put(buf, 0, held ? "AUTO-PAUSED" : "heap MB ");
        if (!held) n = num(buf, n, (int) ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20));
        c.drawText(buf, 0, n, x, y + ts + 3 * lh, text);
    }

    static int put(char[] b, int n, String s) {
        for (int i = 0; i < s.length() && n < b.length; i++) b[n++] = s.charAt(i);
        return n;
    }

    static int num(char[] b, int n, int v) {
        if (v < 0 && n < b.length) {
            b[n++] = '-';
            v = -v;
        }
        int digits = 1;
        for (int t = v; t >= 10; t /= 10) digits++;
        if (n + digits > b.length) return n;
        for (int i = digits - 1; i >= 0; i--) {
            b[n + i] = (char) ('0' + v % 10);
            v /= 10;
        }
        return n + digits;
    }

    static int fixed1(char[] b, int n, float v) {
        int t = Math.round(Math.max(0f, v) * 10f);
        n = num(b, n, t / 10);
        if (n + 2 <= b.length) {
            b[n++] = '.';
            b[n++] = (char) ('0' + t % 10);
        }
        return n;
    }
}
