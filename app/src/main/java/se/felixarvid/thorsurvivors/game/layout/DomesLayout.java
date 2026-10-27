package se.felixarvid.thorsurvivors.game.layout;

import se.felixarvid.thorsurvivors.game.WorldGen;

import static se.felixarvid.thorsurvivors.game.WorldGen.*;

/**
 * Hydroponic Vaults, "DOMES": a grid of 16 x 16 tile cells, 70% of them domes. A dome is a ring of planters (radius 5.5 tiles)
 * with four 2-tile gaps at the compass points and a free interior; 15% of the domes (none next to the start) are flooded
 * ({@link WorldGen#SURF_WATER} inside radius 5). Between the rings run 3-tile corridors on the cell borders, lined with pipe
 * clusters and catwalks; the cells that are not domes hold scattered yard props. Everything is a pure function of the tile
 * (cell hashes), so chunks agree across their borders. The spawn point lies on a corridor crossing and stays clear.
 */
public final class DomesLayout extends PlainLayout {
    public static final int CELL = 16;
    public static final float RING_R = 5.5f, WATER_R = 5.0f, CENTER = CELL / 2f;
    private static final int RING_N = 28;
    private static final float GAP_HALF = (float) Math.asin(1.0 / RING_R);

    private static final int[] PROPS = {P_CRATE, P_TANKER, P_GENERATOR, P_NEST, P_VALVE, P_LUMEN, P_PIPES};
    private static final float[] PROP_W = {18, 6, 6, 8, 8, 30, 14};
    private static final int[] LAND = {P_TANKER, P_GENERATOR, P_NEST};
    private static final int[] DECALS = {DC_MOSS, DC_LUMEN, DC_PUDDLE, DC_GRATE, DC_FOOTPRINTS, DC_BONES, DC_OIL, DC_RUBBLE, DC_BLOOD};
    private static final float[] DECAL_W = {26, 20, 16, 10, 8, 8, 4, 4, 4};

    @Override int[] propKinds() { return PROPS; }
    @Override float[] propWeights() { return PROP_W; }
    @Override float propDensity() { return 0.12f; }
    @Override int[] landmarks() { return LAND; }
    @Override int[] decalKinds() { return DECALS; }
    @Override float[] decalWeights() { return DECAL_W; }

    /** True when cell (cx, cy) holds a dome. */
    public static boolean dome(int cx, int cy) {
        return rnd(cx, cy, 901) < 0.70f;
    }

    /** True when the dome of cell (cx, cy) is flooded (never the four cells around the start). */
    public static boolean flooded(int cx, int cy) {
        if (cx >= -1 && cx <= 0 && cy >= -1 && cy <= 0) return false;
        return dome(cx, cy) && rnd(cx, cy, 902) < 0.15f;
    }

    /** Centre of cell (cx, cy) in tile units. */
    public static float centerX(int cx) {
        return cx * CELL + CENTER;
    }

    @Override
    public int props(WorldGen g, int tx0, int ty0, int tx1, int ty1, float[] xs, float[] ys, int[] kinds) {
        int n = 0;
        // planter rings
        for (int cy = Math.floorDiv(ty0 - 1, CELL); cy <= Math.floorDiv(ty1 + 1, CELL); cy++) {
            for (int cx = Math.floorDiv(tx0 - 1, CELL); cx <= Math.floorDiv(tx1 + 1, CELL); cx++) {
                if (!dome(cx, cy)) continue;
                float mx = centerX(cx), my = centerX(cy);
                for (int k = 0; k < RING_N; k++) {
                    float a = (k + 0.5f) * 6.2831855f / RING_N;
                    float q = (float) Math.abs(((a % 1.5707964f) + 1.5707964f) % 1.5707964f); // angle inside its quadrant
                    float fromAxis = Math.min(q, 1.5707964f - q);
                    if (fromAxis < GAP_HALF) continue; // the gap at a compass point
                    float x = mx + (float) Math.cos(a) * RING_R, y = my + (float) Math.sin(a) * RING_R;
                    if (nearSpawn(x, y, CLEAR)) continue;
                    int kind = rnd(cx * 31 + k, cy, 903) < 0.55f ? P_LUMEN : P_CRATE;
                    n = emit(x, y, kind, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
                }
            }
        }
        // corridors on the cell borders: a pipe cluster or catwalk every 4 tiles on both edges
        for (int j = Math.floorDiv(ty0 - 3, CELL); j <= Math.floorDiv(ty1 + 3, CELL); j++) {
            for (int k = Math.floorDiv(tx0, 4) - 1; k <= Math.floorDiv(tx1, 4) + 1; k++) {
                for (int side = -1; side <= 1; side += 2) {
                    if (rnd(k, j * 2 + (side > 0 ? 1 : 0), 904) >= 0.7f) continue;
                    float x = k * 4 + 0.5f + rnd(k, j, 905) * 3f, y = j * CELL + side * 1.6f;
                    if (nearSpawn(x, y, CLEAR)) continue;
                    n = emit(x, y, rnd(k, j, 906) < 0.5f ? P_PIPES : P_CATWALK, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
                }
            }
        }
        for (int i = Math.floorDiv(tx0 - 3, CELL); i <= Math.floorDiv(tx1 + 3, CELL); i++) {
            for (int k = Math.floorDiv(ty0, 4) - 1; k <= Math.floorDiv(ty1, 4) + 1; k++) {
                for (int side = -1; side <= 1; side += 2) {
                    if (rnd(i * 2 + (side > 0 ? 1 : 0), k, 907) >= 0.7f) continue;
                    float y = k * 4 + 0.5f + rnd(i, k, 908) * 3f, x = i * CELL + side * 1.6f;
                    if (nearSpawn(x, y, CLEAR)) continue;
                    n = emit(x, y, rnd(i, k, 909) < 0.5f ? P_PIPES : P_CATWALK, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
                }
            }
        }
        // yard props in the cells that are not domes (clear of the corridors)
        final int G = 3;
        for (int gy = Math.floorDiv(ty0, G); gy <= Math.floorDiv(ty1, G); gy++) {
            for (int gx = Math.floorDiv(tx0, G); gx <= Math.floorDiv(tx1, G); gx++) {
                float x = gx * G + 0.3f + rnd(gx, gy, 61) * (G - 0.6f), y = gy * G + 0.3f + rnd(gx, gy, 62) * (G - 0.6f);
                int cx = Math.floorDiv((int) Math.floor(x), CELL), cy = Math.floorDiv((int) Math.floor(y), CELL);
                if (dome(cx, cy) || nearSpawn(x, y, CLEAR) || rnd(gx, gy, 63) >= 0.35f) continue;
                float lx = x - cx * CELL, ly = y - cy * CELL;
                if (lx < 2.6f || lx > CELL - 2.6f || ly < 2.6f || ly > CELL - 2.6f) continue;
                n = emit(x, y, PROPS[pick(rnd(gx, gy, 64), PROP_W)], tx0, ty0, tx1, ty1, xs, ys, kinds, n);
            }
        }
        // light posts in the corridors and a landmark now and then outside the domes
        final int R = 14;
        for (int ry = Math.floorDiv(ty0, R); ry <= Math.floorDiv(ty1, R); ry++) {
            for (int rx = Math.floorDiv(tx0, R); rx <= Math.floorDiv(tx1, R); rx++) {
                if (rnd(rx, ry, 81) < lightChance()) {
                    float x = rx * R + 1 + rnd(rx, ry, 82) * (R - 2), y = ry * R + 1 + rnd(rx, ry, 83) * (R - 2);
                    if (!nearSpawn(x, y, 4.1f) && !insideDome(x, y)) n = emit(x, y, P_LIGHT, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
                }
                float x = rx * R + 2 + rnd(rx, ry, 85) * (R - 4), y = ry * R + 2 + rnd(rx, ry, 86) * (R - 4);
                if (rnd(rx, ry, 84) < 0.30f && !nearSpawn(x, y, CLEAR + 2f) && !insideDome(x, y)) {
                    n = emit(x, y, LAND[(int) (rnd(rx, ry, 87) * LAND.length) % LAND.length], tx0, ty0, tx1, ty1, xs, ys, kinds, n);
                }
            }
        }
        return n;
    }

    /** True inside the ring (plus a margin) of a dome: no light post or landmark stands there. */
    private static boolean insideDome(float x, float y) {
        int cx = Math.floorDiv((int) Math.floor(x), CELL), cy = Math.floorDiv((int) Math.floor(y), CELL);
        if (!dome(cx, cy)) return false;
        float dx = x - centerX(cx), dy = y - centerX(cy);
        return dx * dx + dy * dy < (RING_R + 1f) * (RING_R + 1f);
    }

    @Override
    public int decalKind(WorldGen g, int bx, int by, float x, float y) {
        int cx = Math.floorDiv((int) Math.floor(x), CELL), cy = Math.floorDiv((int) Math.floor(y), CELL);
        if (flooded(cx, cy)) {
            float dx = x - centerX(cx), dy = y - centerX(cy);
            if (dx * dx + dy * dy < WATER_R * WATER_R) return DC_PUDDLE;
        }
        float lum = g.lumenField(x, y);
        if (lum > 0.66f && rnd(bx, by, 75) < 0.5f) return DC_LUMEN;
        return super.decalKind(g, bx, by, x, y);
    }

    @Override
    public int surface(int tx, int ty) {
        int cx = Math.floorDiv(tx, CELL), cy = Math.floorDiv(ty, CELL);
        if (!flooded(cx, cy)) return SURF_NONE;
        float dx = tx + 0.5f - centerX(cx), dy = ty + 0.5f - centerX(cy);
        return dx * dx + dy * dy < WATER_R * WATER_R ? SURF_WATER : SURF_NONE;
    }
}
