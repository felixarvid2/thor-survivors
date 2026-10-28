package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.stage.MinesRules;

import java.util.HashMap;

/**
 * Deep Mines add-ons (v1.4 chunk E): mine carts on the central lane, firedamp pockets, Dark Lurkers. Cadence and telegraph, the
 * damage and knockback numbers, that only the lane is hit, what lights a pocket and what does not, the chain, the refill, the
 * lurker visibility rule and cap, the isolation switch and a 30 minute soak.
 */
public class MinesAddonTest {
    private static final float DT = 1f / 60f;

    static Sim run(long seed, double from) throws Exception {
        GameData d = StageSoak.data();
        Sim s = new Sim(d, d.stages.get("MINES"), null, new HashMap<String, Double>(), "BASTION", seed);
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

    static MinesRules rules(Sim s) {
        return (MinesRules) s.rules;
    }

    private static Enemy post(Sim s, String key, float x, float y) {
        Enemy e = s.spawnAt(key, x, y, false);
        e.speed = 0f;
        e.hp = e.maxHp = 100000f;
        e.stun = 1000f;
        return e;
    }

    // ------------------------------------------------------------------ carts

    @Test
    public void aCartComesEveryNinetySecondsFromThreeMinutesWithATwoSecondWarning() throws Exception {
        Sim s = run(1, 150);
        MinesRules r = rules(s);
        step(s, 29.5);
        assertEquals("nothing before 3:00", 0, r.cartsRun);
        step(s, 1.0); // 3:00.5
        assertTrue(r.cartOn);
        assertTrue("warning phase", r.cartT < MinesRules.CART_WARN);
        float x0 = r.cartX;
        step(s, 1.0);
        assertEquals("the cart waits while the lane warns", x0, r.cartX, 0.001f);
        step(s, 1.0 + 1.0 / 60); // past 2.0 s: moving
        float a = r.cartX;
        step(s, 1.0);
        assertEquals("400 u/s", MinesRules.CART_SPEED, Math.abs(r.cartX - a), 3f);
        step(s, 12); // the pass is over (2800 u at 400 u/s)
        assertFalse(r.cartOn);
        step(s, 270.5 - s.time);
        assertEquals("the second cart at 4:30", 2, r.cartsRun);
        step(s, 90);
        assertEquals(3, r.cartsRun);
    }

    @Test
    public void theCartOnlyHitsTheLaneEnemiesTakeSixtyAndAreThrownAside() throws Exception {
        Sim s = run(2, 179);
        MinesRules r = rules(s);
        s.py = 200f; // the hero stands off the lane
        step(s, 0.1);
        float dirX = 0;
        Enemy inLane = post(s, "COLONIST", s.px + 600f, 12f);
        Enemy outside = post(s, "COLONIST", s.px + 600f, 120f);
        Enemy below = post(s, "COLONIST", s.px - 600f, -20f);
        step(s, 1.5);
        assertTrue(r.cartOn);
        step(s, 1.0); // warning ends
        float inHp = inLane.hp, outHp = outside.hp, belowHp = below.hp, y0 = inLane.y;
        step(s, 8);
        // whichever way the cart ran, it crossed both x = +600 and x = -600 (path +-1400)
        assertEquals("lane enemy: 60 damage", inHp - MinesRules.CART_ENEMY, inLane.hp, 0.01f);
        assertEquals("the one 120 u off the lane is untouched", outHp, outside.hp, 0.001f);
        assertEquals("a lane enemy on the other side takes 60 too", belowHp - MinesRules.CART_ENEMY, below.hp, 0.01f);
        assertTrue("thrown out of the strip, the way it was on: " + inLane.y, inLane.y > MinesRules.CART_HALF_W + 20f);
        assertTrue("thrown to the other side below the lane: " + below.y, below.y < -MinesRules.CART_HALF_W - 20f);
        assertEquals("no hit on the hero off the lane", 0, r.cartHeroHits);
        assertEquals(2, r.cartEnemyHits);
        assertEquals(0f, s.maxHp - s.hp, 0.001f);
    }

    @Test
    public void theHeroOnTheLaneTakesThirtyAndIsShovedSixtyUnitsADodgeProtects() throws Exception {
        for (int pass = 0; pass < 2; pass++) {
            boolean dodge = pass == 1;
            Sim s = run(3, 179);
            MinesRules r = rules(s);
            s.py = 10f;
            step(s, 0.1);
            step(s, 2.5);
            assertTrue(r.cartOn);
            float hp = s.maxHp;
            double deadline = s.time + 9;
            float yBefore = s.py;
            Input in = new Input();
            while (s.time < deadline && r.cartHeroHits == 0 && r.cartOn) {
                s.hp = s.maxHp;
                if (dodge) s.invuln = 1f;
                float py = s.py;
                s.update(DT, in);
                if (r.cartHeroHits > 0 || dodge) yBefore = py;
            }
            if (dodge) {
                assertEquals("a dodge protects", 0, r.cartHeroHits);
                assertEquals(s.maxHp, s.hp, 0.001f);
            } else {
                assertEquals(1, r.cartHeroHits);
                float expected = Math.max(1f, MinesRules.CART_HERO - (float) s.stats.armor) * (float) s.stats.damageTaken;
                assertEquals(expected, s.maxHp - s.hp, 0.05f); // + the hero's regeneration of one step
                assertEquals("shoved aside 60 u", MinesRules.CART_SHOVE * (float) s.stats.knockbackTaken, Math.abs(s.py - yBefore), 1.0f);
            }
        }
    }

    @Test
    public void addonsOffMeansNoCartNoPocketsNoLurkers() throws Exception {
        Sim s = run(4, 400);
        s.rules.addonsEnabled = false;
        step(s, 100);
        MinesRules r = rules(s);
        assertEquals(0, r.cartsRun);
        assertEquals(0, r.pocketN);
        assertEquals(0, r.lurkersSpawned);
    }

    // ------------------------------------------------------------------ firedamp

    private static float[] findPocket(MinesRules r) {
        float[] out = new float[2];
        for (int j = -6; j <= 6; j++) for (int i = -6; i <= 6; i++) if (r.pocketOf(i, j, out)) return new float[]{out[0], out[1]};
        return null;
    }

    @Test
    public void aboutThirtyPercentOfCellsHoldAPocketAndItIsAPureFunction() throws Exception {
        Sim s = run(5, 100);
        MinesRules r = rules(s);
        float[] o = new float[2], o2 = new float[2];
        int n = 0, total = 0;
        for (int j = -20; j < 20; j++) for (int i = -20; i < 20; i++) {
            total++;
            boolean a = r.pocketOf(i, j, o);
            assertEquals(a, r.pocketOf(i, j, o2));
            if (a) {
                n++;
                assertEquals(o[0], o2[0], 0f);
                assertTrue("inside its own cell", o[0] >= i * 900f && o[0] < (i + 1) * 900f && o[1] >= j * 900f && o[1] < (j + 1) * 900f);
            }
        }
        assertEquals(0.30, n / (double) total, 0.05);
    }

    static int indexAt(MinesRules r, float[] p) {
        for (int i = 0; i < r.pocketN; i++) if (Math.abs(r.pocketX[i] - p[0]) < 1f && Math.abs(r.pocketY[i] - p[1]) < 1f) return i;
        throw new AssertionError("pocket not listed");
    }

    /** Hero beside a real pocket, one enemy inside it; returns the pocket centre. */
    private static float[] pocketScene(Sim s, MinesRules r) {
        float[] p = findPocket(r);
        s.px = p[0] + 160f;
        s.py = p[1];
        step(s, 0.1);
        assertTrue("pocket listed", r.pocketN >= 1);
        return p;
    }

    @Test
    public void burnAndElectrifyLightAPocketKineticFrostAndDecayDoNot() throws Exception {
        for (int kind : new int[]{Entities.ST_BURN, Entities.ST_ELECTRIFY, Entities.ST_FROST, Entities.ST_DECAY, -1}) {
            Sim s = run(6, 100);
            MinesRules r = rules(s);
            float[] p = pocketScene(s, r);
            Enemy e = post(s, "COLONIST", p[0] + 20f, p[1]);
            if (kind == -1) s.hurt(e, 500f, 0f, 1f, 0f, 0f, 1f);
            else s.applyStatus(e, kind, 3, 20f, 0f);
            step(s, 2.6); // an Electrify tick comes after 2 s
            boolean lit = false;
            for (int i = 0; i < r.pocketN; i++) if (r.pocketFuse[i] >= 0f) lit = true;
            boolean should = kind == Entities.ST_BURN || kind == Entities.ST_ELECTRIFY;
            assertEquals("status " + kind, should, lit || r.detonations > 0);
        }
    }

    @Test
    public void anExplosionLightsAPocketAndABurningEnemyOutsideDoesNot() throws Exception {
        Sim s = run(7, 100);
        MinesRules r = rules(s);
        float[] p = pocketScene(s, r);
        Enemy far = post(s, "COLONIST", p[0] + 400f, p[1] + 400f); // outside the cloud
        s.applyStatus(far, Entities.ST_BURN, 3, 20f, 0f);
        step(s, 0.8);
        for (int i = 0; i < r.pocketN; i++) assertTrue("a burning enemy outside does nothing", r.pocketFuse[i] < 0f);
        s.destN = 0;
        s.addDest(p[0] - 150f, p[1], Sim.DK_BARREL, 777); // a barrel 150 u off: its blast (80) reaches the cloud edge (100)
        s.hitDestForTest(p[0] - 150f, p[1], 5f);
        boolean lit = false;
        for (int i = 0; i < r.pocketN; i++) if (r.pocketFuse[i] >= 0f) lit = true;
        assertTrue("the barrel blast lit it", lit);
    }

    @Test
    public void thePocketBlowsThreeSecondsLaterForFiftyToEveryoneInRangeAndRefillsAfterAMinute() throws Exception {
        Sim s = run(8, 100);
        MinesRules r = rules(s);
        float[] p = pocketScene(s, r);
        s.px = p[0] + 60f; // the hero inside the blast
        s.py = p[1];
        s.invuln = 0f;
        Enemy in = post(s, "COLONIST", p[0] - 80f, p[1]);
        Enemy edge = post(s, "COLONIST", p[0], p[1] - 110f);
        Enemy out = post(s, "COLONIST", p[0], p[1] + 300f);
        r.ignite(indexAt(r, p));
        double lit = s.time;
        float hIn = in.hp, hEdge = edge.hp, hOut = out.hp;
        step(s, 2.9);
        assertEquals(0, r.detonations);
        float hp = s.maxHp;
        s.invuln = 0f;
        Input inp = new Input();
        while (r.detonations == 0 && s.time < lit + 4) {
            s.hp = hp;
            s.update(DT, inp);
        }
        assertEquals(1, r.detonations);
        assertEquals("fuse 3 s", MinesRules.FUSE, s.time - lit, 0.1);
        assertEquals(hIn - MinesRules.BLAST_DMG, in.hp, 0.5f);
        assertEquals(hEdge - MinesRules.BLAST_DMG, edge.hp, 0.5f);
        assertEquals("outside 120 u: untouched", hOut, out.hp, 0.001f);
        float expected = Math.max(1f, MinesRules.BLAST_DMG - (float) s.stats.armor) * (float) s.stats.damageTaken;
        assertEquals("the hero takes it too", expected, hp - s.hp, 0.5f);
        // gone now, refilled after 60 s
        int before = r.pocketN;
        step(s, 30);
        assertEquals(before, r.pocketN);
        step(s, 31);
        assertEquals("the pocket is back", before + 1, r.pocketN);
    }

    @Test
    public void aBlastLightsTheNeighbouringPocketsAChain() throws Exception {
        Sim s = run(9, 100);
        MinesRules r = rules(s);
        float[] p = pocketScene(s, r);
        s.px = p[0] + 2000f; // out of everyone's way
        s.py = p[1];
        // two more pockets by hand: one 180 u away (inside the chain reach 120 + 100), one 600 u away (outside)
        int n = r.pocketN;
        r.pocketX[n] = p[0] + 180f; r.pocketY[n] = p[1]; r.pocketFuse[n] = -1f;
        r.pocketX[n + 1] = p[0] + 600f; r.pocketY[n + 1] = p[1]; r.pocketFuse[n + 1] = -1f;
        // keys are private: reuse refresh-safe state by keeping the hero in the same cell
        s.px = p[0] + 100f;
        s.py = p[1] + 200f;
        reflect(r, n + 2);
        r.ignite(indexAt(r, p));
        step(s, 3.2);
        assertEquals(1, r.detonations);
        boolean nearLit = false, farLit = false;
        for (int i = 0; i < r.pocketN; i++) {
            if (Math.abs(r.pocketX[i] - (p[0] + 180f)) < 1f && r.pocketFuse[i] >= 0f) nearLit = true;
            if (Math.abs(r.pocketX[i] - (p[0] + 600f)) < 1f && r.pocketFuse[i] >= 0f) farLit = true;
        }
        assertTrue("the next pocket caught", nearLit);
        assertFalse("the far one did not", farLit);
        step(s, 3.2);
        assertEquals("and it blew in turn", 2, r.detonations);
    }

    /** Sets MinesRules.pocketN through reflection (the arrays are public, the count is public too: this just documents the intent). */
    private static void reflect(MinesRules r, int n) {
        r.pocketN = n;
    }

    // ------------------------------------------------------------------ lurkers

    @Test
    public void lurkersStartAtMinuteSixAndNeverExceedTen() throws Exception {
        Sim s = run(10, 340);
        MinesRules r = rules(s);
        step(s, 19);
        assertEquals("none before 6:00", 0, r.lurkersSpawned);
        int peak = 0;
        for (int i = 0; i < 240; i++) {
            for (int k = 0; k < s.enemyN; k++) if (s.enemies[k].type.arch == EnemyDef.ARCH_LURKER) { s.enemies[k].stun = 1000f; s.enemies[k].hp = 1e6f; }
            step(s, 1);
            int alive = 0;
            for (int k = 0; k < s.enemyN; k++) if (s.enemies[k].type.arch == EnemyDef.ARCH_LURKER) alive++;
            peak = Math.max(peak, alive);
        }
        assertEquals("cap 10", 10, peak);
        // kill them: new ones come, still never more than ten
        for (int k = 0; k < s.enemyN; k++) if (s.enemies[k].type.arch == EnemyDef.ARCH_LURKER) s.killForTest(s.enemies[k]);
        step(s, 5);
        int alive = 0;
        for (int k = 0; k < s.enemyN; k++) if (!s.enemies[k].dead && s.enemies[k].type.arch == EnemyDef.ARCH_LURKER) alive++;
        assertTrue(alive >= 1 && alive <= 10);
    }

    @Test
    public void aLurkerIsOnlyDrawnInsideTheLightRadiusPlusFortyOrNearALaneLamp() throws Exception {
        Sim s = run(11, 100);
        MinesRules r = rules(s);
        step(s, 0.1);
        EnemyType t = s.type("DARK_LURKER");
        assertNotNull(t);
        float light = s.data.lightingOf("MINES")[1] * s.lightRadiusMul() * (float) s.stats.lightRadius;
        float edge = light + MinesRules.LURKER_SEE;
        s.px = 0f;
        s.py = 0f;
        // pick a spot away from lamps: far along y within the lane would be lit by lamps, so test far off the lane line x where no lamp is
        float[] lamp = new float[2];
        float x = 0f;
        // find an x whose neighbouring lamps are > 200 u away
        for (float cand = 3000f; cand < 6000f; cand += 25f) {
            boolean clear = true;
            int lx = Math.floorDiv((int) (cand / WorldGen.TILE), 14);
            for (int k = lx - 2; k <= lx + 2; k++) {
                MinesRules.lamp(k, lamp);
                if (Math.hypot(cand - lamp[0], 0 - lamp[1]) < 250 || Math.hypot(cand + edge - lamp[0], lamp[1]) < 250) clear = false;
            }
            if (clear) { x = cand; break; }
        }
        assertTrue(x > 0);
        s.px = x;
        s.py = 0f;
        assertFalse("inside the light: drawn", r.hidden(t, x + edge - 5f, 0f));
        assertTrue("outside: hidden", r.hidden(t, x + edge + 10f, 0f));
        assertTrue(r.hidden(t, x, edge + 10f));
        // a lamp lights its surroundings
        MinesRules.lamp(Math.floorDiv((int) (x / WorldGen.TILE), 14) + 3, lamp);
        s.px = lamp[0] - 2000f;
        s.py = lamp[1];
        assertFalse("in a lamp's glow", r.hidden(t, lamp[0] + 60f, lamp[1]));
        assertTrue("away from it", r.hidden(t, lamp[0] + MinesRules.LAMP_R + 150f, lamp[1] + 150f) || r.lit(lamp[0] + MinesRules.LAMP_R + 150f, lamp[1] + 150f));
        // other creatures are never hidden
        assertFalse(r.hidden(s.type("COLONIST"), x + edge + 500f, 0f));
        // a Headlamp widens the light
        s.px = x;
        s.py = 0f;
        s.stats.lightRadius = 1.5;
        assertFalse(r.hidden(t, x + edge + 10f, 0f));
        s.rules.addonsEnabled = false;
        assertFalse("add-ons off: drawn as any colonist", r.hidden(t, x + 3000f, 0f));
    }

    // ------------------------------------------------------------------ soak

    @Test
    public void thirtyMinutesWithTheAddonsStayClean() throws Exception {
        StageSoak.Result r = StageSoak.run("MINES", "BASTION", "immortal", 3, 1800);
        System.out.println(r);
        assertEquals(r.toString(), 0, r.nan);
        assertEquals(r.toString(), 0, r.overflow);
        assertEquals(r.toString(), 0, r.capBreak);
        assertTrue(r.toString(), r.peakEProj <= Sim.MAX_EPROJ && r.peakHaz <= Sim.MAX_HAZ);
    }
}
