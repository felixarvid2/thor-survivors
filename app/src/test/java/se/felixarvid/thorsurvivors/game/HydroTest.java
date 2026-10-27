package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.layout.DomesLayout;
import se.felixarvid.thorsurvivors.game.stage.HydroRules;
import se.felixarvid.thorsurvivors.game.stage.StageRules;

import java.util.HashMap;

/** Hydroponic Vaults (v1.4 chunk B): the DOMES layout, Bloom Hearts and patches, water, Sporefall, Gardener, the Rootmind, events and the soak. */
public class HydroTest {
    private static final float DT = 1f / 60f;

    private static Sim sim(long seed) throws Exception {
        GameData d = StageSoak.data();
        Sim s = new Sim(d, d.stages.get("HYDRO"), null, new HashMap<String, Double>(), "BASTION", seed);
        s.noSpawnsForTest();
        s.findN = 0;
        s.merchant.enabled = false;
        s.pois.enabled = false;
        return s;
    }

    private static HydroRules rules(Sim s) {
        return (HydroRules) s.rules;
    }

    /** Steps {@code seconds} with an immortal hero standing still. */
    private static void step(Sim s, double seconds) {
        Input in = new Input();
        int n = (int) Math.round(seconds * 60);
        for (int i = 0; i < n && s.ended == 0; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
            s.sound.clear();
        }
    }

    // ------------------------------------------------------------------ layout

    private static long layoutHash() {
        WorldGen g = WorldGen.of("HYDRO");
        float[] xs = new float[WorldGen.MAX_LIST], ys = new float[WorldGen.MAX_LIST];
        int[] ks = new int[WorldGen.MAX_LIST];
        long h = 1469598103934665603L;
        for (int cx = 0; cx < 3; cx++) {
            int n = g.props(cx * 8, 0, cx * 8 + 7, 7, xs, ys, ks);
            h = (h ^ n) * 1099511628211L;
            for (int i = 0; i < n; i++) {
                h = (h ^ Float.floatToRawIntBits(xs[i])) * 1099511628211L;
                h = (h ^ Float.floatToRawIntBits(ys[i])) * 1099511628211L;
                h = (h ^ ks[i]) * 1099511628211L;
            }
            n = g.decals(cx * 8, 0, cx * 8 + 7, 7, xs, ys, ks);
            for (int i = 0; i < n; i++) h = (h ^ ks[i]) * 1099511628211L;
        }
        return h;
    }

    @Test
    public void domesLayoutIsDeterministicAndShapedLikeDomes() {
        assertEquals(layoutHash(), layoutHash());
        int domes = 0, flooded = 0, cells = 0;
        for (int cy = -20; cy < 20; cy++) {
            for (int cx = -20; cx < 20; cx++) {
                cells++;
                if (DomesLayout.dome(cx, cy)) domes++;
                if (DomesLayout.flooded(cx, cy)) flooded++;
            }
        }
        assertTrue("about 70% domes: " + domes + "/" + cells, domes > cells * 0.62 && domes < cells * 0.78);
        assertTrue("about 15% of the domes flooded: " + flooded + "/" + domes, flooded > domes * 0.08 && flooded < domes * 0.24);
        for (int cy = -1; cy <= 0; cy++) for (int cx = -1; cx <= 0; cx++) assertFalse("no flood at the start", DomesLayout.flooded(cx, cy));
        // a ring: planters lie 5.5 tiles from the centre of a dome, with a gap at the compass points
        WorldGen g = WorldGen.of("HYDRO");
        int cx = 0, cy = 0;
        outer:
        for (cy = 2; cy < 30; cy++) {
            for (cx = 2; cx < 30; cx++) if (DomesLayout.dome(cx, cy)) break outer;
        }
        float mx = DomesLayout.centerX(cx), my = DomesLayout.centerX(cy);
        float[] xs = new float[WorldGen.MAX_LIST], ys = new float[WorldGen.MAX_LIST];
        int[] ks = new int[WorldGen.MAX_LIST];
        int ring = 0, gapHits = 0;
        for (int ty = cy * 16; ty < cy * 16 + 16; ty += 8) {
            for (int tx = cx * 16; tx < cx * 16 + 16; tx += 8) {
                int n = g.props(tx, ty, tx + 7, ty + 7, xs, ys, ks);
                for (int i = 0; i < n; i++) {
                    float d = (float) Math.hypot(xs[i] - mx, ys[i] - my);
                    if (Math.abs(d - DomesLayout.RING_R) < 0.01f) {
                        ring++;
                        if (Math.abs(xs[i] - mx) < 0.9f || Math.abs(ys[i] - my) < 0.9f) gapHits++;
                    }
                    assertFalse("the interior is free", d < DomesLayout.RING_R - 0.5f && ks[i] != WorldGen.P_LIGHT && ks[i] != WorldGen.P_CRATE
                            && ks[i] != WorldGen.P_NEST && ks[i] != WorldGen.P_TANKER && ks[i] != WorldGen.P_GENERATOR);
                }
            }
        }
        assertTrue("a ring of planters: " + ring, ring >= 18 && ring <= 28);
        assertEquals("the compass gaps are open", 0, gapHits);
    }

    @Test
    public void floodedDomesAreWaterInsideTheirRadius() {
        int found = 0;
        for (int cy = -20; cy < 20 && found < 3; cy++) {
            for (int cx = -20; cx < 20 && found < 3; cx++) {
                if (!DomesLayout.flooded(cx, cy)) continue;
                found++;
                WorldGen g = WorldGen.of("HYDRO");
                int mx = (int) DomesLayout.centerX(cx), my = (int) DomesLayout.centerX(cy);
                assertTrue(g.surface(mx, my) == WorldGen.SURF_WATER);
                assertTrue(g.surface(mx + 4, my) == WorldGen.SURF_WATER);
                assertEquals(WorldGen.SURF_NONE, g.surface(mx + 6, my));
                assertEquals(WorldGen.SURF_NONE, g.surface(cx * 16, cy * 16));
            }
        }
        assertEquals(3, found);
    }

    // ------------------------------------------------------------------ hearts and patches

    @Test
    public void patchRadiusFollowsTheFormula() {
        assertEquals(60f, HydroRules.patchRadius(0f), 1e-4f);
        assertEquals(120f, HydroRules.patchRadius(30f), 1e-4f);
        assertEquals(180f, HydroRules.patchRadius(60f), 1e-4f);
        assertEquals("capped at 180", 180f, HydroRules.patchRadius(500f), 1e-4f);
        assertEquals(60f + 6f, HydroRules.heartHealth(60.0), 1e-3f);
        assertEquals(60f + 6f * 28f, HydroRules.heartHealth(28 * 60.0), 1e-3f);
    }

    @Test
    public void heartsComeAt2MinutesAndNeverExceedTheCaps() throws Exception {
        Sim s = sim(5);
        HydroRules r = rules(s);
        step(s, 119);
        assertEquals("none before 2:00", 0, r.heartN);
        step(s, 2);
        assertEquals("the first at 2:00", 1, r.heartN);
        float d = (float) Math.hypot(r.hx[0] - s.px, r.hy[0] - s.py);
        assertTrue("600-900 u away: " + d, d >= 590f && d <= 910f);
        int peak = r.heartN;
        for (int i = 0; i < 24; i++) {
            step(s, 30);
            peak = Math.max(peak, r.heartN);
            assertTrue("hearts: " + r.heartN, r.heartN <= HydroRules.SOFT_HEARTS);
        }
        assertEquals("six alive when nobody kills them", HydroRules.SOFT_HEARTS, peak);
        // event hearts may go to the hard cap, never past it
        for (int i = 0; i < 6; i++) r.addHeart(5000f + i * 400f, 0f, true);
        assertEquals(HydroRules.MAX_HEARTS, r.heartN);
        assertFalse(r.addHeart(0f, 9000f, true));
        assertEquals(HydroRules.MAX_HEARTS, r.heartN);
    }

    @Test
    public void aHeartLeftAlone150SecondsSeedsANeighbour() throws Exception {
        Sim s = sim(6);
        HydroRules r = rules(s);
        r.heartsOn = true;
        r.addHeart(2000f, 0f, true);
        step(s, 149);
        int before = r.heartN;
        step(s, 3);
        assertEquals(before + 1, r.heartN);
        float d = (float) Math.hypot(r.hx[1] - r.hx[0], r.hy[1] - r.hy[0]);
        assertTrue("seeded 220-300 u away: " + d, d >= 200f && d <= 330f);
    }

    @Test
    public void regenerationIsInsideThePatchOnly() throws Exception {
        Sim s = sim(7);
        HydroRules r = rules(s);
        r.heartsOn = false;
        s.time = 600;
        r.addHeart(500f, 0f, true);
        Enemy in = s.spawnAt("SERGEANT", 500f, 30f, false), out = s.spawnAt("SERGEANT", 500f, 400f, false);
        in.speed = out.speed = 0f;
        in.hp = out.hp = in.maxHp * 0.5f;
        float inHp = in.hp, outHp = out.hp;
        step(s, 1.0);
        assertEquals("2.5% of max a second inside", inHp + in.maxHp * 0.025f, in.hp, in.maxHp * 0.004f);
        assertEquals("nothing outside", outHp, out.hp, 1e-3f);
        s.applyStatus(in, Entities.ST_DECAY, 3, 20f, 0f);
        assertTrue(in.decay > 0);
        step(s, 0.3);
        assertEquals("Decay is cleared inside", 0, in.decay);
    }

    @Test
    public void theHeroSlowsAndStopsRegeneratingInAPatch() throws Exception {
        Sim s = sim(8);
        HydroRules r = rules(s);
        r.heartsOn = false;
        assertEquals(1f, r.heroSpeedMul(), 0f);
        assertEquals(1f, r.regenMul(), 0f);
        r.addHeart(s.px + 20f, s.py, true);
        assertEquals(0.8f, r.heroSpeedMul(), 1e-6f);
        assertEquals(0f, r.regenMul(), 0f);
        assertEquals("spawns +20% with 3+ hearts", 1f, r.spawnMul(), 0f);
        r.addHeart(3000f, 0f, true);
        r.addHeart(3000f, 900f, true);
        assertEquals(1.2f, r.spawnMul(), 1e-6f);
    }

    private static float killTime(boolean burn) throws Exception {
        Sim s = sim(9);
        HydroRules r = rules(s);
        r.heartsOn = false;
        s.time = 600; // 10:00: 120 health
        r.addHeart(300f, 0f, true);
        step(s, 0.05);
        assertEquals(1, r.heartN);
        Enemy e = null;
        if (burn) {
            e = s.spawnAt("SMELTER", 310f, 10f, false);
            e.speed = 0f;
            e.hp = e.maxHp = 1e6f;
            s.applyStatus(e, Entities.ST_BURN, 8, 100f, 0f);
        }
        float t = 0f;
        while (r.heartN > 0 && t < 40f) {
            if (burn) s.applyStatus(e, Entities.ST_BURN, 1, 100f, 0f);
            else if (Math.round(t * 60) % 30 == 0) s.hitDestForTest(300f, 0f, 20f); // a weapon hit every 0.5 s
            step(s, 1.0 / 60);
            t += DT;
        }
        return t;
    }

    @Test
    public void fireKillsAHeartFasterThanKineticHits() throws Exception {
        float burn = killTime(true), kinetic = killTime(false);
        System.out.println("HYDRO heart kill: burn " + burn + " s, kinetic " + kinetic + " s");
        assertTrue("burn under 8 s: " + burn, burn < 8f);
        assertTrue("kinetic under 25 s: " + kinetic, kinetic < 25f);
        assertTrue("burn faster: " + burn + " vs " + kinetic, burn < kinetic);
    }

    @Test
    public void aBlastDoes80AndAHeartDropsGemsAndFadesItsPatch() throws Exception {
        Sim s = sim(10);
        HydroRules r = rules(s);
        r.heartsOn = false;
        r.addHeart(300f, 0f, true);
        step(s, 0.05);
        float hp = r.hHp[0];
        s.explodeBarrel(300f, 0f);
        assertTrue("a blast does 80: " + hp + " -> " + r.hHp[0], r.heartN == 0 || hp - r.hHp[0] >= 79f);
        // finish it and look at the drops
        if (r.heartN > 0) {
            s.hitDestForTest(300f, 0f, 20f);
            for (int i = 0; i < 20 && r.heartN > 0; i++) s.explodeBarrel(300f, 0f);
        }
        assertEquals(0, r.heartN);
        assertEquals(1, r.heartsKilled);
        assertTrue("the patch fades: still a patch for a moment", r.inPatch(300f, 0f));
        step(s, 2.2);
        assertFalse("then gone", r.inPatch(300f, 0f));
        assertTrue("gems dropped", s.pickN >= 3);
    }

    // ------------------------------------------------------------------ water

    @Test
    public void aTankerFloodSlowsEnemiesBoostsElectrifyAndPutsOutBurn() throws Exception {
        Sim s = sim(11);
        HydroRules r = rules(s);
        r.destBroken(Sim.DK_TANKER, 600f, 0f, 1L);
        assertEquals(1, r.flN);
        Enemy in = s.spawnAt("COLONIST", 650f, 0f, false), out = s.spawnAt("COLONIST", 1200f, 0f, false);
        assertEquals(0.6f, r.enemySpeedMul(in), 1e-6f);
        assertEquals(1f, r.enemySpeedMul(out), 1e-6f);
        assertEquals(15f, r.damageIn(in, 10f, StageRules.CAT_ELECTRIFY), 1e-5f);
        assertEquals(10f, r.damageIn(out, 10f, StageRules.CAT_ELECTRIFY), 1e-5f);
        assertEquals(0f, r.damageIn(in, 10f, StageRules.CAT_BURN), 0f);
        assertEquals(10f, r.damageIn(out, 10f, StageRules.CAT_BURN), 0f);
        in.speed = 0f;
        s.applyStatus(in, Entities.ST_BURN, 3, 50f, 0f);
        step(s, 0.3);
        assertEquals("burn put out", 0, in.burn);
        step(s, 10.5);
        assertEquals("the flood dries after 10 s", 0, r.flN);
        assertEquals(1f, r.enemySpeedMul(in), 1e-6f);
    }

    @Test
    public void floodedDomeSlowsAndHurtsWithElectrify() throws Exception {
        Sim s = sim(12);
        HydroRules r = rules(s);
        int fx = 0, fy = 0;
        boolean found = false;
        for (int cy = -20; cy < 20 && !found; cy++) for (int cx = -20; cx < 20 && !found; cx++) if (DomesLayout.flooded(cx, cy)) { fx = cx; fy = cy; found = true; }
        float wx = DomesLayout.centerX(fx) * WorldGen.TILE, wy = DomesLayout.centerX(fy) * WorldGen.TILE;
        Enemy e = s.spawnAt("COLONIST", wx, wy, false);
        assertTrue(r.inWater(wx, wy));
        assertEquals(0.85f, r.enemySpeedMul(e), 1e-6f);
        assertEquals(15f, r.damageIn(e, 10f, StageRules.CAT_ELECTRIFY), 1e-5f);
        s.px = wx;
        s.py = wy;
        assertEquals(0.85f, r.heroSpeedMul(), 1e-6f);
    }

    @Test
    public void aFeederHealsTheHeroAndDecaysNearbyEnemies() throws Exception {
        Sim s = sim(13);
        HydroRules r = rules(s);
        s.hp = s.maxHp * 0.5f;
        Enemy e = s.spawnAt("SERGEANT", 100f, 0f, false);
        e.speed = 0f;
        float hp0 = s.hp;
        r.destBroken(Sim.DK_FEEDER, 90f, 0f, 2L);
        assertTrue("20% heal", s.hp >= hp0 + s.maxHp * 0.19f);
        s.hp = s.maxHp * 0.5f;
        s.stats.healMul = 1;
        step(s, 1.0);
        assertTrue("decay on the enemy", e.decay > 0);
        step(s, 4.5);
        assertEquals("the cloud ends after 4 s", 0, r.fdN);
    }

    @Test
    public void destructiblesOfTheStageAreTankersFeedersAndBarrels() throws Exception {
        Sim s = sim(14);
        int[] n = new int[8];
        for (int cy = -40; cy < 40; cy++) {
            for (int cx = -40; cx < 40; cx++) {
                if (!s.destPlace(cx, cy)) continue;
                n[s.destPlaceKind()]++;
            }
        }
        int total = n[Sim.DK_BARREL] + n[Sim.DK_TANKER] + n[Sim.DK_FEEDER];
        System.out.println("HYDRO destructibles " + java.util.Arrays.toString(n));
        assertEquals("only those three kinds", total, n[1] + n[2] + n[3] + n[4] + n[5] + n[6] + n[7] + n[0]);
        assertTrue("tankers ~35%: " + n[Sim.DK_TANKER] + "/" + total, n[Sim.DK_TANKER] > total * 0.28 && n[Sim.DK_TANKER] < total * 0.42);
        assertTrue("feeders ~25%", n[Sim.DK_FEEDER] > total * 0.18 && n[Sim.DK_FEEDER] < total * 0.32);
        assertTrue("barrels ~40%", n[Sim.DK_BARREL] > total * 0.32 && n[Sim.DK_BARREL] < total * 0.48);
    }

    // ------------------------------------------------------------------ Sporefall

    @Test
    public void sporefallDropsPodsWithinTheCapsAndRespectsTheHazardReserve() throws Exception {
        Sim s = sim(15);
        HydroRules r = rules(s);
        r.startSporefall(20f, false);
        int maxWarn = 0, maxHaz = 0, pods = 0, lastN = 0;
        Input in = new Input();
        for (int i = 0; i < 25 * 60; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
            s.sound.clear();
            maxWarn = Math.max(maxWarn, r.liveWarnings());
            maxHaz = Math.max(maxHaz, s.hazN);
            int w = r.liveWarnings();
            if (w > lastN) pods += w - lastN;
            lastN = w;
        }
        System.out.println("HYDRO sporefall: " + pods + " pods seen, peak warnings " + maxWarn + ", peak hazards " + maxHaz);
        assertTrue("pods fall: " + pods, pods >= 25);
        assertTrue("at most 8 warnings: " + maxWarn, maxWarn <= HydroRules.POD_LIVE);
        assertTrue(maxHaz <= Sim.MAX_HAZ);
        // with the pool nearly full no pod is added
        Sim f = sim(16);
        HydroRules fr = rules(f);
        for (int i = 0; i < Sim.MAX_HAZ - HydroOps.HAZ_RESERVE; i++) f.hazardForTest(Entities.HZ_ASH, 5000f + i, 0f, 20f, 500f, 0f, 0f);
        int full = f.hazN;
        fr.startSporefall(10f, false);
        step(f, 8);
        assertEquals("the reserve is kept", full, f.hazN);
        assertEquals(0, fr.liveWarnings());
    }

    @Test
    public void sporeCloudsHealEnemies() throws Exception {
        Sim s = sim(17);
        HydroRules r = rules(s);
        s.px = 3000f;
        Enemy e = s.spawnAt("SERGEANT", 0f, 0f, false);
        e.speed = 0f;
        e.hp = e.maxHp * 0.5f;
        r.scX[0] = 0f;
        r.scY[0] = 0f;
        r.scT[0] = 3f;
        r.scN = 1;
        float hp0 = e.hp;
        step(s, 1.0);
        assertEquals("3% of max a second", hp0 + e.maxHp * 0.03f, e.hp, e.maxHp * 0.006f);
    }

    // ------------------------------------------------------------------ Gardener

    @Test
    public void aGardenerPulsesSixPercentOverAnEnemyWithin110AndTheCapIsSix() throws Exception {
        Sim s = sim(18);
        s.px = 5000f;
        Enemy g = s.spawnAt("GARDENER", 0f, 0f, false);
        assertNotNull(g);
        assertEquals(EnemyDef.ARCH_HEALER, g.type.arch);
        g.speed = 0f;
        Enemy near = s.spawnAt("SERGEANT", 80f, 0f, false), far = s.spawnAt("SERGEANT", 160f, 0f, false);
        near.speed = far.speed = 0f;
        near.hp = far.hp = near.maxHp * 0.5f;
        float h0 = near.hp, f0 = far.hp;
        step(s, 2.6);
        assertTrue("the pulse reached the near one: " + (near.hp - h0), near.hp - h0 >= near.maxHp * 0.059f);
        assertTrue("six per cent per pulse at most twice in 2.6 s", near.hp - h0 <= near.maxHp * 0.13f);
        assertEquals("not the far one", f0, far.hp, 1e-3f);
        for (int i = 0; i < 9; i++) s.spawnAt("GARDENER", 400f + i * 5f, 400f, false);
        assertEquals("cap of six", 6, HydroOps.aliveOfArch(s, EnemyDef.ARCH_HEALER));
    }

    @Test
    public void aGardenerHasThreeTimesAColonistsHealthAndAMycelidAThirdOfAHound() throws Exception {
        Sim s = sim(19);
        Enemy g = s.spawnAt("GARDENER", 0f, 0f, false), c = s.spawnAt("COLONIST", 0f, 0f, false);
        assertEquals(3f, g.maxHp / c.maxHp, 0.01f);
        Enemy m = s.spawnAt("MYCELID", 0f, 0f, false), h = s.spawnAt("SPORE_HOUND", 0f, 0f, false);
        assertEquals(1f / 3f, m.maxHp / h.maxHp, 0.01f);
    }

    @Test
    public void heartsSendMycelidSwarmsFromThePatchEdge() throws Exception {
        Sim s = sim(20);
        HydroRules r = rules(s);
        r.heartsOn = true;
        s.time = 300;
        r.addHeart(400f, 0f, true);
        r.hSwarm[0] = 0.05f;
        step(s, 0.5);
        int m = 0;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (!e.type.def.key.equals("MYCELID")) continue;
            m++;
            double d = Math.hypot(e.x - 400f, e.y);
            assertEquals("on the patch edge", r.heartR(0), d, 40.0);
        }
        assertEquals(HydroRules.SWARM_SIZE, m);
    }

    // ------------------------------------------------------------------ events

    @Test
    public void thePurgeTerminalBurnsEveryHeartAndDropsACrate() throws Exception {
        Sim s = sim(21);
        HydroRules r = rules(s);
        r.heartsOn = false;
        s.time = 1259;
        for (int i = 0; i < 3; i++) r.addHeart(900f + i * 300f, 700f, true);
        step(s, 1.5);
        assertTrue("terminal up", r.purgeOn);
        s.px = r.purgeX;
        s.py = r.purgeY;
        step(s, 26);
        assertEquals(1, r.purgeWon);
        assertEquals("every heart gone", 0, r.heartN);
        boolean crate = false;
        for (int i = 0; i < s.pickN; i++) if (s.picks[i].kind == Entities.K_CHEST) crate = true;
        assertTrue("an Arms Crate", crate);
    }

    @Test
    public void bloomMaximisesPatchesAddsTwoHeartsAndPaysACheststhatNeedsSix() throws Exception {
        Sim s = sim(22);
        HydroRules r = rules(s);
        r.heartsOn = false;
        s.time = 1439;
        r.addHeart(1200f, 0f, true);
        step(s, 1.5);
        assertTrue(r.bloomOn());
        assertEquals("two more hearts", 3, r.heartN);
        assertEquals("full size at once", 180f, r.heartR(0), 1e-4f);
        Enemy e = s.spawnAt("COLONIST", 2000f, 2000f, false);
        assertEquals(1.15f, r.enemySpeedMul(e), 1e-5f);
        for (int i = 0; i < 6; i++) r.addHeart(-3000f - i * 500f, 0f, true);
        for (int i = r.heartN - 1; i >= 0; i--) if (r.heartN > 0) r.destBroken(Sim.DK_HEART, r.hx[i], r.hy[i], r.hKey[i]);
        step(s, 0.2);
        assertEquals("a chest for six hearts", 1, r.bloomChests);
        step(s, 46);
        assertFalse(r.bloomOn());
    }

    @Test
    public void sprouts() throws Exception {
        Sim s = sim(23);
        HydroRules r = rules(s);
        r.addSprout(500f, 500f);
        step(s, 2.0);
        assertTrue(r.inPatch(530f, 500f));
        assertFalse(r.inPatch(570f, 500f));
        step(s, 14.5);
        assertFalse("gone after 15 s", r.inPatch(500f, 500f));
    }

    @Test
    public void heartwoodHealsMoreAndTheStageDataIsThere() throws Exception {
        GameData d = StageSoak.data();
        assertTrue(d.enemies.containsKey("ROOTMIND") && d.enemies.containsKey("GARDENER") && d.enemies.containsKey("MYCELID"));
        assertEquals(11, new EnemyType(d.enemies.get("ROOTMIND")).pattern);
        assertEquals("ROOTMIND", d.stages.get("HYDRO").timedBoss[4]);
        assertEquals(1680, d.stages.get("HYDRO").timedAt[4]);
        assertNotNull(d.uniqueBySource.get("ROOTMIND"));
        Sim s = sim(24);
        s.hp = 10f;
        s.heal(10f);
        assertEquals(20f, s.hp, 1e-3f);
        s.giveEquipment("ROOTMINDS_HEARTWOOD", Equipment.UNIQUE);
        s.recomputeAll();
        s.hp = 10f;
        s.heal(10f);
        assertEquals("+25% healing", 22.5f, s.hp, 1e-3f);
        assertEquals(0.25, s.stats.heartVacuum, 1e-9);
    }

    // ------------------------------------------------------------------ the Rootmind

    @Test
    public void theRootmindIsShieldedByThreeHeartsAndHasThreePhases() throws Exception {
        Sim s = sim(25);
        HydroRules r = rules(s);
        s.time = 1680;
        Enemy b = s.spawnAt("ROOTMIND", 0f, 0f, true);
        assertNotNull(b);
        float dist = (float) Math.hypot(b.x - s.px, b.y - s.py);
        assertTrue("400-ish u from the hero at a dome centre: " + dist, dist >= 300f && dist <= 650f);
        float cell = DomesLayout.CELL * WorldGen.TILE;
        assertEquals("on a dome centre", 0.5f, ((b.x / cell) % 1f + 1f) % 1f, 1e-3f);
        step(s, 1.2);
        int hearts = 0;
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].type.def.key.equals("ROOT_HEART")) hearts++;
        assertEquals(3, hearts);
        assertEquals(0.2f, r.rmMul, 1e-6f);
        float hp0 = b.hp;
        s.hurt(b, 100f, 0f, 1f, 0f, 0f, 0f);
        assertEquals("x0.2 while shielded", 20f, hp0 - b.hp, 0.5f);
        // kill all three: a 12 s window of x1.0
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].type.def.key.equals("ROOT_HEART")) s.killForTest(s.enemies[i]);
        step(s, 0.2);
        assertEquals(0, r.rmAlive);
        hp0 = b.hp;
        s.hurt(b, 100f, 0f, 1f, 0f, 0f, 0f);
        assertEquals("x1.0 in the window", 100f, hp0 - b.hp, 0.5f);
        step(s, 12.5);
        assertEquals("the window closes", 0.2f, r.rmMul, 1e-6f);
        step(s, 8.5);
        assertEquals("the hearts grew back after 20 s", 3, r.rmAlive);
        // phases
        assertEquals(0, b.phase);
        b.hp = b.maxHp * 0.5f;
        step(s, 2.0);
        assertEquals(1, b.phase);
        step(s, 4.5);
        assertTrue("the ring of shots or the hounds came", s.eprojN > 0 || s.enemyN > 5);
        b.hp = b.maxHp * 0.2f;
        step(s, 5.0);
        assertEquals(2, b.phase);
        assertTrue("phase 3: a full-map Sporefall", r.sporefallOn() || r.sporefalls > 0);
        // dying wins the run and removes the hearts
        b.hp = 0f;
        s.killForTest(b);
        step(s, 3.0);
        assertEquals("won", 2, s.ended);
        assertTrue(s.firstKilled);
        assertEquals(2 * s.gold, s.endGold());
    }

    @Test
    public void phaseOneSweepsVineLines() throws Exception {
        Sim s = sim(26);
        s.time = 1680;
        Enemy b = s.spawnAt("ROOTMIND", 0f, 0f, true);
        int lanes = 0;
        Input in = new Input();
        for (int i = 0; i < 5 * 60; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
            int n = 0;
            for (int k = 0; k < s.hazN; k++) if (s.hazards[k].kind == Entities.HZ_LANE) n++;
            lanes = Math.max(lanes, n);
        }
        assertEquals("two vine lines", 2, lanes);
    }

    // ------------------------------------------------------------------ soak

    private static void soak(String policy, long seed) throws Exception {
        StageSoak.Result r = StageSoak.run("HYDRO", "BASTION", policy, seed, 1800);
        System.out.println("HYDRO SOAK " + r);
        assertEquals(policy + " " + r.firstProblem, 0, r.nan);
        assertEquals(policy + " " + r.firstProblem, 0, r.overflow);
        assertEquals(policy + " " + r.firstProblem, 0, r.capBreak);
        assertTrue(policy + " kills " + r.kills, r.kills > 500);
    }

    @Test
    public void soakImmortal30Minutes() throws Exception {
        soak("immortal", 31);
    }

    @Test
    public void soakMid30Minutes() throws Exception {
        soak("mid", 32);
    }

    @Test
    public void soakNaked30Minutes() throws Exception {
        soak("naked", 33);
    }
}
