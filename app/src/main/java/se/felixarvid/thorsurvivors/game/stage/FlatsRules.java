package se.felixarvid.thorsurvivors.game.stage;

import java.util.Random;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.game.AddonOps;
import se.felixarvid.thorsurvivors.game.Entities;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.SoundEvents;
import se.felixarvid.thorsurvivors.game.WorldGen;

/**
 * Cinder Flats: the ash storms at 12:00 and 22:00 (design 9.1): 20 s long; the light radius shrinks by 40% and spawns grow by
 * 20% at the height of a storm. Moved here from {@code Sim} unchanged; {@code Sim.stormLevel()} and friends ask this object.
 *
 * <p>v1.4 chunk E adds three things on top (all behind {@link #addonsEnabled}, all from the rules' own generators):
 * <ul>
 * <li><b>Storm teeth:</b> in a storm a wind drifts the hero, the enemies and the pickups ({@link #WIND} u/s at full strength) and dry
 * lightning strikes every {@link #STRIKE_EVERY} s: a {@link #STRIKE_WARN} s ring of radius {@link #STRIKE_R}, then
 * {@link #STRIKE_DMG} damage to whoever stands in it (one strike in three aims at the hero; a dodge protects) and every barrel inside
 * explodes. The rings live in the rules' own arrays, so the shared hazard pool is untouched.</li>
 * <li><b>Ash Crawler Convoy</b> three times a run: a slow tanker (400 HP) crosses on a straight line past the hero with eight
 * sergeants (one elite) coming with it. Killing it pays an Arms Crate (the third a Rare gear locker) and gold x2; it leaves after
 * {@link #CONVOY_LEAVE} s. The times are in the stage data ({@code convoyAt0..2}), shifted from 8:00 / 16:00 / 24:00 to keep clear of
 * the timed bosses, events and hordes (see tools/build_ashfall_stages.py).</li>
 * <li><b>Lumen Blooms:</b> a site where the lumen noise is above {@link #BLOOM_FIELD} in a 1,500 u cell; stand in it for 3 s for one
 * level of XP, and six Spore Hounds come.</li>
 * </ul>
 */
public final class FlatsRules extends StageRules {
    public static final float STORM_LEN = 20f, STORM_FADE = 1.5f, STORM_LIGHT = 0.4f, STORM_SPAWNS = 0.2f;
    static final float[] STORM_AT = {720f, 1320f};

    // ---- storm teeth
    public static final float WIND = 25f, STRIKE_EVERY = 1.5f, STRIKE_WARN = 1.0f, STRIKE_R = 50f, STRIKE_DMG = 18f;
    public static final int MAX_STRIKES = 6;
    /** Live warning rings: position and seconds left to the strike. */
    public final float[] strikeX = new float[MAX_STRIKES], strikeY = new float[MAX_STRIKES], strikeT = new float[MAX_STRIKES];
    public int strikeN;
    /** Where the last strike hit and seconds since (the renderer's flash), strikes fired this run. */
    public float lastStrikeX, lastStrikeY, sinceStrike = 99f;
    public int strikesFired, strikesOnHero;
    private float strikeAcc;
    /** Wind direction (unit vector) of the storm blowing now. */
    public float windX = 1f, windY = 0f;
    private int windStorm = -1;

    // ---- convoy
    public static final float CONVOY_SPEED = 90f, CONVOY_HP = 400f, CONVOY_LEAVE = 60f, CONVOY_START = 950f, CONVOY_OFFSET = 150f;
    public static final int ESCORTS = 8;
    private final float[] convoyAt = {495f, 990f, 1485f};
    private int convoyNext;
    /** The tanker on the map (null when none), its direction, seconds alive, and which convoy of the run it is (0..2). */
    public Enemy tanker;
    public int tankerId, convoyIndex;
    public float tankDx = 1f, tankDy = 0f, tankAge;
    public int convoysKilled, convoysLeft;

    // ---- lumen blooms
    public static final float BLOOM_CELL = 1500f, BLOOM_FIELD = 0.78f, BLOOM_R = 70f, BLOOM_HOLD = 3f, BLOOM_FROM = 60f;
    public static final int MAX_BLOOM = 9, BLOOM_HOUNDS = 6, BLOOM_TRIES = 4;
    public int bloomN;
    public final float[] bloomX = new float[MAX_BLOOM], bloomY = new float[MAX_BLOOM];
    private final long[] bloomKey = new long[MAX_BLOOM];
    private final long[] harvested = new long[64];
    private int harvestedN, harvestedHead;
    private int bloomCx = Integer.MIN_VALUE, bloomCy;
    /** Seconds the hero has stood in the bloom under him (0..{@link #BLOOM_HOLD}) and its index, -1 for none. */
    public float bloomHold;
    public int bloomNear = -1;
    public int bloomsDone;

    private AddonOps ops;
    private final Random rnd = new Random();
    private long salt;

    /** 0..1: how strongly the ash storm blows at run time {@code time} (ramps in and out over {@link #STORM_FADE}); 0 outside the storms. */
    public float stormLevel(double time) {
        for (float at : STORM_AT) {
            float t = (float) time - at;
            if (t < 0f || t > STORM_LEN) continue;
            return Math.min(1f, Math.min(t, STORM_LEN - t) / STORM_FADE);
        }
        return 0f;
    }

    /** Seconds since the current ash storm began, or -1 outside the storms (the banner shows for the first seconds). */
    public float stormElapsed(double time) {
        for (float at : STORM_AT) {
            float t = (float) time - at;
            if (t >= 0f && t <= STORM_LEN) return t;
        }
        return -1f;
    }

    private int stormIndex(double time) {
        for (int i = 0; i < STORM_AT.length; i++) if (time >= STORM_AT[i] && time <= STORM_AT[i] + STORM_LEN) return i;
        return -1;
    }

    @Override
    public void init(se.felixarvid.thorsurvivors.game.Sim sim) {
        super.init(sim);
        salt = sim.runSeed ^ 0x0F1A75C0A1D5L;
        rnd.setSeed(salt);
        for (int i = 0; i < convoyAt.length; i++) convoyAt[i] = (float) def.rule("convoyAt" + i, convoyAt[i]);
    }

    @Override
    public float lightMul() {
        return 1f - STORM_LIGHT * stormLevel(s.time);
    }

    @Override
    public float spawnMul() {
        return stormLevel(s.time) > 0f ? 1f + STORM_SPAWNS : 1f;
    }

    // ------------------------------------------------------------------ wind

    @Override
    public boolean push(float x, float y, float[] out) {
        if (!addonsEnabled) return false;
        float k = stormLevel(s.time);
        if (k <= 0f) return false;
        out[0] = windX * k;
        out[1] = windY * k;
        return true;
    }

    /** The drift is {@link #WIND} u/s whatever the mover's speed: the weight turns "fraction of own speed" into units per second. */
    @Override
    public float pushWeight(int who, Enemy e) {
        switch (who) {
            case PUSH_HERO: return WIND / (130f * (float) s.stage.playerSpeed * (float) s.stats.moveSpeedMul);
            case PUSH_PICKUP: return WIND / PICKUP_SPEED;
            default:
                if (e.speed < 1f || e.dormant) return 0f;
                return (e.boss ? 0.2f : 1f) * WIND / e.speed;
        }
    }

    @Override
    public boolean keepFar(Enemy e) {
        return e == tanker;
    }

    // ------------------------------------------------------------------ update

    @Override
    public void update(float dt) {
        if (!addonsEnabled) return;
        if (ops == null) ops = new AddonOps(s);
        sinceStrike += dt;
        updateStorm(dt);
        updateConvoy(dt);
        updateBlooms(dt);
    }

    private void updateStorm(float dt) {
        int storm = stormIndex(s.time);
        if (storm != windStorm) { // a new storm: a new wind
            windStorm = storm;
            if (storm >= 0) {
                rnd.setSeed(salt + 31L * (storm + 1));
                float a = rnd.nextFloat() * 6.2831855f;
                windX = (float) Math.cos(a);
                windY = (float) Math.sin(a);
                strikeAcc = 0f;
            }
        }
        // rings in flight count down and strike
        for (int i = strikeN - 1; i >= 0; i--) {
            if ((strikeT[i] -= dt) > 0f) continue;
            strike(strikeX[i], strikeY[i]);
            strikeN--;
            strikeX[i] = strikeX[strikeN];
            strikeY[i] = strikeY[strikeN];
            strikeT[i] = strikeT[strikeN];
        }
        if (storm < 0) return;
        strikeAcc += dt;
        if (strikeAcc < STRIKE_EVERY) return;
        strikeAcc -= STRIKE_EVERY;
        if (strikeN >= MAX_STRIKES) return;
        float x, y;
        if (rnd.nextInt(3) == 0) { // one in three on the hero
            x = s.px;
            y = s.py;
            strikesOnHero++;
        } else {
            float a = rnd.nextFloat() * 6.2831855f, d = 60f + rnd.nextFloat() * 340f;
            x = s.px + (float) Math.cos(a) * d;
            y = s.py + (float) Math.sin(a) * d * 0.8f;
        }
        strikeX[strikeN] = x;
        strikeY[strikeN] = y;
        strikeT[strikeN++] = STRIKE_WARN;
    }

    private void strike(float x, float y) {
        strikesFired++;
        lastStrikeX = x;
        lastStrikeY = y;
        sinceStrike = 0f;
        ops.sound(SoundEvents.LIGHTNING);
        ops.flash(x, y, STRIKE_R * 3f);
        ops.spark(x, y - 6f, 0xFFE8F4FF, 8);
        ops.shake(0.1f);
        float dx = s.px - x, dy = s.py - y, rr = STRIKE_R + 12f * 0.4f;
        if (dx * dx + dy * dy < rr * rr) ops.hitHero(STRIKE_DMG);
        int n = ops.enemiesIn(x, y, STRIKE_R);
        for (int k = 0; k < n; k++) {
            Enemy e = ops.enemy(ops.hit[k]);
            float ex = e.x - x, ey = e.y - y, d = (float) Math.sqrt(ex * ex + ey * ey) + 0.01f;
            ops.hurt(e, STRIKE_DMG, ex / d, ey / d, 0.5f);
        }
        ops.igniteBarrels(x, y, STRIKE_R);
    }

    // ------------------------------------------------------------------ convoy

    private void updateConvoy(float dt) {
        if (tanker != null) {
            tankAge += dt;
            if (tanker.dead && !tanker.silent) { // killed: onEnemyDeath paid out
                tanker = null;
            } else if (tankAge >= CONVOY_LEAVE) {
                tanker.silent = true; // leaves without a kill or drops
                tanker.dead = true;
                tanker = null;
                convoysLeft++;
                s.toast("The convoy got away", 0xFFFFB347);
            }
        }
        while (convoyNext < convoyAt.length && s.time >= convoyAt[convoyNext] + CONVOY_LEAVE) convoyNext++; // a jump in time skips whole visits
        if (tanker == null && convoyNext < convoyAt.length && s.time >= convoyAt[convoyNext]) spawnConvoy();
    }

    private void spawnConvoy() {
        if (ops.quiet(spawnWhenQuiet)) { // a quiet test scenario: the convoy passes unseen
            convoyNext++;
            return;
        }
        convoyIndex = convoyNext++;
        rnd.setSeed(salt + 977L * (convoyIndex + 1));
        float a = rnd.nextFloat() * 6.2831855f;
        tankDx = (float) Math.cos(a);
        tankDy = (float) Math.sin(a);
        float off = (rnd.nextFloat() - 0.5f) * 2f * CONVOY_OFFSET;
        float sx = s.px - tankDx * CONVOY_START - tankDy * off, sy = s.py - tankDy * CONVOY_START + tankDx * off;
        Enemy t = ops.spawn("CONVOY_TANKER", sx, sy, AddonOps.POI_CONVOY, false);
        if (t == null) { // the pool is full: try again in a moment
            convoyNext = convoyIndex;
            return;
        }
        t.hp = t.maxHp = CONVOY_HP;
        t.kbMul = 0f;
        tanker = t;
        tankerId = t.id;
        tankAge = 0f;
        for (int i = 0; i < ESCORTS; i++) { // two ranks behind and beside the tanker
            float along = -50f - 36f * (i / 2), side = (i % 2 == 0 ? 1f : -1f) * (46f + 10f * (i / 4));
            Enemy e = ops.spawn("SERGEANT", sx + tankDx * along - tankDy * side, sy + tankDy * along + tankDx * side, AddonOps.POI_ESCORT, i == 0);
            if (e == null) break;
        }
        s.toast("ASH CRAWLER CONVOY - see the minimap", 0xFFFFB347);
        ops.sound(SoundEvents.ELITE_SPAWN);
    }

    @Override
    public void initArch(Enemy e) {
        // the tanker and the Lurker need no first timers
    }

    @Override
    public boolean updateArch(Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        if (e.type.arch != EnemyDef.ARCH_CONVOY) return false;
        e.x += tankDx * CONVOY_SPEED * dt;
        e.y += tankDy * CONVOY_SPEED * dt;
        return true;
    }

    @Override
    public void onEnemyDeath(Enemy e) {
        if (e.type.arch != EnemyDef.ARCH_CONVOY || e.poi != AddonOps.POI_CONVOY) return;
        convoysKilled++;
        if (convoyIndex >= 2) ops.gearLocker(); // 24:00: a Rare gear locker
        else ops.armsCrate();
        ops.pickup(Entities.K_COINBAG, e.x - 14f, e.y, 20f); // gold x2: bags of 20 where a bag is worth 10
        ops.pickup(Entities.K_COINBAG, e.x + 14f, e.y, 20f);
        ops.burst(e.x, e.y, 60f, 0xFFFFA030, 14);
        s.toast("CONVOY DESTROYED", 0xFF39FF88);
        if (e == tanker) tanker = null;
    }

    // ------------------------------------------------------------------ lumen blooms

    private static long cellKey(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL) ^ 0x1B100000000L;
    }

    private boolean isHarvested(long key) {
        for (int i = 0; i < harvestedN; i++) if (harvested[i] == key) return true;
        return false;
    }

    /** The bloom site of a 1,500 u cell (writes x, y into out) or false: the first of a few hashed spots where the lumen noise is high. Pure function of the cell. */
    public boolean siteOf(int cx, int cy, float[] out) {
        WorldGen g = s.map;
        for (int k = 0; k < BLOOM_TRIES; k++) {
            float x = (cx + 0.1f + 0.8f * WorldGen.unit(WorldGen.hash(cx, cy, 5100 + k))) * BLOOM_CELL;
            float y = (cy + 0.1f + 0.8f * WorldGen.unit(WorldGen.hash(cx, cy, 5200 + k))) * BLOOM_CELL;
            if (x * x + y * y < 500f * 500f) continue; // not on the start
            if (g.lumenField(x / WorldGen.TILE, y / WorldGen.TILE) <= BLOOM_FIELD) continue;
            out[0] = x;
            out[1] = y;
            return true;
        }
        return false;
    }

    private final float[] site = new float[2];

    private void refreshBlooms() {
        int cx = (int) Math.floor(s.px / BLOOM_CELL), cy = (int) Math.floor(s.py / BLOOM_CELL);
        if (cx == bloomCx && cy == bloomCy) return;
        bloomCx = cx;
        bloomCy = cy;
        bloomN = 0;
        for (int j = cy - 1; j <= cy + 1; j++) {
            for (int i = cx - 1; i <= cx + 1; i++) {
                if (!siteOf(i, j, site)) continue;
                long key = cellKey(i, j);
                if (isHarvested(key) || bloomN >= MAX_BLOOM) continue;
                bloomX[bloomN] = site[0];
                bloomY[bloomN] = site[1];
                bloomKey[bloomN++] = key;
            }
        }
    }

    private void updateBlooms(float dt) {
        if (s.time < BLOOM_FROM) {
            bloomHold = 0f;
            bloomNear = -1;
            return;
        }
        refreshBlooms();
        int near = -1;
        for (int i = 0; i < bloomN; i++) {
            float dx = s.px - bloomX[i], dy = s.py - bloomY[i];
            if (dx * dx + dy * dy < BLOOM_R * BLOOM_R) {
                near = i;
                break;
            }
        }
        if (near != bloomNear) {
            bloomNear = near;
            bloomHold = 0f;
        }
        if (near < 0) return;
        bloomHold += dt;
        if (bloomHold < BLOOM_HOLD) return;
        harvest(near);
    }

    private void harvest(int i) {
        float x = bloomX[i], y = bloomY[i];
        harvested[harvestedHead] = bloomKey[i];
        harvestedHead = (harvestedHead + 1) % harvested.length;
        harvestedN = Math.min(harvested.length, harvestedN + 1);
        bloomN--;
        bloomX[i] = bloomX[bloomN];
        bloomY[i] = bloomY[bloomN];
        bloomKey[i] = bloomKey[bloomN];
        bloomNear = -1;
        bloomHold = 0f;
        bloomsDone++;
        ops.xpPile(s.px, s.py, 1f);
        if (ops.quiet(spawnWhenQuiet)) return;
        ops.sound(SoundEvents.LEVEL_UP);
        ops.burst(x, y, 70f, 0xFF39FF88, 16);
        s.toast("LUMEN BLOOM HARVESTED - hounds!", 0xFF39FF88);
        rnd.setSeed(salt + 7919L * bloomsDone);
        for (int k = 0; k < BLOOM_HOUNDS; k++) {
            float a = (k + rnd.nextFloat() * 0.5f) * (6.2831855f / BLOOM_HOUNDS);
            ops.spawn("SPORE_HOUND", x + (float) Math.cos(a) * 110f, y + (float) Math.sin(a) * 90f, AddonOps.POI_BLOOM, false);
        }
    }
}
