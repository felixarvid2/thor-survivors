package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.game.Entities.Enemy;

/**
 * Elites and champions (design section 5, HoT champions): a flagged slot of the normal enemy pool ({@code Enemy.elite},
 * {@code Enemy.mods}), never a pool of its own. This class holds the modifiers (names, colours, what each does every
 * step) and the schedule numbers; {@link Sim} spawns and rewards them.
 *
 * <ul>
 * <li>Firewalker: a fire patch every 45 units it walks. Frostwalker: a frost patch (slows the hero) every 45 units.</li>
 * <li>Barrage: every 3 s three bolts in an 80 degree fan. Volcano: three lobbed bombs (warn ring, then a blast).</li>
 * <li>Raiser: raises 4 colonists every 10 s. Armored: 3 off every hit it takes, and it cannot be knocked back.</li>
 * <li>v1.5 B6: Shielded (from minute 8): a 120 degree frontal arc blocks 70% of the hero's hits for 4 s, then drops for 3 s;
 * the arc turns slowly, so the hero flanks it. Splitter: three small copies at 25% of its HP when it dies (never above the pool).
 * Volatile: dies in a 0.8 s warned blast of 20% of the hero's max HP in 60 units, also when the hero touches it.</li>
 * </ul>
 */
public final class Elites {
    public static final int M_FIRE = 1, M_FROST = 2, M_BARRAGE = 4, M_VOLCANO = 8, M_RAISER = 16, M_ARMORED = 32,
            M_SHIELDED = 64, M_SPLITTER = 128, M_VOLATILE = 256, COUNT = 9; // v1.5 B6: the last three
    public static final String[] NAMES = {"Firewalker", "Frostwalker", "Barrage", "Volcano", "Raiser", "Armored", "Shielded", "Splitter", "Volatile"};
    /** The marker colour of each modifier (the outline and the floor ring), ARGB. */
    public static final int[] COLORS = {0xFFFF8A2A, 0xFF40D8FF, 0xFFFF3B1F, 0xFFFFD030, 0xFF3DBE5A, 0xFFB0B8C0,
            0xFF5A9BFF, 0xFFC070FF, 0xFFFF6A00};
    /** The skin glyph of each modifier (Skin.G_*: 0..5 are the old six, 8 shield, 9 split, 10 blast). */
    private static final int[] GLYPH = {0, 1, 2, 3, 4, 5, 8, 9, 10};
    /** v1.5 B6: Shielded elites only come from this run time (8:00). */
    public static final float SHIELDED_FROM = 480f;
    /** v1.5 B6 Shielded: the arc is 120 degrees (60 either side), blocks 70%, is up 4 s and down 3 s, turns at most 1 rad/s. */
    public static final float ARC_COS = 0.5f, BLOCK = 0.7f, SHIELD_UP = 4f, SHIELD_DOWN = 3f, FACE_TURN = 1f;
    /** v1.5 B6 Volatile: warn 0.8 s, radius 60, 20% of the hero's max HP. */
    public static final float VOLATILE_WARN = 0.8f, VOLATILE_R = 60f, VOLATILE_PCT = 0.2f;
    /** v1.5 B6 Splitter: copies and their share of the elite's HP. */
    public static final int SPLIT_N = 3;
    public static final float SPLIT_HP = 0.25f;
    static final float TRAIL_EVERY = 45f, BARRAGE_EVERY = 3f, VOLCANO_EVERY = 7f, RAISE_EVERY = 10f, FLAT_ARMOR = 3f;
    private static final float TAU = 6.2831855f;

    private final Sim s;

    Elites(Sim s) {
        this.s = s;
    }

    /** Seconds from one elite to the next at run time {@code t}: 150 s less 1% per minute, never under the floor (100 s). */
    static float interval(se.felixarvid.thorsurvivors.data.GameData d, double t) {
        double base = d.elite("interval", 150), floor = d.elite("min", 100), shrink = d.elite("shrinkPerMin", 0.01);
        return (float) Math.max(floor, base * (1.0 - shrink * (t / 60.0)));
    }

    /** The same with the stage's own elite numbers (stages.KEY.elites) over the global ones. */
    static float interval(se.felixarvid.thorsurvivors.data.GameData d, se.felixarvid.thorsurvivors.data.StageDef st, double t) {
        double base = ov(d, st, "interval", 150), floor = ov(d, st, "min", 100), shrink = ov(d, st, "shrinkPerMin", 0.01);
        return (float) Math.max(floor, base * (1.0 - shrink * (t / 60.0)));
    }

    static double ov(se.felixarvid.thorsurvivors.data.GameData d, se.felixarvid.thorsurvivors.data.StageDef st, String k, double dflt) {
        Double v = st == null ? null : st.eliteOv.get(k);
        return v != null ? v : d.elite(k, dflt);
    }

    static int modCount(se.felixarvid.thorsurvivors.data.GameData d, se.felixarvid.thorsurvivors.data.StageDef st, double t) {
        return t >= ov(d, st, "mods3At", 1440) ? 3 : t >= ov(d, st, "mods2At", 720) ? 2 : 1;
    }

    /** How many modifiers an elite gets at run time {@code t}: 1, 2 after 12:00, 3 after 24:00. */
    static int modCount(se.felixarvid.thorsurvivors.data.GameData d, double t) {
        return t >= d.elite("mods3At", 1440) ? 3 : t >= d.elite("mods2At", 720) ? 2 : 1;
    }

    private static final int[] ALL = {0, 1, 2, 3, 4, 5, 6, 7, 8}, NO_SHIELD = {0, 1, 2, 3, 4, 5, 7, 8};

    /** A random set of {@code n} distinct modifiers as a bit mask (all nine; v1.5 B6: {@link #roll(java.util.Random, int, double)} gates Shielded by time). */
    static int roll(java.util.Random rng, int n) {
        return roll(rng, n, Double.MAX_VALUE);
    }

    /** The same at run time {@code t}: Shielded joins the pool from {@link #SHIELDED_FROM}. */
    static int roll(java.util.Random rng, int n, double t) {
        int mask = 0;
        int[] bits = t >= SHIELDED_FROM ? ALL : NO_SHIELD;
        for (int k = 0; k < n; k++) {
            int pick = rng.nextInt(bits.length - k), bit = 0;
            for (int j = 0; j < bits.length; j++) {
                int i = bits[j];
                if ((mask >> i & 1) != 0) continue;
                if (pick-- == 0) {
                    bit = i;
                    break;
                }
            }
            mask |= 1 << bit;
        }
        return mask;
    }

    /** The colour of the n-th modifier an elite has (cycles, so the outline pulses through them), or 0 without one. */
    public static int colorOf(int mods, int n) {
        int seen = 0;
        for (int i = 0; i < COUNT; i++) {
            if ((mods >> i & 1) == 0) continue;
            if (seen++ == n) return TelegraphPalette.map(COLORS[i]); // v1.5 B6: colorblind hook
        }
        return 0;
    }

    /** The skin glyph kind (Skin.G_*) for a modifier index from {@link #indexOf}. */
    public static int glyphOf(int index) {
        return index < 0 ? 0 : GLYPH[index];
    }

    /** Index (0..8) of the n-th modifier an elite has, or -1 without one. */
    public static int indexOf(int mods, int n) {
        int seen = 0;
        for (int i = 0; i < COUNT; i++) {
            if ((mods >> i & 1) == 0) continue;
            if (seen++ == n) return i;
        }
        return -1;
    }

    /** "Firewalker + Barrage": the modifier names of an elite. */
    public static String label(int mods) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < COUNT; i++) {
            if ((mods >> i & 1) == 0) continue;
            if (b.length() > 0) b.append(" + ");
            b.append(NAMES[i]);
        }
        return b.toString();
    }

    public static int bits(int mods) {
        return Integer.bitCount(mods);
    }

    // ------------------------------------------------------------------ v1.5 B6: Shielded, Splitter, Volatile

    /** Called once when an elite is made: the Shielded arc starts up, facing the hero. */
    void arm(Enemy e) {
        e.faceA = (float) Math.atan2(s.py - e.y, s.px - e.x);
        e.eshT = SHIELD_UP;
    }

    /**
     * True when a hit from the point (sx, sy) lands on the frontal 120 degree arc of an elite that faces {@code face}
     * (radians) from (ex, ey). Pure geometry, no state: the unit test of the arc.
     */
    public static boolean inArc(float face, float ex, float ey, float sx, float sy) {
        float dx = sx - ex, dy = sy - ey, d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 0.001f) return true;
        return (dx * (float) Math.cos(face) + dy * (float) Math.sin(face)) / d >= ARC_COS;
    }

    /** The damage left after a Shielded elite's arc: x0.3 when the shield is up and the hit comes from the hero inside the arc. */
    float shieldedDamage(Enemy e, float dmg) {
        if ((e.mods & M_SHIELDED) == 0 || e.eshT <= 0f) return dmg;
        return inArc(e.faceA, e.x, e.y, s.px, s.py) ? dmg * (1f - BLOCK) : dmg;
    }

    /** A Volatile elite blows up: a warned ring (0.8 s), then 20% of the hero's max HP inside 60 units. No-op when the hazard pool is full. */
    void volatileBlast(Enemy e) {
        float dmg = VOLATILE_PCT * s.maxHp + (float) s.stats.armor; // the blast subtracts armor: the hero loses a clean 20%
        Entities.Hazard h = s.ai.addHazard(Entities.HZ_BOMB, e.x, e.y, VOLATILE_R, 0.4f, dmg, VOLATILE_WARN);
        if (h != null) h.variant = Entities.BV_VOLCANO;
    }

    // dead Splitters wait here until the reap pass is over (spawning inside it would swap the new enemies away)
    private final float[] splX = new float[8], splY = new float[8], splHp = new float[8], splR = new float[8];
    private final String[] splKey = new String[8];
    private int splN;

    /** Called by the reap pass for a dead elite: a Volatile one leaves its blast, a Splitter queues its copies. */
    void onDeath(Enemy e) {
        if ((e.mods & M_VOLATILE) != 0) volatileBlast(e);
        if ((e.mods & M_SPLITTER) != 0 && splN < splX.length) {
            splX[splN] = e.x;
            splY[splN] = e.y;
            splHp[splN] = e.maxHp * SPLIT_HP;
            splR[splN] = e.radius / 1.4f * 0.8f;
            splKey[splN++] = e.type.def.key;
        }
    }

    /** After the reap pass: spawns the queued copies, three per Splitter, only as many as fit under the pool (never past MAX_ENEMIES - 12). */
    void flushSplits() {
        for (int k = 0; k < splN; k++) {
            for (int j = 0; j < SPLIT_N; j++) {
                if (s.enemyN >= Sim.MAX_ENEMIES - 12) break;
                float a = TAU * (j + 0.5f) / SPLIT_N + k;
                if (!s.spawnMinion(splKey[k], splX[k] + (float) Math.cos(a) * 24f, splY[k] + (float) Math.sin(a) * 20f)) break;
                Enemy c = s.enemies[s.enemyN - 1];
                c.hp = c.maxHp = splHp[k];
                c.radius = splR[k];
                c.xp = 0f;
            }
        }
        splN = 0;
    }

    /** One step of the modifiers of a live elite (after its archetype moved it). {@code d} is the distance to the hero. */
    void update(Enemy e, float dt, float d) {
        int m = e.mods;
        if ((m & M_SHIELDED) != 0) { // the arc turns toward the hero at 1 rad/s at most: circling it beats the turn
            float want = (float) Math.atan2(s.py - e.y, s.px - e.x), diff = want - e.faceA;
            while (diff > 3.1415927f) diff -= TAU;
            while (diff < -3.1415927f) diff += TAU;
            float step = FACE_TURN * dt;
            e.faceA += Math.max(-step, Math.min(step, diff));
            if ((e.eshT -= dt) < -SHIELD_DOWN) e.eshT = SHIELD_UP; // up 4 s, down 3 s, up again
        }
        if ((m & M_VOLATILE) != 0 && d < e.radius * 0.8f + Sim.PLAYER_R * 0.8f + 4f && s.ended == 0) {
            volatileBlast(e); // touching it sets it off: it leaves at once, with no kill and no reward
            e.silent = true;
            s.killNow(e);
            return;
        }
        if ((m & (M_FIRE | M_FROST)) != 0) {
            if (e.t2 == 0f && e.t3 == 0f) {
                e.t2 = e.x;
                e.t3 = e.y;
            }
            float mx = e.x - e.t2, my = e.y - e.t3;
            e.t2 = e.x;
            e.t3 = e.y;
            e.etrail += Math.min(60f, (float) Math.sqrt(mx * mx + my * my));
            if (e.etrail >= TRAIL_EVERY && s.hazN < Sim.MAX_HAZ - Sim.HAZ_RESERVE_TRAIL) {
                e.etrail = 0f;
                if ((m & M_FIRE) != 0) s.ai.addHazard(Entities.HZ_SLAG, e.x, e.y + 4f, 22f, 3f, 6f, 0f);
                if ((m & M_FROST) != 0) s.ai.addHazard(Entities.HZ_FROST, e.x + 8f, e.y + 6f, 26f, 4f, 0f, 0f);
            }
        }
        if ((m & M_BARRAGE) != 0 && (e.mt0 -= dt) <= 0f && d < 520f && s.eprojN + 3 <= Sim.MAX_EPROJ) {
            e.mt0 = BARRAGE_EVERY;
            float base = (float) Math.atan2(s.py - e.y, s.px - e.x);
            for (int k = -1; k <= 1; k++) {
                float a = base + k * 0.6981f; // 40 degrees either side: an 80 degree fan
                s.ai.shoot(e.x, e.y - 10f, (float) Math.cos(a) * 230f, (float) Math.sin(a) * 230f, 9f * e.shotMul * e.dmgMul, 2.2f);
            }
            s.sound.emit(SoundEvents.SERGEANT_BURST);
        }
        if ((m & M_VOLCANO) != 0 && (e.mt1 -= dt) <= 0f && d < 600f) {
            e.mt1 = VOLCANO_EVERY;
            for (int k = 0; k < 3; k++) {
                float x = s.px, y = s.py;
                if (k > 0) {
                    float a = s.rng.nextFloat() * TAU, r = 70f + s.rng.nextFloat() * 120f;
                    x += (float) Math.cos(a) * r;
                    y += (float) Math.sin(a) * r * 0.8f;
                }
                Entities.Hazard h = s.ai.addHazard(Entities.HZ_BOMB, x, y, 48f, 0.4f, 12f * e.shotMul, 1.0f + 0.15f * k);
                if (h != null) h.variant = Entities.BV_VOLCANO;
            }
        }
        if ((m & M_RAISER) != 0 && (e.mt2 -= dt) <= 0f) {
            e.mt2 = RAISE_EVERY;
            float a0 = s.rng.nextFloat() * TAU;
            for (int k = 0; k < 4; k++) {
                if (s.enemyN >= Sim.MAX_ENEMIES - 12) break;
                float a = a0 + k * TAU / 4f;
                s.spawnMinion("COLONIST", e.x + (float) Math.cos(a) * 36f, e.y + (float) Math.sin(a) * 30f);
            }
            s.spark(e.x, e.y - 10f, 0xFF3DBE5A, 8);
        }
    }
}
