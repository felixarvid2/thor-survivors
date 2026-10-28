package se.felixarvid.thorsurvivors.game.layout;

import se.felixarvid.thorsurvivors.game.WorldGen;

import static se.felixarvid.thorsurvivors.game.WorldGen.*;

/**
 * Refinery District, "STREETS" (v1.4 chunk C): a grid of 14-tile cells, each a 9 x 9 tile block (furnaces, stacks, wall rows, yards)
 * behind a 5 tile street that runs along its west and north side. Every second street (odd index) carries a conveyor lane 3 tiles
 * wide in its middle: belt segments of 18-26 tiles with a 3 x 3 furnace gate between two segments, each segment with its own base
 * direction. The crossing of two streets is a free plaza (manhole decal); the streets through the start (index 0) never carry a
 * belt, and the nine inner blocks hold no furnace and no molten channel, so the start is a calm plaza. 15% of the blocks outside that
 * carry a molten channel (2 tiles wide, 8-9 long, through the block, with a 2 tile catwalk bridge).
 *
 * <p>Everything here is a pure function of the tile coordinates (no state, no allocation), so the game thread, the render thread and
 * the rules may all ask at once. {@code surface} gives the base state of the ground; the rules add the direction flips and the
 * finale's override on top of it.
 */
public final class StreetsLayout extends PlainLayout {
    /** Cell pitch in tiles, block edge, street width, and where the street starts relative to tile 0 (the plaza of cell 0 is tiles -2..2). */
    public static final int PITCH = 14, BLOCK = 9, STREET = 5, OFF = 2;
    /** Belt segment length is 18..26 tiles, followed by a 3 tile gate. */
    public static final int SEG_MIN = 18, SEG_SPAN = 9, GATE_LEN = 3;
    /** Block kinds. */
    public static final int B_FURNACE = 0, B_STACKS = 1, B_WALLS = 2, B_YARD = 3, B_OPEN = 4;

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

    // ------------------------------------------------------------------ cells, streets, plazas

    public static int cell(int t) {
        return Math.floorDiv(t + OFF, PITCH);
    }

    /** Position inside the cell: 0..4 is the street, 5..13 the block. */
    public static int local(int t) {
        return Math.floorMod(t + OFF, PITCH);
    }

    /** True for tile coordinates inside a street band (either axis). */
    public static boolean street(int t) {
        return local(t) < STREET;
    }

    /** World coordinate of the centre of plaza (or street line) {@code c} on one axis. */
    public static float plazaCentre(int c) {
        return (c * PITCH + 0.5f) * TILE;
    }

    /** The plaza (cell index) nearest to a world coordinate on one axis. */
    public static int nearestPlaza(float w) {
        return Math.round((w / TILE - 0.5f) / PITCH);
    }

    // ------------------------------------------------------------------ surface

    @Override
    public int surface(int tx, int ty) {
        return surfaceAt(tx, ty);
    }

    public static int surfaceAt(int tx, int ty) {
        int lx = local(tx), ly = local(ty);
        int cx = cell(tx), cy = cell(ty);
        int bits = 0;
        if (lx >= 1 && lx <= 3 && (cx & 1) != 0) bits |= beltBits(cx, 0, ty, true);
        if (ly >= 1 && ly <= 3 && (cy & 1) != 0) bits |= beltBits(cy, 1, tx, false);
        if (lx >= STREET && ly >= STREET) bits |= channelCell(cx, cy, lx, ly) == 1 ? SURF_MOLTEN : 0;
        return bits;
    }

    /** One belt lane tile: the belt of its segment (base direction) or the gate between two segments. {@code along} is the tile coordinate along the street. */
    private static int beltBits(int street, int axis, int along, boolean vertical) {
        int seg = SEG_MIN + (int) (rnd(street, axis, 31) * SEG_SPAN);
        int cyc = seg + GATE_LEN;
        int w = along + (int) (rnd(street, axis, 32) * cyc);
        int a = Math.floorMod(w, cyc);
        if (a >= seg) return SURF_FURNACE_GATE;
        boolean neg = rnd(street * 7919 + Math.floorDiv(w, cyc), axis, 33) < 0.5f;
        return vertical ? (neg ? SURF_BELT_N : SURF_BELT_S) : (neg ? SURF_BELT_W : SURF_BELT_E);
    }

    /** 0 = nothing, 1 = molten, 2 = the catwalk bridge over a channel; (lx, ly) are the cell-local tile coordinates (>= STREET both). */
    private static int channelCell(int cx, int cy, int lx, int ly) {
        if (!hasChannel(cx, cy)) return 0;
        boolean horiz = rnd(cx, cy, 204) < 0.5f;
        int len = 8 + (rnd(cx, cy, 205) < 0.5f ? 0 : 1);
        int start = (int) (rnd(cx, cy, 206) * (BLOCK - len + 1));
        int row = 3 + (int) (rnd(cx, cy, 207) * 3);
        int bridge = 2 + (int) (rnd(cx, cy, 208) * (len - 4));
        int bl = lx - STREET, bm = ly - STREET;
        int along = horiz ? bl : bm, across = horiz ? bm : bl;
        if (across < row || across > row + 1) return 0;
        int k = along - start;
        if (k < 0 || k >= len) return 0;
        return k == bridge || k == bridge + 1 ? 2 : 1;
    }

    private static boolean hasChannel(int cx, int cy) {
        return Math.max(Math.abs(cx), Math.abs(cy)) > 1 && rnd(cx, cy, 201) < 0.15f;
    }

    /** True on a catwalk bridge tile across a molten channel (drawn as a plate; safe to walk). */
    public static boolean bridge(int tx, int ty) {
        int lx = local(tx), ly = local(ty);
        return lx >= STREET && ly >= STREET && channelCell(cell(tx), cell(ty), lx, ly) == 2;
    }

    // ------------------------------------------------------------------ blocks

    /** The kind of block (cx, cy): furnace block, stacks, wall rows, yard or open ground; the inner nine never hold a furnace. */
    public static int blockKind(int cx, int cy) {
        float r = rnd(cx, cy, 203);
        boolean inner = Math.max(Math.abs(cx), Math.abs(cy)) <= 1;
        if (r < 0.32f) return inner ? B_STACKS : B_FURNACE;
        if (r < 0.50f) return B_STACKS;
        if (r < 0.70f) return B_WALLS;
        if (r < 0.88f) return B_YARD;
        return B_OPEN;
    }

    /** The furnace of block (cx, cy), when it has one: world position of its feet in {@code out[0..1]}. */
    public static boolean furnace(int cx, int cy, float[] out) {
        if (hasChannel(cx, cy) || blockKind(cx, cy) != B_FURNACE) return false;
        out[0] = (cx * PITCH + OFF + STREET + 4.5f) * TILE;
        out[1] = (cy * PITCH + OFF + STREET + 5.6f) * TILE;
        return true;
    }

    /** A fuel tank (destructible) on the street edge of block (cx, cy): world position in {@code out}. */
    public static boolean fuelTank(int cx, int cy, float[] out) {
        if (rnd(cx, cy, 211) >= 0.30f || hasChannel(cx, cy)) return false;
        out[0] = (cx * PITCH + OFF + STREET - 0.5f) * TILE;
        out[1] = (cy * PITCH + OFF + STREET + 1.5f + rnd(cx, cy, 212) * 6f) * TILE;
        return true;
    }

    /** The coolant valve (destructible) in a corner of plaza (cx, cy), when it has one: world position in {@code out}. */
    public static boolean valve(int cx, int cy, float[] out) {
        if (rnd(cx, cy, 215) >= 0.40f) return false;
        out[0] = (cx * PITCH + 2.3f) * TILE;
        out[1] = (cy * PITCH + 2.3f) * TILE;
        return true;
    }

    // ------------------------------------------------------------------ props

    @Override
    public int props(WorldGen g, int tx0, int ty0, int tx1, int ty1, float[] xs, float[] ys, int[] kinds) {
        int n = 0;
        int cx0 = cell(tx0), cx1 = cell(tx1), cy0 = cell(ty0), cy1 = cell(ty1);
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                float X0 = cx * PITCH + OFF + STREET, Y0 = cy * PITCH + OFF + STREET;
                // a light post at the south-east corner of the plaza of this cell
                float lx = cx * PITCH + 2.6f, ly = cy * PITCH + 2.6f;
                if (rnd(cx, cy, 91) < 0.8f && !nearSpawn(lx, ly, 4.1f)) n = emit(lx, ly, P_LIGHT, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
                if (hasChannel(cx, cy)) {
                    n = put(n, X0 + 1.0f, Y0 + 0.9f, P_PUMP, tx0, ty0, tx1, ty1, xs, ys, kinds);
                    n = put(n, X0 + 7.6f, Y0 + 8.2f, P_SLAG, tx0, ty0, tx1, ty1, xs, ys, kinds);
                    n = put(n, X0 + 0.9f, Y0 + 8.3f, P_PIPES, tx0, ty0, tx1, ty1, xs, ys, kinds);
                    continue;
                }
                float j = rnd(cx, cy, 221) * 0.8f;
                switch (blockKind(cx, cy)) {
                    case B_FURNACE:
                        n = put(n, X0 + 4.5f, Y0 + 5.6f, P_FURNACE, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 1.1f, Y0 + 2.0f + j, P_STACK, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        if (rnd(cx, cy, 222) < 0.6f) n = put(n, X0 + 7.9f, Y0 + 2.2f, P_STACK, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 1.5f, Y0 + 7.9f, P_PIPES, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 7.6f, Y0 + 8.0f, P_PIPES, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        break;
                    case B_STACKS:
                        n = put(n, X0 + 1.5f, Y0 + 2.0f, P_STACK, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 4.5f, Y0 + 3.4f + j * 0.5f, P_STACK, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 7.5f, Y0 + 2.0f, P_STACK, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 3.0f, Y0 + 6.6f, P_STACK, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 7.0f, Y0 + 7.2f, P_GENERATOR, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 1.4f, Y0 + 8.2f, P_PIPES, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        break;
                    case B_WALLS:
                        n = put(n, X0 + 2.4f, Y0 + 1.3f, P_WALL, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 6.6f, Y0 + 1.3f, P_WALL, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        if (rnd(cx, cy, 223) < 0.9f) n = put(n, X0 + 2.4f, Y0 + 7.9f, P_WALL, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        if (rnd(cx, cy, 224) < 0.9f) n = put(n, X0 + 6.6f, Y0 + 7.9f, P_WALL, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 3.6f + j, Y0 + 4.4f, P_CRATE, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 5.4f + j, Y0 + 5.2f, P_CRATE, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        break;
                    case B_YARD:
                        n = put(n, X0 + 1.6f, Y0 + 2.2f, P_COMPRESSOR, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 6.8f, Y0 + 2.4f, P_FUEL_TANK, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 7.9f, Y0 + 3.6f, P_FUEL_TANK, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 4.2f + j, Y0 + 5.0f, P_CRATE, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 5.6f, Y0 + 6.0f + j * 0.5f, P_CRATE, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        if (rnd(cx, cy, 225) < 0.5f) n = put(n, X0 + 2.6f, Y0 + 7.6f, P_PUMP, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        else n = put(n, X0 + 2.6f, Y0 + 7.4f, P_VALVE, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        break;
                    default:
                        n = put(n, X0 + 2.0f + j, Y0 + 2.6f, P_DEBRIS, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 6.2f, Y0 + 4.4f, P_GENERATOR, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 3.4f, Y0 + 7.0f - j * 0.5f, P_DEBRIS, tx0, ty0, tx1, ty1, xs, ys, kinds);
                        n = put(n, X0 + 7.4f, Y0 + 7.8f, P_SLAG, tx0, ty0, tx1, ty1, xs, ys, kinds);
                }
            }
        }
        return n;
    }

    private static int put(int n, float x, float y, int kind, int tx0, int ty0, int tx1, int ty1, float[] xs, float[] ys, int[] kinds) {
        if (nearSpawn(x, y, CLEAR)) return n;
        return emit(x, y, kind, tx0, ty0, tx1, ty1, xs, ys, kinds, n);
    }

    // ------------------------------------------------------------------ decals

    @Override
    public int decalKind(WorldGen g, int bx, int by, float x, float y) {
        int tx = (int) Math.floor(x), ty = (int) Math.floor(y);
        int lx = local(tx), ly = local(ty);
        int bits = surfaceAt(tx, ty);
        if ((bits & SURF_MOLTEN) != 0) return -1;
        if ((bits & (SURF_BELT_E | SURF_BELT_W | SURF_BELT_N | SURF_BELT_S)) != 0) return -1; // the belt lane is drawn by the stage
        if ((bits & SURF_FURNACE_GATE) != 0) return DC_SCORCH;
        float r = rnd(bx, by, 75);
        if (lx < STREET && ly < STREET && lx >= 1 && lx <= 3 && ly >= 1 && ly <= 3) return r < 0.5f ? DC_MANHOLE : DC_SCORCH; // plaza
        if (lx < STREET || ly < STREET) { // street edge: hazard stripes, tracks, oil
            return r < 0.35f ? DC_HAZARD : r < 0.6f ? DC_TRACKS : r < 0.8f ? DC_OIL : r < 0.9f ? DC_GRATE : DC_RUBBLE;
        }
        return DECALS[pick(r, DECAL_W)];
    }
}
