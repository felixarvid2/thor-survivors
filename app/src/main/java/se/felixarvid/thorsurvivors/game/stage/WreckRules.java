package se.felixarvid.thorsurvivors.game.stage;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.data.EquipDef;
import se.felixarvid.thorsurvivors.game.Entities;
import se.felixarvid.thorsurvivors.game.Entities.EProj;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Entities.Pickup;
import se.felixarvid.thorsurvivors.game.Events;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.SoundEvents;
import se.felixarvid.thorsurvivors.game.WorldGen;
import se.felixarvid.thorsurvivors.game.WreckOps;
import se.felixarvid.thorsurvivors.game.layout.DebrisLayout;

import java.util.Random;

/**
 * Meridian Wreck (v1.4 chunk D, "The ship is still falling apart"). Everything the stage does that the other stages do not, all of it
 * allocation-free and drawing from its own {@code Random(seed ^ salt)}, never {@code Sim.rng}:
 * <ul>
 * <li><b>Teleporter pads</b> ({@code WorldGen.pad}, 1,800 u cells): stand on a pad for 0.5 s and you are sent to its twin; both
 * ends then rest for 12 s (6 s with the Echo of the Pillar, 3 s in the Power Surge) and you must step off a pad before it fires
 * again. Arrival sends out a shockwave (r 140, 30 damage x might, knockback). Enemies never use pads; they come back from the ring
 * spawn, so a jump buys a few seconds. That is all a pad does: no invulnerability, no pickups follow.</li>
 * <li><b>Gravity wells</b> ({@code WorldGen.well}, 1,400 u cells): r 220, 10 s on and 6 s off with a 1 s flicker before each change.
 * While on they pull the hero (40 u/s, not while dodging), enemies (70), pickups (120) and bend enemy shots. In the core (r 24)
 * enemies take 40 per second (elites 15, bosses none), gems are destroyed and the hero takes 15 per second. A pull never
 * overshoots the centre and does nothing within 1 u of it, so there is no NaN. A broken generator switches the nearest well off for 60 s.</li>
 * <li><b>Vacuum Breach</b> at 4:00, 9:00, 14:00, 19:00 and 24:00 (25 s, 2 s warning): a safe bubble of r 260 on the hero that shrinks
 * to 140 and drifts 40 u/s; outside it the hero takes 4 per second plus 0.5 per second of time outside and enemies 3% of their
 * maximum health per second (bosses are spared).</li>
 * <li><b>The Pillar Gate</b> (27:30 to 29:30): a big pad 250-350 u from the hero that carries it to the Pillar's arena; {@code PillarAi}
 * fights the finale, THE PILLAR (28:00). Killing it wins the run with double gold.</li>
 * <li><b>Rift Hound</b> (blinks to a flank, packs of 4) and <b>Breacher</b> (a warning line, then a dive that bursts; at most 8 alive).</li>
 * <li><b>Destructibles</b>: cargo crates (30% gold) and a generator by every well.</li>
 * </ul>
 * Parameters come from the stage's {@code rules} in ashfall_data.json (the defaults here are the same numbers).
 */
public final class WreckRules extends StageRules {
    public static final int MAX_PADS = 48, MAX_WELLS = 32, MAX_TEMP = 4;
    /** Pad kinds: a pair end, the Pillar Gate, the one-way exit inside the arena. */
    public static final int PK_PAIR = 0, PK_GATE = 1, PK_EXIT = 2;
    /** Destructible kinds of this stage ({@code Sim.DK_STAGE_BASE} + 8 and + 9). */
    public static final int DK_GENERATOR = Sim.DK_STAGE_BASE + 8, DK_CRATE = Sim.DK_STAGE_BASE + 9;
    /** The Vacuum Breaches (run seconds). */
    public static final float[] BREACH_AT = {240f, 540f, 840f, 1140f, 1440f};
    public static final float PAD_CELL = DebrisLayout.PAD_CELL, WELL_CELL = DebrisLayout.WELL_CELL;
    private static final long SALT = 0x57EC4D0A1B2C3D4FL;
    private static final float TAU = 6.2831855f;
    private static final float GATE_CHANNEL = 0.4f, GATE_R = 62f, CRATE_CELL = 500f;
    private static final String PILLAR_KEY = "THE_PILLAR";

    // ---- parameters (see StageDef.rules)
    private float padR, padChannel, padCooldown, shockR, shockDmg;
    private float wellOnT, wellOffT, pullHero, pullEnemy, pullPickup, coreR, coreEnemyDps, coreEliteDps, coreHeroDps;
    private float breachLen, breachTele, breachR0, breachR1, breachDrift, breachHeroDps, breachRamp, breachEnemyPct;
    private double gateAt, gateUntil;
    private boolean ready;
    private Random rr = new Random();
    private final float[] tmp4 = new float[4], tmp3 = new float[3];

    // ---- pads (index 0..padN-1; the specials come last)
    public final float[] padX = new float[MAX_PADS], padY = new float[MAX_PADS], padCd = new float[MAX_PADS];
    public final int[] padKind = new int[MAX_PADS], padTwin = new int[MAX_PADS];
    public int padN;
    /** Seconds the hero has stood on pad {@link #padChanIdx} (-1 none), and the pad he must step off before it fires again (-1 none). */
    public float padChan;
    public int padChanIdx = -1, padLock = -1;
    private int padCx = Integer.MIN_VALUE, padCy;
    private final float[] oldX = new float[MAX_PADS], oldY = new float[MAX_PADS], oldCd = new float[MAX_PADS];
    /** Pads used by the hero this run (tests, result screen). */
    public int teleports;

    // ---- gravity wells: the cell wells, then the short-lived ones (Alpha's stomp, the Pillar's orbit)
    public final float[] wellX = new float[MAX_WELLS], wellY = new float[MAX_WELLS], wellR = new float[MAX_WELLS], wellPhase = new float[MAX_WELLS];
    public int wellN;
    private int wellCx = Integer.MIN_VALUE, wellCy;
    public final float[] tmpX = new float[MAX_TEMP], tmpY = new float[MAX_TEMP], tmpR = new float[MAX_TEMP], tmpLife = new float[MAX_TEMP];
    private int tmpNext;
    // generators that were broken: the well they switched off (position) and until when
    private final float[] offX = new float[8], offY = new float[8];
    private final double[] offUntil = new double[8];
    private int offHead;
    private float coreAcc;
    /** Hero seconds in a core, enemies killed by cores, gems destroyed by cores (tests). */
    public int coreKills, gemsDestroyed;

    // ---- the bubble (a breach, or the Pillar's phase 3)
    public boolean bubbleOn;
    public float bubbleX, bubbleY, bubbleR, bubbleT;
    /** Seconds until the next breach begins while its warning shows (0 = no warning), the breach index running (-1 none). */
    public float breachWarn;
    public int breachIdx = -1;
    private float bubbleDx, bubbleDy, outT, bubAcc;
    private boolean bubbleByBoss;
    /** Hero HP lost to the bubble this run, and the number of breaches that began (tests). */
    public float bubbleDamage;
    public int breaches;

    // ---- the finale
    public boolean gateOn;
    public float gateX, gateY;
    private float gateChan;
    private boolean gateWasOn, gateLock;
    public float gateBannerT;
    public int pillarId;
    private double pillarDeadAt = -1;
    public boolean pillarDead;
    /** Where the gate sends the hero (the arena's start-side edge) and where the exit pad sends him (far off, start side). */
    public float arenaX, arenaY, exitX, exitY, exitDestX, exitDestY;
    private boolean eventsRenamed;
    private int renamedFor = -1;

    // ---- enemy archetypes
    /** Rift Hounds come in packs of 4 (off in tests that count enemies); tests also switch the pads and the wells off to isolate one system. */
    public boolean packs = true, padsOn = true, wellsOn = true, wellsFixed;
    private boolean spawningMates;
    /** Rift hounds that blinked, breachers that burst (tests). */
    public int blinks, bursts;

    /** A number in [0, 1) from the stage's own generator (bosses of the family use it so a seeded run keeps the main generator's sequence). */
    public float rand() {
        return rr.nextFloat();
    }

    /** The rules of a Wreck run, or null on another stage. */
    public static WreckRules of(Sim s) {
        return s.rules instanceof WreckRules ? (WreckRules) s.rules : null;
    }

    @Override
    public void init(Sim sim) {
        super.init(sim);
        rr = new Random(sim.runSeed ^ SALT);
        padR = (float) def.rule("padR", 70);
        padChannel = (float) def.rule("padChannel", 0.5);
        padCooldown = (float) def.rule("padCooldown", 12);
        shockR = (float) def.rule("padShockR", 140);
        shockDmg = (float) def.rule("padShockDmg", 30);
        wellOnT = (float) def.rule("wellOn", 10);
        wellOffT = (float) def.rule("wellOff", 6);
        pullHero = (float) def.rule("wellPullHero", 40);
        pullEnemy = (float) def.rule("wellPullEnemy", 70);
        pullPickup = (float) def.rule("wellPullPickup", 120);
        coreR = (float) def.rule("coreR", 24);
        coreEnemyDps = (float) def.rule("coreEnemyDps", 40);
        coreEliteDps = (float) def.rule("coreEliteDps", 15);
        coreHeroDps = (float) def.rule("coreHeroDps", 15);
        breachLen = (float) def.rule("breachLen", 25);
        breachTele = (float) def.rule("breachTele", 2);
        breachR0 = (float) def.rule("breachR0", 260);
        breachR1 = (float) def.rule("breachR1", 140);
        breachDrift = (float) def.rule("breachDrift", 40);
        breachHeroDps = (float) def.rule("breachHeroDps", 4);
        breachRamp = (float) def.rule("breachRamp", 0.5);
        breachEnemyPct = (float) def.rule("breachEnemyPct", 0.03);
        gateAt = def.rule("gateAt", 1650);
        gateUntil = def.rule("gateUntil", 1770);
        float px = DebrisLayout.pillarX(), py = DebrisLayout.pillarY();
        float m = (float) Math.sqrt(px * px + py * py);
        float ux = -px / m, uy = -py / m; // from the Pillar toward the start
        arenaX = px + ux * (DebrisLayout.ARENA_R - 180f);
        arenaY = py + uy * (DebrisLayout.ARENA_R - 180f);
        float ca = (float) Math.cos(1.0), sa = (float) Math.sin(1.0);
        exitX = px + (ux * ca - uy * sa) * (DebrisLayout.ARENA_R - 120f);
        exitY = py + (ux * sa + uy * ca) * (DebrisLayout.ARENA_R - 120f);
        exitDestX = px + ux * 1500f;
        exitDestY = py + uy * 1500f;
        ready = true;
    }

    // ------------------------------------------------------------------ queries (renderer, tests)

    /** Pad cooldown the hero's pads get now: 12 s, 6 s with the Echo of the Pillar, 3 s in the Power Surge. */
    public float currentPadCooldown() {
        if (s.events.is(Events.SURGE)) return 3f;
        return echoWorn() ? Math.min(padCooldown, 6f) : padCooldown;
    }

    public boolean echoWorn() {
        for (int i = 0; i < EquipDef.SLOTS; i++) {
            EquipDef d = s.equip.def[i];
            if (d != null && d.key.equals("ECHO_OF_THE_PILLAR")) return true;
        }
        return false;
    }

    /** True while the Power Surge (the Lumen Surge event) runs: wells always on, pads recharge in 3 s, the lights flicker. */
    public boolean surge() {
        return s.events.is(Events.SURGE);
    }

    private double wellPeriod() {
        return wellOnT + wellOffT;
    }

    /** Seconds into well i's cycle (0 .. period). */
    private float cycleT(int i) {
        return (float) ((s.time + wellPhase[i]) % wellPeriod());
    }

    private boolean disabled(float x, float y) {
        for (int k = 0; k < offX.length; k++) {
            if (offUntil[k] > s.time && Math.abs(offX[k] - x) < 1f && Math.abs(offY[k] - y) < 1f) return true;
        }
        return false;
    }

    /** True when well i of the table pulls now (on in its cycle, always in the Power Surge, never while a generator holds it off). */
    public boolean wellOn(int i) {
        if (disabled(wellX[i], wellY[i])) return false;
        return surge() || cycleT(i) < wellOnT;
    }

    /** True while well i flickers (the last second before it turns on or off); the renderer blinks it. */
    public boolean wellFlicker(int i) {
        if (surge() || disabled(wellX[i], wellY[i])) return false;
        float t = cycleT(i);
        return t >= wellOnT + wellOffT - 1f || (t >= wellOnT - 1f && t < wellOnT);
    }

    /** True when well i is held off by a broken generator. */
    public boolean wellHeldOff(int i) {
        return disabled(wellX[i], wellY[i]);
    }

    /** Seconds the hero has been outside the bubble (the red edge of the screen follows it). */
    public float outsideSeconds() {
        return outT;
    }

    /** Radius in which the hero stands "on" a pad (the teleporter prop is about 180 u across). */
    public float padRadius() {
        return padR;
    }

    /** Seconds each pad of the table rests for at most (the renderer scales the recharge arc by it). */
    public float padRestMax() {
        return Math.max(padCooldown, 1f);
    }

    /** True when (x, y) lies inside the running bubble. */
    public boolean inBubble(float x, float y) {
        float dx = x - bubbleX, dy = y - bubbleY;
        return dx * dx + dy * dy < bubbleR * bubbleR;
    }

    // ------------------------------------------------------------------ update

    @Override
    public void update(float dt) {
        if (!ready) return;
        rebuildWells();
        rebuildPads();
        renameEvents();
        updateFinale(dt);
        if (padsOn) stepPads(dt);
        if (wellsOn) stepWells(dt);
        stepBubble(dt);
        if (gateBannerT > 0f) gateBannerT -= dt;
        if (pillarDeadAt >= 0 && s.time - pillarDeadAt >= 2.2 && s.ended == 0) s.ended = 2; // The Pillar fell: the run is won (gold x2)
    }

    // ------------------------------------------------------------------ pads

    private void rebuildPads() {
        int cx = (int) Math.floor(s.px / PAD_CELL), cy = (int) Math.floor(s.py / PAD_CELL);
        if (cx == padCx && cy == padCy && !specialsDirty) return;
        padCx = cx;
        padCy = cy;
        specialsDirty = false;
        int on = padN;
        for (int i = 0; i < on; i++) {
            oldX[i] = padX[i];
            oldY[i] = padY[i];
            oldCd[i] = padCd[i];
        }
        float lockX = padLock >= 0 ? padX[padLock] : 0f, lockY = padLock >= 0 ? padY[padLock] : 0f;
        boolean hadLock = padLock >= 0;
        padN = 0;
        outer:
        for (int j = cy - 2; j <= cy + 2; j++) {
            for (int i = cx - 2; i <= cx + 2; i++) {
                if (!s.map.pad(i, j, tmp4)) continue;
                if (padN + 2 > MAX_PADS - 2) break outer;
                addPad(tmp4[0], tmp4[1], PK_PAIR, padN + 1);
                addPad(tmp4[2], tmp4[3], PK_PAIR, padN - 1);
            }
        }
        if (gateOn) addPad(gateX, gateY, PK_GATE, -1);
        if (pillarId != 0 && !pillarDead) addPad(exitX, exitY, PK_EXIT, -1);
        for (int i = 0; i < padN; i++) { // cooldowns survive a rebuild (a pad is a pure function of its cell, so the same coordinates are the same pad)
            for (int k = 0; k < on; k++) {
                if (Math.abs(oldX[k] - padX[i]) < 0.5f && Math.abs(oldY[k] - padY[i]) < 0.5f) {
                    padCd[i] = oldCd[k];
                    break;
                }
            }
        }
        padLock = -1;
        if (hadLock) {
            for (int i = 0; i < padN; i++) {
                if (Math.abs(padX[i] - lockX) < 0.5f && Math.abs(padY[i] - lockY) < 0.5f) padLock = i;
            }
        }
        padChanIdx = -1;
        padChan = 0f;
    }

    private boolean specialsDirty;

    private void addPad(float x, float y, int kind, int twin) {
        padX[padN] = x;
        padY[padN] = y;
        padKind[padN] = kind;
        padTwin[padN] = twin;
        padCd[padN++] = 0f;
    }

    private void stepPads(float dt) {
        for (int i = 0; i < padN; i++) if (padCd[i] > 0f) padCd[i] -= dt;
        if (padLock >= 0) { // after a jump the hero stands on the twin: it fires again only after he stepped off
            float dx = s.px - padX[padLock], dy = s.py - padY[padLock], r = padR + 14f;
            if (dx * dx + dy * dy > r * r) padLock = -1;
        }
        int cand = -1;
        for (int i = 0; i < padN; i++) {
            if (padCd[i] > 0f || i == padLock) continue;
            float dx = s.px - padX[i], dy = s.py - padY[i], r = padKind[i] == PK_GATE ? GATE_R : padR;
            if (dx * dx + dy * dy < r * r) {
                cand = i;
                break;
            }
        }
        if (cand < 0 || s.ended != 0) {
            padChanIdx = -1;
            padChan = 0f;
            return;
        }
        if (padChanIdx != cand) {
            padChanIdx = cand;
            padChan = 0f;
        }
        padChan += dt;
        if (padChan >= (padKind[cand] == PK_GATE ? GATE_CHANNEL : padChannel)) teleport(cand);
    }

    /** The hero is sent along pad {@code i}: to its twin, to the arena, or out of it; arrival shockwave, both ends rest. */
    public void teleport(int i) {
        float tx, ty;
        int twin = padTwin[i];
        switch (padKind[i]) {
            case PK_GATE:
                tx = arenaX;
                ty = arenaY;
                break;
            case PK_EXIT:
                tx = exitDestX;
                ty = exitDestY;
                break;
            default:
                tx = padX[twin];
                ty = padY[twin];
        }
        WreckOps.fx(s, Entities.FX_BLINK, s.px, s.py, 90f, 0f, 0.4f);
        s.px = tx;
        s.py = ty;
        s.sound.emit(SoundEvents.VIRE_BLINK);
        float cd = currentPadCooldown();
        padCd[i] = cd;
        if (padKind[i] == PK_PAIR && twin >= 0) {
            padCd[twin] = cd;
            padLock = twin;
        } else {
            padLock = -1;
        }
        padChanIdx = -1;
        padChan = 0f;
        teleports++;
        arrivalShock(tx, ty);
    }

    /** The shockwave at an arrival: r 140 (+50% damage with the Echo), 30 damage x might and a knockback to every enemy in it. */
    public void arrivalShock(float x, float y) {
        float dmg = shockDmg * (float) s.stats.might * (echoWorn() ? 1.5f : 1f);
        for (int k = 0; k < s.enemyN; k++) {
            Enemy e = s.enemies[k];
            if (e.dead || e.dormant) continue;
            float dx = e.x - x, dy = e.y - y, rr2 = shockR + e.radius * 0.5f;
            float d2 = dx * dx + dy * dy;
            if (d2 >= rr2 * rr2) continue;
            float d = (float) Math.sqrt(d2) + 0.01f;
            s.hurt(e, dmg, 0f, 1f, dx / d, dy / d, 2.0f);
        }
        WreckOps.fx(s, Entities.FX_SLAM, x, y, shockR, 0f, 0.5f);
        WreckOps.spark(s, x, y - 8f, 0xFF4FD8FF, 14);
        WreckOps.shake(s, 0.2f);
    }

    // ------------------------------------------------------------------ gravity wells

    private void rebuildWells() {
        int cx = (int) Math.floor(s.px / WELL_CELL), cy = (int) Math.floor(s.py / WELL_CELL);
        if (wellsFixed || cx == wellCx && cy == wellCy) return; // wellsFixed: a test froze the table
        wellCx = cx;
        wellCy = cy;
        wellN = 0;
        for (int j = cy - 2; j <= cy + 2; j++) {
            for (int i = cx - 2; i <= cx + 2; i++) {
                if (wellN >= MAX_WELLS || !s.map.well(i, j, tmp3)) continue;
                wellX[wellN] = tmp3[0];
                wellY[wellN] = tmp3[1];
                wellR[wellN] = tmp3[2];
                wellPhase[wellN++] = WorldGen.rnd(i, j, 933) * (float) wellPeriod();
            }
        }
    }

    /** A short-lived well (r, seconds): Hull Breacher Alpha's stomp. Replaces the oldest of the two slots. */
    public void addTempWell(float x, float y, float r, float life) {
        int k = tmpNext;
        tmpNext = (tmpNext + 1) % 2;
        tmpX[k] = x;
        tmpY[k] = y;
        tmpR[k] = r;
        tmpLife[k] = life;
    }

    /** The Pillar's two orbiting wells (slots 2 and 3), opposite each other round (cx, cy); off clears them. */
    public void setOrbit(boolean on, float cx, float cy, float dist, float ang, float r) {
        for (int k = 0; k < 2; k++) {
            int slot = 2 + k;
            if (!on) {
                tmpLife[slot] = 0f;
                continue;
            }
            float a = ang + k * 3.1415927f;
            tmpX[slot] = cx + (float) Math.cos(a) * dist;
            tmpY[slot] = cy + (float) Math.sin(a) * dist;
            tmpR[slot] = r;
            tmpLife[slot] = 1f; // renewed every step by the boss
        }
    }

    private void stepWells(float dt) {
        for (int k = 0; k < 2; k++) if (tmpLife[k] > 0f) tmpLife[k] -= dt; // the orbit slots live as long as the boss renews them
        for (int k = 2; k < MAX_TEMP; k++) if (tmpLife[k] > 0f) tmpLife[k] -= dt;
        coreAcc += dt;
        boolean tick = coreAcc >= 0.1f;
        if (tick) coreAcc -= 0.1f;
        if (coreAcc > 0.1f) coreAcc = 0f;
        for (int i = 0; i < wellN; i++) if (wellOn(i)) applyWell(wellX[i], wellY[i], wellR[i], dt, tick);
        for (int k = 0; k < MAX_TEMP; k++) if (tmpLife[k] > 0f) applyWell(tmpX[k], tmpY[k], tmpR[k], dt, tick);
    }

    /** One running well for one step: pulls, core damage (on the 10 Hz tick) and the bend of enemy shots. */
    private void applyWell(float x, float y, float r, float dt, boolean tick) {
        float r2 = r * r, core2 = coreR * coreR;
        float hx = x - s.px, hy = y - s.py;
        float hd2 = hx * hx + hy * hy;
        if (hd2 < r2) {
            if (s.dodgeT <= 0f && s.ended == 0 && hd2 >= 1f) { // a dodge ignores the pull; the centre itself pulls nothing
                float d = (float) Math.sqrt(hd2), step = Math.min(pullHero * dt, d);
                s.px += hx / d * step;
                s.py += hy / d * step;
            }
            if (hd2 < core2 && s.ended == 0) WreckOps.areaDamage(s, coreHeroDps * dt);
        }
        if (hd2 > (r + 1400f) * (r + 1400f)) return; // nothing alive out there
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (e.dead || e.dormant || e.sleeping) continue;
            float dx = x - e.x, dy = y - e.y, d2 = dx * dx + dy * dy;
            if (d2 >= r2) continue;
            if (d2 >= 1f) {
                float d = (float) Math.sqrt(d2), step = Math.min(pullEnemy * (e.boss ? 0.2f : 1f) * dt, d);
                e.x += dx / d * step;
                e.y += dy / d * step;
            }
            if (tick && d2 < core2 && !e.boss) {
                WreckOps.dot(s, e, (e.elite ? coreEliteDps : coreEnemyDps) * 0.1f);
                if (e.dead) coreKills++;
            }
        }
        for (int i = s.pickN - 1; i >= 0; i--) {
            Pickup p = s.picks[i];
            if (p.kind > Entities.K_ROAST) continue; // gems, coins, bags, food: not the chests and power-ups
            float dx = x - p.x, dy = y - p.y, d2 = dx * dx + dy * dy;
            if (d2 >= r2) continue;
            if (p.kind == Entities.K_GEM && !p.red && d2 < core2) { // a gem that falls into the core is gone
                Pickup last = s.picks[s.pickN - 1];
                s.picks[i] = last;
                s.picks[s.pickN - 1] = p;
                s.pickN--;
                gemsDestroyed++;
                continue;
            }
            if (d2 >= 1f) {
                float d = (float) Math.sqrt(d2), step = Math.min(pullPickup * dt, d);
                p.x += dx / d * step;
                p.y += dy / d * step;
            }
        }
        for (int i = 0; i < s.eprojN; i++) {
            EProj p = s.eprojs[i];
            float dx = x - p.x, dy = y - p.y, d2 = dx * dx + dy * dy;
            if (d2 >= r2 || d2 < 1f) continue;
            float d = (float) Math.sqrt(d2);
            p.vx += dx / d * 150f * dt; // enemy shots curve slightly toward the centre
            p.vy += dy / d * 150f * dt;
        }
    }

    // ------------------------------------------------------------------ the Vacuum Breach

    private void stepBubble(float dt) {
        if (!bubbleByBoss) scheduleBreach(dt);
        if (!bubbleOn) {
            outT = Math.max(0f, outT - dt * 2f);
            return;
        }
        bubbleT += dt;
        if (s.ended == 0) {
            float dx = s.px - bubbleX, dy = s.py - bubbleY;
            if (dx * dx + dy * dy >= bubbleR * bubbleR) { // outside: 4 per second, +0.5 per second of time out here
                outT += dt;
                float dmg = (breachHeroDps + breachRamp * outT) * dt;
                bubbleDamage += dmg * (float) s.stats.damageTaken;
                WreckOps.areaDamage(s, dmg);
            } else {
                outT = Math.max(0f, outT - dt * 2f);
            }
        }
        bubAcc += dt;
        if (bubAcc >= 0.1f) { // 10 Hz: the enemies outside lose a share of their health (a bubble of enemies is a bubble of kills)
            bubAcc -= 0.1f;
            if (bubAcc > 0.1f) bubAcc = 0f;
            float r2 = bubbleR * bubbleR;
            for (int i = 0; i < s.enemyN; i++) {
                Enemy e = s.enemies[i];
                if (e.dead || e.boss || e.dormant) continue;
                float dx = e.x - bubbleX, dy = e.y - bubbleY;
                if (dx * dx + dy * dy < r2) continue;
                WreckOps.dot(s, e, Math.min(e.maxHp * breachEnemyPct * 0.1f, 40f));
            }
        }
    }

    private void scheduleBreach(float dt) {
        double t = s.time;
        int idx = -1;
        for (int i = 0; i < BREACH_AT.length; i++) {
            if (t >= BREACH_AT[i] - breachTele && t < BREACH_AT[i] + breachLen) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            if (bubbleOn) { // the breach ends: the bulkheads seal
                bubbleOn = false;
                breachIdx = -1;
                s.toast("BREACH SEALED", 0xFF39FF88);
            }
            breachWarn = 0f;
            return;
        }
        if (t < BREACH_AT[idx]) { // the warning: siren, red edge, a faint ring
            if (breachWarn <= 0f) {
                s.toast("VACUUM BREACH  -  get inside the bubble", 0xFFFF5A4A);
                s.sound.emit(SoundEvents.BOSS_INTRO);
            }
            breachWarn = (float) (BREACH_AT[idx] - t);
            if (!bubbleOn) { // the ring shows already, on the hero
                bubbleX = s.px;
                bubbleY = s.py;
                bubbleR = breachR0;
            }
            return;
        }
        breachWarn = 0f;
        if (!bubbleOn || breachIdx != idx) { // the breach opens on the hero's position (also when a test jumps the clock into the window)
            bubbleOn = true;
            breachIdx = idx;
            breaches++;
            bubbleX = s.px;
            bubbleY = s.py;
            outT = 0f;
            Random dr = new Random(s.runSeed ^ SALT ^ (idx * 0x9E3779B97F4A7C15L));
            float a = dr.nextFloat() * TAU;
            bubbleDx = (float) Math.cos(a) * breachDrift;
            bubbleDy = (float) Math.sin(a) * breachDrift;
            bubbleT = (float) (t - BREACH_AT[idx]);
        }
        float k = Math.min(1f, bubbleT / breachLen);
        bubbleR = breachR0 + (breachR1 - breachR0) * k;
        bubbleX += bubbleDx * dt;
        bubbleY += bubbleDy * dt;
    }

    /** The Pillar's third phase drives the bubble itself (centre, radius); {@code on} false hands it back to the schedule. */
    public void setArenaBubble(boolean on, float x, float y, float r) {
        bubbleByBoss = on;
        if (on) {
            if (!bubbleOn) {
                bubbleOn = true;
                outT = 0f;
                bubbleT = 0f;
            }
            bubbleX = x;
            bubbleY = y;
            bubbleR = r;
        } else if (bubbleOn && breachIdx < 0) {
            bubbleOn = false;
        }
    }

    // ------------------------------------------------------------------ the finale: gate, Pillar

    private void updateFinale(float dt) {
        double t = s.time;
        boolean want = t >= gateAt && t < gateUntil && !pillarDead;
        if (want && !gateOn) {
            placeGate();
            gateOn = true;
            gateBannerT = 4f;
            specialsDirty = true;
            s.toast("PILLAR GATE OPEN", 0xFF4FD8FF);
            s.sound.emit(SoundEvents.BOSS_INTRO);
        } else if (!want && gateOn) {
            gateOn = false;
            specialsDirty = true;
        }
        if (pillarId != 0 && !pillarDead) { // the exit pad inside the arena exists as long as the Pillar stands
            boolean has = false;
            for (int i = 0; i < padN; i++) if (padKind[i] == PK_EXIT) has = true;
            if (!has) specialsDirty = true;
            Enemy p = pillar();
            if (p == null) {
                pillarId = 0;
            } else {
                p.x = DebrisLayout.pillarX(); // the spire never moves (also right after a test spawned it elsewhere)
                p.y = DebrisLayout.pillarY(); // asleep while the hero is far (a sleeping enemy is not recycled toward him); awake inside 850 u
                float dx = s.px - p.x, dy = s.py - p.y, d2 = dx * dx + dy * dy;
                if (p.sleeping && d2 < 850f * 850f) p.sleeping = false;
                else if (!p.sleeping && d2 > 1000f * 1000f) p.sleeping = true;
            }
        }
    }

    private void placeGate() {
        Random g = new Random(s.runSeed ^ SALT ^ 0x6A7EL);
        for (int attempt = 0; attempt < 60; attempt++) {
            float a = g.nextFloat() * TAU, d = 250f + g.nextFloat() * 100f;
            float x = s.px + (float) Math.cos(a) * d, y = s.py + (float) Math.sin(a) * d;
            if (attempt < 59 && (s.map.blocked(x, y) || s.map.blocked(x + 40f, y) || s.map.blocked(x - 40f, y))) continue;
            gateX = x;
            gateY = y;
            return;
        }
    }

    /** The Pillar enemy of this run, or null. */
    public Enemy pillar() {
        if (pillarId == 0) return null;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (e.id == pillarId && !e.dead) return e;
        }
        return null;
    }

    /** Called by {@code PillarAi.init}: the finale boss exists. */
    public void pillarSpawned(Enemy e) {
        pillarId = e.id;
        specialsDirty = true;
    }

    @Override
    public void onEnemyDeath(Enemy e) {
        if (e.type.def.key.equals(PILLAR_KEY)) { // the finale: the run is won in a moment, gold counts twice
            pillarDead = true;
            pillarDeadAt = s.time;
            s.firstKilled = true;
            s.invuln = Math.max(s.invuln, 3f);
            s.screenFlash = Math.max(s.screenFlash, 0.5f);
            s.toast("THE PILLAR FALLS  -  GOLD x2", 0xFF39FF88);
            bubbleByBoss = false;
            setArenaBubble(false, 0f, 0f, 0f);
            setOrbit(false, 0f, 0f, 0f, 0f, 0f);
            gateOn = false;
            specialsDirty = true;
        }
    }

    // ------------------------------------------------------------------ events (names only: the logic is Events')

    /** The Signal Tower is the Distress Beacon here and the Lumen Surge the Power Surge: same rules, new names. */
    private void renameEvents() {
        int kind = s.events.active;
        if (kind != Events.TOWER && kind != Events.SURGE) {
            renamedFor = -1;
            return;
        }
        if (renamedFor == kind) return;
        renamedFor = kind;
        String from = kind == Events.TOWER ? "SIGNAL TOWER" : "LUMEN SURGE", to = kind == Events.TOWER ? "DISTRESS BEACON" : "POWER SURGE";
        if (from.equals(s.events.bannerTitle)) s.events.bannerTitle = to;
        for (int i = 0; i < Sim.MAX_TOASTS; i++) {
            String t = s.toastText[i];
            if (t != null && t.startsWith(from)) s.toastText[i] = to + t.substring(from.length());
        }
    }

    // ------------------------------------------------------------------ destructibles

    @Override
    public void addDests(int cx, int cy) {
        rebuildWells();
        for (int j = cy - 2; j <= cy + 2; j++) {
            for (int i = cx - 2; i <= cx + 2; i++) {
                if (WorldGen.rnd(i, j, 960) >= 0.30f) continue; // the many cargo crates
                float x = i * CRATE_CELL + 60f + WorldGen.rnd(i, j, 961) * (CRATE_CELL - 120f);
                float y = j * CRATE_CELL + 60f + WorldGen.rnd(i, j, 962) * (CRATE_CELL - 120f);
                if (x * x + y * y < 300f * 300f || s.map.blocked(x, y)) continue;
                s.addStageDest(x, y, DK_CRATE, cellKey(i, j) ^ 0x5A5A000000000000L);
            }
        }
        for (int w = 0; w < wellN; w++) { // a generator by every well (it switches that well off for a minute when broken)
            float a = WorldGen.rnd((int) wellX[w], (int) wellY[w], 963) * TAU;
            float x = wellX[w] + (float) Math.cos(a) * (wellR[w] + 70f), y = wellY[w] + (float) Math.sin(a) * (wellR[w] + 70f);
            int ccx = (int) Math.floor(x / CRATE_CELL), ccy = (int) Math.floor(y / CRATE_CELL);
            if (ccx < cx - 2 || ccx > cx + 2 || ccy < cy - 2 || ccy > cy + 2 || s.map.blocked(x, y)) continue;
            s.addStageDest(x, y, DK_GENERATOR, cellKey((int) wellX[w], (int) wellY[w]) ^ 0x6B6B000000000000L);
        }
    }

    private static long cellKey(int a, int b) {
        return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
    }

    @Override
    public void onDestBroken(int kind, float x, float y) {
        if (kind == DK_CRATE) {
            WreckOps.spark(s, x, y - 8f, 0xFFB08850, 8);
            WreckOps.fx(s, Entities.FX_FLASH, x, y - 6f, 70f, 0f, 0.2f);
            s.sound.emit(SoundEvents.CRATE_BREAK);
            if (rr.nextFloat() < 0.30f) { // 30% of the crates hold gold
                boolean bag = rr.nextFloat() < 0.2f;
                WreckOps.addPickup(s, bag ? Entities.K_COINBAG : Entities.K_COIN, x, y, bag ? 10f : 1f);
            }
            return;
        }
        if (kind != DK_GENERATOR) return;
        WreckOps.fx(s, Entities.FX_BLAST, x, y, 60f, 1f, 0.3f);
        WreckOps.spark(s, x, y - 10f, 0xFF4FD8FF, 14);
        s.sound.emit(SoundEvents.PYLON_USE);
        int best = -1;
        float bd = 700f * 700f;
        for (int i = 0; i < wellN; i++) {
            float dx = wellX[i] - x, dy = wellY[i] - y, d2 = dx * dx + dy * dy;
            if (d2 < bd) {
                bd = d2;
                best = i;
            }
        }
        if (best < 0) return;
        holdOff(wellX[best], wellY[best], 60.0);
        s.toast("GENERATOR DOWN  -  the well is off for 60 s", 0xFF4FD8FF);
    }

    /** Switches the well at (x, y) off for {@code seconds} (a broken generator; tests). */
    public void holdOff(float x, float y, double seconds) {
        offX[offHead] = x;
        offY[offHead] = y;
        offUntil[offHead] = s.time + seconds;
        offHead = (offHead + 1) % offX.length;
    }

    // ------------------------------------------------------------------ light

    /** The Power Surge flickers the lights (the Surge's own lift is added by Events). */
    @Override
    public float ambientLift() {
        if (!surge()) return 0f;
        double t = s.time;
        float f = (float) (Math.sin(t * 31.0) * Math.sin(t * 19.0 + 2.0));
        return f > 0.25f ? -0.28f : 0f;
    }

    // ------------------------------------------------------------------ enemy archetypes

    @Override
    public void initArch(Enemy e) {
        EnemyDef d = e.type.def;
        if (e.type.arch == EnemyDef.ARCH_RIFT) {
            e.actT = d.param("blinkEvery", 6f) * (0.3f + 0.9f * rr.nextFloat());
            e.weaveT = rr.nextFloat() * 6f;
            if (packs && !spawningMates && e.poi < 0 && !e.boss) { // a pack of four: three more beside the first
                int group = Math.round(d.param("group", 4f));
                spawningMates = true;
                for (int k = 1; k < group && s.enemyN < se.felixarvid.thorsurvivors.game.WaveDirector.ENEMY_CAP && WreckOps.roomFor(s, 1); k++) {
                    WreckOps.spawnMinion(s, d.key, e.x + (rr.nextFloat() - 0.5f) * 44f, e.y + (rr.nextFloat() - 0.5f) * 44f);
                }
                spawningMates = false;
            }
        } else if (e.type.arch == EnemyDef.ARCH_BREACHER) {
            e.actT = d.param("gapMin", 2.5f) + rr.nextFloat() * (d.param("gapMax", 5.5f) - d.param("gapMin", 2.5f));
            int cap = Math.round(d.param("cap", 8f)), alive = 0;
            for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].type == e.type && !s.enemies[i].dead && s.enemies[i] != e) alive++;
            if (alive >= cap) { // at most 8 alive: the ninth leaves again without a kill or drops
                e.dead = true;
                e.silent = true;
            }
        }
    }

    private static final int A_WALK = 0, A_TELE = 3, A_DASH = 4;

    @Override
    public boolean updateArch(Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        if (e.type.arch == EnemyDef.ARCH_RIFT) return riftHound(e, dt, moving, dx, dy, d);
        if (e.type.arch == EnemyDef.ARCH_BREACHER) return breacher(e, dt, moving, dx, dy, d);
        return false;
    }

    /** Rift Hound: a weaving chase; every ~6 s a 0.3 s shimmer, then a blink to 150-250 u from the hero at a flank (60-110 degrees off his facing). */
    private boolean riftHound(Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        EnemyDef def = e.type.def;
        if (e.ai == A_TELE) {
            e.clip = 0;
            e.tele -= dt;
            if ((e.aiT -= dt) <= 0f) {
                float fa = (float) Math.atan2(s.faceY, s.faceX), side = rr.nextBoolean() ? 1f : -1f;
                float a = fa + side * (1.05f + rr.nextFloat() * 0.87f), r = def.param("blinkMin", 150f) + rr.nextFloat() * (def.param("blinkMax", 250f) - def.param("blinkMin", 150f));
                e.x = s.px + (float) Math.cos(a) * r;
                e.y = s.py + (float) Math.sin(a) * r;
                WreckOps.fx(s, Entities.FX_BLINK, e.x, e.y, 60f, 0f, 0.3f);
                e.ai = A_WALK;
                e.tele = 0f;
                e.actT = def.param("blinkEvery", 6f) * (0.8f + 0.4f * rr.nextFloat());
                blinks++;
            }
            return true;
        }
        e.clip = 0;
        if (moving && d > 1f) {
            float sp = WreckOps.speedOf(s, e), w = (float) Math.sin(e.anim * 3.2f + e.id) * 0.55f;
            float ux = dx / d, uy = dy / d, mx = ux - uy * w, my = uy + ux * w, m = (float) Math.sqrt(mx * mx + my * my);
            e.x += mx / m * sp * dt;
            e.y += my / m * sp * dt;
        }
        if ((e.actT -= dt) <= 0f) { // even from the hero's heels: a blink to the flank is how it gets out of the melee
            e.ai = A_TELE;
            e.aiT = e.tele = e.teleMax = def.param("tele", 0.3f);
            WreckOps.fx(s, Entities.FX_BLINK, e.x, e.y, 60f, 0f, 0.3f);
        }
        return true;
    }

    /** Breacher: closes to about 300 u, shows a thin line for 1.0 s, dives along it and bursts (r 60, 20 damage); the burst kills it. */
    private boolean breacher(Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        EnemyDef def = e.type.def;
        switch (e.ai) {
            case A_TELE:
                e.clip = 0;
                e.tele -= dt;
                if ((e.aiT -= dt) <= 0f) {
                    e.ai = A_DASH;
                    e.trail = 0f;
                    e.tele = 0f;
                }
                return true;
            case A_DASH: {
                float step = def.param("diveSpeed", 380f) * dt;
                e.x += e.ax * step;
                e.y += e.ay * step;
                e.trail += step;
                float hx = s.px - e.x, hy = s.py - e.y, rr2 = e.radius * 0.8f + Sim.PLAYER_R * 0.8f + 4f;
                if (hx * hx + hy * hy < rr2 * rr2 || e.trail >= e.teleLen) burst(e, def);
                return true;
            }
            default:
        }
        e.clip = 0;
        if (moving && d > 1f) {
            float sp = WreckOps.speedOf(s, e), range = def.param("range", 300f);
            float f = d > range ? 1f : d < range * 0.6f ? -0.5f : 0.3f; // closes in, keeps its distance, waits for its moment
            e.x += dx / d * sp * f * dt;
            e.y += dy / d * sp * f * dt;
        }
        if (d < def.param("range", 300f) * 1.25f && (e.actT -= dt) <= 0f && moving) {
            e.ai = A_TELE;
            float m = Math.max(d, 1f);
            e.ax = dx / m;
            e.ay = dy / m;
            e.aiT = e.tele = e.teleMax = def.param("warn", 1.0f);
            e.teleLen = Math.min(def.param("diveLen", 340f), d + 80f);
            e.teleW = def.param("lineW", 14f);
        }
        return true;
    }

    private void burst(Enemy e, EnemyDef def) {
        float r = def.param("burstR", 60f);
        WreckOps.fx(s, Entities.FX_BLAST, e.x, e.y, r * 0.9f, 1f, 0.3f);
        WreckOps.spark(s, e.x, e.y - 8f, 0xFFE8C23A, 10);
        s.sound.emit(SoundEvents.CARRIER_BURST);
        float hx = s.px - e.x, hy = s.py - e.y, rr2 = r + Sim.PLAYER_R * 0.4f;
        if (hx * hx + hy * hy < rr2 * rr2 && s.invuln <= 0f && s.ended == 0) {
            WreckOps.hurtHero(s, Math.max(1f, def.param("burstDmg", 20f) * e.dmgMul - (float) s.stats.armor), 0.4f, Sim.DMG_AREA);
        }
        bursts++;
        WreckOps.killNow(s, e);
    }
}
