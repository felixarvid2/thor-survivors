package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.WeaponDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.ArrayDeque;
import java.util.HashMap;

/**
 * The places on the map ({@link Pois}): outposts, nests, relay terminals, hazard fields and the vault. Placement, the
 * mechanics, the own random, allocation and two 30 minute seeded runs.
 */
public class PoisTest {
    private static final float DT = 1f / 60f;
    private static final String[] STAGES = {"FLATS", "MINES"};

    private static Sim sim(String stage, long seed) throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get(stage), "WHIP", new HashMap<String, Double>(), null, seed);
        s.noSpawns = true;
        s.enemyN = 0;
        s.pois.calmUntil = 0;
        return s;
    }

    /** No weapons: a weapon aims with Sim.rng whenever an enemy exists anywhere, which would differ between a run with guards and one without. */
    private static Sim unarmed(String stage, long seed) throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get(stage), null, new HashMap<String, Double>(), null, seed);
        s.noSpawns = true;
        s.enemyN = 0;
        s.pois.calmUntil = 0;
        return s;
    }

    private static void run(Sim s, double seconds) {
        Input in = new Input();
        for (int i = 0, n = (int) Math.round(seconds / DT); i < n; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
        }
    }

    private static void at(Sim s, float x, float y) {
        s.px = x;
        s.py = y;
        s.update(DT, new Input());
    }

    private static boolean toasted(Sim s, String text) {
        for (int i = 0; i < Sim.MAX_TOASTS; i++) if (s.toastText[i] != null && s.toastText[i].startsWith(text) && s.toastT[i] > 0f) return true;
        return false;
    }

    /** First hero position on a coarse grid where {@code ventN > 0} (or a nest / terminal stands near), or null. */
    private static float[] scan(Sim s, int what) {
        for (int gy = -12; gy <= 12; gy++) {
            for (int gx = -12; gx <= 12; gx++) {
                float x = gx * 500f, y = gy * 500f;
                at(s, x, y);
                if (what == 0 && s.pois.ventN > 0) return new float[]{s.pois.ventX[0], s.pois.ventY[0]};
                if (what == 1) for (int i = 0; i < s.destN; i++) if (s.destKind[i] == Sim.DK_NEST) return new float[]{s.destX[i], s.destY[i]};
                if (what == 2 && s.pois.termN > 0) return new float[]{s.pois.termX[0], s.pois.termY[0]};
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ placement

    @Test
    public void sameSeedSameLayoutAndEveryPlaceIsFreeAndFarEnoughFromTheFinds() throws Exception {
        for (String stage : STAGES) {
            for (long seed = 1; seed <= 8; seed++) {
                Sim a = sim(stage, seed), b = sim(stage, seed);
                Pois p = a.pois, q = b.pois;
                assertEquals(Pois.MAX_OUT, p.outN);
                assertTrue(p.hasVault);
                assertEquals(q.outN, p.outN);
                for (int i = 0; i < p.outN; i++) {
                    assertEquals(q.outX[i], p.outX[i], 0f);
                    assertEquals(q.outY[i], p.outY[i], 0f);
                    float d = (float) Math.hypot(p.outX[i], p.outY[i]);
                    assertTrue("outpost " + i + " at " + d, d >= Pois.OUT_DIST[i] - 1f && d <= Pois.OUT_DIST[i] + Pois.OUT_SPREAD + 1f);
                    assertFalse(a.map.blocked(p.outX[i], p.outY[i]));
                    for (int k = 0; k < 8; k++) {
                        float ang = k * 0.7853982f;
                        assertFalse(a.map.blocked(p.outX[i] + (float) Math.cos(ang) * 90f, p.outY[i] + (float) Math.sin(ang) * 90f));
                    }
                    assertEquals(i >= 2, p.outElite[i]);
                    for (int f = 0; f < a.findN; f++) assertTrue(Math.hypot(a.findX[f] - p.outX[i], a.findY[f] - p.outY[i]) >= Pois.FIND_APART);
                }
                assertEquals(q.vaultX, p.vaultX, 0f);
                assertEquals(q.vaultY, p.vaultY, 0f);
                float dv = (float) Math.hypot(p.vaultX, p.vaultY);
                assertTrue("vault at " + dv, dv >= Pois.VAULT_DIST - 1f && dv <= Pois.VAULT_DIST + Pois.OUT_SPREAD + 1f);
                assertFalse(a.map.blocked(p.vaultX, p.vaultY));
                for (int k = 0; k < 3; k++) {
                    assertFalse(a.map.blocked(p.vbX[k], p.vbY[k]));
                    assertTrue("a barrel inside the blast of the wall", Math.hypot(p.vbX[k] - p.vaultX, p.vbY[k] - p.vaultY) < Sim.BARREL_R);
                }
                for (int f = 0; f < a.findN; f++) assertTrue(Math.hypot(a.findX[f] - p.vaultX, a.findY[f] - p.vaultY) >= Pois.FIND_APART);
            }
        }
        Sim x = sim("FLATS", 1), y = sim("FLATS", 2);
        assertTrue("another seed, another layout", x.pois.outX[0] != y.pois.outX[0]);
    }

    @Test
    public void outpostsAndTheVaultAreReachableFromTheStart() throws Exception {
        for (String stage : STAGES) {
            for (long seed = 1; seed <= 3; seed++) {
                Sim s = sim(stage, seed);
                final int step = 40, half = 3800 / step, n = 2 * half + 1;
                boolean[] seen = new boolean[n * n];
                ArrayDeque<int[]> q = new ArrayDeque<>();
                q.add(new int[]{half, half});
                seen[half * n + half] = true;
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    for (int[] dd : dirs) {
                        int nx = c[0] + dd[0], ny = c[1] + dd[1];
                        if (nx < 0 || ny < 0 || nx >= n || ny >= n || seen[ny * n + nx]) continue;
                        if (s.map.blocked((nx - half) * step, (ny - half) * step)) continue;
                        seen[ny * n + nx] = true;
                        q.add(new int[]{nx, ny});
                    }
                }
                float[][] targets = new float[Pois.MAX_OUT + 1][];
                for (int i = 0; i < Pois.MAX_OUT; i++) targets[i] = new float[]{s.pois.outX[i], s.pois.outY[i]};
                targets[Pois.MAX_OUT] = new float[]{s.pois.vaultX, s.pois.vaultY + 60f}; // in front of the wall
                for (int t = 0; t < targets.length; t++) {
                    boolean ok = false;
                    int cx = Math.round(targets[t][0] / step) + half, cy = Math.round(targets[t][1] / step) + half;
                    for (int oy = -2; oy <= 2 && !ok; oy++) for (int ox = -2; ox <= 2 && !ok; ox++) ok = seen[(cy + oy) * n + cx + ox];
                    assertTrue(stage + " seed " + seed + " target " + t + " unreachable", ok);
                }
            }
        }
    }

    @Test
    public void legacyStagesGetNothing() throws Exception {
        for (String stage : new String[]{"FOREST", "LIBRARY"}) {
            Sim s = sim(stage, 4);
            assertFalse(s.pois.enabled);
            assertEquals(0, s.pois.outN);
            assertFalse(s.pois.hasVault);
            run(s, 10);
            at(s, 3000f, 3000f);
            assertEquals(0, s.pois.ventN);
            assertEquals(0, s.pois.termN);
            for (int i = 0; i < s.destN; i++) assertTrue(s.destKind[i] != Sim.DK_NEST);
        }
    }

    // ------------------------------------------------------------------ never Sim.rng

    @Test
    public void thePlacesNeverTouchSimRng() throws Exception {
        for (String stage : STAGES) {
            Sim a = unarmed(stage, 5), b = unarmed(stage, 5);
            b.pois.enabled = false;
            assertEquals("construction", a.rng.nextLong(), b.rng.nextLong());
            a.rng.setSeed(11);
            b.rng.setSeed(11);
            float x = a.pois.outX[1] + 650f, y = a.pois.outY[1];
            a.px = b.px = x;
            a.py = b.py = y;
            Input in = new Input();
            for (int i = 0; i < 240; i++) {
                a.hp = a.maxHp;
                b.hp = b.maxHp;
                a.update(DT, in);
                b.update(DT, in);
            }
            assertTrue("the guards stand there", a.pois.outSpawned[1] && a.pois.outGuards[1] >= Pois.GUARDS_MIN);
            assertEquals(stage, b.rng.nextLong(), a.rng.nextLong());
        }
    }

    @Test
    public void theFirstTwentySecondsAreCalm() throws Exception {
        Sim s = sim("FLATS", 3);
        s.pois.calmUntil = 20.0;
        at(s, s.pois.outX[0] + 400f, s.pois.outY[0]);
        assertFalse(s.pois.outSpawned[0]);
        run(s, 19);
        assertFalse(s.pois.outSpawned[0]);
        run(s, 2);
        assertTrue(s.pois.outSpawned[0]);
    }

    // ------------------------------------------------------------------ outposts

    @Test
    public void guardsSleepUntilTheHeroIsWithinTwoHundredSixtyAndThenWake() throws Exception {
        Sim s = sim("FLATS", 3);
        Pois p = s.pois;
        float ox = p.outX[0], oy = p.outY[0];
        at(s, ox + 1000f, oy);
        assertFalse("not spawned from afar", p.outSpawned[0]);
        at(s, ox + 650f, oy);
        assertTrue(p.outSpawned[0]);
        assertTrue(p.outGuards[0] >= 6 && p.outGuards[0] <= 10);
        int guards = 0;
        float[] gx = new float[16], gy = new float[16];
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (e.poi != 0) continue;
            assertTrue(e.sleeping);
            assertFalse(e.elite);
            assertTrue(e.type.def.key.equals("SERGEANT") || e.type.def.key.equals("WELDER"));
            gx[guards] = e.x;
            gy[guards++] = e.y;
        }
        assertEquals(p.outGuards[0], guards);
        run(s, 3);
        for (int i = 0, k = 0; i < s.enemyN; i++) if (s.enemies[i].poi == 0) {
            assertEquals(gx[k], s.enemies[i].x, 0.01f);
            assertEquals(gy[k++], s.enemies[i].y, 0.01f);
        }
        at(s, ox + 400f, oy);
        run(s, 1);
        assertFalse("still asleep at 400 u", p.outWoken[0]);
        at(s, ox + 250f, oy);
        assertTrue(p.outWoken[0]);
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].poi == 0) assertFalse(s.enemies[i].sleeping);
        assertTrue(toasted(s, "THE OUTPOST WAKES"));
    }

    @Test
    public void aHitGuardWakesTheOutpostAndASleepingGuardDoesNotHurt() throws Exception {
        Sim s = sim("FLATS", 3);
        Pois p = s.pois;
        at(s, p.outX[0] + 600f, p.outY[0]);
        Enemy g = null;
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].poi == 0) g = s.enemies[i];
        s.hurt(g, 1f, 0f, 1f, 0f, 0f, 0f);
        run(s, 0.5);
        assertTrue(p.outWoken[0]);
        // a sleeping guard right on the hero: no contact damage
        Sim t = sim("FLATS", 3);
        EnemyType wt = t.type("WELDER");
        Enemy e = t.poiSpawn(wt, t.px + 2f, t.py, 99, false, 0);
        e.sleeping = true;
        float hp0 = t.hp;
        Input in = new Input();
        for (int i = 0; i < 90; i++) t.update(DT, in);
        assertEquals(hp0, t.hp, 0f);
        assertEquals(2f, e.x - t.px, 0.5f);
    }

    @Test
    public void theTwoFarOutpostsHaveOneEliteGuard() throws Exception {
        Sim s = sim("MINES", 6);
        for (int i = 0; i < Pois.MAX_OUT; i++) {
            at(s, s.pois.outX[i] + 650f, s.pois.outY[i]);
            int elites = 0;
            for (int k = 0; k < s.enemyN; k++) if (s.enemies[k].poi == i && s.enemies[k].elite) elites++;
            assertEquals("outpost " + i, i >= 2 ? 1 : 0, elites);
        }
    }

    @Test
    public void clearingAnOutpostUnlocksALockerAndAChest() throws Exception {
        Sim s = sim("FLATS", 3);
        Pois p = s.pois;
        float ox = p.outX[0], oy = p.outY[0];
        at(s, ox + 650f, oy);
        // the loot is locked while guards live
        at(s, ox - Pois.LOCKER_DX, oy);
        assertEquals("locked", 0, p.outLoot[0]);
        at(s, ox + 650f, oy);
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].poi == 0) s.killForTest(s.enemies[i]);
        run(s, 0.6);
        assertTrue(p.outCleared[0]);
        assertEquals(0, p.liveGuards(0));
        int gear = 0;
        for (int i = 0; i < s.equip.def.length; i++) if (s.equip.def[i] != null) gear++;
        int gold = s.gold, picks = s.pickN;
        at(s, ox - Pois.LOCKER_DX, oy);
        assertTrue((p.outLoot[0] & Pois.LOOT_LOCKER) != 0);
        assertTrue(toasted(s, "RARE GEAR LOCKER"));
        int gear2 = 0;
        for (int i = 0; i < s.equip.def.length; i++) if (s.equip.def[i] != null) gear2++;
        assertTrue("a piece of gear worn, waiting or salvaged", gear2 > gear || s.equipN > 0 || s.gold > gold);
        at(s, ox + Pois.LOCKER_DX, oy);
        assertTrue((p.outLoot[0] & Pois.LOOT_CHEST) != 0);
        assertTrue("the chest drops pickups", s.pickN > picks);
    }

    // ------------------------------------------------------------------ nests

    @Test
    public void nestsSpawnHoundPacksNearTheHeroAndAtMostTwoAreActive() throws Exception {
        for (String stage : STAGES) {
            Sim s = sim(stage, 9);
            float[] n = scan(s, 1);
            assertNotNull(stage + ": a nest somewhere on the grid", n);
            at(s, n[0] + 200f, n[1]);
            // five more nests next to it: only two may work at a time
            for (int k = 0; k < 5; k++) s.addDest(n[0] + 100f + k * 20f, n[1] + 60f, Sim.DK_NEST, 0x1234L + k);
            Input in = new Input();
            int maxActive = 0, hounds = 0, peak = 0;
            for (int i = 0; i < 60 * 40; i++) {
                s.hp = s.maxHp;
                s.update(DT, in);
                maxActive = Math.max(maxActive, s.pois.nestActiveN);
                hounds = 0;
                for (int k = 0; k < s.enemyN; k++) if (s.enemies[k].poi == Pois.POI_HOUND && !s.enemies[k].dead) hounds++;
                peak = Math.max(peak, hounds);
            }
            assertEquals(2, maxActive);
            assertTrue(stage + ": hounds came (" + peak + ")", peak >= Pois.NEST_PACK);
            assertTrue("the hound cap holds", peak <= Pois.NEST_HOUNDS_MAX);
            // far away they stop
            Sim t = sim(stage, 9);
            float[] m = scan(t, 1);
            at(t, m[0] + 900f, m[1]);
            run(t, 20);
            for (int k = 0; k < t.enemyN; k++) assertTrue(t.enemies[k].poi != Pois.POI_HOUND);
        }
    }

    @Test
    public void aNestTakesEightWeaponHitsOrTwoBlastsAndDropsAGemPileWorthALevel() throws Exception {
        Sim s = sim("FLATS", 9);
        float[] n = scan(s, 1);
        at(s, n[0] + 300f, n[1]);
        int idx = -1;
        for (int i = 0; i < s.destN; i++) if (s.destKind[i] == Sim.DK_NEST) idx = i;
        assertEquals(Pois.NEST_HP, s.destHp[idx], 0f);
        int dests = s.destN;
        for (int k = 0; k < 7; k++) s.hitDest(n[0], n[1], 10f);
        assertEquals("seven hits: still standing", dests, s.destN);
        assertTrue(s.destHp[idx] > 0f);
        int picks = s.pickN;
        s.hitDest(n[0], n[1], 10f);
        assertEquals("the eighth breaks it", dests - 1, s.destN);
        float gems = 0;
        for (int i = 0; i < s.pickN; i++) if (s.picks[i].kind == Entities.K_GEM) gems += s.picks[i].value;
        assertTrue(s.pickN > picks);
        assertEquals("a gem pile worth one level", s.xpNeed, gems, 0.5f);
        // a barrel blast does 50
        Sim t = sim("FLATS", 9);
        float[] m = scan(t, 1);
        at(t, m[0] + 300f, m[1]);
        t.explodeBarrel(m[0] + 30f, m[1]);
        int ni = -1;
        for (int i = 0; i < t.destN; i++) if (t.destKind[i] == Sim.DK_NEST) ni = i;
        assertEquals(30f, t.destHp[ni], 0.01f);
    }

    // ------------------------------------------------------------------ relay terminals

    @Test
    public void aTerminalOpensAfterTwoSecondsOnItAndIsUsedUpByAPick() throws Exception {
        Sim s = sim("FLATS", 2);
        float[] t = scan(s, 2);
        assertNotNull(t);
        Pois p = s.pois;
        at(s, t[0], t[1]);
        Input in = new Input();
        for (int i = 0; i < 100; i++) s.update(DT, in); // 1.7 s
        assertFalse(p.wantsTerminal());
        for (int i = 0; i < 30; i++) s.update(DT, in);
        assertTrue(p.wantsTerminal());
        int ti = p.termNear;
        p.opened();
        assertFalse(p.wantsTerminal());
        assertTrue(p.cardBuff[0] != p.cardBuff[1] && p.cardBuff[1] != p.cardBuff[2] && p.cardBuff[0] != p.cardBuff[2]);
        p.pick(1);
        assertTrue(p.buff >= 0);
        assertTrue(p.termUsed(ti));
        run(s, 5);
        assertFalse("single use", p.wantsTerminal());
    }

    @Test
    public void decliningLeavesTheTerminalUnusedUntilTheHeroWalksOffAndBack() throws Exception {
        Sim s = sim("FLATS", 2);
        float[] t = scan(s, 2);
        Pois p = s.pois;
        at(s, t[0], t[1]);
        run(s, 2.2);
        assertTrue(p.wantsTerminal());
        int ti = p.termNear;
        p.opened();
        p.declined();
        assertFalse(p.termUsed(ti));
        assertEquals(-1, p.buff);
        run(s, 3);
        assertFalse("not again while standing there", p.wantsTerminal());
        at(s, t[0] + 200f, t[1]);
        run(s, 0.2);
        at(s, t[0], t[1]);
        run(s, 2.2);
        assertTrue(p.wantsTerminal());
    }

    @Test
    public void everyBuffAndItsCostLastFiveMinutesAndThenBothEnd() throws Exception {
        for (int b = 0; b < Pois.BUFFS; b++) {
            Sim s = sim("MINES", 4);
            s.inv.add(s.data.weapons.get("KNIFE"));
            s.recomputeAll();
            double might = s.stats.might, hpMul = s.stats.maxHpMul, move = s.stats.moveSpeedMul, luck = s.stats.luck, light = s.stats.lightRadius;
            int knifeLv = s.inv.weaponLevel("KNIFE"), whipLv = s.inv.weaponLevel("WHIP");
            s.pois.startBuff(b);
            assertEquals(b, s.pois.buff);
            switch (b) {
                case Pois.B_OVERCLOCK:
                    assertEquals(might * 1.15, s.stats.might, 1e-9);
                    assertEquals(1.1f, s.pois.enemySpeedMul, 0f);
                    break;
                case Pois.B_REINFORCE:
                    assertEquals(hpMul * 1.2, s.stats.maxHpMul, 1e-9);
                    assertEquals(move * 0.9, s.stats.moveSpeedMul, 1e-9);
                    break;
                case Pois.B_LENS:
                    assertEquals(luck + 0.1, s.stats.luck, 1e-9);
                    assertEquals(light * 0.8, s.stats.lightRadius, 1e-9);
                    break;
                default:
                    assertEquals(1, s.inv.weaponLevel("KNIFE") + s.inv.weaponLevel("WHIP") - knifeLv - whipLv);
                    assertEquals(1.1f, s.pois.spawnMul, 0f);
            }
            double t0 = s.time;
            Input in = new Input();
            while (s.time < t0 + 299.0) {
                s.hp = s.maxHp;
                s.update(0.1f, in);
            }
            assertEquals("still running at 4:59", b, s.pois.buff);
            while (s.time < t0 + 301.0) {
                s.hp = s.maxHp;
                s.update(0.1f, in);
            }
            assertEquals(-1, s.pois.buff);
            assertEquals(1f, s.pois.enemySpeedMul, 0f);
            assertEquals(1f, s.pois.spawnMul, 0f);
            assertEquals(might, s.stats.might, 1e-9);
            assertEquals(hpMul, s.stats.maxHpMul, 1e-9);
            assertEquals(move, s.stats.moveSpeedMul, 1e-9);
            assertEquals(luck, s.stats.luck, 1e-9);
            assertEquals(light, s.stats.lightRadius, 1e-9);
            if (b == Pois.B_SCRAP) assertEquals("the level stays", 1, s.inv.weaponLevel("KNIFE") + s.inv.weaponLevel("WHIP") - knifeLv - whipLv);
        }
    }

    @Test
    public void overclockMakesEnemiesFasterWhileItLasts() throws Exception {
        Sim s = sim("FLATS", 4);
        Enemy e = s.spawnAt("COLONIST", s.px + 400f, s.py, false);
        float base = s.ai.speedOf(e);
        s.pois.startBuff(Pois.B_OVERCLOCK);
        assertEquals(base * 1.1f, s.ai.speedOf(e), 1e-3f);
    }

    // ------------------------------------------------------------------ hazard fields

    @Test
    public void slagVentsWarnAndThenBurstForEighteenAndHurtEnemiesToo() throws Exception {
        Sim s = sim("FLATS", 2);
        float[] v = scan(s, 0);
        assertNotNull(v);
        Pois p = s.pois;
        at(s, v[0], v[1] + 1f);
        s.px = v[0];
        s.py = v[1];
        Enemy e = s.spawnAt("WELDER", v[0] + 10f, v[1], false);
        e.hp = e.maxHp = 5000f;
        boolean warned = false;
        float hpBefore = s.hp, enemyBefore = e.hp;
        Input in = new Input();
        float lost = 0f;
        for (int i = 0; i < 60 * 9; i++) {
            float h0 = s.hp;
            s.update(DT, in);
            if (p.ventPhase(0) > Pois.VENT_PERIOD - Pois.VENT_WARN) warned = true;
            if (s.hp < h0) lost += h0 - s.hp;
            e.x = v[0] + 10f;
            e.y = v[1];
            s.invuln = Math.min(s.invuln, 0.4f);
            if (s.hp < hpBefore - 60f) break;
        }
        assertTrue(warned);
        assertTrue("took at least one 18 burst, lost " + lost, lost >= 17f);
        assertTrue("the enemy took the burst too", e.hp < enemyBefore);
        // standing outside the geyser: nothing
        Sim t = sim("FLATS", 2);
        float[] w = scan(t, 0);
        at(t, w[0] + 120f, w[1]);
        float h0 = t.hp;
        run(t, 10);
        assertEquals(h0, t.hp, 0f);
    }

    @Test
    public void steamJetsHurtInsideTheConeOnly() throws Exception {
        Sim s = sim("MINES", 2);
        float[] v = scan(s, 0);
        assertNotNull(v);
        Pois p = s.pois;
        // in the middle of the cone, 90 u out; the cone turns 0.55 rad/s, so re-aim every step
        Input in = new Input();
        float lost = 0f;
        for (int i = 0; i < 90; i++) {
            float a = p.jetAngle(0);
            s.px = v[0] + (float) Math.cos(a) * 90f;
            s.py = v[1] + (float) Math.sin(a) * 90f;
            float h0 = s.hp;
            s.update(DT, in);
            if (s.hp < h0) lost += h0 - s.hp;
        }
        assertTrue("hurt in the cone: " + lost, lost >= 9f);
        float behind = 0f;
        for (int i = 0; i < 180; i++) {
            float a = p.jetAngle(0) + 3.1415927f;
            s.px = v[0] + (float) Math.cos(a) * 90f;
            s.py = v[1] + (float) Math.sin(a) * 90f;
            float h0 = s.hp;
            s.update(DT, in);
            if (s.hp < h0) behind += h0 - s.hp;
        }
        assertEquals("behind the jet", 0f, behind, 0f);
    }

    @Test
    public void theVentsLeaveTheHazardPoolAlone() throws Exception {
        Sim s = sim("FLATS", 2);
        float[] v = scan(s, 0);
        at(s, v[0], v[1]);
        run(s, 12);
        assertEquals(0, s.hazN);
    }

    // ------------------------------------------------------------------ the vault

    @Test
    public void theVaultOpensOnlyThroughABarrelBlast() throws Exception {
        for (String stage : STAGES) {
            Sim s = sim(stage, 7);
            Pois p = s.pois;
            at(s, p.vaultX, p.vaultY + 200f);
            assertTrue("found within 600 u", p.vaultFound);
            s.hitDest(p.vaultX, p.vaultY, 40f); // a weapon hit on the wall
            s.hurt(s.spawnAt("COLONIST", p.vaultX, p.vaultY, false), 5f, 0, 1, 0, 0, 0);
            s.explodeBarrel(p.vaultX + 400f, p.vaultY); // a blast elsewhere
            run(s, 2);
            assertEquals(Pois.V_SEALED, p.vaultState);
            int barrels = 0;
            for (int i = 0; i < s.destN; i++) if (s.destKind[i] == Sim.DK_BARREL && Math.hypot(s.destX[i] - p.vaultX, s.destY[i] - p.vaultY) < 100f) barrels++;
            assertEquals("three barrels by the wall", 3, barrels);
            s.hitDest(p.vbX[1], p.vbY[1], 5f); // one barrel goes off and takes the rest with it
            assertEquals(Pois.V_OPEN, p.vaultState);
            assertTrue(toasted(s, "THE VAULT IS OPEN"));
        }
    }

    @Test
    public void theFusionCoreFusesAReadyPairOtherwiseLevelsEveryWeapon() throws Exception {
        Sim s = sim("FLATS", 7);
        Pois p = s.pois;
        s.inv.add(s.data.weapons.get("KNIFE"));
        s.recomputeAll();
        int w0 = s.inv.weaponLevel("WHIP"), k0 = s.inv.weaponLevel("KNIFE");
        at(s, p.vaultX, p.vaultY + 150f);
        s.explodeBarrel(p.vbX[0], p.vbY[0]);
        assertEquals(Pois.V_OPEN, p.vaultState);
        at(s, p.vaultX, p.vaultY);
        assertEquals(Pois.V_DONE, p.vaultState);
        assertEquals(w0 + 1, s.inv.weaponLevel("WHIP"));
        assertEquals(k0 + 1, s.inv.weaponLevel("KNIFE"));
        assertTrue(toasted(s, "FUSION CORE: +1 LEVEL ON 2 WEAPONS"));
        // a ready pair is fused
        Sim f = sim("FLATS", 7);
        WeaponDef fusion = f.data.fusions[0];
        for (int k = 0; k < 2; k++) {
            WeaponDef b = f.data.weapons.get(fusion.parents.get(k));
            f.inv.add(b);
            f.inv.weapons[f.inv.weaponCount - 1].level = b.maxLevel();
        }
        f.recomputeAll();
        assertNotNull(f.inv.readyFusion(f.data.fusions, f.data.weapons));
        at(f, f.pois.vaultX, f.pois.vaultY + 150f);
        f.explodeBarrel(f.pois.vbX[0], f.pois.vbY[0]);
        at(f, f.pois.vaultX, f.pois.vaultY);
        assertEquals(Pois.V_DONE, f.pois.vaultState);
        assertNotNull("fused", f.lastFusedTo);
    }

    // ------------------------------------------------------------------ allocation

    @Test
    public void steppingWithPlacesAroundDoesNotAllocate() throws Exception {
        for (String stage : STAGES) {
            Sim s = sim(stage, 9);
            float[] n = scan(s, 1);
            assertNotNull(n);
            at(s, n[0] + 150f, n[1]);
            Input in = new Input();
            for (int i = 0; i < 600; i++) {
                s.hp = s.maxHp;
                s.update(DT, in);
            }
            long a0 = Alloc.bytes();
            int steps = 1200;
            for (int i = 0; i < steps; i++) {
                s.hp = s.maxHp;
                s.update(DT, in);
            }
            long per = (Alloc.bytes() - a0) / steps;
            System.out.printf("PERF pois %s: %d B per step%n", stage, per);
            assertTrue(stage + " allocates " + per + " B per step", per < 400);
        }
    }

    // ------------------------------------------------------------------ 30 minute seeded runs

    @Test
    public void thirtyMinutesOnEachStageHaveNoNaNAndNoPoolOverflow() throws Exception {
        for (String stage : STAGES) {
            GameData d = TestData.load();
            Sim s = new Sim(d, d.stages.get(stage), "WHIP", new HashMap<String, Double>(), "BASTION", 21L);
            s.pois.calmUntil = 0;
            Input in = new Input();
            float[][] route = new float[Pois.MAX_OUT + 3][];
            for (int i = 0; i < Pois.MAX_OUT; i++) route[i] = new float[]{s.pois.outX[i], s.pois.outY[i]};
            route[Pois.MAX_OUT] = new float[]{s.pois.vaultX, s.pois.vaultY + 80f};
            route[Pois.MAX_OUT + 1] = new float[]{1200f, -900f};
            route[Pois.MAX_OUT + 2] = new float[]{-1500f, 700f};
            int target = 0, maxActive = 0, peak = 0;
            double dwellUntil = 0;
            float dt = 1f / 30f;
            long step = 0;
            while (s.time < 1795 && s.ended == 0) {
                step++;
                float tx = route[target][0], ty = route[target][1], dx = tx - s.px, dy = ty - s.py, dist = (float) Math.hypot(dx, dy);
                if (dist > 40f) {
                    s.px += dx / dist * 220f * dt;
                    s.py += dy / dist * 220f * dt;
                } else if (dwellUntil == 0) {
                    dwellUntil = s.time + 40;
                } else if (s.time > dwellUntil) {
                    dwellUntil = 0;
                    target = (target + 1) % route.length;
                }
                s.hp = s.maxHp;
                s.update(dt, in);
                s.pendingLevelUps = 0;
                s.pendingChests = 0;
                s.equipN = 0;
                maxActive = Math.max(maxActive, s.pois.nestActiveN);
                peak = Math.max(peak, s.enemyN);
                assertTrue(stage + " enemy pool " + s.enemyN, s.enemyN <= Sim.MAX_ENEMIES);
                assertTrue(s.pickN <= Sim.MAX_PICK && s.projN <= Sim.MAX_PROJ && s.hazN <= Sim.MAX_HAZ && s.destN <= Sim.MAX_DEST);
                if (step % 30 == 0) {
                    assertFalse(stage + " NaN hero at " + s.time, Float.isNaN(s.px) || Float.isNaN(s.py) || Float.isNaN(s.hp) || Float.isInfinite(s.px));
                    for (int i = 0; i < s.enemyN; i++) {
                        Enemy e = s.enemies[i];
                        assertFalse(stage + " NaN enemy at " + s.time, Float.isNaN(e.x) || Float.isNaN(e.y) || Float.isNaN(e.hp));
                    }
                }
            }
            assertTrue(stage + ": ran " + s.time, s.time >= 1795 || s.ended != 0);
            assertTrue("never more than two nests work", maxActive <= 2);
            System.out.printf("POIS soak %s: peak enemies %d, outposts cleared %d%n", stage, peak,
                    (s.pois.outCleared[0] ? 1 : 0) + (s.pois.outCleared[1] ? 1 : 0) + (s.pois.outCleared[2] ? 1 : 0) + (s.pois.outCleared[3] ? 1 : 0));
        }
    }
}
