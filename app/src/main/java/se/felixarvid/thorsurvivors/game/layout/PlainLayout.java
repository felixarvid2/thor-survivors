package se.felixarvid.thorsurvivors.game.layout;

import se.felixarvid.thorsurvivors.game.WorldGen;

import static se.felixarvid.thorsurvivors.game.WorldGen.*;

/**
 * The placeholder field of the v1.4 stages: props scattered over a 3-tile grid by a weighted palette, a light post and an
 * occasional landmark per 14-tile region, decals by a weighted palette. A subclass supplies only the palettes; chunks B-D replace
 * it by their real layout (cells, streets, hull islands) in their own subclass or by implementing {@link Layout} directly.
 */
abstract class PlainLayout implements Layout {
    /** Prop kinds ({@code WorldGen.P_*}) and their weights, the chance a grid cell holds a prop, landmark kinds. */
    abstract int[] propKinds();

    abstract float[] propWeights();

    abstract float propDensity();

    abstract int[] landmarks();

    /** Decal kinds ({@code WorldGen.DC_*}) and weights. */
    abstract int[] decalKinds();

    abstract float[] decalWeights();

    /** Chance per region of an emergency light post. */
    float lightChance() {
        return 0.5f;
    }

    @Override
    public int props(WorldGen g, int tx0, int ty0, int tx1, int ty1, float[] xs, float[] ys, int[] kinds) {
        int n = 0;
        final int G = 3;
        int[] pk = propKinds();
        float[] pw = propWeights();
        float dens = propDensity();
        for (int gy = Math.floorDiv(ty0, G); gy <= Math.floorDiv(ty1, G); gy++) {
            for (int gx = Math.floorDiv(tx0, G); gx <= Math.floorDiv(tx1, G); gx++) {
                float x = gx * G + 0.3f + rnd(gx, gy, 61) * (G - 0.6f), y = gy * G + 0.3f + rnd(gx, gy, 62) * (G - 0.6f);
                if (nearSpawn(x, y, CLEAR) || rnd(gx, gy, 63) >= dens) continue;
                n = emit(x, y, pk[pick(rnd(gx, gy, 64), pw)], tx0, ty0, tx1, ty1, xs, ys, kinds, n);
            }
        }
        final int R = 14;
        int[] lm = landmarks();
        for (int ry = Math.floorDiv(ty0, R); ry <= Math.floorDiv(ty1, R); ry++) {
            for (int rx = Math.floorDiv(tx0, R); rx <= Math.floorDiv(tx1, R); rx++) {
                if (rnd(rx, ry, 81) < lightChance()) {
                    float x = rx * R + 1 + rnd(rx, ry, 82) * (R - 2), y = ry * R + 1 + rnd(rx, ry, 83) * (R - 2);
                    if (!nearSpawn(x, y, 4.1f)) n = emit(x, y, P_LIGHT, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
                }
                float x = rx * R + 2 + rnd(rx, ry, 85) * (R - 4), y = ry * R + 2 + rnd(rx, ry, 86) * (R - 4);
                if (lm.length > 0 && rnd(rx, ry, 84) < 0.30f && !nearSpawn(x, y, CLEAR + 2f)) {
                    n = emit(x, y, lm[(int) (rnd(rx, ry, 87) * lm.length) % lm.length], tx0, ty0, tx1, ty1, xs, ys, kinds, n);
                }
            }
        }
        return n;
    }

    @Override
    public int decalKind(WorldGen g, int bx, int by, float x, float y) {
        return decalKinds()[pick(rnd(bx, by, 75), decalWeights())];
    }
}
