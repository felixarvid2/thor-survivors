package se.felixarvid.thorsurvivors.ui.menu;

import android.graphics.Canvas;
import android.graphics.Paint;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Settings;
import se.felixarvid.thorsurvivors.ui.Skin;

/**
 * v1.5 B3 (items 21, 22): the options screen. Music, SFX and lighting are the same settings as the rows on the main menu (the
 * menu's volume steps live in MetaProgress); the new rows are screen shake, damage numbers, the telegraph palette, large text,
 * aim assist and haptics, then REMAP BUTTONS and BACK. Below the rows a preview shows both telegraph palettes side by side.
 */
public final class OptionsScreen {
    private static final String[] LABEL = {"MUSIC", "SOUND EFFECTS", "LIGHTING", "SCREEN SHAKE", "DAMAGE NUMBERS", "TELEGRAPHS",
            "LARGE TEXT", "AUTO-AIM", "HAPTICS", "REMAP BUTTONS", "BACK"};
    private static final String[] VOLUME = {"OFF", "LOW", "MEDIUM", "HIGH"};
    private static final String[] LIGHTING = {"OFF", "LOW", "FULL"};

    /** Value text of a cycle row; static strings only. */
    public static String value(Engine eng, int row) {
        Settings st = Settings.get();
        switch (row) {
            case Engine.OPT_MUSIC: return VOLUME[eng.meta.musicVol];
            case Engine.OPT_SFX: return VOLUME[eng.meta.sfxVol];
            case Engine.OPT_LIGHT: return LIGHTING[eng.meta.lighting];
            case Engine.OPT_SHAKE: return Settings.SHAKE_NAMES[Math.round(st.shake() * 2f)];
            case Engine.OPT_NUMBERS: return Settings.NUM_NAMES[st.damageNumbers()];
            case Engine.OPT_TELE: return Settings.TELE_NAMES[st.colorblindShapes() ? 1 : 0];
            case Engine.OPT_TEXT: return Settings.ON_OFF[st.largeText() ? 1 : 0];
            case Engine.OPT_AIM: return Settings.AIM_NAMES[st.autoAim()];
            case Engine.OPT_HAPTICS: return Settings.ON_OFF[st.haptics() ? 1 : 0];
            default: return "";
        }
    }

    public void draw(Canvas c, int w, int h, Skin k, Engine eng, MenuHits hits) {
        float u = k.u;
        float cw = Math.min(w * 0.94f, 1100f * u), x = (w - cw) / 2f;
        MenuUi.title(c, k, w, "OPTIONS", "SAVED AT ONCE", u);
        float rh = 56f * u, gap = 8f * u, y = 158f * u;
        float ts = Settings.get().textScale();
        for (int i = 0; i < Engine.OPT_COUNT; i++) {
            float t = y + i * (rh + gap), b = t + rh;
            boolean f = eng.focus == i;
            if (i == Engine.OPT_REMAP) { // a little air before the two buttons
                t += 10f * u;
                b += 10f * u;
            } else if (i == Engine.OPT_BACK) {
                t += 10f * u;
                b += 10f * u;
            }
            MenuUi.rowPlate(c, k, x, t, x + cw, b, f, u);
            boolean cycle = i < Engine.OPT_REMAP;
            if (cycle) {
                k.text(c, LABEL[i], x + 28f * u, t + rh * 0.66f, 30f * u, Skin.TEXT, Paint.Align.LEFT);
                float vx = x + cw - 230f * u;
                int col = f ? Skin.ORANGE : Skin.TEXT;
                k.text(c, value(eng, i), vx, t + rh * 0.66f, 30f * u, col, Paint.Align.CENTER);
                MenuUi.arrow(c, k, vx - 150f * u, t + rh / 2f, 11f * u, false, f ? Skin.ORANGE : 0xFF6F655A);
                MenuUi.arrow(c, k, vx + 150f * u, t + rh / 2f, 11f * u, true, f ? Skin.ORANGE : 0xFF6F655A);
                hits.hit(x, t, x + cw * 0.5f, b, i, -1);
                hits.hit(x + cw * 0.5f, t, x + cw, b, i, 1);
            } else {
                k.text(c, LABEL[i], x + cw / 2f, t + rh * 0.66f, 32f * u, f ? 0xFFFFF1DC : Skin.TEXT, Paint.Align.CENTER);
                hits.hit(x, t, x + cw, b, i, 0);
            }
        }
        // the telegraph preview and a sample line at the current text size
        float py = y + Engine.OPT_COUNT * (rh + gap) + 36f * u, pb = Math.min(h - 16f * u, py + 150f * u);
        if (pb - py > 80f * u) preview(c, k, x, py, x + cw, pb, u, ts);
    }

    private void preview(Canvas c, Skin k, float l, float t, float rr, float b, float u, float ts) {
        k.plate(c, l, t, rr, b);
        float mid = (l + rr) / 2f, cy = (t + b) / 2f - 8f * u, r = Math.min((b - t) * 0.34f, 52f * u);
        boolean shapes = Settings.get().colorblindShapes();
        zone(c, k, l + (mid - l) * 0.5f, cy, r, false, u);
        zone(c, k, mid + (rr - mid) * 0.5f, cy, r, true, u);
        k.body(c, "STANDARD", l + (mid - l) * 0.5f, b - 14f * u, 22f * u, shapes ? Skin.DIM : Skin.ORANGE, Paint.Align.CENTER);
        k.body(c, "SHAPES", mid + (rr - mid) * 0.5f, b - 14f * u, 22f * u, shapes ? Skin.ORANGE : Skin.DIM, Paint.Align.CENTER);
        k.body(c, "DANGER ZONE PREVIEW", l + 22f * u, t + 28f * u, 20f * u, Skin.DIM, Paint.Align.LEFT);
        k.body(c, "SAMPLE TEXT", rr - 22f * u, t + 28f * u * ts, 20f * u * ts, Skin.DIM, Paint.Align.RIGHT);
    }

    /** One danger zone: the classic red disc, or the blue disc with a hatched fill and a bold orange outline. */
    private void zone(Canvas c, Skin k, float cx, float cy, float r, boolean shapes, float u) {
        k.fill.setColor(shapes ? Settings.SHAPES_FILL : 0x55E0402A);
        c.drawCircle(cx, cy, r, k.fill);
        if (shapes) {
            c.save();
            k.path.reset();
            k.path.addCircle(cx, cy, r, android.graphics.Path.Direction.CW);
            c.clipPath(k.path);
            k.stroke.setColor(0xCC9CC4FF);
            k.stroke.setStrokeWidth(Math.max(2f, 3f * u));
            float step = 14f * u;
            for (float o = -2f * r; o <= 2f * r; o += step) c.drawLine(cx + o - r, cy + r, cx + o + r, cy - r, k.stroke);
            c.restore();
        }
        k.stroke.setColor(shapes ? Settings.SHAPES_EDGE : 0xFFE0402A);
        k.stroke.setStrokeWidth((shapes ? 6f : 3f) * u);
        c.drawCircle(cx, cy, r, k.stroke);
    }
}
