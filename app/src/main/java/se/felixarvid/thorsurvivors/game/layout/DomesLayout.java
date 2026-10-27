package se.felixarvid.thorsurvivors.game.layout;

import static se.felixarvid.thorsurvivors.game.WorldGen.*;

/**
 * Hydroponic Vaults, "DOMES" (chunk B fills this in: a grid of 16-tile cells, 70% of them planter-ring domes with corridors
 * between, some flooded). For now a plain overgrown field with the stage's decal and prop palette.
 */
public final class DomesLayout extends PlainLayout {
    private static final int[] PROPS = {P_LUMEN, P_NEST, P_PIPES, P_TANKER, P_VALVE, P_CATWALK, P_CRATE, P_GENERATOR};
    private static final float[] PROP_W = {30, 8, 16, 6, 8, 8, 14, 6};
    private static final int[] LAND = {P_TANKER, P_GENERATOR, P_NEST};
    private static final int[] DECALS = {DC_MOSS, DC_LUMEN, DC_PUDDLE, DC_GRATE, DC_FOOTPRINTS, DC_BONES, DC_OIL, DC_RUBBLE, DC_BLOOD};
    private static final float[] DECAL_W = {26, 20, 16, 10, 8, 8, 4, 4, 4};

    @Override int[] propKinds() { return PROPS; }
    @Override float[] propWeights() { return PROP_W; }
    @Override float propDensity() { return 0.12f; }
    @Override int[] landmarks() { return LAND; }
    @Override int[] decalKinds() { return DECALS; }
    @Override float[] decalWeights() { return DECAL_W; }
}
