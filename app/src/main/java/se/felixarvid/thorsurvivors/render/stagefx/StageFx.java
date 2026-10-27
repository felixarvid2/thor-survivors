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

    /**
     * Right after the ground texture, before props, creatures and the lightmap: things painted on the floor itself (belts, molten
     * metal, gates), so the darkness and every sprite lie over them.
     */
    default void drawFloor(WorldRenderer r, Canvas c, Sim s, int w, int h) {
    }

    /** While the lightmap is gathered: stage lights ({@code r.fxLight}), after the dynamic ones. */
    default void lights(WorldRenderer r, Sim s) {
    }

    /**
     * A stage line of the bottom screen HUD in the box (x0, y0)-(x1, y1) under the HP bar (the heat bar of the Refinery); true when
     * something was drawn.
     */
    default boolean hud(Canvas c, Sim s, float x0, float y0, float x1, float y1, float u) {
        return false;
    }

    StageFx NONE = new StageFx() {
    };
    StageFx FLATS = new FlatsFx();
    StageFx MINES = new MinesFx();
    StageFx REFINERY = new RefineryFx(); // v1.4 chunk C
    StageFx WRECK = new WreckFx(); // v1.4 chunk D (Meridian Wreck)

    /** The drawing hooks of a stage key; {@link #NONE} for a stage without any. */
    static StageFx forKey(String key) {
        if ("REFINERY".equals(key)) return REFINERY; // v1.4 chunk C
        if ("WRECK".equals(key)) return WRECK; // v1.4 chunk D
        return "FLATS".equals(key) ? FLATS : "MINES".equals(key) ? MINES : NONE;
    }
}
