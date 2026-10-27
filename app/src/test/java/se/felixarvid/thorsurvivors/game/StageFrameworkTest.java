package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.StageDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.stage.FlatsRules;
import se.felixarvid.thorsurvivors.game.stage.HydroRules;
import se.felixarvid.thorsurvivors.game.stage.MinesRules;
import se.felixarvid.thorsurvivors.game.stage.PlainRules;
import se.felixarvid.thorsurvivors.game.stage.RefineryRules;
import se.felixarvid.thorsurvivors.game.stage.StageRules;
import se.felixarvid.thorsurvivors.game.stage.WreckRules;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

/** Stage framework (v1.4 chunk A): rules per stage, layouts, unlocks, stage select. */
public class StageFrameworkTest {
    /** Folds a float into a running 64 bit hash by its exact bits (any behaviour change moves it). */
    private static long mix(long h, float v) {
        return (h ^ Float.floatToRawIntBits(v)) * 0x100000001B3L + 0x9E3779B97F4A7C15L;
    }

    private static long mix(long h, long v) {
        return (h ^ v) * 0x100000001B3L + 0x9E3779B97F4A7C15L;
    }

    /**
     * A seeded run with a scripted bot (circling, aiming at the nearest enemy, first level-up card, never dying) from run
     * time {@code from} for {@code seconds}; hashes the hero, every enemy and the counters each second, and the storm
     * and light values.
     */
    static long stateHash(String stage, long seed, double from, double seconds) throws Exception {
        GameData d = StageSoak.data();
        Sim s = new Sim(d, d.stages.get(stage), null, new HashMap<String, Double>(), "BASTION", seed);
        s.time = from;
        Random rng = new Random(seed * 31 + 5);
        Input in = new Input();
        float dt = 1f / 60f;
        long h = 1125899906842597L;
        int steps = (int) (seconds * 60);
        for (int i = 0; i < steps; i++) {
            double a = s.time * 0.7;
            in.stickX = (float) Math.cos(a);
            in.stickY = (float) Math.sin(a);
            Enemy best = null;
            float bd = 1e18f;
            for (int k = 0; k < s.enemyN; k++) {
                Enemy e = s.enemies[k];
                float dx = e.x - s.px, dy = e.y - s.py, q = dx * dx + dy * dy;
                if (q < bd) {
                    bd = q;
                    best = e;
                }
            }
            if (best != null) {
                float dx = best.x - s.px, dy = best.y - s.py, m = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
                in.aimStickX = dx / m;
                in.aimStickY = dy / m;
            }
            s.hp = s.maxHp;
            s.update(dt, in);
            if (s.ended == 1) {
                s.ended = 0;
                s.hp = s.maxHp;
            }
            while (s.pendingLevelUps > 0) {
                List<Offer> o = OfferGenerator.generate(s.inv, d.weapons.values(), s.stats.luck, rng,
                        s.main == null ? null : s.main.def, s.main == null ? null : s.main.tier, s.level);
                s.applyOffer(o.get(0));
                s.pendingLevelUps--;
            }
            while (s.pendingChests > 0) s.openChest();
            while (s.equipN > 0) s.resolveEquip(true);
            s.sound.clear();
            if (i % 60 == 59) {
                h = mix(h, s.px);
                h = mix(h, s.py);
                h = mix(h, s.hp);
                h = mix(h, s.xp);
                h = mix(h, (long) s.kills * 1000003L + s.gold * 31L + s.level);
                h = mix(h, s.stormLevel());
                h = mix(h, s.lightRadiusMul());
                h = mix(h, s.enemyN * 1000L + s.pickN * 10L + s.eprojN + s.hazN * 100000L);
                for (int k = 0; k < s.enemyN; k++) {
                    Enemy e = s.enemies[k];
                    h = mix(h, e.x);
                    h = mix(h, e.y);
                    h = mix(h, e.hp);
                }
            }
        }
        h = mix(h, s.rng.nextLong());
        return h;
    }

    /**
     * State hashes of seeded runs recorded on main (v1.3.1, before the framework): five minutes of each old stage, and a window
     * over the Flats ash storm (12:00) so the moved storm code is covered. Any change to Flats or Mines behaviour moves them.
     */
    @Test
    public void flatsAndMinesRunsAreBitIdenticalToBefore() throws Exception {
        assertEquals("FLATS 5 min", -1522504880372771192L, stateHash("FLATS", 12345L, 0, 300));
        assertEquals("MINES 5 min", -875118384609229854L, stateHash("MINES", 12345L, 0, 300));
        assertEquals("FLATS storm window", 3223518685959396603L, stateHash("FLATS", 777L, 705, 60));
        assertEquals("MINES same window", -3313226611156784373L, stateHash("MINES", 777L, 705, 60));
    }

    // ------------------------------------------------------------------ keys, rules, layouts

    private static final String[] KEYS = {"FLATS", "MINES", "HYDRO", "REFINERY", "WRECK"};
    private static final int[] LAYOUTS = {WorldGen.L_FLATS, WorldGen.L_MINES, WorldGen.L_DOMES, WorldGen.L_STREETS, WorldGen.L_DEBRIS};
    private static final Class<?>[] RULES = {FlatsRules.class, MinesRules.class, HydroRules.class, RefineryRules.class, WreckRules.class};

    @Test
    public void allFiveStagesLoadWithTheirRulesLayoutAndCardData() throws Exception {
        GameData d = TestData.load();
        assertEquals(java.util.Arrays.asList(KEYS), java.util.Arrays.asList(GameData.STAGE_ORDER));
        String prev = null;
        for (int i = 0; i < KEYS.length; i++) {
            StageDef def = d.stages.get(KEYS[i]);
            assertNotNull(KEYS[i], def);
            assertEquals(KEYS[i], 1800.0, def.timeLimit, 0);
            assertEquals(KEYS[i] + " difficulty rises along the order", i + 1, def.difficulty);
            assertTrue(KEYS[i] + " blurb fits the card: " + def.blurb, def.blurb.length() > 10 && def.blurb.length() <= 48);
            assertEquals(KEYS[i].equals("REFINERY") ? 6 : 5, def.timedAt.length); // the Refinery adds the finale at 28:00 ...
            assertEquals(KEYS[i].equals("REFINERY") ? 4 : 5, def.eventAt.length); // ... in place of The Tide
            assertNotNull(def.rules);
            if (i == 0) assertEquals(null, def.unlockKey);
            else assertEquals("each stage opens on the one before it", prev, def.unlockKey);
            if (i > 0) assertTrue(def.unlockSec >= 600 && def.unlockSec <= 1500);
            if (i > 1) assertTrue(KEYS[i] + " opens later than the stage before", def.unlockSec > d.stages.get(KEYS[i - 1]).unlockSec);
            assertTrue(KEYS[i] + " has lighting", d.lightingOf(KEYS[i])[0] > 0.5f);
            assertEquals(KEYS[i], LAYOUTS[i], WorldGen.of(KEYS[i]).layout);
            Sim s = new Sim(d, def, null, new HashMap<String, Double>(), "BASTION", 3L);
            assertNotNull(s.rules);
            assertEquals(KEYS[i], RULES[i], s.rules.getClass());
            assertEquals(WorldGen.of(KEYS[i]), s.map);
            assertTrue("the Ashfall stages place finds", s.findN > 0);
            prev = KEYS[i];
        }
        assertEquals(600, d.stages.get("MINES").unlockSec);
        assertEquals(900, d.stages.get("HYDRO").unlockSec);
        assertEquals(1200, d.stages.get("REFINERY").unlockSec);
        assertEquals(1500, d.stages.get("WRECK").unlockSec);
        assertEquals("FLATS", d.stages.get("MINES").unlockKey);
        assertEquals(StageRules.class, StageRules.forKey("FOREST").getClass().getSuperclass());
        assertTrue(StageRules.forKey("LIBRARY") instanceof PlainRules);
        assertFalse("the legacy stages have no finds", StageRules.forKey("FOREST").hasFinds());
        assertTrue("the old WorldGen mines flag is derived from the layout", WorldGen.of("MINES").mines && !WorldGen.of("HYDRO").mines);
        assertEquals(WorldGen.L_FLATS, WorldGen.of("FOREST").layout);
        assertEquals(WorldGen.L_MINES, WorldGen.of("LIBRARY").layout);
    }

    @Test
    public void everyHookDefaultsToNothing() throws Exception {
        GameData d = TestData.load();
        float[] out = {7f, 7f};
        for (String key : new String[]{"MINES", "HYDRO", "REFINERY", "WRECK"}) {
            Sim s = new Sim(d, d.stages.get(key), null, new HashMap<String, Double>(), "BASTION", 3L);
            StageRules r = s.rules;
            assertFalse(key, r.push(10f, 20f, out));
            assertEquals("push leaves out alone when it returns false", 7f, out[0], 0f);
            Enemy e = s.addEnemy("COLONIST", 100f, 100f, false);
            assertEquals(1f, r.enemySpeedMul(e), 0f);
            assertEquals(1f, r.heroSpeedMul(), 0f);
            assertEquals(1f, r.spawnMul(), 0f);
            assertEquals(1f, r.lightMul(), 0f);
            assertEquals(0f, r.ambientLift(), 0f);
            assertEquals(5f, r.damageIn(e, 5f, StageRules.CAT_BURN), 0f);
            assertFalse(r.updateArch(e, 0.016f, true, 1f, 0f, 1f));
            assertEquals(0f, s.stormLevel(), 0f);
            assertEquals(-1f, s.stormElapsed(), 0f);
        }
        Sim f = new Sim(d, d.stages.get("FLATS"), null, new HashMap<String, Double>(), "BASTION", 3L);
        f.time = 730;
        assertEquals(((FlatsRules) f.rules).stormLevel(f.time), f.stormLevel(), 0f);
        assertTrue(f.stormLevel() > 0f && f.ashStorm());
        assertEquals(1f + FlatsRules.STORM_SPAWNS, f.rules.spawnMul(), 1e-6f);
        assertEquals(0.6f, f.lightRadiusMul(), 1e-6f);
    }

    @Test
    public void layoutsAreDeterministicClearAtTheStartAndStubsReportNothing() {
        float[] xs = new float[WorldGen.MAX_LIST], ys = new float[WorldGen.MAX_LIST], xs2 = new float[WorldGen.MAX_LIST],
                ys2 = new float[WorldGen.MAX_LIST], pad = new float[4];
        int[] ks = new int[WorldGen.MAX_LIST], ks2 = new int[WorldGen.MAX_LIST];
        for (String key : new String[]{"HYDRO", "REFINERY", "WRECK"}) {
            WorldGen g = WorldGen.of(key);
            int props = 0, decals = 0;
            for (int cx = -6; cx <= 6; cx++) {
                for (int cy = -6; cy <= 6; cy++) {
                    int n = g.props(cx * 8, cy * 8, cx * 8 + 7, cy * 8 + 7, xs, ys, ks);
                    int n2 = g.props(cx * 8, cy * 8, cx * 8 + 7, cy * 8 + 7, xs2, ys2, ks2);
                    assertEquals(key + " props repeat", n, n2);
                    for (int i = 0; i < n; i++) {
                        assertEquals(xs[i], xs2[i], 0f);
                        assertEquals(ks[i], ks2[i]);
                        assertTrue(key + " kind", ks[i] >= 0 && ks[i] < WorldGen.PROP.length);
                        assertTrue(key + " keeps the start clear", Math.hypot(xs[i], ys[i]) >= WorldGen.CLEAR);
                    }
                    props += n;
                    int dn = g.decals(cx * 8, cy * 8, cx * 8 + 7, cy * 8 + 7, xs, ys, ks);
                    for (int i = 0; i < dn; i++) assertTrue((ks[i] & 255) < WorldGen.DECAL.length);
                    decals += dn;
                    assertEquals(WorldGen.SURF_NONE, g.surface(cx, cy));
                    assertFalse(g.pad(cx, cy, pad));
                    assertFalse(g.well(cx, cy, pad));
                }
            }
            assertTrue(key + " has a field: " + props + " props", props > 150);
            assertTrue(key + " has decals: " + decals, decals > 150);
            assertFalse(key + " start is not blocked", g.blocked(0f, 0f));
        }
    }

    @Test
    public void everyNewStageRunsFiveMinutesWithoutNaNOrOverflow() throws Exception {
        for (String key : new String[]{"HYDRO", "REFINERY", "WRECK"}) {
            for (String policy : new String[]{"immortal", "mid"}) {
                StageSoak.Result r = StageSoak.run(key, "BASTION", policy, 11, 300);
                System.out.println("STAGE " + r);
                assertEquals(key + " " + policy + " " + r.firstProblem, 0, r.nan);
                assertEquals(key + " " + policy + " " + r.firstProblem, 0, r.overflow);
                assertEquals(key + " " + policy + " " + r.firstProblem, 0, r.capBreak);
                assertTrue(key + " " + policy + " kills: " + r.kills, r.kills > 100);
            }
        }
    }

    // ------------------------------------------------------------------ unlocks

    @Test
    public void stagesUnlockAlongTheChainFromBestTimesAndClearedFlags() throws Exception {
        GameData d = TestData.load();
        MetaProgressTest.MemStore store = new MetaProgressTest.MemStore();
        MetaProgress m = new MetaProgress(d, store);
        assertTrue(m.unlocked("FLATS"));
        for (int i = 1; i < KEYS.length; i++) assertFalse(KEYS[i] + " starts locked", m.unlocked(KEYS[i]));
        assertEquals("Reach 10:00 on Cinder Flats (best 00:00)", m.unlockLine("MINES"));
        assertEquals("", m.unlockLine("FLATS"));
        m.recordRun(599, 50, "FLATS");
        assertFalse("one second short", m.unlocked("MINES"));
        m.recordRun(600, 50, "FLATS");
        assertTrue(m.unlocked("MINES"));
        assertFalse(m.unlocked("HYDRO"));
        m.recordRun(800, 50, "MINES");
        assertEquals("Reach 15:00 on Deep Mines (best 13:20)", m.unlockLine("HYDRO"));
        m.recordRun(900, 50, "MINES");
        assertTrue(m.unlocked("HYDRO"));
        assertFalse("the chain is not skipped by a long run elsewhere", m.unlocked("REFINERY"));
        m.recordRun(1790, 50, "FLATS");
        assertFalse(m.unlocked("WRECK"));
        m.markCleared("HYDRO");
        assertTrue("a cleared stage opens the next one", m.unlocked("REFINERY"));
        assertTrue(m.cleared("HYDRO"));
        assertFalse(m.cleared("MINES"));
        // saved: a new MetaProgress over the same store sees the same
        MetaProgress again = new MetaProgress(d, store);
        assertTrue(again.unlocked("REFINERY") && again.cleared("HYDRO") && again.unlocked("HYDRO"));
        assertFalse(again.unlocked("WRECK"));
        // the debug switch opens everything, and is a saved pref
        assertFalse(again.unlockAll);
        again.unlockAll = true;
        assertTrue(again.unlocked("WRECK"));
        store.putInt("unlockAll", 1);
        assertTrue(new MetaProgress(d, store).unlocked("WRECK"));
    }

    // ------------------------------------------------------------------ stage select

    private Engine menu(MetaProgress mp) throws Exception {
        Engine e = Engine.get();
        GameData d = TestData.load();
        e.init(d, mp);
        e.meta = mp;
        e.input.clearMovement();
        e.phase = Engine.PH_MENU;
        e.focus = Engine.MENU_STAGE;
        e.stageIdx = 0;
        e.stageOverride = null;
        e.sound.clear();
        return e;
    }

    @Test
    public void stageSelectWalksAllFiveIncludingLockedOnesAndRefusesToStartThem() throws Exception {
        GameData d = TestData.load();
        Engine e = menu(new MetaProgress(d, new MetaProgressTest.MemStore()));
        assertEquals(5, e.stageKeys.size());
        for (int i = 1; i <= 5; i++) { // right, right ... skips nothing and wraps
            e.input.post(Input.CMD_NAV, Input.RIGHT, 0, 0);
            e.advance(1f / 60f);
            assertEquals(KEYS[i % 5], e.stage().key);
        }
        for (int i = 4; i >= 0; i--) {
            e.input.post(Input.CMD_NAV, Input.LEFT, 0, 0);
            e.advance(1f / 60f);
            assertEquals(KEYS[i], e.stage().key);
        }
        e.input.post(Input.CMD_TAP, Engine.PH_MENU, Engine.MENU_STAGE, 3); // a tap on the 4th thumbnail jumps there
        e.advance(1f / 60f);
        assertEquals("REFINERY", e.stage().key);
        // A on START RUN with a locked stage: no run, a shake, the buzz
        e.focus = Engine.MENU_START;
        e.sound.clear();
        e.input.post(Input.CMD_CONFIRM, 0, 0, 0);
        e.advance(1f / 60f);
        assertEquals(Engine.PH_MENU, e.phase);
        assertTrue("the card shakes", e.lockShake > 0f);
        assertEquals(SoundEvents.UI_BACK, SoundEvents.idOf(e.sound.poll()));
        assertTrue(e.toast.startsWith("Locked: Reach 20:00 on Hydroponic Vaults"));
        for (int i = 0; i < 40; i++) e.advance(1f / 60f);
        assertEquals("the shake dies out", 0f, Math.max(0f, e.lockShake), 0f);
        // the start button of the pad does the same
        e.input.post(Input.CMD_START, 0, 0, 0);
        e.advance(1f / 60f);
        assertEquals(Engine.PH_MENU, e.phase);
        // open it and the run starts on that stage
        e.meta.unlockAll = true;
        e.nextRunSeed = 5L;
        e.input.post(Input.CMD_START, 0, 0, 0);
        e.advance(1f / 60f);
        assertEquals(Engine.PH_PLAYING, e.phase);
        assertEquals("REFINERY", e.sim.stage.key);
        e.phase = Engine.PH_MENU; // the Engine is a JVM-wide singleton: leave the first stage selected for the other tests
        e.selectStage("FLATS");
    }
}
