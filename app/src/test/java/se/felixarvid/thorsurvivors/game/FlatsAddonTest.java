package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.StageDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.stage.FlatsRules;

import java.util.HashMap;

/**
 * Cinder Flats add-ons (v1.4 chunk E): storm wind and dry lightning, the Ash Crawler Convoy, Lumen Blooms. Cadences, telegraph
 * times, damage, ignition chains, the convoy's reward and despawn, the bloom hold and its hound pack, and the isolation switch.
 */
public class FlatsAddonTest {
    private static final float DT = 1f / 60f;

    /** A quiet FLATS run: no wave spawns, no places, hero at the origin, add-ons on. */
    static Sim run(long seed, double from) throws Exception {
        GameData d = StageSoak.data();
        Sim s = new Sim(d, d.stages.get("FLATS"), null, new HashMap<String, Double>(), "BASTION", seed);
        s.noSpawnsForTest();
        s.pois.enabled = false;
        s.rules.spawnWhenQuiet = true; // silent waves, live add-ons
        s.time = from;
        s.enemyN = 0; // the opening crowd would muddy every count
        return s;
    }

    static void step(Sim s, double seconds) {
        Input in = new Input();
        int n = (int) Math.round(seconds * 60);
        for (int i = 0; i < n; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
            s.sound.clear();
        }
    }

    static FlatsRules rules(Sim s) {
        return (FlatsRules) s.rules;
    }

    // ------------------------------------------------------------------ storm

    @Test
    public void lightningStrikesEveryOneAndAHalfSecondsInTheStormOnly() throws Exception {
        Sim s = run(1, 690);
        FlatsRules r = rules(s);
        int maxLive = 0;
        for (int i = 0; i < 60 * 30; i++) { // 11:30 .. 12:00: calm
            step(s, DT);
            maxLive = Math.max(maxLive, r.strikeN);
        }
        assertEquals("no lightning before the storm", 0, r.strikesFired);
        assertEquals(0, maxLive);
        for (int i = 0; i < 60 * 25; i++) { // 12:00 .. 12:25
            step(s, DT);
            maxLive = Math.max(maxLive, r.strikeN);
            assertTrue(r.strikeN <= FlatsRules.MAX_STRIKES);
        }
        // storm 20 s: a ring every 1.5 s starting 1.5 s in (13 rings), each strikes 1 s later; the last ring (t=19.5) lands after the storm
        assertTrue("strikes " + r.strikesFired, r.strikesFired >= 12 && r.strikesFired <= 13);
        assertTrue("only a few warnings live at once: " + maxLive, maxLive >= 1 && maxLive <= 2);
        int before = r.strikesFired;
        step(s, 20);
        assertEquals("quiet again after the storm", before, r.strikesFired);
        assertEquals(0, r.strikeN);
    }

    private static void step(Sim s, float seconds) {
        FlatsAddonTest.step(s, (double) seconds);
    }

    @Test
    public void aWarningRingLastsOneSecondThenStrikes() throws Exception {
        Sim s = run(2, 721.4);
        FlatsRules r = rules(s);
        double born = -1, struck = -1;
        for (int i = 0; i < 60 * 4 && struck < 0; i++) {
            step(s, DT);
            if (born < 0 && r.strikeN > 0) born = s.time;
            if (born >= 0 && r.strikesFired > 0) struck = s.time;
        }
        assertTrue(born > 0 && struck > 0);
        assertEquals(FlatsRules.STRIKE_WARN, struck - born, 0.05);
    }

    @Test
    public void aboutOneStrikeInThreeAimsAtTheHero() throws Exception {
        int onHero = 0, total = 0;
        for (long seed = 1; seed <= 6; seed++) {
            Sim s = run(seed, 1321);
            FlatsRules r = rules(s);
            step(s, 19);
            onHero += r.strikesOnHero;
            total += r.strikeN + r.strikesFired;
        }
        double share = onHero / (double) total;
        assertTrue("share " + share, share > 0.2 && share < 0.47);
    }

    @Test
    public void aStrikeHurtsTheHeroInTheRingForEighteenAndADodgeProtects() throws Exception {
        Sim s = run(3, 100);
        FlatsRules r = rules(s);
        s.hp = s.maxHp;
        float before = s.hp;
        r.strikeX[0] = s.px + 20f;
        r.strikeY[0] = s.py;
        r.strikeT[0] = 0.01f;
        r.strikeN = 1;
        s.update(DT, new Input()); // not in a storm: the ring still resolves
        float expected = Math.max(1f, FlatsRules.STRIKE_DMG - (float) s.stats.armor) * (float) s.stats.damageTaken;
        assertEquals(expected, before - s.hp, 0.01f);
        // outside the ring: nothing
        s.hp = s.maxHp;
        s.invuln = 0f;
        r.strikeX[0] = s.px + FlatsRules.STRIKE_R + 40f;
        r.strikeY[0] = s.py;
        r.strikeT[0] = 0.01f;
        r.strikeN = 1;
        s.update(DT, new Input());
        assertEquals(s.maxHp, s.hp, 0.001f);
        // dodging (invulnerable): protected
        r.strikeX[0] = s.px;
        r.strikeY[0] = s.py;
        r.strikeT[0] = 0.01f;
        r.strikeN = 1;
        s.invuln = 1f;
        s.update(DT, new Input());
        assertEquals(s.maxHp, s.hp, 0.001f);
    }

    @Test
    public void lightningIgnitesBarrelsAndTheyChain() throws Exception {
        Sim s = run(4, 100);
        FlatsRules r = rules(s);
        s.update(DT, new Input());
        float bx = s.px + 500f, by = s.py + 300f;
        s.destN = 0;
        for (int i = 0; i < 3; i++) s.addDest(bx + i * 55f, by, Sim.DK_BARREL, 9000 + i); // 55 u apart: a blast of 80 reaches the next
        s.addDest(bx - 150f, by, Sim.DK_CHEST, 9100); // a chest in range of nothing: the strike skips chests
        Enemy e = s.spawnAt("COLONIST", bx + 70f, by + 10f, false);
        e.speed = 0f;
        float hp = e.hp;
        r.strikeX[0] = bx;
        r.strikeY[0] = by;
        r.strikeT[0] = 0.01f;
        r.strikeN = 1;
        s.rebuildGridForTest();
        s.update(DT, new Input());
        int barrels = 0, chests = 0;
        for (int i = 0; i < s.destN; i++) {
            if (s.destKind[i] == Sim.DK_BARREL) barrels++;
            if (s.destKind[i] == Sim.DK_CHEST) chests++;
        }
        assertEquals("the whole cluster went off", 0, barrels);
        assertEquals(1, chests);
        assertTrue("the blast hurt the colonist", e.hp < hp || e.dead);
    }

    @Test
    public void windDriftsHeroEnemiesAndPickupsAtTwentyFiveUnitsPerSecond() throws Exception {
        Sim s = run(5, 730); // full storm
        FlatsRules r = rules(s);
        s.stats.moveSpeedMul = 1.0;
        s.update(DT, new Input());
        Enemy e = s.spawnAt("COLONIST", s.px + 400f, s.py, false);
        e.stun = 100f;
        s.addPickupForTest(Entities.K_COIN, s.px - 400f, s.py, 1f);
        float x0 = s.px, y0 = s.py, ex = e.x, ey = e.y;
        float px0 = s.picks[0].x, py0 = s.picks[0].y;
        for (int i = 0; i < 60; i++) {
            s.invuln = 1f;
            r.strikeN = 0;
            step(s, DT);
        }
        float hero = (float) Math.hypot(s.px - x0, s.py - y0), enemy = (float) Math.hypot(e.x - ex, e.y - ey);
        assertEquals("hero drift", 25f, hero, 1.5f);
        assertEquals("enemy drift", 25f, enemy, 2f);
        assertEquals("along the storm's wind", r.windX, (s.px - x0) / hero, 0.05f);
        assertEquals("pickup drift", 25f, (float) Math.hypot(s.picks[0].x - px0, s.picks[0].y - py0), 2f);
    }

    @Test
    public void addonsOffMeansNoWindNoLightning() throws Exception {
        Sim s = run(6, 725);
        s.rules.addonsEnabled = false;
        double x0 = s.px;
        step(s, 15);
        assertEquals(0, rules(s).strikesFired);
        assertEquals(x0, s.px, 0.001);
        assertNull(rules(s).tanker);
    }

    // ------------------------------------------------------------------ convoy

    @Test
    public void convoysComeThreeTimesAndKeepClearOfBossesEventsAndStorms() throws Exception {
        GameData d = StageSoak.data();
        StageDef f = d.stages.get("FLATS");
        double[] at = {f.rule("convoyAt0", -1), f.rule("convoyAt1", -1), f.rule("convoyAt2", -1)};
        double[] nominal = {480, 960, 1440};
        for (int i = 0; i < 3; i++) {
            assertTrue("convoy " + i + " at " + at[i], at[i] > 0);
            assertTrue("shifted at most 45 s from 8/16/24 min", Math.abs(at[i] - nominal[i]) <= 45);
            double a = at[i], b = a + FlatsRules.CONVOY_LEAVE;
            for (int k = 0; k < f.timedAt.length; k++) assertFalse("timed boss " + f.timedAt[k] + " vs convoy " + i, a >= f.timedAt[k] && a < f.timedAt[k] + 45);
            for (int k = 0; k < f.eventAt.length; k++) {
                int kind = java.util.Arrays.asList(Events.IDS).indexOf(f.eventId[k]);
                double e0 = f.eventAt[k], e1 = e0 + Events.LEN[kind];
                assertTrue("event " + f.eventId[k] + " vs convoy " + i, b <= e0 || a >= e1);
            }
            for (double[] h : f.hordes) assertTrue("horde at " + h[0], Math.min(b, h[0] + 16) - Math.max(a, h[0]) <= 5);
            assertTrue(b <= 720 || a >= 740);   // the storms
            assertTrue(b <= 1320 || a >= 1340);
        }
    }

    @Test
    public void theConvoyCrossesAtNinetyWithEightEscortsOneElite() throws Exception {
        Sim s = run(7, 494);
        FlatsRules r = rules(s);
        step(s, 1.2); // 495 s
        Enemy t = r.tanker;
        assertNotNull(t);
        assertEquals(EnemyDef.ARCH_CONVOY, t.type.arch);
        assertEquals(400f, t.maxHp, 0.01f);
        int escorts = 0, elite = 0;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (e.poi == AddonOps.POI_ESCORT) {
                escorts++;
                assertEquals("SERGEANT", e.type.def.key);
                if (e.elite) elite++;
            }
        }
        assertEquals(8, escorts);
        assertEquals(1, elite);
        float x0 = t.x, y0 = t.y;
        step(s, 1.0);
        assertEquals(90f, (float) Math.hypot(t.x - x0, t.y - y0), 1.5f);
        // the path goes through the hero's neighbourhood: closest approach within the offset
        double best = 1e9;
        for (int i = 0; i < 60 * 25; i++) {
            step(s, DT);
            best = Math.min(best, Math.hypot(t.x - s.px, t.y - s.py));
        }
        assertTrue("passes near the hero: " + best, best <= FlatsRules.CONVOY_OFFSET + 40);
    }

    @Test
    public void killingTheTankerPaysAnArmsCrateAndDoubleGold() throws Exception {
        Sim s = run(8, 495);
        FlatsRules r = rules(s);
        step(s, 0.2);
        Enemy t = r.tanker;
        assertNotNull(t);
        int bags = 0;
        s.killForTest(t);
        step(s, DT * 3);
        assertEquals(1, r.convoysKilled);
        assertNull(r.tanker);
        for (int i = 0; i < s.pickN; i++) if (s.picks[i].kind == Entities.K_COINBAG) { bags++; assertEquals(20f, s.picks[i].value, 0.01f); }
        assertEquals(2, bags);
        boolean toast = false;
        for (String t2 : s.toastText) if ("CONVOY DESTROYED".equals(t2)) toast = true;
        assertTrue(toast);
        // the third convoy pays gear instead: a worn or queued item changes
        Sim s3 = run(9, 1485);
        step(s3, 0.2);
        int worn = s3.equipN;
        s3.killForTest(rules(s3).tanker);
        step(s3, DT * 3);
        assertEquals(1, rules(s3).convoysKilled);
        assertTrue("a piece of gear arrived", s3.equipN > worn || wornCount(s3) > 0);
    }

    private static int wornCount(Sim s) {
        int n = 0;
        for (int i = 0; i < s.equip.def.length; i++) if (s.equip.def[i] != null) n++;
        return n;
    }

    @Test
    public void anUnkilledTankerLeavesAfterSixtySecondsWithoutAReward() throws Exception {
        Sim s = run(10, 495);
        FlatsRules r = rules(s);
        step(s, 0.2);
        assertNotNull(r.tanker);
        step(s, 60);
        assertNull(r.tanker);
        assertEquals(1, r.convoysLeft);
        assertEquals(0, r.convoysKilled);
        for (String t2 : s.toastText) assertFalse("CONVOY DESTROYED".equals(t2));
        for (int i = 0; i < s.enemyN; i++) assertTrue(s.enemies[i].type.arch != EnemyDef.ARCH_CONVOY);
        step(s, 5);
        assertNull(r.tanker); // not again the same convoy
    }

    @Test
    public void aJumpInTimeSkipsConvoysThatAreOver() throws Exception {
        Sim s = run(11, 1200);
        step(s, 1);
        assertNull(rules(s).tanker);
        s.time = 1484.5;
        step(s, 1);
        assertNotNull(rules(s).tanker);
    }

    // ------------------------------------------------------------------ blooms

    private static float[] findSite(Sim s, FlatsRules r) {
        float[] out = new float[2];
        for (int j = -6; j <= 6; j++) for (int i = -6; i <= 6; i++) if (r.siteOf(i, j, out)) return new float[]{out[0], out[1], i, j};
        return null;
    }

    @Test
    public void bloomSitesAreDeterministicOnePerCellAndOnlyOnLumen() throws Exception {
        Sim a = run(12, 100), b = run(99, 100);
        float[] sa = findSite(a, rules(a)), sb = findSite(b, rules(b));
        assertNotNull("a bloom site within 9 km", sa);
        assertEquals(sa[0], sb[0], 0f);
        assertEquals(sa[1], sb[1], 0f);
        assertTrue(a.map.lumenField(sa[0] / se.felixarvid.thorsurvivors.game.WorldGen.TILE, sa[1] / se.felixarvid.thorsurvivors.game.WorldGen.TILE) > FlatsRules.BLOOM_FIELD);
        int cells = 0, sites = 0;
        float[] out = new float[2];
        for (int j = -10; j <= 10; j++) for (int i = -10; i <= 10; i++) { cells++; if (rules(a).siteOf(i, j, out)) sites++; }
        assertTrue("sites are rare but exist: " + sites + "/" + cells, sites >= 3 && sites < cells / 2);
    }

    @Test
    public void standingThreeSecondsInABloomGivesALevelAndSixHounds() throws Exception {
        Sim s = run(13, 100);
        FlatsRules r = rules(s);
        float[] site = findSite(s, r);
        s.px = site[0];
        s.py = site[1];
        step(s, 0.5); // the cell list refreshes
        assertTrue(r.bloomN >= 1);
        int lvl = s.level;
        step(s, 1.5);
        assertEquals(0, r.bloomsDone);
        assertTrue(r.bloomHold > 1f);
        for (int i = 0; i < 120 && r.bloomsDone == 0; i++) step(s, DT);
        assertEquals(1, r.bloomsDone);
        int hounds = 0;
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].poi == AddonOps.POI_BLOOM && s.enemies[i].type.def.key.equals("SPORE_HOUND")) hounds++;
        assertEquals(6, hounds);
        step(s, 1);
        assertEquals("one level of XP", lvl + 1, s.level);
        // harvested: standing there again does nothing
        step(s, 4);
        assertEquals(1, r.bloomsDone);
    }

    @Test
    public void leavingABloomResetsTheHoldAndTheFirstMinuteIsCalm() throws Exception {
        Sim s = run(14, 100);
        FlatsRules r = rules(s);
        float[] site = findSite(s, r);
        s.px = site[0];
        s.py = site[1];
        step(s, 2.0);
        assertTrue(r.bloomHold > 1.5f);
        s.px += 400f;
        step(s, 0.2);
        assertEquals(0f, r.bloomHold, 0.001f);
        Sim early = run(15, 30);
        FlatsRules re = rules(early);
        early.px = site[0];
        early.py = site[1];
        step(early, 5);
        assertEquals("no harvesting in the first 60 s", 0, re.bloomsDone);
        assertEquals(0, re.bloomN);
    }

    // ------------------------------------------------------------------ soak

    @Test
    public void thirtyMinutesWithTheAddonsStayClean() throws Exception {
        StageSoak.Result r = StageSoak.run("FLATS", "BASTION", "immortal", 3, 1800);
        System.out.println(r);
        assertEquals(r.toString(), 0, r.nan);
        assertEquals(r.toString(), 0, r.overflow);
        assertEquals(r.toString(), 0, r.capBreak);
        assertTrue(r.toString(), r.peakEProj <= Sim.MAX_EPROJ && r.peakHaz <= Sim.MAX_HAZ);
    }
}
