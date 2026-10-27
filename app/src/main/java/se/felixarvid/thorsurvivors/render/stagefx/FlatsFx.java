package se.felixarvid.thorsurvivors.render.stagefx;

import android.graphics.Canvas;

import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.render.WorldRenderer;

/** Cinder Flats: the falling ash and the storm haze and banner (the drawing itself stays in {@link WorldRenderer#drawAsh}, unchanged). */
final class FlatsFx implements StageFx {
    @Override
    public void drawOver(WorldRenderer r, Canvas c, Sim s, int w, int h) {
        r.drawAsh(c, s, w, h);
    }
}
