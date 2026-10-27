package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.StageDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.layout.StreetsLayout;
import se.felixarvid.thorsurvivors.game.stage.RefineryRules;
import se.felixarvid.thorsurvivors.game.stage.StageRules;

import java.util.HashMap;
import java.util.Random;

/** Refinery District (v1.4 chunk C): belts, gates, heat, Heat Wave, molten metal, the new enemies, the bosses, the events, a 30 minute soak. */
public class RefineryTest {
    private static final float DT = 1f / 60f;
    private static final float T = WorldGen.TILE;

    private static Sim run(long seed) throws Exception {
        GameData d = StageSoak.data();
        Sim s = new Sim(d, d.stages.get("REFINERY"), null, new HashMap<String, Double>(), "BASTION", seed);
        s.noSpawnsForTest();
        s.enemyN = 0;
        s.stats.regen = 0;
        return s;
    }

    private static RefineryRules rules(Sim s) {
        return (RefineryRules) s.rules;
    }

    private static void steps(Sim s, Input in, double seconds) {
        int n = (int) Math.round(seconds * 60);
        for (int i = 0; i < n; i++) {
            s.update(DT, in);
            s.sound.clear();
        }
    }

    /** Like {@link #steps} but the hero is topped up every step (a test about something else than survival). */
    private static void safe(Sim s, Input in, double seconds) {
        int n = (int) Math.round(seconds * 60);
        for (int i = 0; i < n; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
            s.sound.clear();
        }
    }

    /** A tile in the middle of a belt lane (3 wide) with belt on both sides along the street: {tx, ty}. */
    private static int[] findBelt(boolean vertical) {
        for (int ty = -80; ty <= 80; ty++) {
            for (int tx = -80; tx <= 80; tx++) {
                if (vertical ? StreetsLayout.local(tx) != 2 : StreetsLayout.local(ty) != 2) continue;
                int mask = vertical ? WorldGen.SURF_BELT_N | WorldGen.SURF_BELT_S : WorldGen.SURF_BELT_E | WorldGen.SURF_BELT_W;
                boolean ok = true;
                for (int k = -4; k <= 4 && ok; k++) {
                    int b = StreetsLayout.surfaceAt(vertical ? tx : tx + k, vertical ? ty + k : ty);
                    ok = (b & mask) != 0 && (b & (vertical ? WorldGen.SURF_BELT_E | WorldGen.SURF_BELT_W : WorldGen.SURF_BELT_N | WorldGen.SURF_BELT_S)) == 0;
                }
                if (ok) return new int[]{tx, ty};
            }
        }
        throw new AssertionError("no belt found");
    }

    private static int[] findTile(int bit, boolean centreOfThree) {
        for (int ty = -120; ty <= 120; ty++) {
            for (int tx = -120; tx <= 120; tx++) {
                if ((StreetsLayout.surfaceAt(tx, ty) & bit) == 0) continue;
                if (centreOfThree && ((StreetsLayout.surfaceAt(tx - 1, ty) & StreetsLayout.surfaceAt(tx + 1, ty) & StreetsLayout.surfaceAt(tx, ty - 1)
                        & StreetsLayout.surfaceAt(tx, ty + 1) & bit) == 0)) continue;
                return new int[]{tx, ty};
            }
        }
        throw new AssertionError("no tile with bit " + bit);
    }

    private static void at(Sim s, int tx, int ty) {
        s.px = (tx + 0.5f) * T;
        s.py = (ty + 0.5f) * T;
    }

    // ------------------------------------------------------------------ belts

    @Test
    public void layoutIsDeterministicAndTheStartIsACalmPlaza() {
        for (int ty = -3; ty <= 3; ty++) for (int tx = -3; tx <= 3; tx++) assertEquals(0, StreetsLayout.surfaceAt(tx, ty));
        for (int i = 0; i < 400; i++) {
            int tx = i * 7 - 1400, ty = i * 13 - 2600;
            assertEquals(StreetsLayout.surfaceAt(tx, ty), StreetsLayout.surfaceAt(tx, ty));
        }
        int belts = 0, gates = 0, molten = 0;
        for (int ty = -150; ty <= 150; ty++) {
            for (int tx = -150; tx <= 150; tx++) {
                int b = StreetsLayout.surfaceAt(tx, ty);
                if ((b & (WorldGen.SURF_BELT_N | WorldGen.SURF_BELT_S | WorldGen.SURF_BELT_E | WorldGen.SURF_BELT_W)) != 0) belts++;
                if ((b & WorldGen.SURF_FURNACE_GATE) != 0) gates++;
                if ((b & WorldGen.SURF_MOLTEN) != 0) molten++;
            }
        }
        assertTrue("belts " + belts, belts > 2000);
        assertTrue("gates " + gates, gates > 100);
        assertTrue("molten " + molten, molten > 40);
    }

    @Test
    public void pushIsZeroOffBelts() throws Exception {
        Sim s = run(1);
        float[] out = new float[2];
        assertFalse(s.rules.push(0f, 0f, out));
        assertFalse(s.rules.push(3 * T, 3 * T, out)); // the start plaza and the street through it carry no belt
        int[] b = findBelt(true);
        assertTrue(s.rules.push((b[0] + 0.5f) * T, (b[1] + 0.5f) * T, out));
        assertEquals(1f, Math.abs(out[0]) + Math.abs(out[1]), 1e-4f);
        // a block tile next to the belt street is no belt
        assertFalse(s.rules.push((b[0] + 4.5f) * T, (b[1] + 0.5f) * T, out));
        assertEquals(RefineryRules.HERO_PUSH, s.rules.pushWeight(StageRules.PUSH_HERO, null), 0f);
    }

    /** Net displacement along the belt over {@code seconds} of holding the stick in direction (sx, sy), from (x0, y0). */
    private static float[] travel(Sim s, float x0, float y0, float sx, float sy, double seconds) {
        Input in = new Input();
        in.stickX = sx;
        in.stickY = sy;
        s.px = x0;
        s.py = y0;
        steps(s, in, seconds);
        return new float[]{s.px - x0, s.py - y0};
    }

    @Test
    public void heroNetSpeedAgainstTheBeltIsHalfAndTheStickStaysInCharge() throws Exception {
        Sim s = run(2);
        int[] b = findBelt(true);
        float x0 = (b[0] + 0.5f) * T, y0 = (b[1] + 0.5f) * T;
        float[] u = new float[2];
        assertTrue(s.rules.push(x0, y0, u));
        float vx = u[0], vy = u[1];
        float[] ref = travel(s, 0f, 0f, 1f, 0f, 0.4); // the free speed of the hero: full stick on the plaza
        float sp = ref[0] / 0.4f;
        assertTrue(sp > 60f);
        // against the belt: half speed backwards
        s.time = 1; // keep inside the first 20 s
        float[] against = travel(s, x0, y0, -vx, -vy, 0.4);
        assertEquals("against the belt: 0.5x", -0.5f * sp * 0.4f, against[0] * vx + against[1] * vy, 1.0f);
        // with the belt: 1.5x
        float[] with = travel(s, x0, y0, vx * 0.5f, vy * 0.5f, 0.4);
        assertEquals("with the belt (half stick): 1.0x of the full speed", 1.0f * sp * 0.4f, with[0] * vx + with[1] * vy, 1.0f);
        // across the belt: the full stick sideways plus the push along
        float[] across = travel(s, x0, y0, vy, vx, 0.4);
        assertEquals(sp * 0.4f, Math.abs(across[0] * vy + across[1] * vx), 1.0f);
        assertEquals(0.5f * sp * 0.4f, across[0] * vx + across[1] * vy, 1.0f);
        // a released stick: only the belt carries, and the drift stops the moment the hero is off the belt (nothing is remembered)
        float[] idle = travel(s, x0, y0, 0f, 0f, 0.4);
        assertEquals(0.5f * sp * 0.4f, idle[0] * vx + idle[1] * vy, 1.0f);
        float[] off = travel(s, 0f, 0f, 0f, 0f, 0.4);
        assertEquals(0f, Math.abs(off[0]) + Math.abs(off[1]), 1e-3f);
        // the net speed never passes 1.5x, even in Overdrive (the hero's weight is capped)
        rules(s).overdrive = true;
        assertTrue(s.rules.pushWeight(StageRules.PUSH_HERO, null) <= RefineryRules.HERO_PUSH_CAP);
        assertTrue(RefineryRules.HERO_PUSH_CAP < 1f);
        assertEquals(1.2f, s.rules.pushWeight(StageRules.PUSH_ENEMY, s.spawnAt("COLONIST", 900f, 900f, false)), 1e-4f);
    }

    @Test
    public void dodgeIgnoresTheBelt() throws Exception {
        Sim s = run(3);
        int[] b = findBelt(true);
        Input in = new Input();
        s.px = (b[0] + 0.5f) * T;
        s.py = (b[1] + 0.5f) * T;
        float[] u = new float[2];
        s.rules.push(s.px, s.py, u);
        in.stickX = u[1]; // dash across the belt
        in.stickY = u[0];
        float x0 = s.px, y0 = s.py;
        in.dodgeReq = true;
        steps(s, in, 0.2);
        float along = (s.px - x0) * u[0] + (s.py - y0) * u[1];
        assertEquals("the dash is not carried by the belt", 0f, along, 1.5f);
    }

    @Test
    public void beltsFlipEvery20SecondsWithAWarningFirst() throws Exception {
        Sim s = run(4);
        RefineryRules r = rules(s);
        int[] b = findBelt(false);
        float x = (b[0] + 0.5f) * T, y = (b[1] + 0.5f) * T;
        float[] before = new float[2], after = new float[2], mid = new float[2];
        s.time = 10;
        assertTrue(r.push(x, y, before));
        assertFalse(r.flipped());
        assertFalse(r.flipFlash());
        s.time = 18.4;
        assertFalse("the flash starts 1.5 s before the flip", r.flipFlash());
        s.time = 18.6;
        assertTrue(r.flipFlash());
        assertEquals(1.4f, r.flipIn(), 1e-3f);
        r.push(x, y, mid);
        assertEquals("still the old way during the warning", before[0], mid[0], 0f);
        s.time = 20.01;
        assertTrue(r.flipped());
        assertFalse(r.flipFlash());
        assertTrue(r.push(x, y, after));
        assertEquals(-before[0], after[0], 0f);
        assertEquals(-before[1], after[1], 0f);
        s.time = 40.01;
        assertFalse(r.flipped());
        s.time = 59.99;
        assertTrue(r.flipFlash());
        // Foreman Brass: a stomp flips them at once, with a short flash
        s.time = 45;
        r.reverseBelts();
        assertTrue(r.flipped());
        assertTrue(r.flipFlash());
    }

    @Test
    public void belt_carriesEnemiesAtSixtyPercentAndBossesLess() throws Exception {
        Sim s = run(5);
        int[] b = findBelt(true);
        float x = (b[0] + 0.5f) * T, y = (b[1] + 0.5f) * T;
        s.px = x + 380f; // a hero off the belt and away, so the enemies only drift
        s.py = y;
        Enemy c = s.spawnAt("COLONIST", x, y, false);
        Enemy bo = s.spawnAt("CARGO_LOADER", x + 40f, y, true);
        c.speed = 60f;
        float[] u = new float[2];
        s.rules.push(x, y, u);
        assertEquals(RefineryRules.ENEMY_PUSH, s.rules.pushWeight(StageRules.PUSH_ENEMY, c), 0f);
        assertEquals(RefineryRules.BOSS_PUSH, s.rules.pushWeight(StageRules.PUSH_ENEMY, bo), 0f);
        assertEquals(RefineryRules.PICKUP_PUSH, s.rules.pushWeight(StageRules.PUSH_PICKUP, null), 0f);
        Enemy el = s.spawnAt("COLONIST", x, y + 400f, false);
        el.elite = true;
        assertEquals(RefineryRules.ELITE_PUSH, s.rules.pushWeight(StageRules.PUSH_ENEMY, el), 0f);
    }

    // ------------------------------------------------------------------ gates

    private static float gems(Sim s) {
        float v = 0;
        for (int i = 0; i < s.pickN; i++) if (s.picks[i].kind == Entities.K_GEM) v += s.picks[i].value;
        return v;
    }

    @Test
    public void aGateKillsASeededLineOfFodderAndHalvesTheGems() throws Exception {
        int[] g = findTile(WorldGen.SURF_FURNACE_GATE, true);
        float gx = (g[0] + 0.5f) * T, gy = (g[1] + 0.5f) * T;
        float totalGate, totalControl;
        float xp;
        for (int pass = 0; pass < 2; pass++) {
            Sim s = run(6);
            s.px = gx + 400f;
            s.py = gy;
            Random rng = new Random(11);
            Enemy[] es = new Enemy[50];
            for (int i = 0; i < 50; i++) {
                float x = gx + (rng.nextFloat() - 0.5f) * 100f, y = gy + (rng.nextFloat() - 0.5f) * 100f;
                if (pass == 1) x += 6f * T; // the control: the same line on other ground (killed by hand)
                es[i] = s.spawnAt("COLONIST", x, y, false);
                es[i].speed = 0f;
            }
            if (pass == 1) {
                for (Enemy e : es) {
                    s.killForTest(e);
                }
            }
            float xpEach = es[0].xp;
            int killsBefore = s.kills;
            s.update(DT, new Input());
            assertEquals("all dead after one step (pass " + pass + ")", 0, s.enemyN);
            assertEquals(50, s.kills - killsBefore);
            float got = gems(s);
            if (pass == 0) {
                totalGate = got;
                assertEquals(50 * xpEach * 0.5f, totalGate, 0.5f);
            } else {
                totalControl = got;
                assertEquals(50 * xpEach, totalControl, 0.5f);
            }
        }
    }

    @Test
    public void gatesSpareElitesAndBossesAndBurnTheHeroOncePerSecond() throws Exception {
        int[] g = findTile(WorldGen.SURF_FURNACE_GATE, true);
        float gx = (g[0] + 0.5f) * T, gy = (g[1] + 0.5f) * T;
        Sim s = run(7);
        s.px = gx + 400f;
        s.py = gy;
        Enemy boss = s.spawnAt("CARGO_LOADER", gx, gy, true);
        Enemy elite = s.spawnAt("COLONIST", gx + 10f, gy, false);
        elite.elite = true;
        boss.speed = elite.speed = 0f;
        s.update(DT, new Input());
        assertFalse(boss.dead);
        assertFalse(elite.dead);
        // the hero: 25 damage and Burn on entering, none while standing in it, and a second before the next entry counts
        Sim h = run(8);
        h.stats.burnImmune = false;
        h.stats.armor = 0;
        h.stats.damageTaken = 1;
        h.px = gx + 3 * T;
        h.py = gy;
        h.update(DT, new Input());
        float hp0 = h.hp;
        h.px = gx;
        h.update(DT, new Input());
        assertEquals(25f, hp0 - h.hp, 0.01f);
        assertTrue(h.heroBurnT > 0f);
        h.invuln = 0f;
        float hp1 = h.hp;
        steps(h, new Input(), 1.5); // standing in the gate: only the Burn ticks
        assertTrue("no second hit while inside", hp1 - h.hp < 6f);
        h.px = gx + 3 * T; // out and in again after the cooldown
        steps(h, new Input(), 0.1);
        h.invuln = 0f;
        h.px = gx;
        float hp2 = h.hp;
        h.update(DT, new Input());
        assertEquals(25f, hp2 - h.hp, 0.01f);
        // the dodge protects
        Sim d = run(9);
        d.px = gx;
        d.py = gy;
        d.dodgeT = 0.2f;
        float hpd = d.hp;
        d.update(DT, new Input());
        assertEquals(hpd, d.hp, 0.01f);
    }

    // ------------------------------------------------------------------ heat

    @Test
    public void heatRisesWhileStillAndFallsWhileMoving() throws Exception {
        Sim s = run(10);
        RefineryRules r = rules(s);
        steps(s, new Input(), 5);
        assertTrue("still: about 6/s, got " + r.heat, r.heat > 24f && r.heat < 31f);
        r.heat = 60f;
        Input in = new Input();
        in.stickX = 1f;
        steps(s, in, 2);
        assertTrue("moving: falls about 12/s, got " + r.heat, r.heat < 42f && r.heat > 30f);
        // frost halves the gain
        Sim f = run(10);
        f.stats.statusChance[Entities.ST_FROST] = 0.06;
        steps(f, new Input(), 5);
        assertEquals(15f, rules(f).heat, 3f);
        // a belt counts as moving
        Sim b = run(11);
        int[] bt = findBelt(true);
        at(b, bt[0], bt[1]);
        rules(b).heat = 40f;
        steps(b, new Input(), 1.5);
        assertTrue("riding a belt cools: " + rules(b).heat, rules(b).heat < 40f);
    }

    @Test
    public void heatBurnsFrom50AndOverheatsAt100() throws Exception {
        Sim s = run(12);
        RefineryRules r = rules(s);
        s.stats.armor = 0;
        s.stats.damageTaken = 1;
        Input in = new Input();
        in.stickX = 1f;
        r.heat = 49f;
        s.hp = s.maxHp;
        s.px = 0; // moving, so the heat only falls: no damage below 50
        float hp0 = s.hp;
        steps(s, in, 0.1);
        assertEquals(hp0, s.hp, 0.01f);
        // the Overheat state: +25% cooldowns, 5 damage per second, until the heat is under 50
        double cd = s.stats.cooldownMul;
        r.heat = 100f;
        steps(s, new Input(), 1.0); // still: the meter climbs back to the top
        assertTrue(r.overheated);
        assertEquals(cd * 1.25, s.stats.cooldownMul, 1e-6);
        r.heat = 70f;
        s.update(DT, new Input());
        assertTrue("stays hot until under 50", r.overheated);
        r.heat = 45f;
        s.update(DT, new Input());
        assertFalse(r.overheated);
        assertEquals(cd, s.stats.cooldownMul, 1e-6);
        // 1.5/s from 50, 5/s while overheated
        Sim t = run(13);
        t.stats.armor = 0;
        t.stats.damageTaken = 1;
        t.stats.burnImmune = true;
        rules(t).heat = 55f;
        Input mv = new Input();
        mv.stickX = 1f;
        float h0 = t.hp;
        steps(t, mv, 0.1); // moving: the heat falls but stays above 50 for this 0.1 s
        assertEquals(1.5f * 0.1f, h0 - t.hp, 0.02f);
        rules(t).heat = 100f;
        steps(t, new Input(), 1.0); // settle: still, the meter is at the top and the Overheat is on
        assertTrue(rules(t).overheated);
        float h1 = t.hp;
        steps(t, new Input(), 1);
        assertEquals(5f, h1 - t.hp, 0.3f);
    }

    @Test
    public void aStandStillNakedHeroDiesOfHeatBeforeSixMinutes() throws Exception {
        Sim s = run(14);
        Input in = new Input();
        float dt = 1f / 30f;
        double diedAt = -1;
        for (int i = 0; i < 360 * 30 && diedAt < 0; i++) {
            s.update(dt, in);
            if (s.ended == 1) diedAt = s.time;
        }
        assertTrue("died of heat at " + diedAt, diedAt > 0 && diedAt < 360);
        assertTrue(rules(s).heat >= 50f);
        assertTrue(s.dmgBy[Sim.DMG_AREA] > 50f);
    }

    @Test
    public void coolantFogClearsHeatAndFrostsEnemies() throws Exception {
        Sim s = run(15);
        RefineryRules r = rules(s);
        s.px = 2000f;
        s.py = 2000f;
        Enemy e = s.spawnAt("COLONIST", 2810f, 2000f, false); // far from the hero's weapon
        e.speed = 0f;
        r.heat = 80f;
        assertTrue(r.destBroken(RefineryRules.DK_COOLANT, 2000f, 2000f));
        assertTrue(r.destBroken(RefineryRules.DK_COOLANT, 2800f, 2000f));
        steps(s, new Input(), 1.2);
        assertEquals(0f, r.heat, 0f);
        assertTrue("fog is up", r.inFog);
        assertTrue("Frost on the enemy", e.frost > 0 || e.dead);
        steps(s, new Input(), 8);
        assertFalse(r.inFog);
    }

    @Test
    public void coolingTowerClearsHeatForThreeMinutes() throws Exception {
        Sim s = run(16);
        s.enemyN = 0;
        s.time = 1259.95;
        steps(s, new Input(), 0.3);
        assertTrue(s.events.towerOn);
        s.px = s.events.tx;
        s.py = s.events.ty;
        Input in = new Input();
        for (int i = 0; i < 30 * 30 && s.events.towerWon == 0; i++) {
            s.hp = s.maxHp;
            s.px = s.events.tx; // the tower may stand on a belt: the player walks against it, the test holds the hero
            s.py = s.events.ty;
            s.update(1f / 30f, in);
        }
        assertEquals(1, s.events.towerWon);
        s.update(DT, in);
        assertEquals(0f, rules(s).heat, 0f);
        assertTrue(rules(s).coolingLocked());
        float hp = s.hp;
        s.px = 0f;
        s.py = 0f;
        safe(s, in, 20); // standing still for 20 s: no heat at all
        assertEquals(0f, rules(s).heat, 0f);
        assertTrue(s.pickN > 0);
        boolean crate = false;
        for (int i = 0; i < s.pickN; i++) crate |= s.picks[i].kind == Entities.K_CHEST;
        assertTrue("an Arms Crate", crate);
        s.time += 181;
        s.px = 0f; // off the belt the tower may stand on (a belt counts as moving)
        s.py = 0f;
        safe(s, in, 3);
        assertTrue("the lock ends", rules(s).heat > 5f);
    }

    // ------------------------------------------------------------------ Heat Wave

    @Test
    public void theHeatWaveHitsAtTheAnalyticPositionOnceAndForty() throws Exception {
        Sim s = run(17);
        RefineryRules r = rules(s);
        s.stats.armor = 0;
        s.stats.damageTaken = 1;
        s.stats.burnImmune = true;
        s.time = 10;
        s.px = 100f;
        s.py = 0f;
        Enemy e = s.spawnAt("COLONIST", 300f, -60f, false);
        e.speed = 0f;
        e.hp = e.maxHp = 1000f;
        double t0 = s.time + 0.1;
        int slot = r.startWave(1f, -300f, t0);
        assertTrue(slot >= 0);
        assertTrue(r.waveWarning(slot));
        assertEquals(-300f + 220f * 2f, r.waveEdge(slot, t0 + 2.0), 1e-3f);
        double hitHero = t0 + (100f + 300f) / 220f, hitEnemy = t0 + (300f + 300f) / 220f;
        float hp0 = s.hp;
        double heroHurtAt = -1, enemyHurtAt = -1;
        Input in = new Input();
        for (int i = 0; i < 60 * 6; i++) {
            s.update(DT, in);
            s.px = 100f; // held in place: the analytic crossing
            s.py = 0f;
            if (heroHurtAt < 0 && s.hp < hp0) heroHurtAt = s.time;
            if (enemyHurtAt < 0 && e.hp < 999f) enemyHurtAt = s.time;
        }
        assertEquals("hero crossing", hitHero, heroHurtAt, 0.03);
        assertEquals("enemy crossing", hitEnemy, enemyHurtAt, 0.03);
        assertEquals("20 damage once", 20f, hp0 - s.hp, 0.05f);
        assertEquals("40 damage once", 40f, 1000f - e.hp, 0.5f);
        // the dodge protects
        Sim d = run(18);
        d.stats.armor = 0;
        d.time = 10;
        d.px = 100f;
        d.py = 0f;
        rules(d).startWave(1f, 0f, d.time);
        float hpd = d.hp;
        for (int i = 0; i < 60 * 2; i++) {
            d.invuln = 1f;
            d.update(DT, in);
        }
        assertEquals(hpd, d.hp, 0.01f);
    }

    @Test
    public void theScheduledWaveWarnsFor1_5SecondsThenRunsAt220() throws Exception {
        Sim s = run(19);
        RefineryRules r = rules(s);
        s.time = 118.0;
        steps(s, new Input(), 0.2);
        assertFalse(r.wLive[0]);
        s.time = 118.55;
        s.hp = s.maxHp;
        steps(s, new Input(), 0.05);
        assertTrue(r.wLive[0]);
        assertTrue(r.waveWarning(0));
        assertEquals(120.0, r.wT0[0], 1e-6);
        assertEquals(s.px - r.wDir[0] * RefineryRules.WAVE_START, r.wStart[0], 1.0f);
        s.time = 121.0;
        assertFalse(r.waveWarning(0));
        assertEquals(r.wStart[0] + r.wDir[0] * 220f, r.waveEdge(0, 121.0), 1e-3f);
        // the next one comes 40 s later, from the other side
        float first = r.wDir[0];
        s.time = 158.55;
        steps(s, new Input(), 0.05);
        boolean other = false;
        for (int i = 0; i < RefineryRules.MAXW; i++) other |= r.wLive[i] && r.wDir[i] == -first && r.wT0[i] == 160.0;
        assertTrue(other);
    }

    // ------------------------------------------------------------------ molten metal, destructibles

    @Test
    public void moltenMetalBurnsHeroAndEnemiesButNotTheBridge() throws Exception {
        int[] m = findTile(WorldGen.SURF_MOLTEN, false);
        Sim s = run(20);
        s.stats.armor = 0;
        s.stats.damageTaken = 1;
        s.stats.burnImmune = false;
        at(s, m[0], m[1]);
        float hp0 = s.hp;
        Input still = new Input();
        // move inside the channel and out of it would hurt; hold the hero on the tile
        for (int i = 0; i < 60; i++) {
            s.update(DT, still);
            at(s, m[0], m[1]);
        }
        float lost = hp0 - s.hp;
        assertTrue("6/s plus Burn 2/s: " + lost, lost > 5.5f && lost < 9.5f);
        Sim e = run(21);
        e.px = (m[0] + 12) * T;
        e.py = (m[1] + 12) * T;
        Enemy en = e.spawnAt("COLONIST", (m[0] + 0.5f) * T, (m[1] + 0.5f) * T, false);
        en.speed = 0f;
        en.hp = en.maxHp = 500f;
        steps(e, new Input(), 1);
        assertEquals("15/s", 15f, 500f - en.hp, 1.2f);
        // a bridge tile is safe
        int bx = 0, by = 0;
        search:
        for (int ty = -120; ty <= 120; ty++) {
            for (int tx = -120; tx <= 120; tx++) {
                if (StreetsLayout.bridge(tx, ty)) {
                    bx = tx;
                    by = ty;
                    break search;
                }
            }
        }
        assertEquals(0, StreetsLayout.surfaceAt(bx, by) & WorldGen.SURF_MOLTEN);
        assertTrue(StreetsLayout.bridge(bx, by));
        // map.blocked keeps the places off the molten tiles
        assertTrue(WorldGen.of("REFINERY").blocked((m[0] + 0.5f) * T, (m[1] + 0.5f) * T));
    }

    @Test
    public void coolantValvesAndFuelTanksAreDestructiblesAndATankBlasts() throws Exception {
        Sim s = run(22);
        // walk the grid of cells until both kinds have been listed around the hero
        boolean valve = false, tank = false;
        for (int i = 0; i < 40 && !(valve && tank); i++) {
            s.px = i * 520f;
            s.py = i * 380f;
            s.update(DT, new Input());
            for (int k = 0; k < s.destN; k++) {
                valve |= s.destKind[k] == RefineryRules.DK_COOLANT;
                tank |= s.destKind[k] == RefineryRules.DK_FUEL;
            }
        }
        assertTrue("valve listed", valve);
        assertTrue("tank listed", tank);
        // a fuel tank: 90 damage in 130 u, 14 to the hero, a fire patch, and a barrel next to it chains
        Sim t = run(23);
        t.stats.armor = 0;
        t.stats.damageTaken = 1;
        t.px = 5000f;
        t.py = 5000f;
        Enemy e = t.spawnAt("COLONIST", 5060f, 5000f, false);
        e.speed = 0f;
        e.hp = e.maxHp = 500f;
        Enemy far = t.spawnAt("COLONIST", 5250f, 5000f, false);
        far.speed = 0f;
        far.hp = far.maxHp = 500f;
        t.rebuildGridForTest();
        float hp0 = t.hp;
        int haz0 = t.hazN;
        assertTrue(t.rules.destBroken(RefineryRules.DK_FUEL, 5000f, 5000f));
        assertEquals(90f, 500f - e.hp, 0.5f);
        assertEquals(500f, far.hp, 0f);
        assertEquals(14f, hp0 - t.hp, 0.01f);
        assertEquals(haz0 + 1, t.hazN);
    }

    // ------------------------------------------------------------------ enemies

    @Test
    public void ironcladsTakeHalfFromHitsAndMoreFromStatusesAndAreCappedAtEight() throws Exception {
        Sim s = run(24);
        Enemy ic = s.spawnAt("IRONCLAD", 500f, 500f, false);
        Enemy st = s.spawnAt("STOKER", 600f, 600f, false);
        Enemy col = s.spawnAt("COLONIST", 700f, 700f, false);
        StageRules r = s.rules;
        assertEquals(5f, r.damageIn(ic, 10f, StageRules.CAT_HIT), 1e-5f);
        assertEquals(15f, r.damageIn(ic, 10f, StageRules.CAT_BURN), 1e-5f);
        assertEquals(15f, r.damageIn(ic, 10f, StageRules.CAT_ELECTRIFY), 1e-5f);
        assertEquals(15f, r.damageIn(ic, 10f, StageRules.CAT_DECAY), 1e-5f);
        assertEquals(10f, r.damageIn(ic, 10f, StageRules.CAT_FROST), 1e-5f);
        assertEquals(10f, r.damageIn(ic, 10f, StageRules.CAT_BLAST), 1e-5f);
        assertEquals(0f, r.damageIn(st, 10f, StageRules.CAT_BURN), 0f);
        assertEquals(10f, r.damageIn(st, 10f, StageRules.CAT_HIT), 0f);
        assertEquals(10f, r.damageIn(col, 10f, StageRules.CAT_BURN), 0f);
        // through the real hit
        float hp = ic.hp;
        s.hurt(ic, 20f, 0f, 1f, 0f, 0f, 0f);
        assertEquals(10f, hp - ic.hp, 0.01f);
        assertEquals(0f, ic.type.def.knockback, 0.0);
        for (int i = 0; i < 6; i++) assertNotNull(s.spawnAt("IRONCLAD", 900f + i * 30f, 100f, false));
        assertNull("7 alive: no cap yet", r.spawnSwap(ic.type));
        assertNotNull(s.spawnAt("IRONCLAD", 900f, 200f, false));
        assertEquals("8 alive: the next is a Colonist", "COLONIST", r.spawnSwap(ic.type));
        assertNull(r.spawnSwap(col.type));
    }

    @Test
    public void aStokerLeavesAFirePatchAfterItsDash() throws Exception {
        Sim s = run(25);
        s.px = 0f;
        s.py = 0f;
        Enemy e = s.spawnAt("STOKER", 120f, 0f, false);
        e.actT = 0f;
        boolean patch = false;
        for (int i = 0; i < 60 * 5 && !patch; i++) {
            s.hp = s.maxHp;
            s.update(DT, new Input());
            for (int k = 0; k < s.hazN; k++) patch |= s.hazards[k].kind == Entities.HZ_SLAG && s.hazards[k].r == 40f && s.hazards[k].life > 2f;
        }
        assertTrue("a fire patch r 40 for 3 s", patch);
    }

    // ------------------------------------------------------------------ bosses

    @Test
    public void foremanBrassReversesTheBeltsOnEveryStomp() throws Exception {
        Sim s = run(26);
        RefineryRules r = rules(s);
        s.px = 0f;
        s.py = 0f;
        Enemy b = s.spawnAt("FOREMAN_BRASS", 300f, 0f, true);
        b.actT = 0f;
        s.time = 2;
        boolean was = r.flipped();
        int flips = 0;
        boolean last = was;
        for (int i = 0; i < 60 * 14; i++) {
            s.hp = s.maxHp;
            s.update(DT, new Input());
            s.time = Math.min(s.time, 17); // keep the scheduled flips out of it
            if (r.flipped() != last) {
                flips++;
                last = r.flipped();
            }
        }
        assertTrue("a flip per stomp: " + flips, flips >= 2);
    }

    @Test
    public void stokerCaptainChainsDashesEachEndingInAFireRing() throws Exception {
        Sim s = run(27);
        s.px = 0f;
        s.py = 0f;
        Enemy b = s.spawnAt("STOKER_CAPTAIN", 250f, 0f, true);
        b.actT = 0f;
        int rings = 0;
        int lastCnt = 0;
        for (int i = 0; i < 60 * 8; i++) {
            s.hp = s.maxHp;
            s.update(DT, new Input());
            for (int k = 0; k < s.hazN; k++) if (s.hazards[k].kind == Entities.HZ_BOMB && s.hazards[k].variant == Entities.BV_SLAG && s.hazards[k].warnMax == 0.55f && s.hazards[k].warn > 0.5f) rings++;
        }
        assertTrue("rings: " + rings, rings >= 3);
    }

    private static Enemy ironheart(Sim s) {
        s.px = 0f;
        s.py = 0f;
        Enemy e = s.spawnAt("IRONHEART", 450f, 200f, true);
        assertNotNull(e);
        return e;
    }

    @Test
    public void theIronheartPhasesBeltsGatesAndWaves() throws Exception {
        Sim s = run(28);
        RefineryRules r = rules(s);
        Enemy e = ironheart(s);
        s.update(DT, new Input());
        assertEquals("born in a plaza", 0f, StreetsLayout.plazaCentre(StreetsLayout.nearestPlaza(e.x)) - e.x, 1e-3f);
        assertSame(e, r.finale);
        s.update(DT, new Input());
        boolean valves = false;
        for (int i = 0; i < s.destN; i++) valves |= s.destKind[i] == RefineryRules.DK_VALVE_HEAVY;
        assertTrue("armoured coolant valves around the plaza", valves);
        // a weapon hit does nothing to them
        int nBefore = s.destN;
        for (int i = 0; i < s.destN; i++) if (s.destKind[i] == RefineryRules.DK_VALVE_HEAVY) s.hitDestForTest(s.destX[i], s.destY[i], 30f);
        assertEquals(nBefore, s.destN);
        s.update(DT, new Input());
        assertEquals(RefineryRules.BELTS_TO_BOSS, r.beltMode);
        assertEquals(0, e.phase);
        assertEquals(0, r.vgN);
        // phase 1 belts point at it: pick a belt near it and test
        float[] u = new float[2];
        int[] vb = null;
        outer:
        for (int ty = -30; ty <= 30; ty++) {
            for (int tx = -30; tx <= 30; tx++) {
                float wx = (tx + 0.5f) * T, wy = (ty + 0.5f) * T;
                if ((StreetsLayout.surfaceAt(tx, ty) & (WorldGen.SURF_BELT_N | WorldGen.SURF_BELT_S)) != 0 && Math.abs(wy - e.y) > 200f && Math.hypot(wx - e.x, wy - e.y) < 900f
                        && (StreetsLayout.surfaceAt(tx, ty) & (WorldGen.SURF_BELT_E | WorldGen.SURF_BELT_W)) == 0) {
                    vb = new int[]{tx, ty};
                    break outer;
                }
            }
        }
        assertNotNull(vb);
        float wx = (vb[0] + 0.5f) * T, wy = (vb[1] + 0.5f) * T;
        assertTrue(r.push(wx, wy, u));
        assertEquals("toward the boss", Math.signum(e.y - wy), Math.signum(u[1]), 0f);
        // phase 2 at 66%: two bands cross
        e.hp = e.maxHp * 0.6f;
        steps(s, new Input(), 2.5);
        assertEquals(1, e.phase);
        int live = 0;
        for (int i = 0; i < RefineryRules.MAXW; i++) if (r.wLive[i]) live++;
        assertTrue("two bands: " + live, live >= 2);
        // phase 3 at 33%: belts away, six gates in a ring
        e.hp = e.maxHp * 0.3f;
        steps(s, new Input(), 0.2);
        assertEquals(2, e.phase);
        assertEquals(RefineryRules.BELTS_FROM_BOSS, r.beltMode);
        assertEquals(RefineryRules.MAXVG, r.vgN);
        assertTrue(r.push(wx, wy, u));
        assertEquals("away from the boss", -Math.signum(e.y - wy), Math.signum(u[1]), 0f);
        // the ring kills fodder
        Enemy f = s.spawnAt("COLONIST", r.vgX[0], r.vgY[0], false);
        f.speed = 0f;
        s.update(DT, new Input());
        assertTrue(f.dead || !contains(s, f));
    }

    private static boolean contains(Sim s, Enemy e) {
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i] == e) return true;
        return false;
    }

    private static void assertSame(Object a, Object b) {
        assertTrue(a == b);
    }

    @Test
    public void thermalShockStunsForFourSecondsAndAddsFiftyPercentForSix() throws Exception {
        Sim s = run(29);
        RefineryRules r = rules(s);
        Enemy e = ironheart(s);
        float[] hpBefore = {e.hp};
        assertEquals(10f, s.rules.damageIn(e, 10f, StageRules.CAT_HIT), 0f);
        r.thermalShock(e);
        assertTrue(e.stun >= 3.99f);
        assertEquals(15f, s.rules.damageIn(e, 10f, StageRules.CAT_HIT), 1e-5f);
        s.time += 5.9;
        assertEquals(15f, s.rules.damageIn(e, 10f, StageRules.CAT_HIT), 1e-5f);
        s.time += 0.2;
        assertEquals(10f, s.rules.damageIn(e, 10f, StageRules.CAT_HIT), 0f);
        assertFalse(r.shocked());
    }

    @Test
    public void aSlamThatSmashesACoolantValveIsAThermalShock() throws Exception {
        Sim s = run(30);
        RefineryRules r = rules(s);
        s.stats.armor = 10;
        Enemy e = ironheart(s);
        s.update(DT, new Input()); // the first step puts it in its plaza with three valves around
        int valveI = -1;
        float best = 1e18f;
        for (int i = 0; i < s.destN; i++) {
            if (s.destKind[i] != RefineryRules.DK_VALVE_HEAVY) continue;
            float q = (s.destX[i] - e.x) * (s.destX[i] - e.x) + (s.destY[i] - e.y) * (s.destY[i] - e.y);
            if (q < best) { best = q; valveI = i; }
        }
        assertTrue(valveI >= 0);
        float vx = s.destX[valveI], vy = s.destY[valveI];
        // the hero stands just behind a valve, seen from the boss: the slam that comes for the hero covers the valve
        float dx = vx - e.x, dy = vy - e.y, m = (float) Math.sqrt(dx * dx + dy * dy);
        s.px = vx + dx / m * 40f;
        s.py = vy + dy / m * 40f;
        boolean shocked = false;
        for (int i = 0; i < 60 * 20 && !shocked; i++) {
            s.hp = s.maxHp;
            s.px = vx + dx / m * 40f;
            s.py = vy + dy / m * 40f;
            s.update(DT, new Input());
            shocked = r.shocked();
        }
        assertTrue("shocked", shocked);
        assertTrue(e.stun > 2f);
        boolean valveStands = false;
        for (int i = 0; i < s.destN; i++) valveStands |= s.destKind[i] == RefineryRules.DK_VALVE_HEAVY && Math.abs(s.destX[i] - vx) < 1f && Math.abs(s.destY[i] - vy) < 1f;
        assertFalse("the valve is gone", valveStands);
    }

    @Test
    public void killingTheIronheartWinsWithDoubleGoldAndDropsTheCore() throws Exception {
        Sim s = run(31);
        Enemy e = ironheart(s);
        s.gold = 100;
        s.killForTest(e);
        steps(s, new Input(), 0.1);
        assertTrue(s.firstKilled);
        assertEquals(200, s.endGold());
        boolean core = false;
        for (int i = 0; i < s.pickN; i++) core |= s.picks[i].kind == Entities.K_EQUIP;
        assertTrue("the Ironheart Core drops", core);
        steps(s, new Input(), 3);
        assertEquals(2, s.ended);
        assertNull(rules(s).finale);
    }

    @Test
    public void theIronheartCoreRaisesTheLimitAndGivesMightFromFiftyHeat() throws Exception {
        Sim s = run(32);
        RefineryRules r = rules(s);
        s.giveEquipment("IRONHEART_CORE", Equipment.UNIQUE);
        s.update(DT, new Input());
        assertTrue(r.core);
        assertEquals(130f, r.heatLimit(), 0f);
        double base = s.stats.might;
        r.heat = 40f;
        Input in = new Input();
        in.stickX = 1f;
        s.update(DT, in);
        assertEquals(base, s.stats.might, 1e-9);
        r.heat = 80f;
        s.update(DT, in);
        assertEquals(base * 1.15, s.stats.might, 1e-9);
        r.heat = 130f;
        s.update(DT, new Input());
        assertTrue("overheat at 130", r.overheated);
        assertEquals(base * 1.15, s.stats.might, 1e-9);
        // a recompute (a level-up) does not stack the bonus
        s.recomputeAll();
        r.heat = 80f;
        s.update(DT, in);
        assertEquals(base * 1.15, s.stats.might, 1e-9);
    }

    @Test
    public void overdriveDoublesTheBeltsAndPulsesTheGates() throws Exception {
        Sim s = run(33);
        RefineryRules r = rules(s);
        s.time = 1439.95;
        s.px = 0;
        s.py = 0;
        steps(s, new Input(), 0.3);
        assertTrue(s.events.is(Events.SURGE));
        assertTrue(r.overdrive);
        assertEquals(2f, r.beltMul(), 0f);
        assertEquals("OVERDRIVE", s.events.bannerTitle);
        assertTrue(r.pulseFlash > 0f);
        // the title of the other two
        s.time = 1559.95;
        steps(s, new Input(), 0.3);
        assertEquals("DEFEND THE BOILER", s.events.bannerTitle);
        assertFalse(r.overdrive);
    }

    @Test
    public void theStageDataMatchesTheSpec() throws Exception {
        GameData d = StageSoak.data();
        StageDef st = d.stages.get("REFINERY");
        assertEquals(6, st.timedBoss.length);
        assertEquals("FOREMAN_BRASS", st.timedBoss[0]);
        assertEquals(450, st.timedAt[0]);
        assertEquals("STOKER_CAPTAIN", st.timedBoss[1]);
        assertEquals(750, st.timedAt[1]);
        assertEquals("COMMANDANT_HALE", st.timedBoss[2]);
        assertEquals("IRONHEART", st.timedBoss[5]);
        assertEquals(1680, st.timedAt[5]);
        assertEquals(4, st.eventId.length);
        assertEquals("SIGNAL_TOWER", st.eventId[0]);
        assertEquals(1260, st.eventAt[0]);
        assertEquals(1440, st.eventAt[2]);
        assertEquals(1560, st.eventAt[3]);
        assertEquals(1800.0, d.enemies.get("IRONHEART").maxHp, 0.0);
        assertEquals(300.0, d.enemies.get("FOREMAN_BRASS").maxHp, 0.0);
        assertEquals(12, d.enemies.get("IRONHEART").param("pattern", 0f), 0f);
        assertNotNull(d.equipment.get("IRONHEART_CORE"));
        assertTrue(d.equipment.get("IRONHEART_CORE").unique);
        // the roster comes in by minute
        assertFalse(java.util.Arrays.asList(st.rowAt(0).enemies).contains("WELDER"));
        assertTrue(java.util.Arrays.asList(st.rowAt(3 * 60).enemies).contains("WELDER"));
        assertFalse(java.util.Arrays.asList(st.rowAt(5 * 60).enemies).contains("SMELTER"));
        assertTrue(java.util.Arrays.asList(st.rowAt(6 * 60).enemies).contains("SMELTER"));
        assertFalse(java.util.Arrays.asList(st.rowAt(8 * 60).enemies).contains("STOKER"));
        assertTrue(java.util.Arrays.asList(st.rowAt(9 * 60).enemies).contains("STOKER"));
        assertFalse(java.util.Arrays.asList(st.rowAt(10 * 60).enemies).contains("IRONCLAD"));
        assertTrue(java.util.Arrays.asList(st.rowAt(11 * 60).enemies).contains("IRONCLAD"));
    }

    // ------------------------------------------------------------------ soak

    @Test
    public void thirtyMinuteSoakImmortalMidAndNakedStayClean() throws Exception {
        for (String policy : new String[]{"immortal", "mid", "naked"}) {
            StageSoak.Result r = StageSoak.run("REFINERY", "BASTION", policy, 1, 1800);
            System.out.println(r);
            assertEquals(r.toString(), 0, r.nan);
            assertEquals(r.toString(), 0, r.overflow);
            assertEquals(r.toString(), 0, r.capBreak);
            assertTrue(r.toString(), r.peakEProj <= Sim.MAX_EPROJ && r.peakHaz <= Sim.MAX_HAZ);
            assertTrue("the run ends: " + r, r.ended != 0 || r.endTime >= 1800 - 1);
            if (policy.equals("immortal")) {
                assertEquals("won: " + r, 2, r.ended);
                assertTrue("Brass and the Captain came: " + r.bossLog, r.bossLog.toString().contains("FOREMAN_BRASS") && r.bossLog.toString().contains("STOKER_CAPTAIN"));
            }
        }
    }
}
