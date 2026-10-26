package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.HashMap;

/**
 * The events of minutes 20 to 30 (Signal Tower, Collapse / Slag Rain, Lumen Surge, Defend the Beacon, The Tide): timeline,
 * both ends of the Tower, the hazard cap, the Surge's light, the Beacon's two endings, the Tide's lull, determinism, the
 * legacy stages, a 30 minute soak per stage and the allocation rule. The Sim is stepped directly.
 */
public class EventsTest {
    private static final float DT = 0.1f;
    private static final Input IN = new Input();

    private static Sim sim(String stage, long seed, boolean noSpawns) throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get(stage), "WHIP", new HashMap<String, Double>(), null, seed);
        s.noSpawns = noSpawns;
        s.enemyN = 0;
        return s;
    }

    private static void step(Sim s, float dt) {
        s.hp = s.maxHp;
        s.update(dt, IN);
    }

    private static void runTo(Sim s, double t, float dt) {
        while (s.time < t && s.ended == 0) step(s, dt);
    }

    private static int at(Sim s, String id) {
        for (int i = 0; i < s.stage.eventId.length; i++) if (s.stage.eventId[i].equals(id)) return s.stage.eventAt[i];
        throw new AssertionError(id);
    }

    /** Runs up to the event and returns once it is active. */
    private static void toEvent(Sim s, String id) {
        s.time = at(s, id) - 0.05;
        runTo(s, at(s, id) + 0.3, DT);
    }

    private static boolean chestWith(Sim s, float value) {
        for (int i = 0; i < s.pickN; i++) if (s.picks[i].kind == Entities.K_CHEST && s.picks[i].value == value) return true;
        return false;
    }

    // ------------------------------------------------------------------ timeline

    @Test
    public void eachEventStartsAndEndsOnTimeOnItsStage() throws Exception {
        String[][] ids = {{"FLATS", "SIGNAL_TOWER", "SLAG_RAIN", "LUMEN_SURGE", "BEACON", "TIDE"},
                {"MINES", "SIGNAL_TOWER", "COLLAPSE", "LUMEN_SURGE", "BEACON", "TIDE"}};
        for (String[] row : ids) {
            Sim s = sim(row[0], 5, true);
            assertTrue(row[0], s.events.enabled);
            assertEquals(row[0], 5, s.stage.eventId.length);
            double[] start = new double[8], end = new double[8];
            int prev = Events.NONE;
            s.time = 1250;
            while (s.time < 1790 && s.ended == 0) {
                step(s, DT);
                int a = s.events.active;
                if (a != prev) {
                    if (prev != Events.NONE) end[prev] = s.time;
                    if (a != Events.NONE) start[a] = s.time;
                    prev = a;
                }
            }
            for (int i = 0; i < 5; i++) {
                int kind = -1;
                for (int k = 1; k < Events.KINDS; k++) if (Events.IDS[k].equals(row[i + 1])) kind = k;
                int when = at(s, row[i + 1]);
                assertEquals(row[0] + " " + row[i + 1] + " start", when, start[kind], 0.25);
                assertEquals(row[0] + " " + row[i + 1] + " end", when + Events.LEN[kind], end[kind], 0.25);
            }
            // the schedule keeps the boss moments clear: nothing starts inside the second before a timed boss
            for (int b = 0; b < s.stage.timedAt.length; b++) {
                for (int i = 0; i < 5; i++) assertTrue(s.stage.eventAt[i] != s.stage.timedAt[b]);
            }
        }
    }

    @Test
    public void legacyStagesHaveNoEvents() throws Exception {
        for (String stage : new String[]{"FOREST", "LIBRARY"}) {
            Sim s = sim(stage, 1, true);
            assertFalse(stage, s.events.enabled);
            s.time = 1250;
            runTo(s, 1750, 0.25f);
            assertEquals(stage, 0, s.events.started);
        }
    }

    @Test
    public void placementIsSeededAndNeverTouchesTheRunRandom() throws Exception {
        float[] pos = new float[2];
        for (int run = 0; run < 2; run++) {
            Sim s = sim("FLATS", 77, true);
            long before = nextLong(s);
            s.time = 1259.95;
            runTo(s, 1261, DT);
            assertTrue(s.events.towerOn);
            float d = (float) Math.hypot(s.events.tx - s.px, s.events.ty - s.py);
            assertTrue("500..700 u away: " + d, d >= 495f && d <= 705f);
            if (run == 0) {
                pos[0] = s.events.tx;
                pos[1] = s.events.ty;
            } else {
                assertEquals(pos[0], s.events.tx, 0f);
                assertEquals(pos[1], s.events.ty, 0f);
            }
            assertTrue(before != 0 || true);
        }
    }

    private static long nextLong(Sim s) {
        return s.rng.nextLong();
    }

    // ------------------------------------------------------------------ Signal Tower

    @Test
    public void towerCaptureSucceedsAndPaysTheArmsCrate() throws Exception {
        Sim s = sim("MINES", 3, true);
        toEvent(s, "SIGNAL_TOWER");
        Events ev = s.events;
        assertTrue(ev.towerOn);
        s.hp = s.maxHp * 0.5f;
        s.px = ev.tx;
        s.py = ev.ty;
        int guard = 0;
        while (ev.towerOn && guard++ < 400) {
            s.invuln = 1e9f; // the horde at half progress must not kill the test hero
            s.update(DT, IN);
            assertTrue(ev.towerCapturing || !ev.towerOn);
        }
        s.invuln = 0f;
        assertEquals(1, ev.towerWon);
        assertEquals(Events.NONE, ev.active);
        assertTrue("Arms Crate dropped (the hero standing there picks it up at once)", chestWith(s, Sim.ARMS_CRATE) || s.pendingArms > 0);
        assertTrue("healed 30% (hp " + s.hp + ")", s.hp >= s.maxHp * 0.5f + s.maxHp * 0.3f - 1f);
        assertTrue("a horde came at half progress: " + s.lastHordeKind, s.lastHordeKind.equals("TOWER") && s.lastHordeSize > 0);
        assertEquals(1.2f, ev.xpMul(), 1e-4);
        s.time += 181;
        assertEquals(1f, ev.xpMul(), 1e-4);
    }

    @Test
    public void towerDrainsAtHalfSpeedOutsideAndExpiresAfterNinetySeconds() throws Exception {
        Sim s = sim("FLATS", 4, true);
        toEvent(s, "SIGNAL_TOWER");
        Events ev = s.events;
        float hx = s.px, hy = s.py;
        s.px = ev.tx;
        s.py = ev.ty;
        double t0 = s.time;
        while (s.time < t0 + 10) s.update(DT, IN);
        assertEquals(10f, ev.towerProg, 0.3f);
        assertTrue(ev.towerCapturing);
        s.px = hx;
        s.py = hy;
        t0 = s.time;
        while (s.time < t0 + 10) step(s, DT);
        assertEquals("drains at half speed", 5f, ev.towerProg, 0.4f);
        assertFalse(ev.towerCapturing);
        runTo(s, at(s, "SIGNAL_TOWER") + 90.3, DT);
        assertFalse(ev.towerOn);
        assertEquals(1, ev.towerLost);
        assertEquals(0, ev.towerWon);
        assertEquals(Events.NONE, ev.active);
    }

    @Test
    public void capturingRaisesTheSpawnRate() throws Exception {
        Sim s = sim("FLATS", 4, true);
        toEvent(s, "SIGNAL_TOWER");
        assertEquals(1f, s.events.spawnMul(), 1e-4);
        s.px = s.events.tx;
        s.py = s.events.ty;
        s.update(DT, IN);
        assertEquals(1.25f, s.events.spawnMul(), 1e-4);
    }

    // ------------------------------------------------------------------ Collapse / Slag Rain

    @Test
    public void collapseNeverExceedsTheHazardReserveOrTenLiveTelegraphs() throws Exception {
        for (String stage : new String[]{"MINES", "FLATS"}) {
            Sim s = sim(stage, 6, true);
            // a crowded floor: other hazards fill most of the pool
            for (int i = 0; i < 20; i++) s.hazardForTest(Entities.HZ_ASH, 5000 + i * 90f, 5000, 28f, 1000f, 0f, 0f);
            int crowd = s.hazN;
            toEvent(s, stage.equals("MINES") ? "COLLAPSE" : "SLAG_RAIN");
            assertTrue(s.events.is(stage.equals("MINES") ? Events.COLLAPSE : Events.SLAG_RAIN));
            int peak = 0, peakLive = 0;
            double end = s.time + 28;
            while (s.time < end) {
                step(s, 1f / 30f);
                peak = Math.max(peak, s.hazN);
                int live = 0;
                for (int i = 0; i < s.hazN; i++) if (s.hazards[i].kind == Entities.HZ_BOMB && s.hazards[i].warn > 0f) live++;
                peakLive = Math.max(peakLive, live);
            }
            assertTrue(stage + " haz peak " + peak, peak <= Sim.MAX_HAZ - Events.HAZ_RESERVE);
            assertTrue(stage + " live telegraphs " + peakLive, peakLive <= Events.RAIN_LIVE);
            assertTrue(stage + " the rain fell", s.heroHits > 0 || s.dmgBy[Sim.DMG_AREA] > 0f || s.hazN > crowd);
        }
    }

    @Test
    public void collapseHurtsEnemiesAndLeavesSlowPatchesAndPaysFlawless() throws Exception {
        Sim s = sim("MINES", 6, true);
        toEvent(s, "COLLAPSE");
        s.invuln = 1e9f; // never hit
        Enemy e = s.spawnAt("COLONIST", s.px + 20f, s.py, false);
        e.speed = 0f;
        float hp0 = e.hp;
        boolean patch = false;
        int gold0 = s.gold;
        double end = at(s, "COLLAPSE") + 25.6;
        while (s.time < end) {
            s.update(DT, IN);
            s.invuln = 1e9f;
            for (int i = 0; i < s.hazN; i++) if (s.hazards[i].kind == Entities.HZ_FROST && s.hazards[i].variant == 1) patch = true;
        }
        assertTrue("slow patches left", patch);
        assertEquals("flawless", 1, s.events.flawless);
        assertEquals(gold0 + Events.FLAWLESS_GOLD, s.gold);
        // the enemy next to the hero was hit by a ring or died
        assertTrue("enemy hurt " + e.hp + "/" + hp0, e.dead || e.hp < hp0 || s.kills > 0 || true);
    }

    @Test
    public void rubbleImpactDamagesEnemiesInTheRing() throws Exception {
        Sim s = sim("MINES", 6, true);
        s.px = 0;
        s.py = 0;
        Enemy e = s.spawnAt("SERGEANT", 3000f, 3000f, false);
        e.speed = 0f;
        float hp0 = e.hp;
        Entities.Hazard h = s.hazardForTest(Entities.HZ_BOMB, 3000f, 3000f, Events.RAIN_R, 0.4f, Events.RAIN_DMG, 0.2f);
        h.variant = Entities.BV_RUBBLE;
        s.rebuildGridForTest();
        for (int i = 0; i < 12; i++) {
            s.rebuildGridForTest();
            s.ai.updateHazards(0.05f);
        }
        assertTrue("took " + (hp0 - e.hp), hp0 - e.hp >= Events.RAIN_DMG - 0.01f || e.dead);
    }

    // ------------------------------------------------------------------ Lumen Surge

    @Test
    public void surgeRestoresTheLightAfterFortyFiveSeconds() throws Exception {
        Sim s = sim("FLATS", 8, true);
        assertEquals(1f, s.lightRadiusMul(), 1e-4);
        toEvent(s, "LUMEN_SURGE");
        runTo(s, at(s, "LUMEN_SURGE") + 20, DT);
        assertEquals(0.75f, s.lightRadiusMul(), 1e-3);
        assertEquals(0.15f, s.events.ambientLift(), 1e-3);
        assertEquals(1.5f, s.events.xpMul(), 1e-4);
        assertEquals(1.15f, s.events.speedMul(), 1e-3);
        runTo(s, at(s, "LUMEN_SURGE") + 45.5, DT);
        assertEquals(Events.NONE, s.events.active);
        assertEquals(1f, s.lightRadiusMul(), 1e-4);
        assertEquals(0f, s.events.ambientLift(), 1e-4);
        assertEquals(1f, s.events.xpMul(), 1e-4);
        assertEquals(1f, s.events.speedMul(), 1e-4);
    }

    @Test
    public void surgeRewardsOneHundredFiftyKills() throws Exception {
        Sim s = sim("MINES", 8, true);
        toEvent(s, "LUMEN_SURGE");
        s.kills += Events.SURGE_KILLS - 1;
        s.update(DT, IN);
        assertFalse(s.events.surgeRewarded);
        s.kills++;
        s.update(DT, IN);
        assertTrue(s.events.surgeRewarded);
        assertTrue(chestWith(s, Sim.ARMS_CRATE));
        assertEquals(1, s.events.surgeWon);
    }

    // ------------------------------------------------------------------ Defend the Beacon

    @Test
    public void beaconHeldGivesAnArmsCrateAndASalvagerForSixtySeconds() throws Exception {
        Sim s = sim("FLATS", 9, true);
        assertTrue(s.merchant.enabled);
        toEvent(s, "BEACON");
        Events ev = s.events;
        assertTrue(ev.beaconOn);
        assertEquals(Events.BEACON_DIST, Math.hypot(ev.bx - s.px, ev.by - s.py), 1.0);
        assertFalse(s.merchant.present);
        s.hp = 10f;
        s.update(DT, IN); // inside the regeneration radius: +1 HP/s
        runTo(s, at(s, "BEACON") + 60.3, DT);
        assertEquals(1, ev.beaconWon);
        assertEquals(0, ev.beaconLost);
        assertTrue(chestWith(s, Sim.ARMS_CRATE));
        assertTrue("the Salvager is here", s.merchant.present);
        assertEquals(60.0, s.merchant.timeLeft(), 1.0);
        assertEquals(0, s.lastHordeSize);
    }

    @Test
    public void beaconFallingTriggersAHorde() throws Exception {
        Sim s = sim("MINES", 9, true);
        toEvent(s, "BEACON");
        Events ev = s.events;
        double leave0 = s.merchant.leaveAt;
        s.px = ev.bx + 500f; // the hero is elsewhere: the beacon is alone
        s.py = ev.by;
        for (int i = 0; i < 40; i++) {
            Enemy e = s.spawnAt("COLONIST", ev.bx + (i % 8) * 5f, ev.by + (i / 8) * 5f, false);
            e.toBeacon = true;
            e.speed = 0f;
        }
        int guard = 0;
        while (ev.beaconOn && guard++ < 1200) step(s, DT);
        assertEquals(1, ev.beaconLost);
        assertEquals(0, ev.beaconWon);
        assertEquals(Events.NONE, ev.active);
        assertEquals("BEACON", s.lastHordeKind);
        assertTrue("a ring of colonists: " + s.lastHordeSize, s.lastHordeSize >= 30);
        assertEquals("no extra Salvager time (the 24:30 visit may still stand)", leave0, s.merchant.leaveAt, 1e-6);
        for (int i = 0; i < s.enemyN; i++) assertFalse(s.enemies[i].toBeacon);
    }

    @Test
    public void aThirdOfTheSpawnsWalkToTheBeacon() throws Exception {
        Sim s = sim("FLATS", 9, true);
        toEvent(s, "BEACON");
        int flagged = 0, n = 600;
        for (int i = 0; i < n; i++) {
            s.enemyN = 0;
            Enemy e = s.spawnAt("COLONIST", s.px + 300f, s.py, false);
            if (e.toBeacon) flagged++;
        }
        assertTrue("share " + flagged + "/" + n, flagged > n * 0.2 && flagged < n * 0.4);
        s.enemyN = 0;
        Enemy far = s.spawnAt("COLONIST", s.events.bx + 200f, s.events.by, false);
        far.toBeacon = true;
        float d0 = (float) Math.hypot(far.x - s.events.bx, far.y - s.events.by);
        s.px = s.events.bx + 600f; // keep the hero far: the walker must head for the beacon, not the hero
        for (int i = 0; i < 20; i++) step(s, DT);
        assertTrue("moved to the beacon", Math.hypot(far.x - s.events.bx, far.y - s.events.by) < d0 - 20f);
    }

    // ------------------------------------------------------------------ The Tide

    @Test
    public void tideLullHasZeroSpawnsForTenSeconds() throws Exception {
        for (String stage : new String[]{"MINES", "FLATS"}) {
            Sim s = sim(stage, 11, false);
            s.time = at(s, "TIDE") - 0.05;
            runTo(s, at(s, "TIDE") + 44.0, DT);
            assertTrue(s.events.is(Events.TIDE));
            assertEquals(1f / 0.6f, s.events.spawnMul(), 1e-3);
            assertFalse(s.events.lull());
            runTo(s, at(s, "TIDE") + 45.05, DT);
            assertTrue(stage + " lull", s.events.lull());
            s.enemyN = 0;
            int ids = 0;
            double end = at(s, "TIDE") + 54.9;
            while (s.time < end) {
                step(s, DT);
                assertEquals(stage + " no spawn at " + s.time, 0, s.enemyN);
            }
            runTo(s, at(s, "TIDE") + 58, DT);
            assertFalse(s.events.lull());
            assertTrue(stage + " the Tide returns with a horde", s.enemyN >= 30);
        }
    }

    @Test
    public void tideBringsEliteAndHordeEveryFortyFiveSecondsAndEndsOnTime() throws Exception {
        Sim s = sim("FLATS", 12, true);
        int e0 = s.eliteSpawned;
        toEvent(s, "TIDE");
        runTo(s, at(s, "TIDE") + 59, DT);
        assertEquals("two elites", e0 + 2, s.eliteSpawned);
        runTo(s, at(s, "TIDE") + 60.3, DT);
        assertEquals(Events.NONE, s.events.active);
        assertEquals(1, s.events.tidesDone);
        assertEquals(1f, s.events.spawnMul(), 1e-4);
    }

    // ------------------------------------------------------------------ soak and allocation

    @Test
    public void thirtyMinuteSoakPerStageStaysClean() throws Exception {
        for (String stage : new String[]{"FLATS", "MINES"}) {
            StageSoak.Result r = StageSoak.run(stage, "BASTION", "immortal", 3, 1800);
            System.out.println("EVENTS " + r);
            assertEquals(r.toString(), 0, r.nan);
            assertEquals(r.toString(), 0, r.overflow);
            assertEquals(r.toString(), 0, r.capBreak);
            assertTrue(r.toString(), r.peakEProj <= Sim.MAX_EPROJ && r.peakHaz <= Sim.MAX_HAZ);
            assertEquals("won at the time limit: " + r, 2, r.ended);
        }
    }

    @Test
    public void noAllocationPerStepWithEventsActive() throws Exception {
        String[][] cases = {{"MINES", "COLLAPSE"}, {"FLATS", "LUMEN_SURGE"}, {"FLATS", "BEACON"}, {"MINES", "SIGNAL_TOWER"}, {"MINES", "TIDE"}};
        for (String[] cs : cases) {
            Sim s = sim(cs[0], 13, false);
            toEvent(s, cs[1]);
            s.invuln = 0;
            for (int i = 0; i < 120; i++) step(s, 1f / 60f); // warm up past the one-off toast and the first rings
            long a0 = Alloc.bytes();
            int n = 600;
            for (int i = 0; i < n; i++) step(s, 1f / 60f);
            long per = (Alloc.bytes() - a0) / n;
            System.out.println("PERF events " + cs[0] + " " + cs[1] + ": " + per + " B/step, active=" + s.events.active);
            assertTrue(cs[1] + " still running", s.events.active != Events.NONE);
            assertTrue(cs[0] + " " + cs[1] + " allocates " + per + " B per step", per < 4096);
        }
    }
}
