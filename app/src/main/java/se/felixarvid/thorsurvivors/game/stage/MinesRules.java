package se.felixarvid.thorsurvivors.game.stage;

import java.util.Random;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.game.AddonOps;
import se.felixarvid.thorsurvivors.game.EnemyType;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.SoundEvents;
import se.felixarvid.thorsurvivors.game.WaveDirector;
import se.felixarvid.thorsurvivors.game.WorldGen;

/**
 * Deep Mines. The lumen tentacle bursts come from {@code StageDef.tentacle*} and the steam jets from the lane layout
 * ({@code WorldGen.mines}). v1.4 chunk E adds three things (all behind {@link #addonsEnabled}, all from the rules' own generator):
 * <ul>
 * <li><b>Mine carts:</b> every {@link #CART_EVERY} s from 3:00 a cart runs the central lane (y = 0, a 3-tile strip, 400 u/s). The strip
 * warns for {@link #CART_WARN} s first. Enemies the cart passes take {@link #CART_ENEMY} damage and are thrown out of the lane; the
 * hero takes {@link #CART_HERO} and is thrown {@link #CART_SHOVE} u (a dodge protects). Nothing outside the strip is touched.</li>
 * <li><b>Firedamp:</b> a methane pocket (r {@link #POCKET_R}) in 30% of the 900 u cells. Burn or Electrify on an enemy inside it, or any
 * explosion reaching it, lights a {@link #FUSE} s fuse; then it blows (r {@link #BLAST_R}, {@link #BLAST_DMG} to everything, the hero
 * included, and it lights the pockets around it). It comes back {@link #REFILL} s after.</li>
 * <li><b>Dark Lurkers</b> from minute 6, at most {@link #LURKER_CAP} alive: the renderer draws one only inside the hero's light radius
 * (plus 40 u) or the glow of a lane lamp.</li>
 * </ul>
 */
public final class MinesRules extends StageRules {
    // ---- carts
    public static final float CART_FROM = 180f, CART_EVERY = 90f, CART_WARN = 2f, CART_SPEED = 400f, CART_HALF_W = 62.25f, CART_LEN = 80f,
            CART_REACH = 1400f, CART_ENEMY = 60f, CART_HERO = 30f, CART_SHOVE = 60f, CART_KNOCK = 1.8f;
    /** The cart of the pass in flight: x range (the path is fixed when the warning starts), direction, seconds since the warning, and the front's position. */
    public boolean cartOn;
    public float cartX0, cartX1, cartDir = 1f, cartT, cartX;
    private float cartFrontPrev;
    public int cartsRun, cartIndex = -1;
    public int cartEnemyHits, cartHeroHits;

    // ---- firedamp
    public static final float POCKET_CELL = 900f, POCKET_P = 0.30f, POCKET_R = 100f, BLAST_R = 120f, BLAST_DMG = 50f, FUSE = 3f, REFILL = 60f;
    public static final int MAX_POCKET = 40, MAX_SPENT = 64;
    public int pocketN;
    public final float[] pocketX = new float[MAX_POCKET], pocketY = new float[MAX_POCKET], pocketFuse = new float[MAX_POCKET];
    private final long[] pocketKey = new long[MAX_POCKET];
    private final long[] spentKey = new long[MAX_SPENT];
    private final double[] spentUntil = new double[MAX_SPENT];
    private int spentN, spentHead;
    private int pocketCx = Integer.MIN_VALUE, pocketCy;
    public int detonations;
    /** Pocket detonation seen by the renderer: where and seconds since. */
    public float lastBlastX, lastBlastY, sinceBlast = 99f;

    // ---- lurkers
    public static final float LURKER_FROM = 360f, LURKER_EVERY = 3f, LURKER_SEE = 40f, LAMP_R = 120f;
    public static final int LURKER_CAP = 10;
    private float lurkerAcc;
    public int lurkersAlive, lurkersSpawned;
    private float lightR = 200f;

    private AddonOps ops;
    private final Random rnd = new Random();

    @Override
    public void init(Sim sim) {
        super.init(sim);
        rnd.setSeed(sim.runSeed ^ 0x0A11E5C0DE77L);
        lightR = sim.data.lightingOf(sim.stage.key)[1];
    }

    @Override
    public void update(float dt) {
        if (!addonsEnabled) return;
        if (ops == null) ops = new AddonOps(s);
        sinceBlast += dt;
        updateCarts(dt);
        updatePockets(dt);
        updateLurkers(dt);
    }

    // ------------------------------------------------------------------ carts

    private void updateCarts(float dt) {
        if (cartOn) {
            cartT += dt;
            if (cartT >= CART_WARN) {
                float prev = cartX;
                cartX = cartX0 + cartDir * CART_SPEED * (cartT - CART_WARN);
                cartFrontPrev = prev + cartDir * CART_LEN * 0.5f;
                float front = cartX + cartDir * CART_LEN * 0.5f;
                if (cartT - dt < CART_WARN) cartFrontPrev = front; // the first step: no sweep yet
                sweep(cartFrontPrev, front);
                if ((cartX - cartX1) * cartDir > 0f) cartOn = false; // out at the far end
            }
            return;
        }
        if (s.time < CART_FROM) return;
        int k = (int) ((s.time - CART_FROM) / CART_EVERY);
        if (k == cartIndex) return; // this slot's cart already ran (or was skipped by a jump in time)
        cartIndex = k;
        // the warning starts at 3:00 + k * 90 s; a late arrival (a jump in time) does not run a stale cart
        if (s.time - (CART_FROM + k * CART_EVERY) > 1f) return;
        cartDir = (WorldGen.hash(k, 0, 8800) & 1) == 0 ? 1f : -1f;
        cartX0 = s.px - cartDir * CART_REACH;
        cartX1 = s.px + cartDir * CART_REACH;
        cartX = cartX0;
        cartT = 0f;
        cartOn = true;
        cartsRun++;
        ops.sound(SoundEvents.BOSS_SLAM); // the rumble
    }

    /** The cart's front moved from {@code a} to {@code b} this step: whoever in the strip lies between was run over (once per pass). */
    private void sweep(float a, float b) {
        float lo = Math.min(a, b), hi = Math.max(a, b);
        int n = ops.enemiesIn((lo + hi) * 0.5f, 0f, (hi - lo) * 0.5f + CART_HALF_W + 40f);
        for (int k = 0; k < n; k++) {
            Enemy e = ops.enemy(ops.hit[k]);
            if (Math.abs(e.y) >= CART_HALF_W + e.radius * 0.5f || !crossed(e.x, a, b)) continue;
            float side = e.y >= 0f ? 1f : -1f;
            ops.hurt(e, CART_ENEMY, 0f, side, CART_KNOCK);
            cartEnemyHits++;
        }
        if (crossed(s.px, a, b) && Math.abs(s.py) < CART_HALF_W + 6f && ops.hitHero(CART_HERO)) {
            ops.shoveHero(0f, s.py >= 0f ? 1f : -1f, CART_SHOVE);
            cartHeroHits++;
        }
    }

    /** True when x lies in the half-open interval the front swept from a to b (so two steps in a row never both catch it). */
    private static boolean crossed(float x, float a, float b) {
        return b >= a ? x > a && x <= b : x < a && x >= b;
    }

    // ------------------------------------------------------------------ firedamp

    private static long cellKey(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL) ^ 0x2B200000000L;
    }

    /** The pocket of a 900 u cell (writes x, y into out) or false. Pure function of the cell. */
    public boolean pocketOf(int cx, int cy, float[] out) {
        if (WorldGen.unit(WorldGen.hash(cx, cy, 6100)) >= POCKET_P) return false;
        float x = (cx + 0.15f + 0.7f * WorldGen.unit(WorldGen.hash(cx, cy, 6101))) * POCKET_CELL;
        float y = (cy + 0.15f + 0.7f * WorldGen.unit(WorldGen.hash(cx, cy, 6102))) * POCKET_CELL;
        if (x * x + y * y < 300f * 300f) return false;
        out[0] = x;
        out[1] = y;
        return true;
    }

    private final float[] site = new float[2];

    private boolean spent(long key) {
        for (int i = 0; i < spentN; i++) if (spentKey[i] == key && s.time < spentUntil[i]) return true;
        return false;
    }

    private void refreshPockets(boolean force) {
        int cx = (int) Math.floor(s.px / POCKET_CELL), cy = (int) Math.floor(s.py / POCKET_CELL);
        if (!force && cx == pocketCx && cy == pocketCy) return;
        pocketCx = cx;
        pocketCy = cy;
        int old = pocketN;
        // keep the fuses that are burning, then fill in the pockets of the 5 x 5 cells around
        int keep = 0;
        for (int i = 0; i < old; i++) {
            if (pocketFuse[i] < 0f) continue;
            pocketX[keep] = pocketX[i];
            pocketY[keep] = pocketY[i];
            pocketFuse[keep] = pocketFuse[i];
            pocketKey[keep++] = pocketKey[i];
        }
        pocketN = keep;
        for (int j = cy - 2; j <= cy + 2; j++) {
            for (int i = cx - 2; i <= cx + 2; i++) {
                if (!pocketOf(i, j, site)) continue;
                long key = cellKey(i, j);
                if (spent(key) || pocketN >= MAX_POCKET) continue;
                boolean have = false;
                for (int q = 0; q < pocketN; q++) if (pocketKey[q] == key) have = true;
                if (have) continue;
                pocketX[pocketN] = site[0];
                pocketY[pocketN] = site[1];
                pocketFuse[pocketN] = -1f;
                pocketKey[pocketN++] = key;
            }
        }
    }

    private void updatePockets(float dt) {
        refreshPockets(false);
        // a refilled cell comes back without the hero changing cell
        for (int i = pocketN - 1; i >= 0; i--) {
            if (pocketFuse[i] < 0f) continue;
            if ((pocketFuse[i] -= dt) > 0f) continue;
            detonate(i);
        }
        boolean refilled = false;
        for (int i = 0; i < spentN; i++) if (s.time >= spentUntil[i] && spentUntil[i] > 0) { spentUntil[i] = 0; refilled = true; }
        if (refilled) refreshPockets(true);
    }

    /** Lights the fuse of pocket i (nothing if it is already burning). */
    public void ignite(int i) {
        if (i < 0 || i >= pocketN || pocketFuse[i] >= 0f) return;
        pocketFuse[i] = FUSE;
        if (ops != null) ops.sound(SoundEvents.DECAY_HISS);
    }

    /** Lights every pocket whose cloud the circle (x, y, r) touches. Returns how many caught. */
    public int igniteNear(float x, float y, float r) {
        int n = 0;
        for (int i = 0; i < pocketN; i++) {
            float dx = pocketX[i] - x, dy = pocketY[i] - y, rr = r + POCKET_R;
            if (dx * dx + dy * dy < rr * rr && pocketFuse[i] < 0f) {
                ignite(i);
                n++;
            }
        }
        return n;
    }

    private void detonate(int i) {
        float x = pocketX[i], y = pocketY[i];
        long key = pocketKey[i];
        pocketN--;
        pocketX[i] = pocketX[pocketN];
        pocketY[i] = pocketY[pocketN];
        pocketFuse[i] = pocketFuse[pocketN];
        pocketKey[i] = pocketKey[pocketN];
        // remember the cell as spent until it refills
        int slot = spentN < MAX_SPENT ? spentN++ : spentHead++ % MAX_SPENT;
        spentKey[slot] = key;
        spentUntil[slot] = s.time + REFILL;
        detonations++;
        lastBlastX = x;
        lastBlastY = y;
        sinceBlast = 0f;
        ops.sound(SoundEvents.BARREL_EXPLODE);
        ops.burst(x, y, BLAST_R, 0xFFFFD040, 18);
        ops.shake(0.25f);
        int n = ops.enemiesIn(x, y, BLAST_R);
        for (int k = 0; k < n; k++) {
            Enemy e = ops.enemy(ops.hit[k]);
            float dx = e.x - x, dy = e.y - y, d = (float) Math.sqrt(dx * dx + dy * dy) + 0.01f;
            ops.hurt(e, BLAST_DMG, dx / d, dy / d, 1.2f);
        }
        float hx = s.px - x, hy = s.py - y;
        if (hx * hx + hy * hy < BLAST_R * BLAST_R) ops.hitHero(BLAST_DMG);
        ops.igniteBarrels(x, y, BLAST_R);
        igniteNear(x, y, BLAST_R); // the neighbouring pockets catch
    }

    @Override
    public void onBlast(float x, float y, float r) {
        if (!addonsEnabled || pocketN == 0) return;
        igniteNear(x, y, r);
    }

    @Override
    public float damageIn(Enemy e, float amount, int category) {
        if (addonsEnabled && pocketN > 0 && (category == CAT_BURN || category == CAT_ELECTRIFY)) {
            for (int i = 0; i < pocketN; i++) {
                float dx = e.x - pocketX[i], dy = e.y - pocketY[i], rr = POCKET_R + e.radius * 0.5f;
                if (dx * dx + dy * dy < rr * rr) ignite(i);
            }
        }
        return amount;
    }

    // ------------------------------------------------------------------ lurkers

    private void updateLurkers(float dt) {
        int alive = 0;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (!e.dead && e.type.arch == EnemyDef.ARCH_LURKER) alive++;
        }
        lurkersAlive = alive;
        if (s.time < LURKER_FROM || alive >= LURKER_CAP || ops.quiet(spawnWhenQuiet)) return;
        if ((lurkerAcc += dt) < LURKER_EVERY) return;
        lurkerAcc = 0f;
        if (s.enemyN >= WaveDirector.ENEMY_CAP) return;
        float a = rnd.nextFloat() * 6.2831855f, d = 560f + rnd.nextFloat() * 120f;
        Enemy e = ops.spawn("DARK_LURKER", s.px + (float) Math.cos(a) * d, s.py + (float) Math.sin(a) * d * 0.6f, AddonOps.POI_LURKER, false);
        if (e != null) lurkersSpawned++;
    }

    /** Lamp lights along the lane (the same positions as WorldGen's emergency lights): writes the lamp of index lx into out. */
    public static void lamp(int lx, float[] out) {
        out[0] = (lx * 14 + 3 + WorldGen.rnd(lx, 0, 91) * 8f) * WorldGen.TILE;
        out[1] = ((WorldGen.hash(lx, 0, 92) & 1) == 0 ? 4.8f : -4.8f) * WorldGen.TILE;
    }

    private final float[] lampPos = new float[2];

    /** True when the point is lit: inside the hero's light radius plus {@link #LURKER_SEE}, or in the glow of a lane lamp. */
    public boolean lit(float x, float y) {
        float r = lightR * s.lightRadiusMul() * (float) s.stats.lightRadius + LURKER_SEE;
        float dx = x - s.px, dy = y - s.py;
        if (dx * dx + dy * dy <= r * r) return true;
        int lx = Math.floorDiv((int) Math.floor(x / WorldGen.TILE), 14);
        for (int k = lx - 1; k <= lx + 1; k++) {
            lamp(k, lampPos);
            float lx2 = x - lampPos[0], ly2 = y - lampPos[1];
            if (lx2 * lx2 + ly2 * ly2 <= LAMP_R * LAMP_R) return true;
        }
        return false;
    }

    @Override
    public boolean hidden(EnemyType t, float x, float y) {
        return addonsEnabled && t.arch == EnemyDef.ARCH_LURKER && !lit(x, y);
    }
}
