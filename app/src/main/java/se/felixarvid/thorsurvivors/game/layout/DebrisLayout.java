package se.felixarvid.thorsurvivors.game.layout;

import static se.felixarvid.thorsurvivors.game.WorldGen.*;

/**
 * Meridian Wreck, "DEBRIS" (chunk D fills this in: a dense hull core thinning into hull islands over void, teleporter pads, gravity
 * wells, the Pillar arena). For now a plain wreck field with the stage's decal and prop palette.
 */
public final class DebrisLayout extends PlainLayout {
    private static final int[] PROPS = {P_CRATE, P_SEGMENT, P_WALL, P_CATWALK, P_TERMINAL, P_GENERATOR, P_DEBRIS, P_LANDER, P_POD};
    private static final float[] PROP_W = {30, 12, 10, 8, 6, 6, 18, 3, 3};
    private static final int[] LAND = {P_LANDER, P_POD, P_GENERATOR};
    private static final int[] DECALS = {DC_HAZARD, DC_GRATE, DC_RUBBLE, DC_SCORCH, DC_OIL, DC_BLOOD, DC_FOOTPRINTS, DC_CRYSTAL, DC_LUMEN};
    private static final float[] DECAL_W = {20, 18, 16, 12, 8, 8, 6, 6, 6};

    @Override int[] propKinds() { return PROPS; }
    @Override float[] propWeights() { return PROP_W; }
    @Override float propDensity() { return 0.14f; }
    @Override int[] landmarks() { return LAND; }
    @Override int[] decalKinds() { return DECALS; }
    @Override float[] decalWeights() { return DECAL_W; }
    @Override float lightChance() { return 0.7f; }
}
