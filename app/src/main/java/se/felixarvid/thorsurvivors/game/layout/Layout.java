package se.felixarvid.thorsurvivors.game.layout;

import se.felixarvid.thorsurvivors.game.WorldGen;

/**
 * The generator of one v1.4 stage layout (DOMES, STREETS, DEBRIS), called by {@link WorldGen}. Every method is a pure function
 * of its arguments (the tile coordinates and the hash helpers of WorldGen), so any chunk can be generated on its own and two
 * chunks agree on what lies across their border; no allocation, the caller passes the arrays that receive lists.
 */
public interface Layout {
    /** Props whose feet lie in tiles [tx0, tx1] x [ty0, ty1]: same contract as {@code WorldGen.props}. */
    int props(WorldGen g, int tx0, int ty0, int tx1, int ty1, float[] xs, float[] ys, int[] kinds);

    /** The decal kind ({@code WorldGen.DC_*}) of the 2 x 2 tile block (bx, by) whose candidate lies at tile position (x, y), or -1 for none. */
    int decalKind(WorldGen g, int bx, int by, float x, float y);

    /** {@code WorldGen.SURF_*} bits at a tile. */
    default int surface(int tx, int ty) {
        return WorldGen.SURF_NONE;
    }

    /** See {@code WorldGen.pad}. */
    default boolean pad(int cellX, int cellY, float[] out) {
        return false;
    }

    /** See {@code WorldGen.well}. */
    default boolean well(int cellX, int cellY, float[] out) {
        return false;
    }
}
