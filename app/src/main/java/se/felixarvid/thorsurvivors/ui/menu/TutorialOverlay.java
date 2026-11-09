package se.felixarvid.thorsurvivors.ui.menu;

import android.graphics.Canvas;
import android.graphics.Paint;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Settings;
import se.felixarvid.thorsurvivors.game.Tutorial;
import se.felixarvid.thorsurvivors.ui.Skin;

/** v1.5 B3 (item 27): draws the current first-run hint of {@link Tutorial} as a steel banner; nothing after the first run. */
public final class TutorialOverlay {
    public void draw(Canvas c, int w, int h, Skin k, Engine eng) {
        Tutorial t = eng.tutorial;
        if (!t.active()) return;
        int hint = t.hint(eng.phase);
        if (hint < 0) return;
        float u = k.u, ts = Settings.get().textScale();
        float bw = Math.min(w * 0.94f, 1000f * u), bh = 64f * u * ts, x = (w - bw) / 2f;
        // the move hint sits over the empty slot rows (the bars above stay readable); the card and cache hints at the bottom, clear of the cards
        float y = hint == Tutorial.H_MOVE ? 380f * u : h - bh - 20f * u;
        k.plate(c, x, y, x + bw, y + bh, 0xCC140E0A, Skin.ORANGE, 3f * u);
        k.fit(c, Tutorial.TEXT[hint], w / 2f, y + bh * 0.66f, 28f * u * ts, bw - 40f * u, 0xFFFFF1DC, Paint.Align.CENTER);
    }
}
