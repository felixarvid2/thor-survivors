package se.felixarvid.thorsurvivors.ui.menu;

import android.graphics.Canvas;
import android.graphics.Paint;

import se.felixarvid.thorsurvivors.data.HeroDef;
import se.felixarvid.thorsurvivors.data.MainWeaponDef;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.render.SpriteAtlas;
import se.felixarvid.thorsurvivors.render.WorldRenderer;
import se.felixarvid.thorsurvivors.ui.Skin;

/**
 * v1.5 B3 (item 30): the extra strip on the hero card of the main menu. Every hero is open from the start (nothing is locked
 * today), so instead of an unlock hint the card shows the hero's main weapon and the best time on the stage selected in the menu.
 * The stats line (HP, armor, speed) is the card's own. The two texts are cached until the hero, the stage or the record changes.
 */
public final class HeroSelect {
    private final SpriteAtlas atlas = SpriteAtlas.get();
    private String cHero, cStage;
    private int cSec = -2;
    private String weaponText = "", bestText = "";

    /** Draws the strip at the bottom-left of the card (l, t)-(rr, b), above the stats line. */
    public void draw(Canvas c, Skin k, Engine eng, HeroDef d, float l, float t, float rr, float b, float u) {
        MainWeaponDef mw = eng.data.mainWeapons.get(d.main);
        String stageKey = eng.stage().key;
        int sec = eng.meta.bestTimeOf(stageKey);
        if (!d.key.equals(cHero) || !stageKey.equals(cStage) || sec != cSec) {
            cHero = d.key;
            cStage = stageKey;
            cSec = sec;
            weaponText = mw == null ? "" : mw.name.toUpperCase(java.util.Locale.ROOT);
            bestText = sec > 0 ? WorldRenderer.fmtTime(sec) : "--:--";
        }
        float y = b - 92f * u, s = 46f * u;
        k.slotWell(c, l + 22f * u, y, s);
        if (mw != null) atlas.drawIcon(c, null, mw.key, l + 22f * u + s / 2, y + s / 2, s * 0.8f);
        k.body(c, "MAIN WEAPON", l + 22f * u + s + 14f * u, y + 18f * u, 18f * u, Skin.ORANGE, Paint.Align.LEFT);
        k.text(c, weaponText, l + 22f * u + s + 14f * u, y + 42f * u, 26f * u, Skin.TEXT, Paint.Align.LEFT);
        k.body(c, "BEST TIME HERE", rr - 22f * u, y + 18f * u, 18f * u, Skin.ORANGE, Paint.Align.RIGHT);
        k.text(c, bestText, rr - 22f * u, y + 42f * u, 26f * u, sec > 0 ? Skin.GOLD : Skin.DIM, Paint.Align.RIGHT);
    }
}
