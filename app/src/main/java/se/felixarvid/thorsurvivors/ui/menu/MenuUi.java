package se.felixarvid.thorsurvivors.ui.menu;

import android.graphics.Canvas;
import android.graphics.Paint;

import se.felixarvid.thorsurvivors.ui.Skin;

/** v1.5 B3: the few drawing helpers the menu screens share (all allocation free, they draw through the shared {@link Skin} paints). */
final class MenuUi {
    private MenuUi() {
    }

    /** Big screen title with the hazard stripe under it; returns nothing, the content starts at about 150 u. */
    static void title(Canvas c, Skin k, int w, String title, String sub, float u) {
        k.shadowed(c, title, w / 2f, 100f * u, 88f * u, Skin.ORANGE, Paint.Align.CENTER);
        if (sub != null) k.body(c, sub, w / 2f, 134f * u, 26f * u, Skin.DIM, Paint.Align.CENTER);
    }

    /** A triangle arrow pointing left or right. */
    static void arrow(Canvas c, Skin k, float cx, float cy, float sz, boolean right, int color) {
        k.path.reset();
        float d = right ? 1f : -1f;
        k.path.moveTo(cx + d * sz, cy);
        k.path.lineTo(cx - d * sz * 0.7f, cy - sz);
        k.path.lineTo(cx - d * sz * 0.7f, cy + sz);
        k.path.close();
        k.fill.setColor(color);
        c.drawPath(k.path, k.fill);
    }

    /** A row plate with a label on the left; focus draws the orange frame. Returns nothing. */
    static void rowPlate(Canvas c, Skin k, float l, float t, float rr, float b, boolean focus, float u) {
        k.plate(c, l, t, rr, b, focus ? 0x40FF8A2A : 0, focus ? Skin.ORANGE : Skin.EDGE, focus ? 4f * u : 2f * u);
        if (focus) k.focusMarks(c, l, t, rr, b);
    }
}
