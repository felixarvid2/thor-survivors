package se.felixarvid.thorsurvivors.game.layout;

import static se.felixarvid.thorsurvivors.game.WorldGen.*;

/**
 * Refinery District, "STREETS" (chunk C fills this in: a 14-tile block grid with 5-tile streets, conveyor belts, furnace gates and
 * molten channels). For now a plain industrial field with the stage's decal and prop palette.
 */
public final class StreetsLayout extends PlainLayout {
    private static final int[] PROPS = {P_STACK, P_PIPES, P_WALL, P_CATWALK, P_COMPRESSOR, P_FUEL_TANK, P_VALVE, P_GENERATOR, P_CRATE};
    private static final float[] PROP_W = {6, 14, 12, 8, 10, 10, 8, 8, 24};
    private static final int[] LAND = {P_STACK, P_COMPRESSOR, P_GENERATOR};
    private static final int[] DECALS = {DC_SCORCH, DC_OIL, DC_HAZARD, DC_GRATE, DC_RUBBLE, DC_TRACKS, DC_MANHOLE, DC_BLOOD, DC_BONES};
    private static final float[] DECAL_W = {22, 18, 14, 14, 10, 8, 6, 4, 4};

    @Override int[] propKinds() { return PROPS; }
    @Override float[] propWeights() { return PROP_W; }
    @Override float propDensity() { return 0.13f; }
    @Override int[] landmarks() { return LAND; }
    @Override int[] decalKinds() { return DECALS; }
    @Override float[] decalWeights() { return DECAL_W; }
}
