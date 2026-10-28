package se.felixarvid.thorsurvivors.game.stage;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.data.EquipDef;
import se.felixarvid.thorsurvivors.game.Entities;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.EnemyType;
import se.felixarvid.thorsurvivors.game.Events;
import se.felixarvid.thorsurvivors.game.PlayerStats;
import se.felixarvid.thorsurvivors.game.RefineryOps;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.SoundEvents;
import se.felixarvid.thorsurvivors.game.Status;
import se.felixarvid.thorsurvivors.game.WorldGen;
import se.felixarvid.thorsurvivors.game.layout.StreetsLayout;

import java.util.Random;

/**
 * Refinery District, "Stay moving" (v1.4 chunk C, spec 1.2). The ground moves, burns and heats:
 * <ul>
 * <li><b>Conveyors</b>: {@link #push} drifts the hero (50% of its own speed, added to the input: the stick stays authoritative and
 * nothing is remembered, so leaving a belt stops the drift at once), enemies (60%, elites 40%, bosses 20%) and pickups. All belts
 * flip direction every 20 s with a 1.5 s arrow flash first; Foreman Brass flips them on every stomp.</li>
 * <li><b>Furnace gates</b> at the belt ends: 25 damage and Burn to the hero (1 s between hits, the dodge protects); fodder (not elites,
 * not bosses) dies on touch and leaves a gem worth half.</li>
 * <li><b>Heat</b> 0..100 (130 with the Ironheart Core): +6/s standing still or within 200 u of a furnace or gate, -12/s moving; 1.5 true
 * damage/s from 50, Overheat at 100 (5/s and +25% cooldowns until it is under 50). Coolant clears it; frost items halve the gain.</li>
 * <li><b>Heat Wave</b> every 40 s from 2:00: a 6 tile band along the x axis at 220 u/s, edge = start + dir * 220 * (t - t0) (analytic),
 * 20 damage and Burn to the hero, 40 to enemies, once each.</li>
 * <li><b>Molten channels</b>: 6/s and Burn to the hero, 15/s to enemies. <b>Coolant valves</b> and <b>fuel tanks</b> are destructibles.</li>
 * <li>Stoker (a fire patch after every dash, immune to Burn) and Ironclad (-50% from hits, +50% from Burn, Electrify, Decay; at most 8).</li>
 * <li>Events: Cooling Tower (the Signal Tower; a success clears heat for 3 min), Overdrive (the Lumen Surge; belts x2, the gates pulse
 * every 20 s) and Defend the Boiler (the Beacon).</li>
 * </ul>
 * Randomness comes from this object's own {@code Random(seed ^ salt)}, never from {@code Sim.rng}.
 */
public final class RefineryRules extends StageRules {
    /** Coolant valve, fuel tank, and the armoured valves of the finale (only the Ironheart's slam breaks those, never a weapon). */
    public static final int DK_COOLANT = Sim.DK_STAGE, DK_FUEL = Sim.DK_STAGE + 1, DK_VALVE_HEAVY = Sim.DK_STAGE + 2;
    // belts
    public static final float HERO_PUSH = 0.5f, ENEMY_PUSH = 0.6f, ELITE_PUSH = 0.4f, BOSS_PUSH = 0.2f, PICKUP_PUSH = 0.6f,
            HERO_PUSH_CAP = 0.85f, OVERDRIVE_MUL = 2f;
    public static final float FLIP_EVERY = 20f, FLIP_TELE = 1.5f;
    // gates and molten
    public static final float GATE_DMG = 25f, GATE_COOLDOWN = 1f, GATE_GEM = 0.5f, MOLTEN_HERO = 6f, MOLTEN_ENEMY = 15f;
    // heat
    public static final float HEAT_GAIN = 6f, HEAT_LOSS = 12f, HEAT_NEAR = 200f, HEAT_STILL = 0.35f, HEAT_BURN_AT = 50f, HEAT_BURN_DPS = 1.5f,
            HEAT_MAX = 100f, HEAT_MAX_CORE = 130f, OVERHEAT_DPS = 5f, COOLING_LOCK = 180f;
    public static final double OVERHEAT_COOLDOWN = 1.25, CORE_MIGHT = 1.15;
    // Heat Wave
    public static final float WAVE_EVERY = 40f, WAVE_FIRST = 120f, WAVE_TELE = 1.5f, WAVE_SPEED = 220f, WAVE_THICK = 6f * WorldGen.TILE,
            WAVE_HERO = 20f, WAVE_ENEMY = 40f, WAVE_START = 560f, WAVE_LIFE = 14f;
    public static final int MAXW = 4;
    // coolant fog and fuel tanks
    public static final float FOG_R = 100f, FOG_LIFE = 8f, FUEL_R = 130f, FUEL_DMG = 90f, FUEL_HERO = 14f, FUEL_FIRE_T = 6f;
    public static final int MAXF = 4;
    private static final float DEST_CELL = 500f, DEST_CLEAR = 300f, DEST_R = 16f; // Sim.DEST_*, which are package-private
    public static final float SHOCK_STUN = 4f, SHOCK_VULN = 6f, SHOCK_MUL = 1.5f;
    public static final int IRONCLAD_CAP = 8;
    /** Belt modes of the finale: 0 as laid out, 1 every belt near the boss points to it, 2 away from it. */
    public static final int BELTS_NORMAL = 0, BELTS_TO_BOSS = 1, BELTS_FROM_BOSS = 2;
    public static final float BOSS_BELT_RANGE = 1200f, VGATE_R = 62f, VGATE_RING = 300f;
    public static final int MAXVG = 6;

    private static final long SALT = 0x4E1F3A7C0DEA11B5L;
    private static final int A_DASH = 4, A_REST = 5;

    private Random rr;
    private final float[] pv = new float[2], pw = new float[2], tmp = new float[2];

    /** Damage the hero took from each source this run (heat, gates and pulses, molten metal, Heat Waves): for balance runs and the result screen. */
    public float dmgHeat, dmgGate, dmgMolten, dmgWave;

    // ---- hero heat
    public float heat;
    public boolean overheated;
    private float lockUntil, lastPx, lastPy, speedEma, nearT;
    private boolean nearHeat, started;
    private PlayerStats tracked;
    private double baseMight, baseCd;
    public boolean core;

    // ---- belts
    private int manualFlips;
    public float manualFlash;
    public int beltMode;
    public float beltX, beltY;
    public boolean overdrive;

    // ---- gates, molten
    private float gateCd, overdriveNext;
    private boolean heroInGate;
    public float pulseFlash;
    public final float[] vgX = new float[MAXVG], vgY = new float[MAXVG];
    public int vgN;

    // ---- heat waves (slot arrays are read by RefineryFx)
    public final boolean[] wLive = new boolean[MAXW];
    public final float[] wDir = new float[MAXW], wStart = new float[MAXW];
    public final double[] wT0 = new double[MAXW];
    private final int[] wSerial = new int[MAXW], wHero = new int[MAXW];
    private final int[] waveMark = new int[4096];
    private int serial;
    private double nextWave;
    private int waveCount;

    // ---- coolant fog
    public final float[] fogX = new float[MAXF], fogY = new float[MAXF], fogT = new float[MAXF];
    private float fogTick;
    public boolean inFog;

    // ---- finale and events
    public Enemy finale;
    public double shockUntil;
    private final float[] fvX = new float[4], fvY = new float[4];
    private int fvN;
    private int lastTowerWon;

    @Override
    public void init(Sim sim) {
        super.init(sim);
        rr = new Random(sim.runSeed ^ SALT);
        waveEvery = (float) def.rule("waveEvery", WAVE_EVERY);
        nextWave = (float) def.rule("waveFirst", WAVE_FIRST);
        areaMul0 = (float) def.rule("areaMul0", 1);
        areaMul1 = (float) def.rule("areaMul1", 1);
        heatGain = (float) def.rule("heatGain", HEAT_GAIN);
        overdriveNext = 0f;
    }

    /** v1.4 tuning: damage to the hero from heat, molten floor, gates and heat waves, x areaMul0 at 0:00 rising linearly to x areaMul1 at 30:00. */
    private float am() {
        return areaMul0 + (areaMul1 - areaMul0) * (float) Math.min(1.0, s.time / 1800.0);
    }
    private float waveEvery = WAVE_EVERY, areaMul0 = 1f, areaMul1 = 1f, heatGain = HEAT_GAIN;

    // ------------------------------------------------------------------ queries

    /** Scheduled flips so far plus the stomp flips: odd = every belt runs the other way. */
    public boolean flipped() {
        return (((int) (s.time / FLIP_EVERY)) + manualFlips & 1) != 0;
    }

    /** Seconds to the next scheduled flip. */
    public float flipIn() {
        return FLIP_EVERY - (float) (s.time % FLIP_EVERY);
    }

    /** True during the arrow flash before a flip (1.5 s) and the short flash after a stomp flip. */
    public boolean flipFlash() {
        return flipIn() < FLIP_TELE || manualFlash > 0f;
    }

    public float heatLimit() {
        return core ? HEAT_MAX_CORE : HEAT_MAX;
    }

    /** Belt speed factor of the moment (Overdrive doubles it). */
    public float beltMul() {
        return overdrive ? OVERDRIVE_MUL : 1f;
    }

    public boolean coolingLocked() {
        return s.time < lockUntil;
    }

    /** Leading edge of heat wave slot i at run time t (world x), the analytic position. */
    public float waveEdge(int i, double t) {
        return wStart[i] + wDir[i] * WAVE_SPEED * (float) (t - wT0[i]);
    }

    /** True when a wave is in its 1.5 s warning (edge not yet moving). */
    public boolean waveWarning(int i) {
        return wLive[i] && s.time < wT0[i];
    }

    // ------------------------------------------------------------------ belts: push

    @Override
    public boolean push(float x, float y, float[] out) {
        int tx = (int) Math.floor(x / WorldGen.TILE), ty = (int) Math.floor(y / WorldGen.TILE);
        int b = StreetsLayout.surfaceAt(tx, ty);
        if ((b & (WorldGen.SURF_BELT_E | WorldGen.SURF_BELT_W | WorldGen.SURF_BELT_N | WorldGen.SURF_BELT_S)) == 0) return false;
        float ux = 0f, uy = 0f;
        float f = flipped() ? -1f : 1f;
        if ((b & WorldGen.SURF_BELT_S) != 0) uy += f;
        if ((b & WorldGen.SURF_BELT_N) != 0) uy -= f;
        if ((b & WorldGen.SURF_BELT_E) != 0) ux += f;
        if ((b & WorldGen.SURF_BELT_W) != 0) ux -= f;
        if (beltMode != BELTS_NORMAL) {
            float dx = beltX - x, dy = beltY - y;
            if (dx * dx + dy * dy < BOSS_BELT_RANGE * BOSS_BELT_RANGE) {
                float g = beltMode == BELTS_TO_BOSS ? 1f : -1f;
                if ((b & (WorldGen.SURF_BELT_S | WorldGen.SURF_BELT_N)) != 0) uy = dy >= 0f ? g : -g;
                if ((b & (WorldGen.SURF_BELT_E | WorldGen.SURF_BELT_W)) != 0) ux = dx >= 0f ? g : -g;
            }
        }
        float m = ux * ux + uy * uy; // two crossing belts add up, the total stays a unit vector at most
        if (m > 1f) {
            m = (float) Math.sqrt(m);
            ux /= m;
            uy /= m;
        }
        out[0] = ux;
        out[1] = uy;
        return m > 0f;
    }

    @Override
    public float pushWeight(int who, Enemy e) {
        float m = beltMul();
        switch (who) {
            case PUSH_HERO: return Math.min(HERO_PUSH_CAP, HERO_PUSH * m); // the stick stays in charge even in Overdrive
            case PUSH_ENEMY: return (e != null && e.boss ? BOSS_PUSH : e != null && e.elite ? ELITE_PUSH : ENEMY_PUSH) * m;
            default: return PICKUP_PUSH * m;
        }
    }

    /** Foreman Brass stomps: every belt turns around now (with a short flash). */
    public void reverseBelts() {
        manualFlips++;
        manualFlash = 0.8f;
    }

    // ------------------------------------------------------------------ update

    @Override
    public void update(float dt) {
        if (s.ended != 0) return;
        double t = s.time;
        if (!started) {
            started = true;
            lastPx = s.px;
            lastPy = s.py;
        }
        if (manualFlash > 0f) manualFlash -= dt;
        if (pulseFlash > 0f) pulseFlash -= dt;
        if (gateCd > 0f) gateCd -= dt;
        // the equipment: the Ironheart Core raises the limit and gives might from 50 heat
        EquipDef ring = s.equip.def[EquipDef.RING];
        core = ring != null && "IRONHEART_CORE".equals(ring.key);
        events(dt);
        updateHeat(dt, t);
        updateWaves(dt, t);
        updateFog(dt);
        updateGroundHits(dt);
        applyStats();
    }

    private void applyStats() {
        PlayerStats st = s.stats;
        if (st != tracked) {
            tracked = st;
            baseMight = st.might;
            baseCd = st.cooldownMul;
        }
        st.might = baseMight * (core && heat > HEAT_BURN_AT ? CORE_MIGHT : 1.0);
        st.cooldownMul = baseCd * (overheated ? OVERHEAT_COOLDOWN : 1.0);
    }

    // ---- heat

    private void updateHeat(float dt, double t) {
        float inst = (float) Math.sqrt((s.px - lastPx) * (s.px - lastPx) + (s.py - lastPy) * (s.py - lastPy)) / Math.max(dt, 1e-4f);
        lastPx = s.px;
        lastPy = s.py;
        speedEma += (Math.min(inst, 2000f) - speedEma) * Math.min(1f, dt / 0.15f);
        float maxSp = 130f * (float) s.stage.playerSpeed * (float) s.stats.moveSpeedMul;
        boolean moving = speedEma > HEAT_STILL * maxSp;
        if ((nearT -= dt) <= 0f) {
            nearT = 0.2f;
            nearHeat = scanHeat();
        }
        float limit = heatLimit();
        if (t < lockUntil || inFog) {
            heat = 0f;
        } else if (nearHeat || !moving) {
            boolean frost = s.stats.statusChance[Entities.ST_FROST] > 0.0 || s.inv.weaponLevel("HOLYWATER") > 0;
            heat += heatGain * (frost ? 0.5f : 1f) * dt;
        } else {
            heat -= HEAT_LOSS * dt;
        }
        heat = Math.max(0f, Math.min(limit, heat));
        if (heat >= limit - 1e-3f) overheated = true;
        else if (heat < HEAT_BURN_AT) overheated = false;
        if (overheated) {
            RefineryOps.areaDamage(s, OVERHEAT_DPS * am() * dt);
            dmgHeat += OVERHEAT_DPS * dt;
        } else if (heat >= HEAT_BURN_AT) {
            RefineryOps.areaDamage(s, HEAT_BURN_DPS * am() * dt);
            dmgHeat += HEAT_BURN_DPS * dt;
        }
    }

    public void addHeat(float v) {
        heat = Math.max(0f, Math.min(heatLimit(), heat + v));
    }

    /** A furnace or gate within {@link #HEAT_NEAR} of the hero (a gate counts from its tile centres, 40 u closer). */
    private boolean scanHeat() {
        int tx = (int) Math.floor(s.px / WorldGen.TILE), ty = (int) Math.floor(s.py / WorldGen.TILE);
        int cx0 = StreetsLayout.cell(tx - 6), cx1 = StreetsLayout.cell(tx + 6), cy0 = StreetsLayout.cell(ty - 6), cy1 = StreetsLayout.cell(ty + 6);
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                if (!StreetsLayout.furnace(cx, cy, tmp)) continue;
                float dx = tmp[0] - s.px, dy = tmp[1] - s.py;
                if (dx * dx + dy * dy < HEAT_NEAR * HEAT_NEAR) return true;
            }
        }
        float rg = HEAT_NEAR - 40f;
        for (int j = -5; j <= 5; j++) {
            for (int i = -5; i <= 5; i++) {
                if ((StreetsLayout.surfaceAt(tx + i, ty + j) & WorldGen.SURF_FURNACE_GATE) == 0) continue;
                float dx = (tx + i + 0.5f) * WorldGen.TILE - s.px, dy = (ty + j + 0.5f) * WorldGen.TILE - s.py;
                if (dx * dx + dy * dy < rg * rg) return true;
            }
        }
        for (int k = 0; k < vgN; k++) {
            float dx = vgX[k] - s.px, dy = vgY[k] - s.py;
            if (dx * dx + dy * dy < HEAT_NEAR * HEAT_NEAR) return true;
        }
        return false;
    }

    // ---- heat waves

    private void updateWaves(float dt, double t) {
        if (t >= nextWave - WAVE_TELE) { // the regular wave: alternating sides, starting off screen
            float dir = (waveCount++ & 1) == 0 ? 1f : -1f;
            startWave(dir, s.px - dir * WAVE_START, nextWave);
            nextWave += waveEvery;
        }
        for (int i = 0; i < MAXW; i++) {
            if (!wLive[i]) continue;
            if (t > wT0[i] + WAVE_LIFE) {
                wLive[i] = false;
                continue;
            }
            if (t < wT0[i]) continue;
            float edge = waveEdge(i, t), dirW = wDir[i];
            float hx = (s.px - edge) * dirW; // inside the band: between -thickness and 0 behind the leading edge
            if (wHero[i] != wSerial[i] && hx <= 0f && hx >= -WAVE_THICK) {
                wHero[i] = wSerial[i];
                if (s.invuln <= 0f && s.dodgeT <= 0f && s.ended == 0) {
                    RefineryOps.hurtHero(s, Math.max(1f, WAVE_HERO * am() - (float) s.stats.armor), 0.5f, Sim.DMG_AREA);
                    dmgWave += WAVE_HERO;
                    if (!s.stats.burnImmune) s.heroBurnT = Status.HERO_BURN_TIME;
                }
            }
            for (int k = 0; k < s.enemyN; k++) {
                Enemy e = s.enemies[k];
                if (e.dead || e.dormant) continue;
                float ex = (e.x - edge) * dirW;
                if (ex > 0f || ex < -WAVE_THICK) continue;
                int slot = e.id & 4095;
                if (waveMark[slot] == wSerial[i]) continue;
                waveMark[slot] = wSerial[i];
                s.hurt(e, WAVE_ENEMY, 0f, 1f, dirW, 0f, 0.4f);
            }
        }
    }

    /** A Heat Wave sweeping along x in direction {@code dir} (+1 / -1) from {@code startEdge}; it moves from run time {@code t0} (the 1.5 s before are the warning). */
    public int startWave(float dir, float startEdge, double t0) {
        int slot = -1;
        for (int i = 0; i < MAXW; i++) if (!wLive[i]) { slot = i; break; }
        if (slot < 0) return -1;
        wLive[slot] = true;
        wDir[slot] = dir;
        wStart[slot] = startEdge;
        wT0[slot] = t0;
        wSerial[slot] = ++serial;
        wHero[slot] = 0;
        s.sound.emit(SoundEvents.BOSS_SLAM);
        return slot;
    }

    // ---- coolant fog

    private void updateFog(float dt) {
        inFog = false;
        boolean tick = (fogTick += dt) >= 1f;
        if (tick) fogTick -= 1f;
        for (int i = 0; i < MAXF; i++) {
            if (fogT[i] <= 0f) continue;
            fogT[i] -= dt;
            float dx = s.px - fogX[i], dy = s.py - fogY[i];
            if (dx * dx + dy * dy < FOG_R * FOG_R) inFog = true;
            if (!tick) continue;
            int n = RefineryOps.query(s, fogX[i], fogY[i], FOG_R + 30f);
            for (int k = 0; k < n; k++) {
                Enemy e = RefineryOps.queried(s, k);
                float ex = e.x - fogX[i], ey = e.y - fogY[i];
                if (!e.dead && ex * ex + ey * ey < FOG_R * FOG_R) s.applyStatus(e, Entities.ST_FROST, 1, 10f, 0f);
            }
        }
    }

    // ---- gates, molten ground

    private boolean onGate(float x, float y) {
        if ((StreetsLayout.surfaceAt((int) Math.floor(x / WorldGen.TILE), (int) Math.floor(y / WorldGen.TILE)) & WorldGen.SURF_FURNACE_GATE) != 0) return true;
        for (int k = 0; k < vgN; k++) {
            float dx = x - vgX[k], dy = y - vgY[k];
            if (dx * dx + dy * dy < VGATE_R * VGATE_R) return true;
        }
        return false;
    }

    private void updateGroundHits(float dt) {
        // enemies: fodder dies in a gate, everything burns in molten metal
        for (int i = s.enemyN - 1; i >= 0; i--) {
            Enemy e = s.enemies[i];
            if (e.dead || e.dormant) continue;
            int b = StreetsLayout.surfaceAt((int) Math.floor(e.x / WorldGen.TILE), (int) Math.floor(e.y / WorldGen.TILE));
            if ((b & WorldGen.SURF_MOLTEN) != 0) RefineryOps.statusDamage(s, e, MOLTEN_ENEMY * dt, Status.C_BURN);
            if (!e.boss && !e.elite && !e.silent && ((b & WorldGen.SURF_FURNACE_GATE) != 0 || vgN > 0 && onGate(e.x, e.y))) RefineryOps.gateKill(s, e, GATE_GEM);
        }
        if (s.ended != 0) return;
        // the hero
        boolean safe = s.dodgeT > 0f;
        int b = StreetsLayout.surfaceAt((int) Math.floor(s.px / WorldGen.TILE), (int) Math.floor(s.py / WorldGen.TILE));
        if ((b & WorldGen.SURF_MOLTEN) != 0 && !safe) {
            RefineryOps.areaDamage(s, MOLTEN_HERO * am() * dt);
            dmgMolten += MOLTEN_HERO * dt;
            if (!s.stats.burnImmune) s.heroBurnT = Status.HERO_BURN_TIME;
        }
        boolean inGate = (b & WorldGen.SURF_FURNACE_GATE) != 0 || vgN > 0 && onGate(s.px, s.py);
        if (inGate && !heroInGate && gateCd <= 0f) { // the gate hurts on entering, not while standing in it; one second before the next entry counts
            gateCd = GATE_COOLDOWN;
            if (s.invuln <= 0f && !safe) {
                RefineryOps.hurtHero(s, Math.max(1f, GATE_DMG * am() - (float) s.stats.armor), 0.5f, Sim.DMG_AREA);
                dmgGate += GATE_DMG;
                if (!s.stats.burnImmune) s.heroBurnT = Status.HERO_BURN_TIME;
                RefineryOps.fx(s, Entities.FX_FLASH, s.px, s.py - 8f, 90f, 0f, 0.25f);
            }
        }
        heroInGate = inGate;
    }

    /** Overdrive: every gate near the hero flashes and kills the fodder around it, and hurts the hero standing at it. */
    private void gatePulse() {
        pulseFlash = 0.6f;
        s.sound.emit(SoundEvents.BARREL_EXPLODE);
        int tx = (int) Math.floor(s.px / WorldGen.TILE), ty = (int) Math.floor(s.py / WorldGen.TILE);
        for (int j = -16; j <= 16; j++) {
            for (int i = -16; i <= 16; i++) {
                if ((StreetsLayout.surfaceAt(tx + i, ty + j) & WorldGen.SURF_FURNACE_GATE) == 0) continue;
                float gx = (tx + i + 0.5f) * WorldGen.TILE, gy = (ty + j + 0.5f) * WorldGen.TILE;
                int n = RefineryOps.query(s, gx, gy, 90f);
                for (int k = 0; k < n; k++) {
                    Enemy e = RefineryOps.queried(s, k);
                    if (!e.dead && !e.boss && !e.elite && !e.dormant) RefineryOps.gateKill(s, e, GATE_GEM);
                }
                float dx = s.px - gx, dy = s.py - gy;
                if (dx * dx + dy * dy < 70f * 70f && s.invuln <= 0f && s.dodgeT <= 0f && gateCd <= 0f) {
                    gateCd = GATE_COOLDOWN;
                    RefineryOps.hurtHero(s, Math.max(1f, GATE_DMG * am() - (float) s.stats.armor), 0.5f, Sim.DMG_AREA);
                }
            }
        }
    }

    // ---- events

    private void events(float dt) {
        Events ev = s.events;
        if (ev.active == Events.TOWER && ev.bannerT > 0f) ev.bannerTitle = "COOLING TOWER";
        if (ev.active == Events.BEACON && ev.bannerT > 0f) ev.bannerTitle = "DEFEND THE BOILER";
        boolean od = ev.active == Events.SURGE;
        if (od && !overdrive) {
            overdriveNext = 0f;
            s.toast("OVERDRIVE  -  belts run double, the gates flash", 0xFFFF8A30);
        }
        overdrive = od;
        if (od) {
            ev.bannerTitle = ev.bannerT > 0f ? "OVERDRIVE" : ev.bannerTitle;
            if (ev.elapsed() >= overdriveNext) {
                overdriveNext += 20f;
                gatePulse();
            }
        }
        if (ev.towerWon > lastTowerWon) { // the Cooling Tower is online
            lastTowerWon = ev.towerWon;
            lockUntil = (float) s.time + COOLING_LOCK;
            heat = 0f;
            RefineryOps.addPickup(s, Entities.K_CHEST, ev.tx, ev.ty + 52f, Sim.ARMS_CRATE);
            s.toast("COOLING TOWER ONLINE  -  heat cleared for 3 min, an Arms Crate", 0xFF4FD8FF);
        }
    }

    // ------------------------------------------------------------------ enemies

    @Override
    public void initArch(Enemy e) {
        if (e.type.arch == EnemyDef.ARCH_STOKER) {
            EnemyDef d = e.type.def;
            float a = d.param("gapMin", 2f), b = d.param("gapMax", 6f);
            e.actT = a + rr.nextFloat() * (b - a);
        }
    }

    @Override
    public boolean updateArch(Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        if (e.type.arch != EnemyDef.ARCH_STOKER) return false; // the Ironclad just walks
        int before = e.ai;
        RefineryOps.charger(s, e, dt, moving, dx, dy, d);
        if (before == A_DASH && e.ai == A_REST && RefineryOps.hazardRoom(s, Events.HAZ_RESERVE)) { // the dash ends in a fire patch
            RefineryOps.hazard(s, Entities.HZ_SLAG, e.x, e.y + 4f, e.type.def.param("fireR", 40f), e.type.def.param("fireT", 3f),
                    e.type.def.param("fireDps", 6f), 0f);
        }
        return true;
    }

    @Override
    public float damageIn(Enemy e, float amount, int category) {
        int arch = e.type.arch;
        if (arch == EnemyDef.ARCH_STOKER && category == CAT_BURN) return 0f;
        if (arch == EnemyDef.ARCH_IRONCLAD) {
            if (category == CAT_HIT) amount *= 0.5f;
            else if (category == CAT_BURN || category == CAT_ELECTRIFY || category == CAT_DECAY) amount *= 1.5f;
        }
        if (e == finale && s.time < shockUntil) amount *= SHOCK_MUL;
        return amount;
    }

    @Override
    public void onEnemyDeath(Enemy e) {
        if (e == finale) {
            finale = null;
            beltMode = BELTS_NORMAL;
            vgN = 0; // the win itself is Sim.bossRewards (Sim.isFinale)
        }
    }

    @Override
    public String spawnSwap(EnemyType t) {
        if (t.arch != EnemyDef.ARCH_IRONCLAD) return null;
        int n = 0;
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].type.arch == EnemyDef.ARCH_IRONCLAD && !s.enemies[i].dead) n++;
        return n >= IRONCLAD_CAP ? "COLONIST" : null;
    }

    // ------------------------------------------------------------------ finale support (IronheartAi)

    /** The Ironheart exists: belts, gates and shock are tied to this enemy from now on. */
    public void setFinale(Enemy e) {
        finale = e;
    }

    public void thermalShock(Enemy e) {
        e.stun = Math.max(e.stun, SHOCK_STUN); // the full time, not the boss share
        shockUntil = s.time + SHOCK_VULN;
        RefineryOps.fx(s, Entities.FX_SLAM, e.x, e.y, 150f, 0f, 0.5f);
        RefineryOps.spark(s, e.x, e.y - 20f, 0xFF9FF0FF, 16);
        s.toast("THERMAL SHOCK  -  the Ironheart is stunned and brittle", 0xFF9FF0FF);
        s.sound.emit(SoundEvents.FROST_HIT);
    }

    public boolean shocked() {
        return s.time < shockUntil;
    }

    /** Phase 3: a ring of gates around (cx, cy), radius {@link #VGATE_RING}. */
    public void openGateRing(float cx, float cy) {
        vgN = MAXVG;
        for (int k = 0; k < MAXVG; k++) {
            double a = k * Math.PI * 2 / MAXVG;
            vgX[k] = cx + (float) Math.cos(a) * VGATE_RING;
            vgY[k] = cy + (float) Math.sin(a) * VGATE_RING;
        }
    }

    public void closeGateRing() {
        vgN = 0;
    }

    public void setBelts(int mode, float x, float y) {
        beltMode = mode;
        beltX = x;
        beltY = y;
    }

    /** Coolant valves around the plaza of the finale (so Thermal Shock can be set up); they join the destructibles at once and on every refresh. */
    public void placeFinaleValves(float cx, float cy) {
        fvN = 3;
        for (int k = 0; k < fvN; k++) {
            double a = 0.6 + k * Math.PI * 2 / 3.0;
            fvX[k] = cx + (float) Math.cos(a) * 210f;
            fvY[k] = cy + (float) Math.sin(a) * 210f;
            RefineryOps.addDest(s, fvX[k], fvY[k], DK_VALVE_HEAVY, 0x5F00000000000000L ^ k);
        }
    }

    // ------------------------------------------------------------------ destructibles

    @Override
    public void addDests(int cx, int cy) {
        float x0 = (cx - 2) * DEST_CELL, y0 = (cy - 2) * DEST_CELL, x1 = (cx + 3) * DEST_CELL, y1 = (cy + 3) * DEST_CELL;
        int c0x = StreetsLayout.cell((int) Math.floor(x0 / WorldGen.TILE)), c1x = StreetsLayout.cell((int) Math.floor(x1 / WorldGen.TILE));
        int c0y = StreetsLayout.cell((int) Math.floor(y0 / WorldGen.TILE)), c1y = StreetsLayout.cell((int) Math.floor(y1 / WorldGen.TILE));
        for (int j = c0y; j <= c1y; j++) {
            for (int i = c0x; i <= c1x; i++) {
                if (StreetsLayout.valve(i, j, tmp)) put(tmp[0], tmp[1], DK_COOLANT, 0x5100000000000000L ^ ((long) i << 28) ^ (j & 0xFFFFFFFL), x0, y0, x1, y1);
                if (StreetsLayout.fuelTank(i, j, tmp)) put(tmp[0], tmp[1], DK_FUEL, 0x5200000000000000L ^ ((long) i << 28) ^ (j & 0xFFFFFFFL), x0, y0, x1, y1);
            }
        }
        for (int k = 0; k < fvN; k++) {
            if (fvX[k] >= x0 && fvX[k] < x1 && fvY[k] >= y0 && fvY[k] < y1) RefineryOps.addDest(s, fvX[k], fvY[k], DK_VALVE_HEAVY, 0x5F00000000000000L ^ k);
        }
    }

    private void put(float x, float y, int kind, long key, float x0, float y0, float x1, float y1) {
        if (x < x0 || x >= x1 || y < y0 || y >= y1) return;
        if (x * x + y * y < DEST_CLEAR * DEST_CLEAR) return; // the start stays clear
        RefineryOps.addDest(s, x, y, kind, key);
    }

    /** Set by the Ironheart's slam while it breaks a valve: only then does an armoured valve give. */
    public boolean slamBreaks;

    @Override
    public boolean destHit(int kind, long key, float dmg) {
        return kind == DK_VALVE_HEAVY && !slamBreaks;
    }

    @Override
    public boolean destBroken(int kind, float x, float y, long key) {
        if (kind == DK_COOLANT || kind == DK_VALVE_HEAVY) { // vents a fog: heat to 0 inside, Frost on the enemies
            int slot = 0;
            for (int i = 0; i < MAXF; i++) {
                if (fogT[i] <= 0f) { slot = i; break; }
                if (fogT[i] < fogT[slot]) slot = i;
            }
            fogX[slot] = x;
            fogY[slot] = y;
            fogT[slot] = FOG_LIFE;
            s.sound.emit(SoundEvents.CRATE_BREAK);
            RefineryOps.fx(s, Entities.FX_SPORE, x, y, FOG_R, 0f, 0.5f);
            RefineryOps.spark(s, x, y - 10f, 0xFF9FF0FF, 10);
            return true;
        }
        if (kind == DK_FUEL) {
            s.sound.emit(SoundEvents.BARREL_EXPLODE);
            RefineryOps.fx(s, Entities.FX_FLASH, x, y - 10f, 230f, 0f, 0.4f);
            RefineryOps.fx(s, Entities.FX_BLAST, x, y, FUEL_R * 0.6f, 1f, 0.35f);
            RefineryOps.spark(s, x, y - 10f, 0xFFFFA030, 18);
            RefineryOps.shake(s, 0.2f);
            int n = RefineryOps.query(s, x, y, FUEL_R + 40f);
            for (int k = 0; k < n; k++) {
                Enemy e = RefineryOps.queried(s, k);
                float dx = e.x - x, dy = e.y - y, rr2 = FUEL_R + e.radius * 0.5f;
                float d2 = dx * dx + dy * dy;
                if (e.dead || d2 >= rr2 * rr2) continue;
                float dd = (float) Math.sqrt(d2) + 0.01f;
                s.hurt(e, FUEL_DMG, 0f, 1f, dx / dd, dy / dd, 1.4f);
            }
            float hx = s.px - x, hy = s.py - y;
            if (hx * hx + hy * hy < FUEL_R * FUEL_R && s.invuln <= 0f) RefineryOps.hurtHero(s, Math.max(1f, FUEL_HERO - (float) s.stats.armor), 0.5f, Sim.DMG_BLAST);
            if (RefineryOps.hazardRoom(s, Events.HAZ_RESERVE)) RefineryOps.hazard(s, Entities.HZ_SLAG, x, y + 4f, 60f, FUEL_FIRE_T, 5f, 0f);
            RefineryOps.hitDest(s, x, y, FUEL_R - DEST_R); // other barrels and tanks in range chain
            return true;
        }
        return false;
    }
}
