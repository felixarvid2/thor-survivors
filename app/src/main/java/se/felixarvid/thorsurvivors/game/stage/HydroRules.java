package se.felixarvid.thorsurvivors.game.stage;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.game.Entities;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Events;
import se.felixarvid.thorsurvivors.game.HydroOps;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.SoundEvents;
import se.felixarvid.thorsurvivors.game.Status;
import se.felixarvid.thorsurvivors.game.WaveDirector;
import se.felixarvid.thorsurvivors.game.WorldGen;

import java.util.Random;

/**
 * Hydroponic Vaults, "Nothing here stays dead" (v1.4 chunk B).
 *
 * <ul>
 * <li><b>Overgrowth</b>: Bloom Hearts (destructibles, {@link HydroRules#DK_HEART}) spread a growth patch round them, radius
 * {@code 60 + 2 * age} up to 180. Enemies inside regenerate 2.5% of max health a second and lose Decay; the hero inside walks
 * 20% slower and does not regenerate. The first heart comes at 2:00, then one every 30 s (at most 6 alive, 8 with event
 * hearts); a heart left 150 s seeds a neighbour. Health {@code 60 + 6 * minute}: a weapon hit does 10, a blast 80, a heart in
 * fire loses 3 x a hit per 0.4 s. A dead heart drops half a level of gems, 10% a Vacuum, and its patch fades in 2 s. With 3 or
 * more hearts alive the waves come 20% faster; a heart sends a swarm of 8 Mycelids from its patch edge now and then.</li>
 * <li><b>Water</b>: flooded domes ({@link WorldGen#SURF_WATER}) and the flood of a broken water tanker slow everyone 15% / enemies
 * 40% and make Electrify hit 1.5 x; a flood also puts out Burn.</li>
 * <li><b>Sporefall</b> (4:00, 8:00 ... 20:00 and the 23:00 event, 20-25 s; the Rootmind calls it too): spore pods fall like Slag Rain
 * (a ring of r 40 for 0.9 s, one per 0.5 s, every third on the hero, at most 8 warnings) and leave a 3 s cloud that decays the
 * hero and heals the enemies 3%/s.</li>
 * <li><b>Events</b>: Purge Terminal (21:00, hold 25 s: every heart dies, an Arms Crate), Bloom (24:00, 45 s: patches at full size,
 * 2 more hearts, enemies +15% speed, XP +50%; 6 hearts killed in it give a chest). The Beacon is the data's.</li>
 * <li><b>Destructibles</b>: water tanker (a flood r 120 for 10 s), spore feeder (20% heal and a Decay cloud on enemies for 4 s).</li>
 * <li><b>Gardener</b> ({@code ARCH_HEALER}): a pulse every 2 s heals every enemy within 110 u by 6%; at most 6 alive.</li>
 * <li><b>Verdant Welder</b>: its dashes leave sprouts, mini patches (r 50, 15 s). <b>The Rootmind</b> (pattern 11) is {@code game/boss/RootmindAi}.</li>
 * </ul>
 * All random numbers come from this object's own {@code Random(seed ^ salt)}. Nothing here allocates after {@link #init}.
 */
public final class HydroRules extends StageRules {
    // ---- hearts and patches
    /** Destructible kinds of this stage ({@code Sim.DK_STAGE} + 16 to + 18): a Bloom Heart, a water tanker, a spore feeder. */
    public static final int DK_HEART = Sim.DK_STAGE + 16, DK_TANKER = Sim.DK_STAGE + 17, DK_FEEDER = Sim.DK_STAGE + 18;
    public static final int MAX_HEARTS = 8, SOFT_HEARTS = 6;
    public static final float FIRST_HEART = 120f, HEART_EVERY = 30f, SEED_AFTER = 150f, SPAWN_MIN = 600f, SPAWN_MAX = 900f,
            SEED_MIN = 220f, SEED_MAX = 300f;
    public static final float PATCH_R0 = 60f, PATCH_GROW = 2f, PATCH_MAX = 180f, FADE_TIME = 2f;
    public static final float HEART_HP0 = 60f, HEART_HP_MIN = 6f, HEART_BLAST = 80f, BURN_TICK = 0.4f, BURN_MUL = 3f,
            HIT_DMG = 10f, IGNITE_TIME = 2.5f;
    public static final float REGEN = 0.025f, HERO_PATCH_SLOW = 0.8f, SPAWN_BOOST = 1.2f;
    public static final float SWARM_EVERY = 16f;
    public static final int SWARM_SIZE = 8;
    // ---- water
    public static final float WADE = 0.85f, FLOOD_R = 120f, FLOOD_T = 10f, FLOOD_SLOW = 0.6f, ELEC_WET = 1.5f;
    // ---- sporefall
    public static final float POD_R = 40f, POD_WARN = 0.9f, POD_EVERY = 0.5f, POD_DMG = 14f, CLOUD_R = 55f, CLOUD_T = 3f,
            CLOUD_DPS = 2.5f, CLOUD_HEAL = 0.03f;
    public static final int POD_LIVE = 8;
    // ---- feeder, gardener, events
    public static final float FEEDER_HEAL = 0.2f, FEEDER_R = 110f, FEEDER_T = 4f;
    public static final float GARDEN_EVERY = 2f, GARDEN_R = 110f, GARDEN_HEAL = 0.06f;
    public static final int GARDEN_CAP = 6;
    public static final float PURGE_R = 140f, PURGE_NEED = 25f, PURGE_DRAIN = 0.5f, PURGE_LEN = 90f;
    public static final float BLOOM_LEN = 45f, BLOOM_SPEED = 1.15f, BLOOM_XP = 1.5f;
    public static final int BLOOM_KILLS = 6;
    static final float LATE = 20f;
    static final float[] SPORE_AT = {240f, 480f, 720f, 960f, 1200f, 1380f}, SPORE_LEN = {20f, 20f, 20f, 20f, 20f, 25f};
    public static final float SPROUT_R = 50f, SPROUT_LIFE = 15f, SPROUT_GROW = 1.5f, SPROUT_EVERY = 80f;

    private static final long SALT = 0x48594452304E4943L, KEY_BASE = 0x4845000000000000L;
    private static final float TAU = 6.2831855f, DEST_CELL = 500f;
    private static final int TR_MAX = 16, FL_MAX = 4, SC_MAX = 12, FD_MAX = 6, PD_MAX = 16, GP_MAX = 8;

    // switches for the soaks and the tests
    public boolean heartsOn = true, weatherOn = true, eventsOn = true, swarmsOn = true;
    public double firstHeartAt = FIRST_HEART;

    private Random rng;
    private boolean ready;
    private float pass;

    // hearts
    public final float[] hx = new float[MAX_HEARTS], hy = new float[MAX_HEARTS], hAge = new float[MAX_HEARTS],
            hHp = new float[MAX_HEARTS], hMax = new float[MAX_HEARTS], hBurn = new float[MAX_HEARTS], hSwarm = new float[MAX_HEARTS];
    public final long[] hKey = new long[MAX_HEARTS];
    public final boolean[] hSeeded = new boolean[MAX_HEARTS], hEvent = new boolean[MAX_HEARTS];
    public int heartN;
    public int heartsKilled, heartsSpawned;
    private double nextHeart;
    private int keySeq;
    private boolean purging;

    // transient patches: Verdant Welder sprouts (type 0) and the fade of a dead heart (type 1)
    public final float[] tx = new float[TR_MAX], ty = new float[TR_MAX], tr0 = new float[TR_MAX], tAge = new float[TR_MAX],
            tLife = new float[TR_MAX];
    public final int[] tType = new int[TR_MAX];
    public int trN;
    private float sproutX = Float.NaN, sproutY;

    // floods (tanker), spore clouds, feeder clouds, pending pods, gardener pulses
    public final float[] flX = new float[FL_MAX], flY = new float[FL_MAX], flT = new float[FL_MAX];
    public int flN;
    public final float[] scX = new float[SC_MAX], scY = new float[SC_MAX], scT = new float[SC_MAX];
    public int scN;
    public final float[] fdX = new float[FD_MAX], fdY = new float[FD_MAX], fdT = new float[FD_MAX];
    public int fdN;
    private final float[] pdX = new float[PD_MAX], pdY = new float[PD_MAX], pdT = new float[PD_MAX];
    private int pdN;
    public final float[] gpX = new float[GP_MAX], gpY = new float[GP_MAX], gpT = new float[GP_MAX];
    public int gpN;

    // weather
    public double weatherUntil;
    private float podAcc;
    private int podSeq;
    private boolean weatherWide;
    private final boolean[] sporeDone = new boolean[SPORE_AT.length];
    public int sporefalls;

    // events
    public boolean purgeOn;
    public float purgeX, purgeY, purgeProg;
    public int purgeWon, purgeLost;
    private boolean purgeStarted, purgeHorde;
    private double purgeEnd;
    public double bloomUntil;
    private boolean bloomStarted, bloomChest;
    private int bloomKills0;
    public int bloomsDone, bloomChests;

    // the Rootmind (state used by game/boss/RootmindAi and HydroFx)
    public static final int SHIELDS = 3;
    public final int[] rmId = new int[SHIELDS];
    public final float[] rmT = new float[SHIELDS], rmX = new float[SHIELDS], rmY = new float[SHIELDS];
    public final boolean[] rmUp = new boolean[SHIELDS];
    public float rmMul = 1f, rmWindow;
    public int rmAlive;
    public boolean rmOn;
    public float rmBossX, rmBossY;

    @Override
    public void init(Sim sim) {
        super.init(sim);
        rng = new Random(sim.runSeed ^ SALT);
        nextHeart = def.rule("firstHeart", FIRST_HEART);
        firstHeartAt = nextHeart;
        pdN = scN = fdN = flN = trN = gpN = heartN = 0;
        ready = true;
    }

    // ------------------------------------------------------------------ queries

    /** Radius of heart i's patch right now. */
    public float heartR(int i) {
        if (bloomOn()) return PATCH_MAX;
        return patchRadius(hAge[i]);
    }

    /** The patch radius of a heart of this age in seconds: 60 + 2 * age, at most 180. */
    public static float patchRadius(float age) {
        return Math.min(PATCH_MAX, PATCH_R0 + PATCH_GROW * age);
    }

    /** Health a heart born at run second {@code t} starts with: 60 + 6 per minute. */
    public static float heartHealth(double t) {
        return HEART_HP0 + HEART_HP_MIN * (float) (t / 60.0);
    }

    /** Current radius of transient patch i (a sprout grows in, both fade out at the end). */
    public float trRadius(int i) {
        float a = tAge[i], life = tLife[i];
        if (tType[i] == 1) return tr0[i] * Math.max(0f, 1f - a / life);
        float grow = Math.min(1f, a / SPROUT_GROW), fade = Math.min(1f, (life - a) / 1f);
        return tr0[i] * Math.max(0f, Math.min(grow, fade));
    }

    /** True when (x, y) is inside any growth patch (hearts, sprouts, fading hearts). */
    public boolean inPatch(float x, float y) {
        for (int i = 0; i < heartN; i++) {
            float dx = x - hx[i], dy = y - hy[i], r = heartR(i);
            if (dx * dx + dy * dy < r * r) return true;
        }
        for (int i = 0; i < trN; i++) {
            float dx = x - tx[i], dy = y - ty[i], r = trRadius(i);
            if (dx * dx + dy * dy < r * r) return true;
        }
        return false;
    }

    /** True inside the flood of a broken tanker. */
    public boolean inFlood(float x, float y) {
        for (int i = 0; i < flN; i++) {
            float dx = x - flX[i], dy = y - flY[i];
            if (dx * dx + dy * dy < FLOOD_R * FLOOD_R) return true;
        }
        return false;
    }

    /** True on the water of a flooded dome. */
    public boolean inWater(float x, float y) {
        return (s.map.surface((int) Math.floor(x / WorldGen.TILE), (int) Math.floor(y / WorldGen.TILE)) & WorldGen.SURF_WATER) != 0;
    }

    public boolean bloomOn() {
        return s.time < bloomUntil;
    }

    public int aliveHearts() {
        return heartN;
    }

    private int normalHearts() {
        int n = 0;
        for (int i = 0; i < heartN; i++) if (!hEvent[i]) n++;
        return n;
    }

    /** Index of the heart with this key, or -1. */
    public int heartOf(long key) {
        for (int i = 0; i < heartN; i++) if (hKey[i] == key) return i;
        return -1;
    }

    /** Pods warning right now (hazards of the spore bomb variant that have not landed). */
    public int liveWarnings() {
        int n = 0;
        for (int i = 0; i < s.hazN; i++) {
            Entities.Hazard h = s.hazards[i];
            if (h.kind == Entities.HZ_BOMB && h.variant == Entities.BV_SPORE && h.warn > 0f) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ hooks

    @Override
    public float enemySpeedMul(Enemy e) {
        float m = 1f;
        if (bloomOn()) m *= BLOOM_SPEED;
        if (flN > 0 && inFlood(e.x, e.y)) m *= FLOOD_SLOW;
        else if (inWater(e.x, e.y)) m *= WADE;
        return m;
    }

    @Override
    public float heroSpeedMul() {
        float m = 1f;
        if (inPatch(s.px, s.py)) m *= HERO_PATCH_SLOW;
        if (inWater(s.px, s.py) || (flN > 0 && inFlood(s.px, s.py))) m *= WADE;
        return m;
    }

    @Override
    public float regenMul() {
        return inPatch(s.px, s.py) ? 0f : 1f;
    }

    @Override
    public float spawnMul() {
        return heartN >= 3 ? SPAWN_BOOST : 1f;
    }

    @Override
    public float ambientLift() {
        return bloomOn() ? 0.08f : 0f;
    }

    @Override
    public float damageIn(Enemy e, float amount, int category) {
        if (e.boss && e.type.pattern == 11) amount *= rmMul;
        if (category == CAT_ELECTRIFY && (inWater(e.x, e.y) || (flN > 0 && inFlood(e.x, e.y)))) amount *= ELEC_WET;
        else if (category == CAT_BURN && flN > 0 && inFlood(e.x, e.y)) return 0f;
        return amount;
    }

    @Override
    public void onEnemyDeath(Enemy e) {
        if (bloomOn()) e.xp *= BLOOM_XP;
        if (e.boss && e.type.pattern == 11) { // the Rootmind is dead: its hearts and vines go with it
            rmOn = false;
            rmMul = 1f;
            for (int i = 0; i < s.enemyN; i++) {
                Enemy h = s.enemies[i];
                if (h != e && !h.dead && h.type.def.key.equals("ROOT_HEART")) {
                    h.silent = true;
                    HydroOps.kill(s, h);
                }
            }
            for (int i = 0; i < s.hazN; i++) if (s.hazards[i].kind == Entities.HZ_LANE) s.hazards[i].life = Math.min(s.hazards[i].life, 0.05f);
        }
    }

    /** A number in [0, 1) from this stage's own generator (the Rootmind's choices). */
    public float rand() {
        return rng.nextFloat();
    }

    // ---- destructibles

    @Override
    public int destKindAt(int cx, int cy, int kind) {
        if (kind != Sim.DK_BARREL && kind != Sim.DK_CHEST) return kind;
        float u = WorldGen.unit(WorldGen.hash(cx, cy, 745));
        return u < 0.35f ? HydroRules.DK_TANKER : u < 0.60f ? HydroRules.DK_FEEDER : Sim.DK_BARREL;
    }

    @Override
    public void addDests(int cx, int cy) {
        for (int i = 0; i < heartN; i++) {
            int hcx = (int) Math.floor(hx[i] / DEST_CELL), hcy = (int) Math.floor(hy[i] / DEST_CELL);
            if (hcx < cx - 2 || hcx > cx + 2 || hcy < cy - 2 || hcy > cy + 2) continue;
            s.addStageDest(hx[i], hy[i], HydroRules.DK_HEART, hKey[i], hHp[i]);
        }
    }

    @Override
    public boolean destHit(int kind, long key, float dmg) {
        int i = heartOf(key);
        if (i < 0) return false;
        hHp[i] -= dmg;
        return hHp[i] > 0f;
    }

    @Override
    public boolean destBroken(int kind, float x, float y, long key) {
        switch (kind) {
            case HydroRules.DK_HEART: {
                int i = heartOf(key);
                if (i >= 0) heartDied(i, !purging);
                break;
            }
            case HydroRules.DK_TANKER:
                if (flN < FL_MAX) {
                    flX[flN] = x;
                    flY[flN] = y;
                    flT[flN++] = FLOOD_T;
                } else { // the oldest flood is replaced
                    for (int k = 1; k < FL_MAX; k++) {
                        flX[k - 1] = flX[k];
                        flY[k - 1] = flY[k];
                        flT[k - 1] = flT[k];
                    }
                    flX[FL_MAX - 1] = x;
                    flY[FL_MAX - 1] = y;
                    flT[FL_MAX - 1] = FLOOD_T;
                }
                s.sound.emit(SoundEvents.CRATE_BREAK);
                HydroOps.fx(s, Entities.FX_SLAM, x, y, FLOOD_R * 0.7f, 0.5f);
                HydroOps.spark(s, x, y - 10f, 0xFF6FC8FF, 12);
                break;
            case HydroRules.DK_FEEDER:
                s.sound.emit(SoundEvents.CARRIER_BURST);
                s.heal(s.maxHp * FEEDER_HEAL);
                if (fdN < FD_MAX) {
                    fdX[fdN] = x;
                    fdY[fdN] = y;
                    fdT[fdN++] = FEEDER_T;
                }
                HydroOps.fx(s, Entities.FX_SPORE, x, y, FEEDER_R, 0.5f);
                HydroOps.spark(s, x, y - 10f, 0xFF90E040, 10);
                break;
            default:
                return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ hearts

    /** Spawns a heart at (x, y) (tests and events); false when the hard cap of 8 is reached. */
    public boolean addHeart(float x, float y, boolean event) {
        if (heartN >= MAX_HEARTS) return false;
        int i = heartN++;
        hx[i] = x;
        hy[i] = y;
        hAge[i] = 0f;
        hHp[i] = hMax[i] = heartHealth(s.time);
        hBurn[i] = 0f;
        hSwarm[i] = 4f + rng.nextFloat() * 6f;
        hSeeded[i] = false;
        hEvent[i] = event;
        hKey[i] = KEY_BASE | (++keySeq);
        heartsSpawned++;
        s.invalidateDests();
        return true;
    }

    /** A heart at 600-900 u from the hero (or {@code lo..hi} for a seed) that is free and away from the other hearts; false when none found. */
    private boolean spawnHeartAround(float cx, float cy, float lo, float hi, boolean event) {
        for (int attempt = 0; attempt < 14; attempt++) {
            float a = rng.nextFloat() * TAU, d = lo + rng.nextFloat() * (hi - lo);
            float x = cx + (float) Math.cos(a) * d, y = cy + (float) Math.sin(a) * d * 0.9f;
            if (s.map.blocked(x, y)) continue;
            boolean near = false;
            for (int i = 0; i < heartN; i++) {
                float dx = x - hx[i], dy = y - hy[i];
                if (dx * dx + dy * dy < 160f * 160f) near = true;
            }
            if (near) continue;
            return addHeart(x, y, event);
        }
        return false;
    }

    private void heartDied(int i, boolean drops) {
        float x = hx[i], y = hy[i];
        if (trN < TR_MAX) { // the patch fades in 2 s
            tx[trN] = x;
            ty[trN] = y;
            tr0[trN] = heartR(i);
            tAge[trN] = 0f;
            tLife[trN] = FADE_TIME;
            tType[trN++] = 1;
        }
        int last = --heartN;
        hx[i] = hx[last];
        hy[i] = hy[last];
        hAge[i] = hAge[last];
        hHp[i] = hHp[last];
        hMax[i] = hMax[last];
        hBurn[i] = hBurn[last];
        hSwarm[i] = hSwarm[last];
        hKey[i] = hKey[last];
        hSeeded[i] = hSeeded[last];
        hEvent[i] = hEvent[last];
        heartsKilled++;
        HydroOps.fx(s, Entities.FX_BLAST, x, y, 60f, 0.4f);
        HydroOps.spark(s, x, y - 14f, 0xFF39FF88, 14);
        s.sound.emit(SoundEvents.CARRIER_BURST);
        if (!drops) return;
        float each = (float) (s.xpNeed * 0.5 * s.stage.xpFade(s.time) / 3.0);
        for (int k = 0; k < 3; k++) {
            float a = k * TAU / 3f;
            HydroOps.addPickup(s, Entities.K_GEM, x + (float) Math.cos(a) * 20f, y + (float) Math.sin(a) * 14f, each);
        }
        float vac = Math.max(0.10f, (float) s.stats.heartVacuum);
        if (rng.nextFloat() < vac) HydroOps.addPickup(s, Entities.K_VACUUM, x, y + 18f, 0f);
        s.toast("BLOOM HEART DESTROYED", 0xFF39FF88);
    }

    /** Breaks heart i through the Sim when it is listed (the drops come from {@link #destBroken}), else directly. */
    private void smash(int i, boolean drops) {
        long k = hKey[i];
        boolean was = purging;
        purging = !drops;
        s.breakDestByKey(k);
        int j = heartOf(k);
        if (j >= 0) heartDied(j, drops);
        purging = was;
    }

    // ------------------------------------------------------------------ update

    @Override
    public void update(float dt) {
        if (!ready || s.ended != 0) return;
        double t = s.time;
        for (int i = 0; i < heartN; i++) hAge[i] += dt;
        if (heartsOn && t >= nextHeart) {
            nextHeart += HEART_EVERY;
            if (normalHearts() < SOFT_HEARTS) spawnHeartAround(s.px, s.py, SPAWN_MIN, SPAWN_MAX, false);
        }
        if (heartsOn) {
            for (int i = 0; i < heartN; i++) {
                if (hSeeded[i] || hAge[i] < SEED_AFTER) continue;
                hSeeded[i] = true;
                if (normalHearts() < SOFT_HEARTS) spawnHeartAround(hx[i], hy[i], SEED_MIN, SEED_MAX, false);
            }
        }
        for (int i = trN - 1; i >= 0; i--) {
            tAge[i] += dt;
            if (tAge[i] >= tLife[i]) removeTr(i);
        }
        for (int i = flN - 1; i >= 0; i--) {
            if ((flT[i] -= dt) <= 0f) {
                for (int k = i + 1; k < flN; k++) {
                    flX[k - 1] = flX[k];
                    flY[k - 1] = flY[k];
                    flT[k - 1] = flT[k];
                }
                flN--;
            }
        }
        for (int i = scN - 1; i >= 0; i--) if ((scT[i] -= dt) <= 0f) { scN--; scX[i] = scX[scN]; scY[i] = scY[scN]; scT[i] = scT[scN]; }
        for (int i = fdN - 1; i >= 0; i--) if ((fdT[i] -= dt) <= 0f) { fdN--; fdX[i] = fdX[fdN]; fdY[i] = fdY[fdN]; fdT[i] = fdT[fdN]; }
        for (int i = gpN - 1; i >= 0; i--) if ((gpT[i] -= dt) <= 0f) { gpN--; gpX[i] = gpX[gpN]; gpY[i] = gpY[gpN]; gpT[i] = gpT[gpN]; }
        for (int i = pdN - 1; i >= 0; i--) {
            if ((pdT[i] -= dt) > 0f) continue;
            if (scN < SC_MAX) {
                scX[scN] = pdX[i];
                scY[scN] = pdY[i];
                scT[scN++] = CLOUD_T;
            }
            pdN--;
            pdX[i] = pdX[pdN];
            pdY[i] = pdY[pdN];
            pdT[i] = pdT[pdN];
        }
        updateWeather(dt, t);
        if (eventsOn) updateEvents(dt, t);
        pass += dt;
        if (pass >= Status.PASS) {
            pass -= Status.PASS;
            if (pass > Status.PASS) pass = 0f;
            statusPass(Status.PASS);
        }
    }

    private void removeTr(int i) {
        trN--;
        tx[i] = tx[trN];
        ty[i] = ty[trN];
        tr0[i] = tr0[trN];
        tAge[i] = tAge[trN];
        tLife[i] = tLife[trN];
        tType[i] = tType[trN];
    }

    /** The 10 Hz pass: regeneration, Decay and Burn in the zones, fire on the hearts, swarms, the welder's sprouts. */
    private void statusPass(float dt) {
        boolean zones = heartN > 0 || trN > 0;
        boolean wet = flN > 0, spores = scN > 0, feed = fdN > 0;
        if (zones || wet || spores || feed) {
            for (int i = 0; i < s.enemyN; i++) {
                Enemy e = s.enemies[i];
                if (e.dead || e.dormant) continue;
                if (zones && !e.boss && e.hp < e.maxHp && inPatch(e.x, e.y)) e.hp = Math.min(e.maxHp, e.hp + e.maxHp * REGEN * dt);
                if (zones && e.decay > 0 && inPatch(e.x, e.y)) {
                    e.decay = 0;
                    e.decayT = 0f;
                    e.decayHit = 0f;
                    e.dmgMul = 1f;
                }
                if (wet && e.burn > 0 && inFlood(e.x, e.y)) {
                    e.burn = 0;
                    e.burnT = 0f;
                    e.burnHit = 0f;
                }
                if (spores && !e.boss && e.hp < e.maxHp) {
                    for (int k = 0; k < scN; k++) {
                        float dx = e.x - scX[k], dy = e.y - scY[k];
                        if (dx * dx + dy * dy < CLOUD_R * CLOUD_R) {
                            e.hp = Math.min(e.maxHp, e.hp + e.maxHp * CLOUD_HEAL * dt);
                            break;
                        }
                    }
                }
                if (feed) {
                    for (int k = 0; k < fdN; k++) {
                        float dx = e.x - fdX[k], dy = e.y - fdY[k];
                        if (dx * dx + dy * dy < FEEDER_R * FEEDER_R) {
                            if (e.decay < 3) s.applyStatus(e, Entities.ST_DECAY, 1, 12f, 0f);
                            break;
                        }
                    }
                }
            }
        }
        fireOnHearts(dt);
        if (heartsOn && swarmsOn) swarms(dt);
        sprouts();
    }

    /** Fire next to a heart (a burning projectile, patch or enemy, a slag pool) sets it alight: 3 hits' worth every 0.4 s. */
    private void fireOnHearts(float dt) {
        for (int h = heartN - 1; h >= 0; h--) {
            boolean fire = false;
            float x = hx[h], y = hy[h];
            for (int j = 0; j < s.projN && !fire; j++) {
                Entities.Projectile p = s.projs[j];
                if (p.status != Entities.ST_BURN && p.kind != Entities.P_FIRE && p.sub != Entities.SUB_PATCH_FIRE) continue;
                float dx = p.x - x, dy = p.y - y, r = p.radius + 26f;
                if (dx * dx + dy * dy < r * r) fire = true;
            }
            for (int j = 0; j < s.enemyN && !fire; j++) {
                Enemy e = s.enemies[j];
                if (e.dead || e.burn <= 0) continue;
                float dx = e.x - x, dy = e.y - y;
                if (dx * dx + dy * dy < 44f * 44f) fire = true;
            }
            for (int j = 0; j < s.hazN && !fire; j++) {
                Entities.Hazard z = s.hazards[j];
                if (z.kind != Entities.HZ_SLAG || z.warn > 0f) continue;
                float dx = z.x - x, dy = z.y - y, r = z.r + 20f;
                if (dx * dx + dy * dy < r * r) fire = true;
            }
            if (fire) hBurn[h] = IGNITE_TIME;
            if (hBurn[h] <= 0f) continue;
            hBurn[h] -= dt;
            hHp[h] -= BURN_MUL * HIT_DMG * dt / BURN_TICK;
            if (hHp[h] <= 0f) smash(h, true);
        }
    }

    private void swarms(float dt) {
        for (int h = 0; h < heartN; h++) {
            if ((hSwarm[h] -= dt) > 0f) continue;
            hSwarm[h] = SWARM_EVERY;
            float dx = s.px - hx[h], dy = s.py - hy[h];
            if (dx * dx + dy * dy > 900f * 900f || s.enemyN > WaveDirector.ENEMY_CAP - SWARM_SIZE - 4) continue;
            float a0 = (float) Math.atan2(dy, dx), r = heartR(h);
            for (int k = 0; k < SWARM_SIZE; k++) {
                float a = a0 + (k - (SWARM_SIZE - 1) * 0.5f) * 0.17f + (rng.nextFloat() - 0.5f) * 0.05f;
                HydroOps.spawnMinion(s, "MYCELID", hx[h] + (float) Math.cos(a) * r, hy[h] + (float) Math.sin(a) * r);
            }
        }
    }

    /** The Verdant Welder's dashes leave a sprout every 80 u: a mini patch (r 50, 15 s). */
    private void sprouts() {
        Enemy w = null;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (e.boss && !e.dead && e.type.def.key.equals("VERDANT_WELDER") && HydroOps.isDashing(e)) {
                w = e;
                break;
            }
        }
        if (w == null) {
            sproutX = Float.NaN;
            return;
        }
        if (Float.isNaN(sproutX) || (w.x - sproutX) * (w.x - sproutX) + (w.y - sproutY) * (w.y - sproutY) >= SPROUT_EVERY * SPROUT_EVERY) {
            sproutX = w.x;
            sproutY = w.y;
            addSprout(w.x, w.y);
        }
    }

    /** A mini growth patch (tests and the welder). When the pool is full the oldest sprout makes room. */
    public void addSprout(float x, float y) {
        if (trN >= TR_MAX) {
            int oldest = -1;
            for (int i = 0; i < trN; i++) if (tType[i] == 0 && (oldest < 0 || tAge[i] > tAge[oldest])) oldest = i;
            if (oldest < 0) return;
            removeTr(oldest);
        }
        tx[trN] = x;
        ty[trN] = y;
        tr0[trN] = SPROUT_R;
        tAge[trN] = 0f;
        tLife[trN] = SPROUT_LIFE;
        tType[trN++] = 0;
    }

    // ------------------------------------------------------------------ Sporefall

    /** Starts (or extends) a Sporefall of {@code seconds}; {@code wide} scatters the pods over the whole screen and beyond. */
    public void startSporefall(float seconds, boolean wide) {
        if (!weatherOn) return;
        weatherUntil = Math.max(weatherUntil, s.time + seconds);
        weatherWide = wide;
        sporefalls++;
    }

    public boolean sporefallOn() {
        return s.time < weatherUntil;
    }

    private void updateWeather(float dt, double t) {
        if (eventsOn && weatherOn) {
            for (int k = 0; k < SPORE_AT.length; k++) {
                if (sporeDone[k] || t < SPORE_AT[k]) continue;
                sporeDone[k] = true;
                if (t - SPORE_AT[k] > LATE) continue;
                startSporefall(SPORE_LEN[k], false);
                banner("SPOREFALL", 0xFF7CE04A);
                s.toast("SPOREFALL  -  keep moving, the pods heal them", 0xFF7CE04A);
            }
        }
        if (t >= weatherUntil) {
            podAcc = 0f;
            return;
        }
        podAcc += dt;
        while (podAcc >= POD_EVERY) {
            podAcc -= POD_EVERY;
            dropPod();
        }
    }

    private void dropPod() {
        if (s.hazN >= Sim.MAX_HAZ - HydroOps.HAZ_RESERVE || liveWarnings() >= POD_LIVE || pdN >= PD_MAX) return;
        int i = podSeq++;
        float a = rng.nextFloat() * TAU;
        float d = i % 3 == 0 ? rng.nextFloat() * 40f : 60f + rng.nextFloat() * (weatherWide ? 270f : 170f);
        float x = s.px + (float) Math.cos(a) * d, y = s.py + (float) Math.sin(a) * d * 0.8f;
        Entities.Hazard h = HydroOps.addHazard(s, Entities.HZ_BOMB, x, y, POD_R, 0.4f, POD_DMG, POD_WARN);
        if (h == null) return;
        h.variant = Entities.BV_SPORE;
        h.w = CLOUD_R;
        h.spin = CLOUD_T;
        h.acc = CLOUD_DPS;
        pdX[pdN] = x;
        pdY[pdN] = y;
        pdT[pdN++] = POD_WARN;
    }

    // ------------------------------------------------------------------ events

    private void banner(String title, int color) {
        Events ev = s.events;
        ev.bannerT = Events.BANNER_LIFE;
        ev.bannerTitle = title;
        ev.bannerColor = color;
    }

    private void updateEvents(float dt, double t) {
        double purgeAt = def.rule("purgeAt", 1260), bloomAt = def.rule("bloomAt", 1440);
        if (!purgeStarted && t >= purgeAt) {
            purgeStarted = true;
            if (t - purgeAt <= LATE) startPurge(purgeAt);
        }
        if (purgeOn) updatePurge(dt, t);
        if (!bloomStarted && t >= bloomAt) {
            bloomStarted = true;
            if (t - bloomAt <= LATE) startBloom(bloomAt);
        }
        if (bloomStarted && bloomUntil > 0 && !bloomChest && heartsKilled - bloomKills0 >= BLOOM_KILLS && t < bloomUntil) {
            bloomChest = true;
            bloomChests++;
            HydroOps.addPickup(s, Entities.K_CHEST, s.px + 40f, s.py + 10f, HydroOps.ARMS_CRATE);
            s.sound.emit(SoundEvents.CHEST_OPEN);
            s.toast("BLOOM CHEST  -  " + BLOOM_KILLS + " hearts", 0xFF39FF88);
        }
    }

    private void startPurge(double at) {
        for (int attempt = 0; attempt < 200; attempt++) {
            float a = rng.nextFloat() * TAU, r = 500f + rng.nextFloat() * 200f;
            float x = s.px + (float) Math.cos(a) * r, y = s.py + (float) Math.sin(a) * r;
            if (s.map.blocked(x, y) || s.map.blocked(x + 30f, y) || s.map.blocked(x - 30f, y)) continue;
            purgeOn = true;
            purgeX = x;
            purgeY = y;
            purgeProg = 0f;
            purgeHorde = false;
            purgeEnd = at + PURGE_LEN;
            banner("PURGE TERMINAL", 0xFF4FD8FF);
            s.toast("PURGE TERMINAL  -  hold it to burn every heart", 0xFF4FD8FF);
            return;
        }
    }

    private void updatePurge(float dt, double t) {
        float dx = s.px - purgeX, dy = s.py - purgeY;
        boolean in = dx * dx + dy * dy < PURGE_R * PURGE_R;
        if (in) purgeProg = Math.min(PURGE_NEED, purgeProg + dt);
        else if (purgeProg > 0f) purgeProg = Math.max(0f, purgeProg - dt * PURGE_DRAIN);
        if (in && !purgeHorde && purgeProg >= PURGE_NEED * 0.5f) {
            purgeHorde = true;
            s.requestHorde("PURGE");
            s.toast("The terminal draws a horde", 0xFFFF6A5A);
        }
        if (purgeProg >= PURGE_NEED) {
            purgeOn = false;
            purgeWon++;
            for (int i = heartN - 1; i >= 0; i--) smash(i, false);
            HydroOps.addPickup(s, Entities.K_CHEST, purgeX, purgeY + 26f, HydroOps.ARMS_CRATE);
            s.sound.emit(SoundEvents.EVOLUTION);
            s.toast("PURGED  -  every heart burns, an Arms Crate", 0xFF4FD8FF);
        } else if (t >= purgeEnd) {
            purgeOn = false;
            purgeLost++;
            s.toast("TERMINAL LOST", 0xFFFF6A5A);
        }
    }

    private void startBloom(double at) {
        bloomUntil = at + BLOOM_LEN;
        bloomKills0 = heartsKilled;
        bloomChest = false;
        bloomsDone++;
        for (int k = 0; k < 2; k++) spawnHeartAround(s.px, s.py, SPAWN_MIN, SPAWN_MAX, true);
        banner("BLOOM", 0xFF39FF88);
        s.toast("BLOOM  -  kill " + BLOOM_KILLS + " hearts for a chest", 0xFF39FF88);
    }

    // ------------------------------------------------------------------ Gardener (ARCH_HEALER)

    @Override
    public void initArch(Enemy e) {
        if (e.type.arch != EnemyDef.ARCH_HEALER) return;
        if (HydroOps.aliveOfArch(s, EnemyDef.ARCH_HEALER) > GARDEN_CAP) { // over the cap of 6: a plain colonist comes instead
            se.felixarvid.thorsurvivors.game.EnemyType c = HydroOps.type(s, "COLONIST");
            if (c != null) {
                float k = (float) (c.def.maxHp / e.type.def.maxHp);
                e.type = c;
                e.hp *= k;
                e.maxHp *= k;
                e.radius = c.radius;
                return;
            }
        }
        e.actT = 0.6f + (rng != null ? rng.nextFloat() : 0.5f) * 1.4f;
        e.ax = e.ay = 0f;
        e.cnt = 0;
    }

    @Override
    public boolean updateArch(Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        if (e.type.arch != EnemyDef.ARCH_HEALER) return false;
        if (e.dormant) return true;
        e.clip = e.actT > GARDEN_EVERY - 0.35f ? Entities.CL_CAST : 0;
        if ((e.actT -= dt) <= 0f) {
            e.actT = GARDEN_EVERY;
            pulse(e);
        }
        if (!moving || d < 1f) return true;
        float tx = dx, ty = dy, td = d, f = 1f;
        if (e.cnt >= 3) { // among friends: it holds back and only edges toward the hero
            f = d > 240f ? 0.45f : 0f;
        } else if (e.ax != 0f || e.ay != 0f) { // looks for the nearest group
            tx = e.ax - e.x;
            ty = e.ay - e.y;
            td = (float) Math.sqrt(tx * tx + ty * ty);
            if (td < 40f) {
                tx = dx;
                ty = dy;
                td = d;
                f = d > 200f ? 0.6f : 0f;
            }
        }
        if (f > 0f && td > 1f) {
            float sp = HydroOps.speedOf(s, e) * f * dt;
            e.x += tx / td * sp;
            e.y += ty / td * sp;
        }
        return true;
    }

    /** The heal pulse: every enemy within 110 u gets 6% of its max health; also finds the allies to stand near. */
    private void pulse(Enemy g) {
        int near = 0, group = 0;
        float cx = 0f, cy = 0f;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (e == g || e.dead || e.dormant) continue;
            float dx = e.x - g.x, dy = e.y - g.y, q = dx * dx + dy * dy;
            if (q < GARDEN_R * GARDEN_R) {
                near++;
                if (!e.boss && e.hp < e.maxHp) e.hp = Math.min(e.maxHp, e.hp + e.maxHp * GARDEN_HEAL);
            }
            if (q < 280f * 280f && e.type.arch != EnemyDef.ARCH_HEALER && !e.boss) {
                group++;
                cx += e.x;
                cy += e.y;
            }
        }
        g.cnt = near;
        if (group >= 2) {
            g.ax = cx / group;
            g.ay = cy / group;
        } else {
            g.ax = g.ay = 0f;
        }
        if (gpN < GP_MAX) {
            gpX[gpN] = g.x;
            gpY[gpN] = g.y;
            gpT[gpN++] = 0.5f;
        }
    }

    /** Fills the weather clock so that a test can start from a clean slate. */
    public void resetWeather() {
        weatherUntil = 0;
        pdN = scN = 0;
    }
}
