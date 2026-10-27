package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Entities.EProj;
import se.felixarvid.thorsurvivors.game.Entities.Hazard;
import se.felixarvid.thorsurvivors.game.Entities.Pickup;
import se.felixarvid.thorsurvivors.game.layout.DebrisLayout;
import se.felixarvid.thorsurvivors.game.stage.WreckRules;

import java.util.HashMap;
import java.util.Random;

/**
 * Meridian Wreck (v1.4 chunk D): pads (pair distances, round trip, cooldown, arrival shock), gravity wells (pull on the hero, an enemy,
 * a pickup and a shot, the core, the on/off cycle, the generator, the exact centre), the Vacuum Breach bubble, the Pillar Gate and
 * arena, the finale, the Rift Hound and the Breacher, and 30 minute soaks.
 */
public class WreckTest {
    private static final float DT = 1f / 60f;

    private static Sim run(long seed) throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get("WRECK"), null, new HashMap<String, Double>(), "BASTION", seed);
        s.noSpawnsForTest();
        s.findN = 0;
        s.pois.enabled = false;
        s.merchant.enabled = false;
        s.events.enabled = false;
        return s;
    }

    /** Steps the whole Sim {@code seconds} with the hero standing still and never dying. */
    private static void steps(Sim s, double seconds) {
        Input in = new Input();
        int n = (int) Math.round(seconds * 60);
        for (int i = 0; i < n; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
            s.sound.clear();
        }
    }

    /** The index of the pad at (x, y) in the rules' table, or -1. */
    private static int padAt(WreckRules wr, float x, float y) {
        for (int i = 0; i < wr.padN; i++) if (Math.abs(wr.padX[i] - x) < 0.5f && Math.abs(wr.padY[i] - y) < 0.5f) return i;
        return -1;
    }

    // ------------------------------------------------------------------ layout

    @Test
    public void padPairsAreNineHundredToFourteenHundredApartAndNoneWithinAThousandOfTheStart() {
        WorldGen g = WorldGen.of("WRECK");
        float[] p = new float[4];
        int pairs = 0;
        for (int cx = -8; cx <= 8; cx++) {
            for (int cy = -8; cy <= 8; cy++) {
                if (!g.pad(cx, cy, p)) continue;
                pairs++;
                double dist = Math.hypot(p[2] - p[0], p[3] - p[1]);
                assertTrue("pair distance " + dist, dist >= 899.5 && dist <= 1400.5);
                assertTrue("A within 1000 of the start", Math.hypot(p[0], p[1]) >= 1000.0);
                assertTrue("B within 1000 of the start", Math.hypot(p[2], p[3]) >= 1000.0);
                assertFalse("pads stay out of the arena", DebrisLayout.inArena(p[0], p[1], 0f) || DebrisLayout.inArena(p[2], p[3], 0f));
                float[] q = new float[4];
                assertTrue(g.pad(cx, cy, q));
                assertEquals("a pure function of the cell", p[0], q[0], 0f);
                assertEquals(p[3], q[3], 0f);
            }
        }
        assertTrue("about 60% of 289 cells: " + pairs, pairs > 120 && pairs < 230);
    }

    @Test
    public void wellsAndThePillarAreDeterministicAndKeepTheirDistance() {
        WorldGen g = WorldGen.of("WRECK");
        float[] w = new float[3], pl = new float[2];
        DebrisLayout.pillar(pl);
        assertEquals("the Pillar stands 2400 u out", 2400.0, Math.hypot(pl[0], pl[1]), 0.5);
        int wells = 0;
        for (int cx = -8; cx <= 8; cx++) {
            for (int cy = -8; cy <= 8; cy++) {
                if (!g.well(cx, cy, w)) continue;
                wells++;
                assertEquals(220f, w[2], 0f);
                assertTrue("no well on the start", Math.hypot(w[0], w[1]) >= 520.0);
                assertFalse(DebrisLayout.inArena(w[0], w[1], 0f));
            }
        }
        assertTrue("55% of 289 cells, minus the start's: " + wells, wells > 100 && wells < 200);
        // the hull core is solid, the far field has void, and the arena is hull again
        int voidNear = 0, voidFar = 0;
        for (int i = -17; i <= 17; i++) for (int j = -17; j <= 17; j++) if (i * i + j * j < 17 * 17 && (g.surface(i, j) & WorldGen.SURF_VOID) != 0) voidNear++;
        for (int i = -120; i <= 120; i += 2) for (int j = -120; j <= 120; j += 2) if ((g.surface(i, j) & WorldGen.SURF_VOID) != 0) voidFar++;
        assertEquals("the hull core within 18 tiles has no void", 0, voidNear);
        assertTrue("void gaps between the islands: " + voidFar, voidFar > 400);
        assertEquals(WorldGen.SURF_NONE, g.surface((int) (pl[0] / WorldGen.TILE), (int) (pl[1] / WorldGen.TILE)));
    }

    // ------------------------------------------------------------------ pads

    @Test
    public void aPadSendsTheHeroToItsTwinRestsTwelveSecondsAndNeedsASteppingOff() throws Exception {
        Sim s = run(5);
        WreckRules wr = WreckRules.of(s);
        wr.wellsOn = false; // a well beside a pad would carry the hero off it
        float[] p = new float[4];
        assertTrue(WorldGen.of("WRECK").pad(1, 0, p) || WorldGen.of("WRECK").pad(0, 1, p) || WorldGen.of("WRECK").pad(-1, 0, p) || WorldGen.of("WRECK").pad(0, -1, p));
        s.px = p[0];
        s.py = p[1];
        steps(s, 0.3);
        assertEquals("half a second is needed: not yet", p[0], s.px, 0.01f);
        steps(s, 0.4);
        assertEquals("sent to the twin", p[2], s.px, 0.5f);
        assertEquals(p[3], s.py, 0.5f);
        assertEquals(1, wr.teleports);
        int a = padAt(wr, p[0], p[1]), b = padAt(wr, p[2], p[3]);
        assertTrue(a >= 0 && b >= 0);
        assertEquals("both ends rest 12 s", 12f, wr.padCd[a], 0.8f);
        assertEquals(12f, wr.padCd[b], 0.8f);
        steps(s, 3.0);
        assertEquals("standing on the twin does not send him back", p[2], s.px, 0.5f);
        assertEquals(1, wr.teleports);
        // step off and on again inside the cooldown: nothing
        s.px = p[2] + 160f;
        steps(s, 0.2);
        s.px = p[2];
        s.py = p[3];
        steps(s, 1.5);
        assertEquals("cooldown", 1, wr.teleports);
        assertEquals(p[2], s.px, 0.5f);
        // after the cooldown, a fresh step-on works (the way back)
        s.px = p[2] + 400f; // wait the rest of the cooldown off the pad (standing on it he would fire the moment it is ready)
        steps(s, 9.0);
        s.px = p[2] + 160f;
        steps(s, 0.2);
        s.px = p[2];
        s.py = p[3];
        steps(s, 0.7);
        assertEquals(2, wr.teleports);
        assertEquals("the round trip ends at the first pad: hero " + s.px + "," + s.py + " pads " + wr.padN + " A " + p[0] + "," + p[1] + " B " + p[2] + "," + p[3], p[0], s.px, 0.5f);
        assertEquals(p[1], s.py, 0.5f);
    }

    @Test
    public void thePadCooldownIsSixSecondsWithTheEchoAndThreeInThePowerSurge() throws Exception {
        Sim s = run(5);
        WreckRules wr = WreckRules.of(s);
        assertEquals(12f, wr.currentPadCooldown(), 0f);
        s.events.active = Events.SURGE;
        assertEquals(3f, wr.currentPadCooldown(), 0f);
        s.events.active = Events.NONE;
        s.giveEquipment("ECHO_OF_THE_PILLAR", Equipment.UNIQUE);
        assertTrue(wr.echoWorn());
        assertEquals(6f, wr.currentPadCooldown(), 0f);
    }

    @Test
    public void arrivalSendsAShockwaveThroughASeededHundredEnemyField() throws Exception {
        Sim s = run(9);
        WreckRules wr = WreckRules.of(s);
        float[] p = new float[4];
        assertTrue(WorldGen.of("WRECK").pad(0, 1, p) || WorldGen.of("WRECK").pad(1, 0, p) || WorldGen.of("WRECK").pad(-1, 0, p) || WorldGen.of("WRECK").pad(0, -1, p));
        s.px = p[0];
        s.py = p[1];
        steps(s, 0.05); // builds the table
        int ia = padAt(wr, p[0], p[1]);
        assertTrue(ia >= 0);
        Random r = new Random(77);
        Enemy[] field = new Enemy[100];
        float[] hp0 = new float[100];
        for (int i = 0; i < 100; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 20 + r.nextDouble() * 380;
            field[i] = s.spawnAt("COLONIST", p[2] + (float) (Math.cos(a) * d), p[3] + (float) (Math.sin(a) * d), false);
            field[i].hp = field[i].maxHp = 5000f; // survives the hit so the damage can be read
            field[i].speed = 0f;
            hp0[i] = field[i].hp;
        }
        wr.teleport(ia);
        int inside = 0, outside = 0;
        float might = (float) s.stats.might;
        for (int i = 0; i < 100; i++) {
            double d = Math.hypot(field[i].x - p[2], field[i].y - p[3]);
            double lost = hp0[i] - field[i].hp;
            if (d < 140.0) {
                inside++;
                assertEquals("30 damage x might inside r 140 (d=" + d + ")", 30f * might, lost, 0.01f);
            } else if (d > 140.0 + field[i].radius * 0.5 + 1.0) {
                outside++;
                assertEquals("nothing outside the ring", 0.0, lost, 0.0);
            }
        }
        assertTrue("a field of 100 puts some inside: " + inside, inside >= 5);
        assertTrue(outside >= 50);
    }

    // ------------------------------------------------------------------ gravity wells

    /** Keeps only well {@code w} in the table (index 0), so another well that happens to lie near cannot disturb a measurement. */
    private static int isolate(WreckRules wr, int w) {
        wr.wellX[0] = wr.wellX[w];
        wr.wellY[0] = wr.wellY[w];
        wr.wellR[0] = wr.wellR[w];
        wr.wellPhase[0] = wr.wellPhase[w];
        wr.wellN = 1;
        wr.wellsFixed = true; // the table is not rebuilt when the hero crosses a cell border
        return 0;
    }

    /** Makes well 0 of the table run (moves the clock to its on phase) and returns its index. */
    private static int runningWell(Sim s, WreckRules wr) {
        steps(s, 0.05);
        assertTrue("the table has wells", wr.wellN > 0);
        for (int i = 0; i < wr.wellN; i++) {
            for (double t = 100; t < 120; t += 0.25) {
                s.time = t;
                if (wr.wellOn(i) && !wr.wellFlicker(i) && wr.wellOn(i)) return i;
            }
        }
        throw new AssertionError("no well");
    }

    @Test
    public void aRunningWellPullsTheHeroAnEnemyAPickupAndAShot() throws Exception {
        Sim s = run(3);
        WreckRules wr = WreckRules.of(s);
        int w = isolate(wr, runningWell(s, wr));
        float wx = wr.wellX[w], wy = wr.wellY[w];
        s.time = 0;
        double t0 = 0;
        for (double t = 100; t < 130; t += 0.01) { // a moment where the well is on for the whole step
            s.time = t;
            if (wr.wellOn(w)) {
                t0 = t;
                break;
            }
        }
        s.px = wx + 100f;
        s.py = wy;
        Enemy e = s.spawnAt("COLONIST", wx, wy + 100f, false);
        e.speed = 0f;
        s.addPickupForTest(Entities.K_GEM, wx - 100f, wy, 1f);
        Pickup gem = s.picks[s.pickN - 1];
        float gx = gem.x, gy = gem.y;
        EProj shot = s.shootForTest(wx, wy - 100f, 0f, 0f, 5f, 5f);
        s.time = t0;
        wr.update(DT);
        assertEquals("hero 40 u/s toward the centre", 40f * DT, 100f - (s.px - wx), 1e-3f);
        assertEquals("enemy 70 u/s", 70f * DT, 100f - (e.y - wy), 1e-3f);
        assertEquals("pickup 120 u/s", 120f * DT, Math.hypot(gem.x - gx, gem.y - gy), 1e-3f);
        assertTrue("a pickup moves toward the well", gem.x > gx);
        assertTrue("the shot curves toward the centre", shot.vy > 0f);
        // a dodge ignores the pull
        s.px = wx + 100f;
        s.dodgeT = 0.2f;
        wr.update(DT);
        assertEquals(wx + 100f, s.px, 1e-4f);
        s.dodgeT = 0f;
        // out of range nothing moves
        s.px = wx + 400f;
        wr.update(DT);
        assertEquals(wx + 400f, s.px, 1e-4f);
        // off: nothing moves either
        s.px = wx + 100f;
        for (double t = t0; t < t0 + 20; t += 0.05) {
            s.time = t;
            if (!wr.wellOn(w)) break;
        }
        assertFalse(wr.wellOn(w));
        wr.update(DT);
        assertEquals("an off well pulls nothing", wx + 100f, s.px, 1e-4f);
    }

    @Test
    public void theCoreHurtsEnemiesAndTheHeroButSparesBossesAndDestroysGems() throws Exception {
        Sim s = run(3);
        WreckRules wr = WreckRules.of(s);
        int w = isolate(wr, runningWell(s, wr));
        float wx = wr.wellX[w], wy = wr.wellY[w];
        double t0 = s.time;
        Enemy a = s.spawnAt("COLONIST", wx + 6f, wy, false), el = s.spawnAt("COLONIST", wx, wy + 6f, false), boss = s.spawnAt("COLONIST", wx - 6f, wy, true); // a boss flag without a boss pattern: it stands there
        for (Enemy e : new Enemy[]{a, el, boss}) {
            e.hp = e.maxHp = 100000f;
            e.speed = 0f;
        }
        el.elite = true;
        s.px = wx;
        s.py = wy + 3f;
        s.hp = s.maxHp = 100000f;
        s.addPickupForTest(Entities.K_GEM, wx + 2f, wy + 2f, 3f);
        s.addPickupForTest(Entities.K_COIN, wx - 2f, wy - 2f, 1f);
        int picks0 = s.pickN;
        float hp0 = s.hp;
        for (int i = 0; i < 60; i++) {
            s.time = t0 + i * DT * 0.0; // keep the well on for the whole second
            wr.update(DT);
        }
        assertEquals("an enemy in the core takes 40 a second", 40f, 100000f - a.hp, 2f);
        assertEquals("an elite 15", 15f, 100000f - el.hp, 1f);
        assertEquals("a boss none", 0f, 100000f - boss.hp, 0f);
        assertEquals("the hero takes 15 a second", 15f, hp0 - s.hp, 1f);
        assertEquals("the gem is gone", 1, wr.gemsDestroyed);
        assertEquals(picks0 - 1, s.pickN);
        boolean coin = false;
        for (int i = 0; i < s.pickN; i++) if (s.picks[i].kind == Entities.K_COIN) coin = true;
        assertTrue("a coin is only pulled, not destroyed", coin);
    }

    @Test
    public void aWellRunsTenSecondsOnAndSixOffFlickersAndIsAlwaysOnInThePowerSurge() throws Exception {
        Sim s = run(3);
        WreckRules wr = WreckRules.of(s);
        steps(s, 0.05);
        int on = 0, n = 0, flickers = 0;
        for (double t = 0; t < 160; t += 0.1) {
            s.time = t;
            n++;
            if (wr.wellOn(0)) on++;
            if (wr.wellFlicker(0)) flickers++;
        }
        assertEquals("10 of 16 seconds", 10.0 / 16.0, on / (double) n, 0.02);
        assertEquals("a second before each change, two a cycle: 2 of 16", 2.0 / 16.0, flickers / (double) n, 0.02);
        s.events.active = Events.SURGE;
        for (double t = 0; t < 40; t += 0.5) {
            s.time = t;
            assertTrue(wr.wellOn(0));
            assertFalse(wr.wellFlicker(0));
        }
    }

    @Test
    public void aBrokenGeneratorSwitchesTheNearestWellOffForAMinute() throws Exception {
        Sim s = run(3);
        WreckRules wr = WreckRules.of(s);
        int w = runningWell(s, wr);
        s.time = 100;
        wr.destBroken(WreckRules.DK_GENERATOR, wr.wellX[w] + 260f, wr.wellY[w], 0L);
        for (double t = 100; t < 159; t += 0.5) {
            s.time = t;
            assertFalse("held off at " + t, wr.wellOn(w));
            assertTrue(wr.wellHeldOff(w));
        }
        s.time = 160.5;
        assertFalse(wr.wellHeldOff(w));
        // generators and crates exist as destructibles around the wells
        int gens = 0, crates = 0;
        Sim s2 = run(4);
        for (int i = 0; i < s2.destN; i++) {
            if (s2.destKind[i] == WreckRules.DK_GENERATOR) gens++;
            if (s2.destKind[i] == WreckRules.DK_CRATE) crates++;
        }
        assertTrue("crates around the start: " + crates, crates > 0);
        // walk far enough that wells (and so generators) lie within the destructible cells
        for (int k = 0; k < 6 && gens == 0; k++) {
            s2.px = 900f + 700f * k;
            s2.py = -400f + 500f * k;
            steps(s2, 0.05);
            for (int i = 0; i < s2.destN; i++) if (s2.destKind[i] == WreckRules.DK_GENERATOR) gens++;
        }
        assertTrue("a generator by a well", gens > 0);
    }

    @Test
    public void aCargoCrateDropsGoldInAboutThirtyPercentOfTheCases() throws Exception {
        Sim s = run(11);
        WreckRules wr = WreckRules.of(s);
        int before = s.pickN;
        for (int i = 0; i < 400; i++) {
            wr.destBroken(WreckRules.DK_CRATE, 0f, 0f, 0L);
            s.pickN = Math.min(s.pickN, 900); // keep the pool from filling up
        }
        int gold = 0;
        for (int i = 0; i < s.pickN; i++) if (s.picks[i].kind == Entities.K_COIN || s.picks[i].kind == Entities.K_COINBAG) gold++;
        assertTrue("about 120 of 400: " + gold, gold > 80 && gold < 170);
        assertTrue(before <= s.pickN);
    }

    @Test
    public void noNaNWithEveryoneOnTheExactCentreOfAWell() throws Exception {
        Sim s = run(3);
        WreckRules wr = WreckRules.of(s);
        int w = isolate(wr, runningWell(s, wr));
        float wx = wr.wellX[w], wy = wr.wellY[w];
        s.px = wx;
        s.py = wy;
        Enemy e = s.spawnAt("COLONIST", wx, wy, false);
        e.speed = 0f;
        e.hp = e.maxHp = 1e6f;
        s.addPickupForTest(Entities.K_COIN, wx, wy, 1f);
        Pickup c = s.picks[s.pickN - 1];
        c.x = wx;
        c.y = wy;
        EProj shot = s.shootForTest(wx, wy, 0f, 0f, 5f, 5f);
        shot.x = wx;
        shot.y = wy;
        s.hp = s.maxHp = 1e6f;
        for (int i = 0; i < 120; i++) {
            s.time = 100 + i * 0.0;
            if (!wr.wellOn(w)) s.time = 0;
            wr.update(DT);
        }
        for (float v : new float[]{s.px, s.py, s.hp, e.x, e.y, e.hp, c.x, c.y, shot.x, shot.y, shot.vx, shot.vy}) assertTrue("finite " + v, !Float.isNaN(v) && !Float.isInfinite(v));
        assertEquals(wx, s.px, 1e-3f);
        assertEquals(wy, e.y, 1e-3f);
    }

    // ------------------------------------------------------------------ the Vacuum Breach

    /** Steps until the breach of 9:00 has opened on the hero and returns its centre in {@code at}. */
    private static void openBreach(Sim s, float[] at) {
        s.time = 536.5;
        Input in = new Input();
        for (int i = 0; i < 600; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
            if (WreckRules.of(s).bubbleOn) break;
        }
        WreckRules wr = WreckRules.of(s);
        assertTrue("the breach opened", wr.bubbleOn);
        at[0] = wr.bubbleX;
        at[1] = wr.bubbleY;
    }

    @Test
    public void aHeroWhoIgnoresTheBubbleLosesAtLeastFiftyHealthOverABreach() throws Exception {
        Sim s = run(2);
        WreckRules wr = WreckRules.of(s);
        wr.padsOn = false;
        wr.wellsOn = false; // the bubble drifts: keep wells and pads out of what is measured
        float[] at = new float[2];
        openBreach(s, at);
        assertEquals("a bubble of 260 on the hero, shrinking", 260f, wr.bubbleR, 12f);
        float hp0 = s.dmgBy[Sim.DMG_AREA];
        Input in = new Input();
        while (wr.bubbleOn && s.ended == 0) {
            s.hp = s.maxHp; // the damage counter is read, not the health
            s.update(DT, in); // never moves
        }
        float lost = s.dmgBy[Sim.DMG_AREA] - hp0;
        assertTrue("outside loses at least 50: " + lost, lost >= 50f);
        assertEquals("the damage counter agrees", lost, wr.bubbleDamage, 1f);
    }

    @Test
    public void aHeroInsideTheBubbleLosesNothingAndEnemiesOutsideLoseAShareOfTheirHealth() throws Exception {
        Sim s = run(2);
        WreckRules wr = WreckRules.of(s);
        wr.padsOn = false;
        wr.wellsOn = false; // the bubble drifts: keep wells and pads out of what is measured
        float[] at = new float[2];
        openBreach(s, at);
        Enemy out = s.spawnAt("COLONIST", wr.bubbleX + 400f, wr.bubbleY, false), in2 = s.spawnAt("COLONIST", wr.bubbleX + 100f, wr.bubbleY, false),
                boss = s.spawnAt("COLONIST", wr.bubbleX - 400f, wr.bubbleY, true);
        for (Enemy e : new Enemy[]{out, in2, boss}) {
            e.speed = 0f;
            e.hp = e.maxHp = 10000f;
        }
        boss.x = wr.bubbleX - 400f;
        float hp0 = s.dmgBy[Sim.DMG_AREA];
        Input in = new Input();
        int steps = 0;
        float[] c = new float[2];
        while (wr.bubbleOn && steps++ < 600) { // the bot rides the bubble's centre
            s.px = wr.bubbleX;
            s.py = wr.bubbleY;
            out.x = wr.bubbleX + 400f;
            out.y = wr.bubbleY;
            in2.x = wr.bubbleX + 100f;
            in2.y = wr.bubbleY;
            boss.x = wr.bubbleX - 400f;
            boss.y = wr.bubbleY;
            s.hp = s.maxHp;
            s.update(DT, in);
        }
        assertEquals("inside loses nothing", 0f, s.dmgBy[Sim.DMG_AREA] - hp0, 0.01f);
        assertEquals(0f, wr.bubbleDamage, 0.01f);
        assertTrue("an enemy outside lost health: " + out.hp, out.hp < 9990f);
        assertTrue("an enemy inside the bubble is not hurt by it (only the hero's own weapon): " + in2.hp, in2.hp > out.hp || out.dead);
        assertEquals("bosses are spared", 10000f, boss.hp, 0.01f);
    }

    @Test
    public void breachesComeAtTheFiveSetTimesWithTwoSecondsOfWarning() throws Exception {
        Sim s = run(2);
        WreckRules wr = WreckRules.of(s);
        float[] at = {240f, 540f, 840f, 1140f, 1440f};
        Input in = new Input();
        for (float t : at) {
            s.time = t - 5f;
            for (int i = 0; i < 60; i++) s.update(DT, in);
            assertFalse("quiet before", wr.bubbleOn || wr.breachWarn > 0f);
            for (int i = 0; i < 330; i++) { // from 4 s before to 1.5 s after
                s.hp = s.maxHp;
                s.update(DT, in);
                if (s.time > t - 1.9 && s.time < t - 0.1) assertTrue("warning at " + s.time, wr.breachWarn > 0f && !wr.bubbleOn);
            }
            assertTrue("open at " + t, wr.bubbleOn);
            s.time = t + 30f;
            s.update(DT, in);
            assertFalse("sealed after 25 s", wr.bubbleOn);
        }
        assertEquals(5, wr.breaches);
    }

    // ------------------------------------------------------------------ the Pillar Gate and the finale

    @Test
    public void theGateOpensAtTwentySevenThirtyAndTakesTheHeroToTheArenaDeterministically() throws Exception {
        float[][] gate = new float[2][2], arrival = new float[2][2];
        for (int run = 0; run < 2; run++) {
            Sim s = run(77);
            WreckRules wr = WreckRules.of(s);
            s.time = 1640;
            steps(s, 5);
            assertFalse(wr.gateOn);
            steps(s, 7); // 27:30 passes
            assertTrue("the gate is open", wr.gateOn);
            gate[run][0] = wr.gateX;
            gate[run][1] = wr.gateY;
            double d = Math.hypot(wr.gateX - s.px, wr.gateY - s.py);
            assertTrue("250-350 u from the hero: " + d, d >= 249.0 && d <= 351.0);
            assertTrue(wr.gateBannerT > 0f);
            s.px = wr.gateX;
            s.py = wr.gateY;
            steps(s, 0.6);
            arrival[run][0] = s.px;
            arrival[run][1] = s.py;
            assertEquals("in the arena", wr.arenaX, s.px, 0.5f);
            assertEquals(wr.arenaY, s.py, 0.5f);
            assertTrue("inside the 500 u arena", DebrisLayout.inArena(s.px, s.py, 0f));
            assertEquals("320 u from the spire", 320.0, Math.hypot(s.px - DebrisLayout.pillarX(), s.py - DebrisLayout.pillarY()), 1.0);
            s.time = 1771;
            steps(s, 0.1);
            assertFalse("closed at 29:30", wr.gateOn);
        }
        assertEquals("same seed, same gate", gate[0][0], gate[1][0], 0f);
        assertEquals(gate[0][1], gate[1][1], 0f);
        assertEquals("same arrival", arrival[0][0], arrival[1][0], 0f);
        assertEquals(arrival[0][1], arrival[1][1], 0f);
    }

    /** Spawns the Pillar and puts the hero in its arena. */
    private static Enemy pillarFight(Sim s) {
        Enemy p = s.spawnAt("THE_PILLAR", 0f, 0f, true);
        assertNotNull(p);
        s.px = DebrisLayout.pillarX() + 200f;
        s.py = DebrisLayout.pillarY();
        return p;
    }

    @Test
    public void thePillarArrivesAtItsSpireAsleepWhileTheHeroIsFarAndWakesInTheArena() throws Exception {
        Sim s = run(5);
        Enemy p = s.spawnAt("THE_PILLAR", 10f, 10f, true);
        assertTrue("asleep while the hero is 2400 u away", p.sleeping);
        assertTrue(p.boss);
        steps(s, 1.0);
        assertEquals("it stands at its spire", DebrisLayout.pillarX(), p.x, 0f);
        assertEquals(DebrisLayout.pillarY(), p.y, 0f);
        assertEquals("a sleeping boss is not recycled toward the hero", DebrisLayout.pillarX(), p.x, 0f);
        s.px = DebrisLayout.pillarX() + 300f;
        s.py = DebrisLayout.pillarY();
        steps(s, 0.2);
        assertFalse("awake in the arena", p.sleeping);
        // the real thing: the fifth timed boss at 28:00
        GameData d = TestData.load();
        assertEquals(1680, d.stages.get("WRECK").timedAt[4]);
        assertEquals("THE_PILLAR", d.stages.get("WRECK").timedBoss[4]);
    }

    @Test
    public void thePillarFightRunsThroughItsThreePhasesAndKillingItWinsWithDoubleGold() throws Exception {
        Sim s = run(5);
        WreckRules wr = WreckRules.of(s);
        s.time = 1700;
        Enemy p = pillarFight(s);
        s.hp = s.maxHp = 100000f;
        steps(s, 3.5);
        int lanes = 0;
        for (int i = 0; i < s.hazN; i++) if (s.hazards[i].kind == Entities.HZ_LANE) lanes++;
        assertEquals("phase 1: four rotating beams", 4, lanes);
        assertEquals(0, p.phase);
        assertNotNull("the exit pad exists in the arena", wr.pillar());
        boolean exit = false;
        for (int i = 0; i < wr.padN; i++) if (wr.padKind[i] == WreckRules.PK_EXIT) exit = true;
        assertTrue("an emergency exit pad", exit);

        p.hp = p.maxHp * 0.6f; // phase 2: two orbiting wells and an echo wave
        steps(s, 3.4);
        assertEquals(1, p.phase);
        assertTrue("two wells orbit", wr.tmpLife[2] > 0f && wr.tmpLife[3] > 0f);
        double sep = Math.hypot(wr.tmpX[2] - wr.tmpX[3], wr.tmpY[2] - wr.tmpY[3]);
        assertEquals("opposite each other round the spire", 520.0, sep, 2.0);
        assertTrue("an echo wave: " + s.eprojN, s.eprojN >= 8);
        int echoes = s.eprojN;

        p.hp = p.maxHp * 0.3f; // phase 3: a breach inside the arena and the pulse
        steps(s, 0.2);
        assertEquals(2, p.phase);
        assertTrue("the arena bubble", wr.bubbleOn);
        assertFalse("the orbit wells are gone", wr.tmpLife[2] > 0f);
        float cx = wr.bubbleX, cy = wr.bubbleY;
        steps(s, 0.5);
        assertTrue("the bubble follows the Pillar's pulse (moves)", Math.hypot(wr.bubbleX - cx, wr.bubbleY - cy) > 1.0);
        // stand far outside the bubble but in the arena: the pulse (28) and the drain hurt
        float before = s.dmgBy[Sim.DMG_AREA];
        s.px = DebrisLayout.pillarX() + 420f;
        s.py = DebrisLayout.pillarY();
        steps(s, 7.5);
        assertTrue("the pulse and the breach hurt the hero outside: " + (s.dmgBy[Sim.DMG_AREA] - before), s.dmgBy[Sim.DMG_AREA] - before >= 28f);

        // the Pillar falls
        s.gold = 100;
        s.killForTest(p);
        steps(s, 0.1);
        assertTrue(s.firstKilled);
        assertTrue(wr.pillarDead);
        assertEquals("not won yet: a moment of triumph", 0, s.ended);
        steps(s, 2.6);
        assertEquals("won", 2, s.ended);
        assertEquals("gold counts twice", s.gold * 2, s.endGold());
        assertFalse("the bubble is gone with it", wr.bubbleOn);
    }

    // ------------------------------------------------------------------ roster

    @Test
    public void aRiftHoundBlinksToAFlankEverySixSecondsAndComesInFours() throws Exception {
        Sim s = run(8);
        WreckRules wr = WreckRules.of(s);
        wr.packs = false;
        Enemy h = s.spawnAt("RIFT_HOUND", 400f, 0f, false);
        h.speed = 60f;
        h.hp = h.maxHp = 1e6f;
        s.faceX = 1f;
        s.faceY = 0f;
        Input in = new Input();
        int b0 = wr.blinks;
        for (int i = 0; i < 60 * 14 && wr.blinks == b0; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
        }
        assertTrue("it blinked within 14 s", wr.blinks > b0);
        double d = Math.hypot(h.x - s.px, h.y - s.py);
        assertTrue("150-250 u from the hero: " + d, d >= 149.0 && d <= 251.0);
        double ang = Math.abs(Math.atan2(h.y - s.py, h.x - s.px)); // the hero faces +x: a flank is 60-110 degrees off it
        assertTrue("a flank, not in front or behind: " + Math.toDegrees(ang), ang > Math.toRadians(58) && ang < Math.toRadians(112));
        // packs of four
        Sim t = run(8);
        t.spawnAt("RIFT_HOUND", 400f, 0f, false);
        int hounds = 0;
        for (int i = 0; i < t.enemyN; i++) if (t.enemies[i].type.def.key.equals("RIFT_HOUND")) hounds++;
        assertEquals("a pack of 4", 4, hounds);
    }

    @Test
    public void aBreacherShowsALineDivesAndBurstsForTwentyAndNoMoreThanEightLive() throws Exception {
        Sim s = run(8);
        WreckRules wr = WreckRules.of(s);
        Enemy b = s.spawnAt("BREACHER", 250f, 0f, false);
        b.hp = b.maxHp = 1e6f;
        s.hp = s.maxHp = 1000f;
        float armor = (float) s.stats.armor;
        boolean line = false;
        Input in = new Input();
        for (int i = 0; i < 60 * 14 && wr.bursts == 0; i++) {
            s.update(DT, in);
            if (b.tele > 0f) {
                line = true;
                assertEquals("a thin line", 14f, b.teleW, 0f);
            }
        }
        assertTrue("a warning line was shown", line);
        assertEquals(1, wr.bursts);
        assertTrue("the burst kills it", b.dead);
        float lost = 1000f - s.hp;
        assertTrue("about 20 damage: " + lost, lost >= 20f - armor - 0.5f && lost <= 20.5f);
        // the cap
        Sim t = run(8);
        for (int i = 0; i < 12; i++) t.spawnAt("BREACHER", 600f + i, 0f, false);
        int alive = 0;
        for (int i = 0; i < t.enemyN; i++) if (t.enemies[i].type.def.key.equals("BREACHER") && !t.enemies[i].dead) alive++;
        assertEquals("at most 8 alive", 8, alive);
    }

    @Test
    public void hullBreacherAlphaOpensAWellWithEveryStompAndTheWardenFansBoltsAndLeavesDecoys() throws Exception {
        Sim s = run(6);
        WreckRules wr = WreckRules.of(s);
        Enemy a = s.spawnAt("HULL_BREACHER_ALPHA", 200f, 0f, true);
        a.hp = a.maxHp = 1e6f;
        s.hp = s.maxHp = 1e6f;
        Input in = new Input();
        boolean well = false, ring = false;
        for (int i = 0; i < 60 * 12 && !well; i++) {
            s.update(DT, in);
            for (int k = 0; k < s.hazN; k++) if (s.hazards[k].kind == Entities.HZ_BOMB && s.hazards[k].variant == Entities.BV_STOMP) ring = true;
            if (wr.tmpLife[0] > 0f || wr.tmpLife[1] > 0f) well = true;
        }
        assertTrue("a stomp ring first", ring);
        assertTrue("the stomp opened a well", well);
        int k = wr.tmpLife[0] > 0f ? 0 : 1;
        assertEquals(160f, wr.tmpR[k], 0f);
        assertEquals("on the hero (the stomp shoves him a little)", s.px, wr.tmpX[k], 120f);
        assertTrue("lives about 6 s", wr.tmpLife[k] > 4f && wr.tmpLife[k] <= 6f);

        Sim w = run(6);
        Enemy ward = w.spawnAt("SALVAGE_WARDEN", 300f, 0f, true);
        ward.hp = ward.maxHp = 1e6f;
        w.hp = w.maxHp = 1e6f;
        int shots = 0, decoys = 0;
        for (int i = 0; i < 60 * 14; i++) {
            w.update(DT, in);
            shots = Math.max(shots, w.eprojN);
            int dc = 0;
            for (int j = 0; j < w.enemyN; j++) if (w.enemies[j].type.def.key.equals("WARDEN_DECOY")) dc++;
            decoys = Math.max(decoys, dc);
        }
        assertTrue("a fan of bolts: " + shots, shots >= 5);
        assertTrue("its blink leaves decoys: " + decoys, decoys >= 1);
    }

    @Test
    public void theRosterDataFollowsTheSpec() throws Exception {
        GameData d = TestData.load();
        se.felixarvid.thorsurvivors.data.StageDef st = d.stages.get("WRECK");
        assertEquals("Meridian Wreck", st.name);
        String[] first = st.rows.get(0).enemies, m4 = st.rowAt(4 * 60 + 1).enemies, m9 = st.rowAt(9 * 60 + 1).enemies, m14 = st.rowAt(14 * 60 + 1).enemies;
        assertTrue(java.util.Arrays.asList(first).contains("SECURITY_DRONE") && java.util.Arrays.asList(first).contains("COLONIST"));
        assertFalse(java.util.Arrays.asList(first).contains("RIFT_HOUND"));
        assertTrue("Rift Hounds from 4", java.util.Arrays.asList(m4).contains("RIFT_HOUND"));
        assertTrue("Sergeants from 6", java.util.Arrays.asList(st.rowAt(6 * 60 + 1).enemies).contains("SERGEANT") && !java.util.Arrays.asList(m4).contains("SERGEANT"));
        assertTrue("Vire from 8", java.util.Arrays.asList(st.rowAt(8 * 60 + 1).enemies).contains("VIRE") && !java.util.Arrays.asList(st.rowAt(7 * 60 + 1).enemies).contains("VIRE"));
        assertTrue("Breachers from 9", java.util.Arrays.asList(m9).contains("BREACHER") && !java.util.Arrays.asList(st.rowAt(8 * 60 + 1).enemies).contains("BREACHER"));
        assertTrue("Carriers from 14", java.util.Arrays.asList(m14).contains("SPORE_CARRIER") && !java.util.Arrays.asList(st.rowAt(13 * 60 + 1).enemies).contains("SPORE_CARRIER"));
        assertEquals(java.util.Arrays.asList("HULL_BREACHER_ALPHA", "SALVAGE_WARDEN", "COMMANDANT_HALE", "OVERSEER_UNIT_7", "THE_PILLAR"),
                java.util.Arrays.asList(st.timedBoss));
        assertEquals(450, st.timedAt[0]);
        assertEquals(750, st.timedAt[1]);
        assertEquals("the Pillar at 28:00", 1680, st.timedAt[4]);
        for (String id : st.eventId) assertFalse("no Tide on the Wreck", id.equals("TIDE"));
        assertEquals(1480, st.eventAt[2]); // Power Surge at 24:40
        assertEquals("the echo is a Neck unique of the Pillar", "THE_PILLAR", d.equipment.get("ECHO_OF_THE_PILLAR").source);
        assertEquals(se.felixarvid.thorsurvivors.data.EquipDef.NECK, d.equipment.get("ECHO_OF_THE_PILLAR").slot);
        assertEquals(0.62f, d.lightingOf("WRECK")[0], 1e-6f);
        // events and hordes keep clear of every breach
        for (double[] hb : st.hordes) {
            for (float b : WreckRules.BREACH_AT) assertTrue("horde at " + hb[0] + " vs breach " + b, hb[0] + hb[1] <= b - 2 || hb[0] >= b + 25);
        }
        for (int i = 0; i < st.eventAt.length; i++) {
            double end = st.eventAt[i] + Events.LEN[java.util.Arrays.asList(Events.IDS).indexOf(st.eventId[i])];
            for (float b : WreckRules.BREACH_AT) assertTrue("event " + st.eventId[i] + " vs breach " + b, end <= b - 2 || st.eventAt[i] >= b + 25);
        }
    }

    // ------------------------------------------------------------------ soak

    @Test
    public void thirtyMinuteSoaksOnTheWreckHaveNoNaNAndNoOverflow() throws Exception {
        String[] policies = {"immortal", "mid", "naked"};
        for (String policy : policies) {
            StageSoak.Result r = StageSoak.run("WRECK", "BASTION", policy, 21, 1800);
            System.out.println("WRECK SOAK " + r);
            assertEquals(policy + " NaN: " + r.firstProblem, 0, r.nan);
            assertEquals(policy + " overflow: " + r.firstProblem, 0, r.overflow);
            assertEquals(policy + " cap: " + r.firstProblem, 0, r.capBreak);
            if (policy.equals("immortal")) assertEquals("immortal reaches the end", 2, r.ended);
        }
    }
}
