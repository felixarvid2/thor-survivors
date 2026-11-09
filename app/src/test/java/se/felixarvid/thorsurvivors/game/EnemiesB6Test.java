package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.HashMap;

/**
 * v1.5 batch 6, enemies and bosses: the elite modifiers Shielded / Splitter / Volatile, ranged lead-shot, the charger
 * telegraph, the pack flank, the boss phase shield and the boss enrage.
 */
public class EnemiesB6Test {
    private static final float DT = 1f / 60f;

    private static Sim sim(double time, long seed) throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get("FLATS"), null, new HashMap<String, Double>(), null, seed); // no hero weapons
        s.noSpawns = true;
        s.enemyN = 0;
        s.time = time;
        s.px = 0;
        s.py = 0;
        return s;
    }

    private static void step(Sim s, double seconds) {
        Input in = new Input();
        for (int i = 0, n = (int) Math.round(seconds / DT); i < n; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
        }
    }

    // ------------------------------------------------------------------ elites

    @Test
    public void shieldedOnlyFromMinuteEightAndAllNineModifiersAreInThePool() {
        java.util.Random rng = new java.util.Random(3);
        int early = 0, late = 0;
        for (int i = 0; i < 500; i++) {
            early |= Elites.roll(rng, 3, 7 * 60 + 59);
            late |= Elites.roll(rng, 3, 8 * 60);
            assertEquals(3, Elites.bits(Elites.roll(rng, 3, 8 * 60)));
        }
        assertEquals("no Shielded before 8:00", 0, early & Elites.M_SHIELDED);
        assertEquals("the other eight are rolled", 511 & ~Elites.M_SHIELDED, early);
        assertEquals("all nine from 8:00", 511, late);
        assertEquals(9, Elites.NAMES.length);
        assertEquals(9, Elites.COLORS.length);
        assertEquals("Shielded + Volatile", "Shielded + Volatile", Elites.label(Elites.M_SHIELDED | Elites.M_VOLATILE));
        assertEquals(8, Elites.glyphOf(6));
        assertEquals(5, Elites.glyphOf(5));
    }

    @Test
    public void theShieldArcIs120DegreesAndBlocksSeventyPercentUntilItDrops() throws Exception {
        // the pure geometry: facing +x, hits from the front half-arc land on the shield, the side and back do not
        assertTrue(Elites.inArc(0f, 0, 0, 100, 0));
        assertTrue("59 degrees", Elites.inArc(0f, 0, 0, (float) Math.cos(Math.toRadians(59)) * 100, (float) Math.sin(Math.toRadians(59)) * 100));
        assertFalse("61 degrees", Elites.inArc(0f, 0, 0, (float) Math.cos(Math.toRadians(61)) * 100, (float) Math.sin(Math.toRadians(61)) * 100));
        assertFalse("behind", Elites.inArc(0f, 0, 0, -100, 0));
        assertFalse("beside", Elites.inArc(0f, 0, 0, 0, 100));

        Sim s = sim(9 * 60, 5);
        Enemy e = s.spawnEliteForTest("COLONIST", Elites.M_SHIELDED);
        e.x = 200;
        e.y = 0;
        e.hp = e.maxHp = 1e6f;
        e.faceA = (float) Math.PI; // faces the hero at the origin
        e.eshT = Elites.SHIELD_UP;
        float before = e.hp;
        s.hurt(e, 100f, 0f, 1f, 0f, 0f, 0f);
        assertEquals("front: 70% blocked", 30f, before - e.hp, 0.01f);
        e.faceA = 0f; // facing away: the hero is behind it
        before = e.hp;
        s.hurt(e, 100f, 0f, 1f, 0f, 0f, 0f);
        assertEquals("flanked: full damage", 100f, before - e.hp, 0.01f);
        e.faceA = (float) Math.PI;
        e.eshT = -0.5f; // the shield is down
        before = e.hp;
        s.hurt(e, 100f, 0f, 1f, 0f, 0f, 0f);
        assertEquals("down: full damage", 100f, before - e.hp, 0.01f);
    }

    @Test
    public void theShieldIsUpFourSecondsThenDownThenUpAgainAndTheArcTurnsAtOneRadianPerSecond() throws Exception {
        Sim s = sim(9 * 60, 6);
        Enemy e = s.spawnEliteForTest("COLONIST", Elites.M_SHIELDED);
        e.x = 300;
        e.y = 0;
        e.speed = 0f;
        e.hp = e.maxHp = 1e6f;
        s.px = 0;
        s.py = 0;
        e.faceA = 0f; // turned away by a full half turn
        Elites el = new Elites(s);
        el.update(e, 1f, 300f);
        assertEquals("one second of turning is at most one radian", 1f, e.faceA, 0.01f);
        e.eshT = Elites.SHIELD_UP;
        float up = 0, t = 0;
        boolean seenDown = false, seenUpAgain = false;
        for (int i = 0; i < 60 * 9; i++, t += DT) {
            el.update(e, DT, 300f);
            if (e.eshT > 0 && !seenDown) up += DT;
            if (e.eshT <= 0) seenDown = true;
            else if (seenDown) seenUpAgain = true;
        }
        assertEquals("up 4 s", 4f, up, 0.1f);
        assertTrue(seenDown && seenUpAgain);
    }

    @Test
    public void aSplitterLeavesThreeCopiesAtAQuarterOfItsHpAndNeverOverfillsThePool() throws Exception {
        Sim s = sim(5 * 60, 7);
        Enemy e = s.spawnEliteForTest("COLONIST", Elites.M_SPLITTER);
        s.enemyN = 1; // drop the escort: only the elite
        s.enemies[0] = e;
        float hp = e.maxHp;
        e.x = 400;
        e.y = 0;
        s.killNow(e);
        step(s, 0.1);
        int copies = 0;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy c = s.enemies[i];
            if (c.elite || c.dead) continue;
            copies++;
            assertEquals("a quarter of the elite's HP", hp * 0.25f, c.maxHp, 0.01f);
            assertFalse(c.elite);
            assertEquals(0, c.mods);
        }
        assertEquals(3, copies);

        // the pool: with room for one only one is made, never past MAX_ENEMIES
        Sim t = sim(5 * 60, 8);
        Enemy e2 = t.spawnEliteForTest("COLONIST", Elites.M_SPLITTER);
        t.enemyN = 1;
        t.enemies[0] = e2;
        int fill = Sim.MAX_ENEMIES - 12 - 1 - 1; // room for exactly one copy
        for (int i = 0; i < fill; i++) {
            assertTrue(t.spawnMinion("COLONIST", 900, 900));
            t.enemies[t.enemyN - 1].speed = 0f;
        }
        t.killNow(e2);
        step(t, 0.05);
        assertTrue("never past the cap: " + t.enemyN, t.enemyN <= Sim.MAX_ENEMIES - 12);
        t.enemyN = 0;
    }

    @Test
    public void aVolatileBlastsForTwentyPercentOfMaxHpAfterAWarnAndAlsoWhenTouched() throws Exception {
        Sim s = sim(5 * 60, 9);
        Enemy e = s.spawnEliteForTest("COLONIST", Elites.M_VOLATILE);
        s.enemyN = 1;
        s.enemies[0] = e;
        e.x = 30;
        e.y = 0;
        e.speed = 0f;
        e.hp = 1f;
        s.hurt(e, 50f, 0f, 1f, 0f, 0f, 0f); // killed by the hero: the blast goes off at its place after the warn
        s.invuln = 0f;
        float maxHp = s.maxHp;
        s.hp = maxHp;
        s.update(DT, new Input());
        Entities.Hazard h = null;
        for (int i = 0; i < s.hazN; i++) if (s.hazards[i].kind == Entities.HZ_BOMB) h = s.hazards[i];
        assertNotNull("a warn ring", h);
        assertEquals(60f, h.r, 0.01f);
        assertEquals(0.8f, h.warnMax, 0.01f);
        float lost0 = maxHp - s.hp;
        for (int i = 0; i < 60; i++) {
            Input in = new Input();
            s.update(DT, in);
        }
        assertEquals("hit for 20% of the max HP (armor is added back)", maxHp * 0.2f, maxHp - s.hp, maxHp * 0.01f + 0.5f);
        assertEquals(0f, lost0, 0.001f);

        // touching it sets it off with no kill: it leaves silently and the ring is planted
        Sim t = sim(5 * 60, 10);
        Enemy v = t.spawnEliteForTest("COLONIST", Elites.M_VOLATILE);
        t.enemyN = 1;
        t.enemies[0] = v;
        v.x = 5;
        v.y = 0;
        v.speed = 0f;
        int kills = t.kills;
        step(t, 0.05);
        assertEquals("no kill credit for the touch", kills, t.kills);
        assertEquals("it is gone", 0, t.enemyN);
        assertTrue("and left its ring", t.hazN > 0);
    }

    // ------------------------------------------------------------------ ranged, charger, flank

    @Test
    public void rangedEnemiesLeadAMovingHeroEveryThirdVolleyCappedAt120AndHitAStandingOneDeadOn() throws Exception {
        Sim s = sim(60, 11);
        Enemy r = s.spawnAt("SERGEANT", 0, -200, false);
        assertNotNull(r);
        r.y = -200 + 8f; // the muzzle is 8 up: the shot starts at (0, -200)
        r.x = 0;
        s.ai.lead(100f, 0f);
        assertEquals(35f, s.ai.leadX, 0.001f);
        s.ai.lead(1000f, 0f);
        assertEquals("capped", 120f, s.ai.leadX, 0.001f);
        s.ai.lead(600f, 800f);
        assertEquals(120f, Math.sqrt(s.ai.leadX * s.ai.leadX + s.ai.leadY * s.ai.leadY), 0.01f);

        int burst = Math.round(r.type.def.param("burst", 1f));
        s.eprojN = 0;
        s.pvx = 0f;
        s.pvy = 0f;
        r.shots = burst; // the opening bolt of a volley ...
        r.cnt = (EnemyAi.LEAD_EVERY - r.id % EnemyAi.LEAD_EVERY) % EnemyAi.LEAD_EVERY; // ... of a led one
        s.ai.fireAtHero(r, 240f, 5f, 200f);
        Entities.EProj still = s.eprojs[0];
        assertEquals("standing still: straight at the hero", 0f, still.vx, 0.01f);
        s.eprojN = 0;
        s.pvx = 200f; // moving right at 200 u/s: aim 70 units ahead
        r.shots = burst;
        r.cnt = (EnemyAi.LEAD_EVERY - r.id % EnemyAi.LEAD_EVERY) % EnemyAi.LEAD_EVERY;
        s.ai.fireAtHero(r, 240f, 5f, 200f);
        Entities.EProj led = s.eprojs[0];
        assertTrue("aimed to the right of the hero: " + led.vx, led.vx > 50f);
        float ang = (float) Math.atan2(led.vx, led.vy);
        assertEquals(Math.atan2(70.0, 200.0), ang, 0.02);
        assertEquals("speed unchanged", 240f, Math.sqrt(led.vx * led.vx + led.vy * led.vy), 0.5f);
        // only every third volley is led, and never the follow-up bolts of a burst
        int ledCount = 0;
        for (int v = 0; v < 9; v++) {
            s.eprojN = 0;
            r.shots = burst;
            s.ai.fireAtHero(r, 240f, 5f, 200f);
            if (s.eprojs[0].vx > 20f) ledCount++;
        }
        assertEquals("every third volley", 3, ledCount);
        s.eprojN = 0;
        r.shots = burst - 1;
        r.cnt = (EnemyAi.LEAD_EVERY - r.id % EnemyAi.LEAD_EVERY) % EnemyAi.LEAD_EVERY;
        s.ai.fireAtHero(r, 240f, 5f, 200f);
        if (burst > 1) assertEquals("a follow-up bolt goes straight", 0f, s.eprojs[0].vx, 0.01f);
    }

    @Test
    public void aChargerLocksItsDirectionForAtLeastHalfASecondAndTheLineIsAsWideAsTheHit() throws Exception {
        Sim s = sim(60, 12);
        Enemy c = s.spawnAt("WELDER", 140, 0, false);
        assertNotNull(c);
        s.invuln = 1e6f;
        c.actT = 0f;
        c.ai = EnemyAi.A_WALK;
        float ax = 0, ay = 0;
        boolean locked = false;
        for (int i = 0; i < 200 && !locked; i++) {
            s.update(DT, new Input());
            if (c.ai == EnemyAi.A_TELE) locked = true;
        }
        assertTrue("it started a telegraph", locked);
        ax = c.ax;
        ay = c.ay;
        assertTrue("windup at least 0.5 s: " + c.teleMax, c.teleMax >= 0.5f);
        assertEquals("the line is the hit width", 2f * (c.radius * 0.8f + Sim.PLAYER_R * 0.8f), c.teleW, 0.001f);
        // the hero sidesteps during the windup: the line does not follow
        s.py = 90f;
        while (c.ai == EnemyAi.A_TELE) {
            s.px = 0f;
            s.py += 1f;
            s.update(DT, new Input());
            if (c.ai == EnemyAi.A_TELE) {
                assertEquals(ax, c.ax, 1e-6f);
                assertEquals(ay, c.ay, 1e-6f);
            }
        }
        assertEquals("then it dashes along the locked line", EnemyAi.A_DASH, c.ai);
        float x0 = c.x, y0 = c.y;
        step(s, 0.2);
        float mx = c.x - x0, my = c.y - y0, l = (float) Math.sqrt(mx * mx + my * my);
        assertEquals(ax, mx / l, 0.01f);
        assertEquals(ay, my / l, 0.01f);
    }

    @Test
    public void aBigPackFansOutAcrossMinusFortyToPlusFortyDegreesDeterministicallyByEntityId() throws Exception {
        assertEquals(5, EnemyAi.FLANK_COS.length);
        assertEquals(Math.cos(Math.toRadians(40)), EnemyAi.FLANK_COS[0], 1e-5);
        assertEquals(Math.sin(Math.toRadians(-40)), EnemyAi.FLANK_SIN[0], 1e-5);
        assertEquals(Math.sin(Math.toRadians(40)), EnemyAi.FLANK_SIN[4], 1e-5);
        assertEquals(0f, EnemyAi.flankBlend(100f), 0f);
        assertEquals(1f, EnemyAi.flankBlend(250f), 0f);
        String[] pos = new String[2];
        for (int run = 0; run < 2; run++) {
            Sim s = sim(3 * 60, 13);
            s.invuln = 1e6f;
            for (int i = 0; i < 10; i++) s.spawnAt("COLONIST", 600f * (float) Math.cos(i * 0.6283), 600f * (float) Math.sin(i * 0.6283), false);
            step(s, 0.5);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.enemyN; i++) b.append(s.enemies[i].id).append(':').append(s.enemies[i].x).append(',').append(s.enemies[i].y).append(';');
            pos[run] = b.toString();
        }
        assertEquals("the same seed gives the same paths", pos[0], pos[1]);
        // 10 chasers on a ring far from the hero: each walks at its id-slot offset from the direct line; 6 walk straight
        for (int n : new int[]{10, 6}) {
            Sim s = sim(3 * 60, 14);
            s.invuln = 1e6f;
            float[] sx = new float[n], sy = new float[n];
            for (int i = 0; i < n; i++) {
                s.spawnAt("COLONIST", 600f * (float) Math.cos(i * 6.2831853 / n), 600f * (float) Math.sin(i * 6.2831853 / n), false);
                sx[i] = s.enemies[i].x;
                sy[i] = s.enemies[i].y;
            }
            step(s, 0.1); // the first step only counts the chasers; the spread starts the step after
            for (int i = 0; i < n; i++) { sx[i] = s.enemies[i].x; sy[i] = s.enemies[i].y; }
            step(s, 0.4);
            int spread = 0;
            for (int i = 0; i < n; i++) {
                Enemy e = s.enemies[i];
                double toHero = Math.atan2(-sy[i], -sx[i]), moved = Math.atan2(e.y - sy[i], e.x - sx[i]);
                double off = Math.toDegrees(moved - toHero);
                while (off > 180) off -= 360;
                while (off < -180) off += 360;
                int slot = e.id % 5;
                double want = n > 6 ? (slot - 2) * 20.0 : 0.0;
                assertEquals("n=" + n + " id " + e.id + " offset", want, off, 3.0);
                if (Math.abs(off) > 15) spread++;
            }
            if (n == 10) assertTrue("some walk at +-20 and +-40", spread >= 4);
        }
    }

    // ------------------------------------------------------------------ bosses

    @Test
    public void aPhaseChangeGivesOneSecondOfShieldAndClearsOldAddsButNotTheNewOnes() throws Exception {
        Sim s = sim(20 * 60, 15);
        s.invuln = 1e6f;
        s.px = 700;
        Enemy v = s.spawnAt("VIRE_AWAKENED", 0, 0, true);
        for (int i = 0; i < 5; i++) {
            Enemy a = s.spawnAt("COLONIST", 100 + i * 10, 20, false);
            a.speed = 0f;
        }
        Enemy far = s.spawnAt("COLONIST", 380, 0, false); // outside the 320 pulse radius
        far.speed = 0f;
        assertEquals(0f, BossAi.phaseShield(v), 0f);
        assertEquals(0, v.phase);
        v.hp = v.maxHp * 0.65f;
        s.update(DT, new Input());
        assertEquals("66% reached", 1, v.phase);
        assertEquals(BossAi.PHASE_SHIELD, BossAi.phaseShield(v), 0.05f);
        float hp = v.hp;
        s.hurt(v, 1000f, 0f, 1f, 0f, 0f, 0f);
        assertEquals("no damage through the shield", hp, v.hp, 0f);
        s.statusDamage(v, 500f, Status.C_BURN, false);
        assertEquals("nor from statuses", hp, v.hp, 0f);
        step(s, 0.1);
        int colonistsNear = 0, carriers = 0;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy a = s.enemies[i];
            if (a.dead) continue;
            if (a.type.def.key.equals("COLONIST") && Math.abs(a.x - 100) < 100 && a.x < 160 && a.y < 100) colonistsNear++;
            if (a.type.def.key.equals("SPORE_CARRIER")) carriers++;
        }
        assertEquals("the five old adds are cleared", 0, colonistsNear);
        assertEquals("the new carriers of the phase are not", 4, carriers);
        assertEquals("a far add is left alone", 1, countKey(s, "COLONIST", 340));
        assertEquals("the pulse gave no kills", 0, s.kills);
        step(s, 1.0);
        assertEquals(0f, BossAi.phaseShield(v), 0f);
        hp = v.hp;
        s.hurt(v, 1000f, 0f, 1f, 0f, 0f, 0f);
        assertTrue("damage again after the shield", v.hp < hp);
    }

    private static int countKey(Sim s, String key, float minX) {
        int n = 0;
        for (int i = 0; i < s.enemyN; i++) if (!s.enemies[i].dead && s.enemies[i].type.def.key.equals(key) && s.enemies[i].x > minX) n++;
        return n;
    }

    @Test
    public void everyBossPatternShieldsAtItsPhaseThresholds() throws Exception {
        // the pattern ids whose phase changes at a threshold: 3 Vire, 5 The First (66 / 33), 4 Foreman (25%), 10 Kade
        String[] keys = {"VIRE_AWAKENED", "THE_FIRST", "SMELTER_FOREMAN"};
        float[] hpAt = {0.65f, 0.65f, 0.2f};
        for (int k = 0; k < keys.length; k++) {
            Sim s = sim(20 * 60, 20 + k);
            s.invuln = 1e6f;
            s.px = 700;
            Enemy b = s.spawnAt(keys[k], 0, 0, true);
            assertEquals(keys[k], 0, b.phase);
            assertEquals(0f, BossAi.phaseShield(b), 0f);
            b.hp = b.maxHp * hpAt[k];
            s.update(DT, new Input());
            assertTrue(keys[k] + " changed phase", b.phase >= 1);
            assertTrue(keys[k] + " shielded", BossAi.phaseShield(b) > 0.9f);
        }
    }

    @Test
    public void aBossAliveNinetySecondsEnragesWithMoreSpeedAndDamageAndTheFlagIsPublic() throws Exception {
        Sim s = sim(20 * 60, 30);
        s.invuln = 1e6f;
        s.px = 700;
        Enemy b = s.spawnAt("CARGO_LOADER", 0, 0, true);
        float speed = b.speed, power = b.power, shot = b.shotMul;
        assertFalse(BossAi.enraged(b));
        assertEquals(90f, BossAi.enrageIn(b), 0.01f);
        b.bossAge = 89.99f;
        assertEquals(0.01f, BossAi.enrageIn(b), 0.01f);
        s.update(DT, new Input());
        assertTrue(BossAi.enraged(b));
        assertEquals(0f, BossAi.enrageIn(b), 0f);
        assertEquals(speed * 1.25f, b.speed, 0.001f);
        assertEquals(power * 1.15f, b.power, 0.001f);
        assertEquals(shot * 1.15f, b.shotMul, 0.001f);
        step(s, 3.0);
        assertEquals("only once", speed * 1.25f, b.speed, 0.001f);
        assertFalse(BossAi.enraged(null));
        assertEquals(0f, BossAi.phaseShield(null), 0f);
    }

    @Test
    public void anEnragedBossSpawnsHazardsAndBoltsThatHitFifteenPercentHarder() throws Exception {
        Sim a = sim(20 * 60, 31), b = sim(20 * 60, 31);
        float[] dmg = new float[2];
        Sim[] both = {a, b};
        for (int k = 0; k < 2; k++) {
            Sim s = both[k];
            s.invuln = 1e6f;
            s.px = 300;
            Enemy boss = s.spawnAt("SLAGBORN", 0, 0, true);
            if (k == 1) boss.bossAge = 95f;
            float most = 0f;
            for (int i = 0; i < 60 * 12; i++) {
                s.update(DT, new Input());
                for (int h = 0; h < s.hazN; h++) most = Math.max(most, s.hazards[h].kind == Entities.HZ_BOMB ? s.hazards[h].dps : 0f);
            }
            dmg[k] = most;
        }
        assertTrue("slag bombs were thrown: " + dmg[0], dmg[0] > 0f);
        assertEquals(dmg[0] * 1.15f, dmg[1], 0.05f);
    }
}
