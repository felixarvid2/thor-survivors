package se.felixarvid.thorsurvivors.render.stagefx;

import android.graphics.Canvas;

import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.render.WorldRenderer;

/**
 * What a stage draws on top of the shared renderer (v1.4 framework): the drawing half of a stage's rules, which lives here so
 * {@code game/} stays free of android types. {@link #forKey} hands out one stateless instance per stage; the renderer calls the
 * hooks from fixed places. No hook may allocate (RenderCostTest, PanelCostTest); the default of each is to draw nothing.
 */
public interface StageFx {
    /** After the ground and the props of the world, before the lighting glows: stage decorations that belong to the floor. */
    default void drawGround(WorldRenderer r, Canvas c, Sim s, int w, int h) {
    }

    /** After the darkness and mood, over the whole frame (ash, spores, heat haze, banners). {@code w} x {@code h} is the screen. */
    default void drawOver(WorldRenderer r, Canvas c, Sim s, int w, int h) {
    }

    /**
     * On the minimap, after the pois: (cx, cy) is the hero's dot in pixels, {@code kk} the pixels per world unit and {@code side}
     * the map's edge in pixels (the same arguments as the other minimap layers).
     */
    default void minimap(Canvas c, Sim s, float cx, float cy, float kk, float side, float u) {
    }

    StageFx NONE = new StageFx() {
    };
    StageFx FLATS = new FlatsFx();
    StageFx WRECK = new WreckFx(); // v1.4 chunk D (Meridian Wreck)

    /** The drawing hooks of a stage key; {@link #NONE} for a stage without any. */
    static StageFx forKey(String key) {
        if ("WRECK".equals(key)) return WRECK; // v1.4 chunk D
        return "FLATS".equals(key) ? FLATS : NONE;
    }
}
