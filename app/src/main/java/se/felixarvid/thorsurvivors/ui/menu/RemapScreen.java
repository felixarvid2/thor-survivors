package se.felixarvid.thorsurvivors.ui.menu;

import android.graphics.Canvas;
import android.graphics.Paint;

import se.felixarvid.thorsurvivors.game.ButtonMap;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Settings;
import se.felixarvid.thorsurvivors.ui.Skin;

/**
 * v1.5 B3 (item 29): the gamepad button remap screen. Four actions (confirm, back, pause, dash), each shows its button; confirming
 * a row waits for the next button press (the InputRouter sends it as CMD_BIND). A button that already has a job on the same
 * screen is refused with a toast. RESET DEFAULTS restores A / B / START / any face button.
 */
public final class RemapScreen {
    private static final String[] WHAT = {"IN MENUS", "IN MENUS, CANCELS", "STARTS A RUN, PAUSES", "WHILE PLAYING"};

    public void draw(Canvas c, int w, int h, Skin k, Engine eng, MenuHits hits) {
        float u = k.u;
        float cw = Math.min(w * 0.94f, 1000f * u), x = (w - cw) / 2f;
        MenuUi.title(c, k, w, "REMAP BUTTONS", "GAMEPAD", u);
        ButtonMap bm = Settings.get().buttons;
        float rh = 96f * u, gap = 14f * u, y = 190f * u;
        int listening = eng.remapListening;
        for (int i = 0; i < Engine.REMAP_COUNT; i++) {
            float t = y + i * (rh + gap), b = t + rh;
            boolean f = eng.focus == i;
            if (i >= ButtonMap.COUNT) { // reset and back are plain buttons, a little further down
                t += 24f * u;
                b = t + 84f * u;
            }
            if (i < ButtonMap.COUNT) {
                MenuUi.rowPlate(c, k, x, t, x + cw, b, f, u);
                k.text(c, ButtonMap.NAMES[i], x + 28f * u, t + 44f * u, 36f * u, Skin.TEXT, Paint.Align.LEFT);
                k.body(c, WHAT[i], x + 28f * u, t + 80f * u, 22f * u, Skin.DIM, Paint.Align.LEFT);
                boolean wait = listening == i;
                boolean any = i == ButtonMap.DASH && bm.legacyDash();
                float bx = x + cw - 28f * u, bw = 330f * u;
                k.plate(c, bx - bw, t + 14f * u, bx, b - 14f * u, wait ? 0x66FF8A2A : 0x33000000, wait ? Skin.ORANGE : Skin.EDGE, 2f * u);
                String v = wait ? ((int) (eng.menuTime * 2.5f) % 2 == 0 ? "PRESS A BUTTON" : "...") : any ? "ANY FACE BUTTON" : ButtonMap.buttonName(bm.code(i));
                k.fit(c, v, bx - bw / 2f, t + 58f * u, 36f * u, bw - 24f * u, wait ? Skin.ORANGE : Skin.GOLD, Paint.Align.CENTER);
                hits.hit(x, t, x + cw, b, i, 0);
            } else {
                k.button(c, x, t, x + cw, b, i == Engine.REMAP_RESET ? "RESET TO DEFAULT" : "BACK", f);
                hits.hit(x, t, x + cw, b, i, 0);
            }
        }
        float ny = y + (ButtonMap.COUNT + 1) * (rh + gap) + 24f * u + 84f * u + 36f * u;
        if (ny < h - 10f * u) {
            k.body(c, "ONE BUTTON CANNOT HAVE TWO JOBS ON THE SAME SCREEN.", w / 2f, ny, 22f * u, Skin.DIM, Paint.Align.CENTER);
            k.body(c, listening >= 0 ? "B CANCELS" : "A OR TAP: CHANGE", w / 2f, ny + 30f * u, 22f * u, Skin.DIM, Paint.Align.CENTER);
        }
    }
}
