package se.felixarvid.thorsurvivors.game.layout;

import static se.felixarvid.thorsurvivors.game.WorldGen.*;

/**
 * Meridian Wreck, "DEBRIS" (v1.4 chunk D): a dense hull core within 18 tiles of the start that thins outward into hull islands
 * of about 6-14 tiles separated by void gaps (a visual surface only, {@link #surface}; nothing collides and the void does no damage).
 * Every function is a pure function of its arguments:
 * <ul>
 * <li>{@link #pad}: teleporter pad pairs, one 1,800 u cell each (60% of cells), the twins 900-1,400 u apart, none within 1,000 u of
 * the start and none in the Pillar's arena.</li>
 * <li>{@link #well}: one gravity well per 1,400 u cell at 55%, never within 520 u of the start nor in the arena.</li>
 * <li>{@link #pillar}: THE PILLAR stands {@link #PILLAR_DIST} u from the start in a direction chosen by a hash (the same on every
 * run, like the rest of the map), with a {@link #ARENA_R} u arena kept clear of props.</li>
 * </ul>
 * The extra prop kinds (teleporter, the Pillar, the freighter landmark, elevator foundation) use the kind range of
 * {@code WorldGen.P_EXT_BASE} so they cannot collide with the kinds other stages add.
 */
public final class DebrisLayout extends PlainLayout {
    /** Pad cell, well cell and the Pillar's distance and arena radius, in world units. */
    public static final float PAD_CELL = 1800f, WELL_CELL = 1400f, PILLAR_DIST = 2400f, ARENA_R = 500f;
    public static final float PAD_MIN = 900f, PAD_MAX = 1400f, PAD_START_CLEAR = 1000f, WELL_START_CLEAR = 520f, WELL_RADIUS = 220f;

    /** Prop kinds of this layout (atlas {@code prop.NAME}) and their footprint radius. */
    public static final int K_TELEPORTER = P_EXT_BASE, K_PILLAR = P_EXT_BASE + 1, K_FREIGHTER = P_EXT_BASE + 2, K_ELEVATOR = P_EXT_BASE + 3;
    public static final String[] PROP_NAMES = {"teleporter", "the_pillar", "meridian_freighter", "elevator_foundation"};
    private static final float[] PROP_RADIUS = {60f, 50f, 160f, 60f};

    private static final float TAU = 6.2831855f;
    private static final int[] PROPS = {P_CRATE, P_SEGMENT, P_WALL, P_CATWALK, P_TERMINAL, P_GENERATOR, P_DEBRIS, P_LANDER, P_POD};
    private static final float[] PROP_W = {30, 12, 10, 8, 6, 6, 18, 3, 3};
    private static final int[] LAND = {P_LANDER, P_POD, P_GENERATOR};
    private static final int[] DECALS = {DC_HAZARD, DC_GRATE, DC_RUBBLE, DC_SCORCH, DC_OIL, DC_BLOOD, DC_FOOTPRINTS, DC_CRYSTAL, DC_LUMEN};
    private static final float[] DECAL_W = {20, 18, 16, 12, 8, 8, 6, 6, 6};
    private static final float PILLAR_X, PILLAR_Y;

    static {
        float a = rnd(13, 37, 1400) * TAU;
        PILLAR_X = (float) Math.cos(a) * PILLAR_DIST;
        PILLAR_Y = (float) Math.sin(a) * PILLAR_DIST;
    }

    /** Footprint radius of an extra prop kind (index into {@link #PROP_NAMES}). */
    public static float extRadius(int i) {
        return i >= 0 && i < PROP_RADIUS.length ? PROP_RADIUS[i] : 30f;
    }

    /** Writes the Pillar's foot (world units) into {@code out[0..1]}. */
    public static void pillar(float[] out) {
        out[0] = PILLAR_X;
        out[1] = PILLAR_Y;
    }

    public static float pillarX() {
        return PILLAR_X;
    }

    public static float pillarY() {
        return PILLAR_Y;
    }

    /** True when (x, y) (world units) is inside the Pillar's arena grown by {@code margin}. */
    public static boolean inArena(float x, float y, float margin) {
        float dx = x - PILLAR_X, dy = y - PILLAR_Y, r = ARENA_R + margin;
        return dx * dx + dy * dy < r * r;
    }

    @Override int[] propKinds() { return PROPS; }
    @Override float[] propWeights() { return PROP_W; }
    @Override float propDensity() { return 0.14f; }
    @Override int[] landmarks() { return LAND; }
    @Override int[] decalKinds() { return DECALS; }
    @Override float[] decalWeights() { return DECAL_W; }
    @Override float lightChance() { return 0.7f; }

    // ------------------------------------------------------------------ pads and wells

    // the pad pair of a cell is found by up to six tries; the helpers below give the coordinates of a try without any scratch (the
    // layout is shared by the sim, render and ground threads)
    private static float padAx(int cx, int cy, int t) {
        return (cx + 0.15f + 0.7f * rnd(cx, cy, 910 + t * 8)) * PAD_CELL;
    }

    private static float padAy(int cx, int cy, int t) {
        return (cy + 0.15f + 0.7f * rnd(cx, cy, 911 + t * 8)) * PAD_CELL;
    }

    private static float padBx(int cx, int cy, int t) {
        return padAx(cx, cy, t) + (float) Math.cos(rnd(cx, cy, 912 + t * 8) * TAU) * (PAD_MIN + (PAD_MAX - PAD_MIN) * rnd(cx, cy, 913 + t * 8));
    }

    private static float padBy(int cx, int cy, int t) {
        return padAy(cx, cy, t) + (float) Math.sin(rnd(cx, cy, 912 + t * 8) * TAU) * (PAD_MIN + (PAD_MAX - PAD_MIN) * rnd(cx, cy, 913 + t * 8));
    }

    /** The first valid try of the cell's pad pair, or -1 (no pad here). */
    private static int padTry(int cx, int cy) {
        if (rnd(cx, cy, 909) >= 0.6f) return -1;
        float clear = (PAD_START_CLEAR + 60f) * (PAD_START_CLEAR + 60f);
        for (int t = 0; t < 6; t++) {
            float ax = padAx(cx, cy, t), ay = padAy(cx, cy, t), bx = padBx(cx, cy, t), by = padBy(cx, cy, t);
            if (ax * ax + ay * ay < clear || bx * bx + by * by < clear) continue;
            if (inArena(ax, ay, 150f) || inArena(bx, by, 150f)) continue;
            return t;
        }
        return -1;
    }

    /** See {@code WorldGen.pad}: writes {ax, ay, bx, by} (world units) of the pair seeded in the cell. */
    @Override
    public boolean pad(int cx, int cy, float[] out) {
        int t = padTry(cx, cy);
        if (t < 0) return false;
        out[0] = padAx(cx, cy, t);
        out[1] = padAy(cx, cy, t);
        out[2] = padBx(cx, cy, t);
        out[3] = padBy(cx, cy, t);
        return true;
    }

    /** See {@code WorldGen.well}: writes {x, y, radius} (world units). */
    @Override
    public boolean well(int cx, int cy, float[] out) {
        if (rnd(cx, cy, 930) >= 0.55f) return false;
        float x = (cx + 0.15f + 0.7f * rnd(cx, cy, 931)) * WELL_CELL, y = (cy + 0.15f + 0.7f * rnd(cx, cy, 932)) * WELL_CELL;
        if (x * x + y * y < WELL_START_CLEAR * WELL_START_CLEAR || inArena(x, y, 300f)) return false;
        out[0] = x;
        out[1] = y;
        out[2] = WELL_RADIUS;
        return true;
    }

    // ------------------------------------------------------------------ the hull

    /** True where the ship still has a deck at (x, y) in tile units: the core, the arena, and noise islands that thin with the distance. */
    private static boolean hull(float x, float y) {
        float r2 = x * x + y * y;
        if (r2 < 18f * 18f) return true;
        float ax = x * TILE - PILLAR_X, ay = y * TILE - PILLAR_Y, ar = ARENA_R + 3f * TILE;
        if (ax * ax + ay * ay < ar * ar) return true;
        float r = (float) Math.sqrt(r2);
        float thr = 0.12f + 0.46f * Math.min(1f, (r - 18f) / 62f);
        return noise(311, x / 10f, y / 10f) > thr;
    }

    @Override
    public int surface(int tx, int ty) {
        return hull(tx + 0.5f, ty + 0.5f) ? SURF_NONE : SURF_VOID;
    }

    @Override
    public int decalKind(se.felixarvid.thorsurvivors.game.WorldGen g, int bx, int by, float x, float y) {
        if (!hull(x, y)) return -1;
        return super.decalKind(g, bx, by, x, y);
    }

    // ------------------------------------------------------------------ props

    @Override
    public int props(se.felixarvid.thorsurvivors.game.WorldGen g, int tx0, int ty0, int tx1, int ty1, float[] xs, float[] ys, int[] kinds) {
        int n = super.props(g, tx0, ty0, tx1, ty1, xs, ys, kinds);
        // nothing stands over the void, in the Pillar's arena or on a pad
        int m = 0;
        for (int i = 0; i < n; i++) {
            float x = xs[i], y = ys[i];
            if (!hull(x, y) || inArena(x * TILE, y * TILE, 0f) || nearPad(x * TILE, y * TILE, 130f)) continue;
            xs[m] = x;
            ys[m] = y;
            kinds[m++] = kinds[i];
        }
        n = m;
        // pad pairs (the pad cell is 1,800 u = 43 tiles; a twin may lie a cell away, so look one cell around)
        int cx0 = (int) Math.floor(tx0 * TILE / PAD_CELL) - 1, cx1 = (int) Math.floor((tx1 + 1) * TILE / PAD_CELL) + 1;
        int cy0 = (int) Math.floor(ty0 * TILE / PAD_CELL) - 1, cy1 = (int) Math.floor((ty1 + 1) * TILE / PAD_CELL) + 1;
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                int t = padTry(cx, cy);
                if (t < 0) continue;
                n = emit(padAx(cx, cy, t) / TILE, padAy(cx, cy, t) / TILE, K_TELEPORTER, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
                n = emit(padBx(cx, cy, t) / TILE, padBy(cx, cy, t) / TILE, K_TELEPORTER, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
            }
        }
        // the Pillar
        n = emit(PILLAR_X / TILE, PILLAR_Y / TILE, K_PILLAR, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
        // landmarks: a freighter hulk per 3,000 u cell (half of them), an elevator foundation per 2,400 u cell (a third)
        int fx0 = (int) Math.floor(tx0 * TILE / 3000f) - 1, fx1 = (int) Math.floor((tx1 + 1) * TILE / 3000f) + 1;
        int fy0 = (int) Math.floor(ty0 * TILE / 3000f) - 1, fy1 = (int) Math.floor((ty1 + 1) * TILE / 3000f) + 1;
        for (int cy = fy0; cy <= fy1; cy++) {
            for (int cx = fx0; cx <= fx1; cx++) {
                if (rnd(cx, cy, 940) >= 0.5f) continue;
                float x = (cx + 0.1f + 0.8f * rnd(cx, cy, 941)) * 3000f, y = (cy + 0.1f + 0.8f * rnd(cx, cy, 942)) * 3000f;
                if (x * x + y * y < 700f * 700f || inArena(x, y, 250f)) continue;
                n = emit(x / TILE, y / TILE, K_FREIGHTER, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
            }
        }
        int ex0 = (int) Math.floor(tx0 * TILE / 2400f) - 1, ex1 = (int) Math.floor((tx1 + 1) * TILE / 2400f) + 1;
        int ey0 = (int) Math.floor(ty0 * TILE / 2400f) - 1, ey1 = (int) Math.floor((ty1 + 1) * TILE / 2400f) + 1;
        for (int cy = ey0; cy <= ey1; cy++) {
            for (int cx = ex0; cx <= ex1; cx++) {
                if (rnd(cx, cy, 950) >= 0.34f) continue;
                float x = (cx + 0.1f + 0.8f * rnd(cx, cy, 951)) * 2400f, y = (cy + 0.1f + 0.8f * rnd(cx, cy, 952)) * 2400f;
                if (x * x + y * y < 600f * 600f || inArena(x, y, 200f)) continue;
                n = emit(x / TILE, y / TILE, K_ELEVATOR, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
            }
        }
        return n;
    }

    /** True when a pad lies within {@code r} units of (x, y). */
    private static boolean nearPad(float x, float y, float r) {
        int cx0 = (int) Math.floor((x - r - PAD_MAX) / PAD_CELL), cx1 = (int) Math.floor((x + r + PAD_MAX) / PAD_CELL);
        int cy0 = (int) Math.floor((y - r - PAD_MAX) / PAD_CELL), cy1 = (int) Math.floor((y + r + PAD_MAX) / PAD_CELL);
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                int t = padTry(cx, cy);
                if (t < 0) continue;
                float dx = padAx(cx, cy, t) - x, dy = padAy(cx, cy, t) - y;
                if (dx * dx + dy * dy < r * r) return true;
                dx = padBx(cx, cy, t) - x;
                dy = padBy(cx, cy, t) - y;
                if (dx * dx + dy * dy < r * r) return true;
            }
        }
        return false;
    }
}
