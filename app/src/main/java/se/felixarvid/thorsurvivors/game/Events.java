package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.WaveRow;
import se.felixarvid.thorsurvivors.data.WaveSelector;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.Random;

/**
 * The events of minutes 20 to 30 (design v1.3 section 4): the Signal Tower, Collapse (Deep Mines) / Slag Rain (Cinder
 * Flats), the Lumen Surge, Defend the Beacon and The Tide. A timeline from the overlay ({@code stage.eventId/eventAt}, built by
 * tools/build_ashfall_stages.py) starts them; at most one is active at a time and none opens a pausing phase (the Sim never
 * pauses itself). The legacy stages carry no timeline and so no events.
 *
 * <p>All placement comes from {@code Random(seed ^ salt)}, never {@link Sim#rng}, so seeded runs stay identical. State lives
 * in fields (no allocation while it runs); the renderers read the public fields and accessors. The Sim calls {@link #update}
 * once per step and asks {@link #spawnMul}, {@link #spawnsBlocked}, {@link #xpMul}, {@link #speedMul}, {@link #lightMul} and
 * {@link #ambientLift} for the changes events make to the world.
 */
public final class Events {
    public static final int NONE = 0, TOWER = 1, COLLAPSE = 2, SLAG_RAIN = 3, SURGE = 4, BEACON = 5, TIDE = 6, KINDS = 7;
    public static final String[] IDS = {"", "SIGNAL_TOWER", "COLLAPSE", "SLAG_RAIN", "LUMEN_SURGE", "BEACON", "TIDE"};
    public static final String[] TITLES = {"", "SIGNAL TOWER", "COLLAPSE", "SLAG RAIN", "LUMEN SURGE", "DEFEND THE BEACON", "THE TIDE"};
    /** Banner colours (ARGB, the title text) per kind. */
    public static final int[] COLORS = {0, 0xFF4FD8FF, 0xFFFFB347, 0xFFFF6A2A, 0xFF39FF88, 0xFFFFD84A, 0xFFB070FF};
    /** How long each event runs, in seconds. */
    public static final float[] LEN = {0f, 90f, 25f, 25f, 45f, 60f, 60f};
    /** A start that is later than this behind its second (a test that jumps the clock) is skipped, not started late. */
    static final float LATE = 20f;

    // Signal Tower
    public static final float TOWER_R = 140f, TOWER_NEED = 25f, TOWER_DRAIN = 0.5f, TOWER_MIN = 500f, TOWER_MAX = 700f,
            TOWER_SPAWNS = 0.25f, TOWER_HEAL = 0.3f, TOWER_XP = 1.2f, TOWER_XP_TIME = 180f;
    // Collapse / Slag Rain: rubble rings (hero and enemies take 20), at most RAIN_LIVE telegraphs, patches of 8 s
    public static final float RAIN_R = 60f, RAIN_WARN = 1.0f, RAIN_DMG = 20f, RAIN_EVERY = 0.42f, RAIN_STOP = 23.9f,
            PATCH_R = 48f, PATCH_LIFE = 8f;
    public static final int RAIN_LIVE = 10, FLAWLESS_GOLD = 200;
    /** Hazard slots events leave free: they add nothing once {@code hazN >= MAX_HAZ - HAZ_RESERVE} (bosses keep their room). */
    public static final int HAZ_RESERVE = 8;
    // Lumen Surge
    public static final float SURGE_LIGHT = 0.15f, SURGE_RADIUS = 0.25f, SURGE_SPEED = 0.15f, SURGE_XP = 1.5f, SURGE_FADE = 1.5f;
    public static final int SURGE_KILLS = 150;
    // Defend the Beacon
    public static final float BEACON_HP = 600f, BEACON_DIST = 250f, BEACON_R = 34f, BEACON_REGEN_R = 300f, BEACON_PATH = 0.3f,
            BEACON_DPS_K = 0.5f, SALVAGER_STAY = 60f;
    // The Tide: spawn interval x0.6, an elite and a horde every 45 s, a 10 s lull at +45
    public static final float TIDE_INTERVAL = 0.6f, TIDE_EVERY = 45f, LULL_AT = 45f, LULL_LEN = 10f;
    static final float TIDE_HORDE_FIRST = 8f, TIDE_ELITE_FIRST = 12f;
    public static final float BANNER_LIFE = 3.5f;

    private static final long SALT = 0x7E5A11D0B1EAC0DEL;
    private static final float TAU = 6.2831855f;

    private final Sim s;
    /** False on the legacy stages (no timeline). */
    public boolean enabled;
    private long seed;
    private Random er = new Random();
    private int next;

    /** The running event (NONE, TOWER ...), the run second it began at, and its length. */
    public int active;
    public double startAt;
    public float len;
    /** Seconds the event banner still shows (set when an event starts) and the title and colour it shows. */
    public float bannerT;
    public String bannerTitle = "";
    public int bannerColor;

    // tower
    public boolean towerOn, towerCapturing;
    public float tx, ty, towerProg;
    private boolean towerHorde;
    // rain
    private float rainAcc;
    private int rainN, hitsAtStart;
    // surge
    private int killsAtStart;
    public boolean surgeRewarded;
    // beacon
    public boolean beaconOn;
    public float bx, by, beaconHp;
    // tide
    private float nextHorde, nextElite;
    private boolean lullAnnounced;
    // buffs that outlive their event
    private double xpBuffUntil;

    /** Results for the tests and the result screen: how each event ended. */
    public int towerWon, towerLost, beaconWon, beaconLost, surgeWon, flawless, rainsDone, tidesDone, started;

    Events(Sim s) {
        this.s = s;
    }

    void init(long runSeed) {
        this.seed = runSeed ^ SALT;
        this.enabled = s.stage.eventAt.length > 0;
    }

    // ------------------------------------------------------------------ queries used by the Sim and the renderers

    /** True while the kind runs. */
    public boolean is(int kind) {
        return active == kind;
    }

    /** Seconds since the running event began (0 when none). */
    public float elapsed() {
        return active == NONE ? 0f : (float) (s.time - startAt);
    }

    /** 0..1: how strongly the Lumen Surge shines now (eases in and out over {@link #SURGE_FADE}), 0 outside it. */
    public float surgeLevel() {
        if (active != SURGE) return 0f;
        float t = elapsed();
        return Math.max(0f, Math.min(1f, Math.min(t, LEN[SURGE] - t) / SURGE_FADE));
    }

    /** True in the quiet 10 seconds of The Tide: no spawns, flickering light. */
    public boolean lull() {
        if (active != TIDE) return false;
        float t = elapsed();
        return t >= LULL_AT && t < LULL_AT + LULL_LEN;
    }

    /** Waves and the elite schedule pause (the lull). */
    boolean spawnsBlocked() {
        return lull();
    }

    /** Multiplier on how fast the spawn timer runs: the Tide x1/0.6, a Tower being captured +25%. */
    float spawnMul() {
        float m = 1f;
        if (active == TIDE) m /= TIDE_INTERVAL;
        if (active == TOWER && towerCapturing) m *= 1f + TOWER_SPAWNS;
        return m;
    }

    /** Multiplier on the XP of a gem dropped now: the Surge +50%, the Tower's buff +20% for three minutes. */
    float xpMul() {
        float m = 1f + (SURGE_XP - 1f) * (active == SURGE ? 1f : 0f);
        if (s.time < xpBuffUntil) m *= TOWER_XP;
        return m;
    }

    /** Multiplier on enemy speed (the Surge's +15%, eased). */
    float speedMul() {
        return active == SURGE ? 1f + SURGE_SPEED * surgeLevel() : 1f;
    }

    /** Multiplier on the hero's light radius (the Surge shrinks it by 25%). */
    public float lightMul() {
        return 1f - SURGE_RADIUS * surgeLevel();
    }

    /**
     * Change to the darkness of the ground (subtract from the stage's ambient): negative = brighter. The Surge lifts it by
     * 0.15; the Tide's lull flickers it dark.
     */
    public float ambientLift() {
        if (active == SURGE) return SURGE_LIGHT * surgeLevel();
        if (lull()) {
            double t = s.time;
            float f = (float) (Math.sin(t * 37.0) * Math.sin(t * 23.0 + 1.0));
            return f > 0.15f ? -0.22f : 0f;
        }
        return 0f;
    }

    /** True when a Beacon lives and a new spawn may be sent to it (30%, from the events' own Random). */
    boolean flagBeacon() {
        return active == BEACON && beaconOn && er.nextFloat() < BEACON_PATH;
    }

    // ------------------------------------------------------------------ update

    void update(float dt) {
        if (!enabled || s.ended != 0) return;
        double t = s.time;
        while (next < s.stage.eventAt.length && t >= s.stage.eventAt[next]) {
            int i = next++;
            if (t - s.stage.eventAt[i] > LATE) continue;
            int kind = kindOf(s.stage.eventId[i]);
            if (kind == NONE) continue;
            if (active != NONE) end(true);
            begin(kind, s.stage.eventAt[i], i);
        }
        if (bannerT > 0f) bannerT -= dt;
        if (active == NONE) return;
        float e = (float) (t - startAt);
        switch (active) {
            case TOWER: updateTower(dt, e); break;
            case COLLAPSE:
            case SLAG_RAIN: updateRain(dt, e); break;
            case SURGE: updateSurge(e); break;
            case BEACON: updateBeacon(dt, e); break;
            case TIDE: updateTide(e); break;
            default:
        }
        if (active != NONE && e >= len) end(false);
    }

    private static int kindOf(String id) {
        for (int i = 1; i < KINDS; i++) if (IDS[i].equals(id)) return i;
        return NONE;
    }

    private void begin(int kind, int at, int index) {
        active = kind;
        startAt = at;
        len = LEN[kind];
        started++;
        er = new Random(seed + index * 0x9E3779B97F4A7C15L);
        bannerT = BANNER_LIFE;
        bannerTitle = TITLES[kind];
        bannerColor = COLORS[kind];
        switch (kind) {
            case TOWER:
                if (place(TOWER_MIN, TOWER_MAX)) {
                    towerOn = true;
                    tx = placeX;
                    ty = placeY;
                    towerProg = 0f;
                    towerCapturing = false;
                    towerHorde = false;
                    s.toast("SIGNAL TOWER  -  capture it, check the minimap", COLORS[TOWER]);
                } else {
                    active = NONE; // nowhere to stand (cannot happen on the Ashfall maps)
                    return;
                }
                break;
            case COLLAPSE:
            case SLAG_RAIN:
                rainAcc = 0f;
                rainN = 0;
                hitsAtStart = s.heroHits;
                s.toast(kind == COLLAPSE ? "COLLAPSE  -  the ceiling is coming down" : "SLAG RAIN  -  take cover from the sky", COLORS[kind]);
                break;
            case SURGE:
                killsAtStart = s.kills;
                surgeRewarded = false;
                s.toast("LUMEN SURGE  -  kill " + SURGE_KILLS + " for a chest", COLORS[SURGE]);
                break;
            case BEACON:
                if (place(BEACON_DIST, BEACON_DIST)) {
                    beaconOn = true;
                    bx = placeX;
                    by = placeY;
                    beaconHp = BEACON_HP;
                    s.toast("DEFEND THE BEACON  -  hold for 60 seconds", COLORS[BEACON]);
                } else {
                    active = NONE;
                    return;
                }
                break;
            case TIDE:
                nextHorde = TIDE_HORDE_FIRST;
                nextElite = TIDE_ELITE_FIRST;
                lullAnnounced = false;
                s.toast("THE TIDE  -  everything comes at once", COLORS[TIDE]);
                s.sound.emit(SoundEvents.BOSS_INTRO);
                break;
            default:
        }
    }

    /** The running event ends: {@code interrupted} when another one needed the slot (a test that jumps the clock). */
    private void end(boolean interrupted) {
        int kind = active;
        switch (kind) {
            case TOWER:
                if (towerOn) {
                    towerOn = false;
                    towerLost++;
                    s.toast("SIGNAL LOST", 0xFFFF6A5A);
                }
                towerCapturing = false;
                break;
            case COLLAPSE:
            case SLAG_RAIN:
                rainsDone++;
                if (!interrupted && s.heroHits == hitsAtStart) {
                    flawless++;
                    s.gold += FLAWLESS_GOLD;
                    s.toast("FLAWLESS  +" + FLAWLESS_GOLD + " gold", 0xFF39FF88);
                    s.sound.emit(SoundEvents.COIN);
                }
                break;
            case SURGE:
                s.toast("The surge fades", 0xFFB8B0A4);
                break;
            case BEACON:
                if (beaconOn) { // time ran out with the Beacon standing
                    beaconWon++;
                    s.addPickup(Entities.K_CHEST, bx, by + 26f, Sim.ARMS_CRATE);
                    s.merchant.summon(SALVAGER_STAY);
                    s.toast("BEACON HELD  -  Arms Crate, a Salvager comes", COLORS[BEACON]);
                    s.sound.emit(SoundEvents.CHEST_OPEN);
                }
                clearBeacon();
                break;
            case TIDE:
                tidesDone++;
                break;
            default:
        }
        active = NONE;
    }

    // ------------------------------------------------------------------ Signal Tower

    private void updateTower(float dt, float e) {
        if (!towerOn) return;
        float dx = s.px - tx, dy = s.py - ty;
        boolean in = dx * dx + dy * dy < TOWER_R * TOWER_R;
        towerCapturing = in;
        if (in) {
            towerProg = Math.min(TOWER_NEED, towerProg + dt);
        } else if (towerProg > 0f) {
            towerProg = Math.max(0f, towerProg - dt * TOWER_DRAIN);
        }
        if (in && !towerHorde && towerProg >= TOWER_NEED * 0.5f) {
            towerHorde = true;
            s.requestHorde("TOWER");
            s.toast("The signal draws a horde", 0xFFFF6A5A);
        }
        if (towerProg >= TOWER_NEED) {
            towerOn = false;
            towerCapturing = false;
            towerWon++;
            s.addPickup(Entities.K_CHEST, tx, ty + 26f, Sim.ARMS_CRATE);
            s.heal(s.maxHp * TOWER_HEAL);
            xpBuffUntil = s.time + TOWER_XP_TIME;
            s.sound.emit(SoundEvents.EVOLUTION);
            s.toast("SIGNAL CAPTURED  -  Arms Crate, +20% XP for 3 min", COLORS[TOWER]);
            active = NONE; // the tower is done: the event ends here, early
        }
    }

    /** Tower capture progress 0..1 (the minimap arc). */
    public float towerFrac() {
        return towerProg / TOWER_NEED;
    }

    // ------------------------------------------------------------------ Collapse / Slag Rain

    private void updateRain(float dt, float e) {
        if (e > RAIN_STOP) return;
        rainAcc += dt;
        if (rainAcc < RAIN_EVERY) return;
        rainAcc -= RAIN_EVERY;
        if (s.hazN >= Sim.MAX_HAZ - HAZ_RESERVE || liveRain() >= RAIN_LIVE) return;
        int i = rainN++;
        // every third ring lands on the hero (keep moving), the rest scatter around
        float a = er.nextFloat() * TAU, d = i % 3 == 0 ? er.nextFloat() * 40f : 60f + er.nextFloat() * 170f;
        Entities.Hazard h = s.ai.addHazard(Entities.HZ_BOMB, s.px + (float) Math.cos(a) * d, s.py + (float) Math.sin(a) * d * 0.8f,
                RAIN_R, 0.4f, RAIN_DMG, RAIN_WARN);
        if (h == null) return;
        h.variant = active == COLLAPSE ? Entities.BV_RUBBLE : Entities.BV_SLAGRAIN;
        h.w = PATCH_R;
        h.spin = PATCH_LIFE;
    }

    /** Rubble telegraphs still warning (the live cap of 10). */
    private int liveRain() {
        int n = 0;
        for (int i = 0; i < s.hazN; i++) {
            Entities.Hazard h = s.hazards[i];
            if (h.kind == Entities.HZ_BOMB && h.warn > 0f && (h.variant == Entities.BV_RUBBLE || h.variant == Entities.BV_SLAGRAIN)) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ Lumen Surge

    private void updateSurge(float e) {
        if (surgeRewarded || s.kills - killsAtStart < SURGE_KILLS) return;
        surgeRewarded = true;
        surgeWon++;
        s.addPickup(Entities.K_CHEST, s.px + 40f, s.py + 10f, Sim.ARMS_CRATE);
        s.sound.emit(SoundEvents.CHEST_OPEN);
        s.toast("SURGE CHEST  -  " + SURGE_KILLS + " kills", COLORS[SURGE]);
    }

    // ------------------------------------------------------------------ Defend the Beacon

    private void updateBeacon(float dt, float e) {
        if (!beaconOn) return;
        float dx = s.px - bx, dy = s.py - by;
        if (dx * dx + dy * dy < BEACON_REGEN_R * BEACON_REGEN_R && s.hp < s.maxHp) s.hp = Math.min(s.maxHp, s.hp + dt); // +1 HP/s
        float hit = 0f;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy en = s.enemies[i];
            if (!en.toBeacon || en.dead) continue;
            float ex = en.x - bx, ey = en.y - by, r = BEACON_R + en.radius;
            if (ex * ex + ey * ey < r * r) hit += en.power * BEACON_DPS_K * dt;
        }
        beaconHp -= hit;
        if (beaconHp <= 0f) {
            beaconHp = 0f;
            beaconOn = false;
            beaconLost++;
            clearBeacon();
            s.toast("BEACON LOST  -  a horde follows", 0xFFFF6A5A);
            s.requestHorde("BEACON");
            active = NONE;
        }
    }

    private void clearBeacon() {
        beaconOn = false;
        for (int i = 0; i < s.enemyN; i++) s.enemies[i].toBeacon = false;
    }

    /** Beacon health 0..1 and survive progress 0..1 (the minimap arc and the bar in the world). */
    public float beaconFrac() {
        return beaconHp / BEACON_HP;
    }

    public float beaconTimeFrac() {
        return active == BEACON ? Math.min(1f, elapsed() / LEN[BEACON]) : 0f;
    }

    // ------------------------------------------------------------------ The Tide

    private void updateTide(float e) {
        boolean lull = lull();
        if (lull && !lullAnnounced) {
            lullAnnounced = true;
            s.toast("THE TIDE DRAWS BREATH", COLORS[TIDE]);
        }
        if (lull) return; // nothing arrives in the quiet
        if (lullAnnounced && e >= LULL_AT + LULL_LEN && nextHorde < e) { // the first thing after the quiet is the horde
            lullAnnounced = false;
            s.toast("THE TIDE RETURNS", COLORS[TIDE]);
        }
        if (e >= nextHorde) {
            nextHorde += TIDE_EVERY;
            s.requestHorde("TIDE");
        }
        if (e >= nextElite) {
            nextElite += TIDE_EVERY;
            WaveRow row = s.stage.rows.get(WaveSelector.indexAt(s.stage.rows, s.time));
            if (row.enemies.length > 0) {
                EnemyType t = s.type(row.enemies[er.nextInt(row.enemies.length)]);
                s.spawnElite(t, Elites.roll(er, Elites.modCount(s.data, s.time)));
            }
        }
    }

    // ------------------------------------------------------------------ placement

    private float placeX, placeY;

    /** A free spot {@code lo .. hi} units from the hero (same blocked check as the finds and the Salvager). */
    private boolean place(float lo, float hi) {
        for (int attempt = 0; attempt < 200; attempt++) {
            float a = er.nextFloat() * TAU, r = lo + er.nextFloat() * (hi - lo);
            if (free(s.px + (float) Math.cos(a) * r, s.py + (float) Math.sin(a) * r)) return true;
        }
        float mid = (lo + hi) * 0.5f;
        for (int i = 0; i < 72; i++) {
            float a = i * TAU / 72f;
            if (free(s.px + (float) Math.cos(a) * mid, s.py + (float) Math.sin(a) * mid)) return true;
        }
        return false;
    }

    private boolean free(float cx, float cy) {
        if (s.map.blocked(cx, cy) || s.map.blocked(cx + 30f, cy) || s.map.blocked(cx - 30f, cy)
                || s.map.blocked(cx, cy + 30f) || s.map.blocked(cx, cy - 30f)) return false;
        placeX = cx;
        placeY = cy;
        return true;
    }
}
