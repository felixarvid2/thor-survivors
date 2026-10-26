package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.data.EquipDef;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.StageDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Entities.Hazard;
import se.felixarvid.thorsurvivors.game.Entities.Pickup;

import java.util.HashMap;

/**
 * v1.3 timed bosses (BossAi patterns 6..10): Brood Mother, Frozen Welder, Commandant Hale, Overseer Unit 7 and Governor
 * Kade. Every attack is telegraphed for at least 0.8 s, hurts only inside its ring or line, the live bullets stay under
 * 25, the timed schedule fires at the right second on both stages and a kill pays chest, Rare crate and XP pile.
 */
public class NewBossTest {
    private static final float DT = 1f / 60f;
    private static final String[] KEYS = {"BROOD_MOTHER", "FROZEN_WELDER", "COMMANDANT_HALE", "OVERSEER_UNIT_7", "GOVERNOR_KADE"};
    private static final int[] AT_FLATS = {450, 750, 1050, 1350, 1650}, AT_MINES = {450, 750, 1050, 1350, 1590};

    private static Sim sim(String stage, double time, long seed) throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get(stage), null, new HashMap<String, Double>(), null, seed);
        s.noSpawns = true;
        s.enemyN = 0;
        s.time = time;
        s.px = 0;
        s.py = 0;
        return s;
    }

    /** Runs {@code seconds} with the hero's health refilled before every frame; returns the damage the hero took in total. */
    private static float damageIn(Sim s, double seconds) {
        Input in = new Input();
        float total = 0f;
        for (int i = 0, n = (int) Math.round(seconds / DT); i < n; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
            total += s.maxHp - s.hp;
        }
        s.hp = s.maxHp;
        return total;
    }

    /** Seconds until the hero first takes damage (limit if never). */
    private static float firstDamage(Sim s, double limit) {
        Input in = new Input();
        for (int i = 0, n = (int) Math.round(limit / DT); i < n; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
            if (s.hp < s.maxHp) return (i + 1) * DT;
        }
        return (float) limit;
    }

    private static int count(Sim s, int kind, int variant) {
        int n = 0;
        for (int i = 0; i < s.hazN; i++) if (s.hazards[i].kind == kind && (variant < 0 || s.hazards[i].variant == variant)) n++;
        return n;
    }

    private static int countEnemies(Sim s, String key) {
        int n = 0;
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].type.def.key.equals(key) && !s.enemies[i].dead) n++;
        return n;
    }

    private static Enemy boss(Sim s, String key, float x, float y) {
        Enemy b = s.spawnAt(key, x, y, true);
        assertNotNull(key, b);
        return b;
    }

    // ------------------------------------------------------------------ the data and the schedule

    @Test
    public void theFiveBossesAreDataDrivenWithModelTintAndScale() throws Exception {
        GameData d = TestData.load();
        String[] model = {"spore_carrier", "welder", "sergeant", "security_drone", "vire"};
        double[] scale = {2.2, 2.0, 2.2, 3.0, 2.0};
        double[] hp = {220, 420, 400, 300, 650};
        int[] gear = {EquipDef.NECK, EquipDef.RING, EquipDef.HEAD, EquipDef.GLOVES, EquipDef.BODY};
        for (int i = 0; i < KEYS.length; i++) {
            EnemyDef e = d.enemies.get(KEYS[i]);
            assertNotNull(KEYS[i], e);
            assertEquals(EnemyDef.ARCH_BOSS, e.archetype);
            assertTrue(e.ashfallBoss);
            assertEquals(6 + i, Math.round(e.param("pattern", 0)));
            assertEquals(model[i], e.sprite);
            assertEquals(scale[i], e.scale, 1e-6);
            assertEquals(hp[i], e.maxHp, 1e-6);
            assertEquals(20f, e.param("hpRamp", 0), 0f);
            assertTrue("tinted", e.param("tintRGB", 0) > 0);
            assertEquals(gear[i], Math.round(e.param("gearSlot", -1)));
            assertEquals(1.5f, e.param("xpPileLevels", 0), 0f);
            assertFalse(e.name.isEmpty());
            EnemyType t = new EnemyType(e);
            assertEquals(6 + i, t.pattern);
            assertTrue(t.tint != 0);
        }
        for (String k : new String[]{"BROOD_EGG", "HALE_GUARD", "KADE_DECOY"}) {
            assertEquals(k, EnemyDef.ARCH_TENTACLE, d.enemies.get(k).archetype);
            assertFalse(d.enemies.get(k).ashfallBoss);
        }
        assertNotNull("portraits for the bottom screen: the manifest has icon.boss_<key>", null == null ? "" : null);
    }

    @Test
    public void thePortraitsAreInTheAtlasManifest() throws Exception {
        String m = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/assets/ashfall/manifest.json")),
                java.nio.charset.StandardCharsets.UTF_8);
        for (String k : KEYS) assertTrue("icon.boss_" + k, m.contains("\"icon.boss_" + k + "\""));
    }

    @Test
    public void theScheduleIsInTheDataForBothStages() throws Exception {
        GameData d = TestData.load();
        for (String stage : new String[]{"FLATS", "MINES"}) {
            StageDef sd = d.stages.get(stage);
            int[] want = stage.equals("FLATS") ? AT_FLATS : AT_MINES;
            assertEquals(5, sd.timedAt.length);
            for (int i = 0; i < 5; i++) {
                assertEquals(stage + " " + KEYS[i], want[i], sd.timedAt[i]);
                assertEquals(KEYS[i], sd.timedBoss[i]);
            }
        }
        // 7:30 / 12:30 / 17:30 / 22:30 on both, Kade at 27:30 on Cinder Flats and 26:30 on Deep Mines
        assertEquals(27 * 60 + 30, AT_FLATS[4]);
        assertEquals(26 * 60 + 30, AT_MINES[4]);
    }

    @Test
    public void theScheduleSpawnsEachBossAtItsSecondOnBothStagesWithABanner() throws Exception {
        GameData d = TestData.load();
        for (String stage : new String[]{"FLATS", "MINES"}) {
            int[] at = stage.equals("FLATS") ? AT_FLATS : AT_MINES;
            for (int i = 0; i < 5; i++) {
                Sim s = new Sim(d, d.stages.get(stage), null, new HashMap<String, Double>(), null, 40 + i);
                s.time = at[i] - 1.0;
                s.hp = s.maxHp;
                Input in = new Input();
                boolean seenBefore = false, seenAt = false;
                double spawnTime = -1;
                for (int f = 0; f < 60 * 3; f++) {
                    s.hp = s.maxHp;
                    s.update(DT, in);
                    boolean here = countEnemies(s, KEYS[i]) > 0;
                    if (here && s.time < at[i] - 0.05) seenBefore = true;
                    if (here && spawnTime < 0) spawnTime = s.time;
                }
                assertFalse(stage + " " + KEYS[i] + " not early", seenBefore);
                assertEquals(stage + " " + KEYS[i] + " exactly once", 1, countEnemies(s, KEYS[i]));
                assertTrue(stage + " " + KEYS[i] + " at " + spawnTime, spawnTime >= at[i] - 0.02 && spawnTime < at[i] + 0.1);
                assertEquals("the banner names the boss", s.type(KEYS[i]).nameUpper, s.bannerName);
                assertEquals(KEYS[i], s.bannerKey);
                assertEquals(i + 1, s.timedBossesDone());
            }
        }
    }

    @Test
    public void aClockJumpPastABossSkipsItInsteadOfSpawningItLate() throws Exception {
        Sim s = sim("FLATS", 0, 50);
        s.noSpawns = false;
        s.time = 1000;
        damageIn(s, 0.5);
        assertEquals("both Brood Mother and Frozen Welder are skipped", 2, s.timedBossesDone());
        assertEquals(0, countEnemies(s, "BROOD_MOTHER") + countEnemies(s, "FROZEN_WELDER"));
    }

    // ------------------------------------------------------------------ 6 Brood Mother

    @Test
    public void broodMotherLeavesASporeTrailLaysEggsThatHatchHoundsAndOneHitBreaksAnEgg() throws Exception {
        Sim s = sim("FLATS", 450, 61);
        s.px = 480;
        Enemy b = boss(s, "BROOD_MOTHER", 0, 0);
        b.actT = 1000f; // no eggs yet
        damageIn(s, 4);
        int clouds = count(s, Entities.HZ_CLOUD, -1);
        assertTrue("a cloud trail: " + clouds + " after walking " + b.x, clouds >= 2);
        for (int i = 0; i < s.hazN; i++) {
            Hazard h = s.hazards[i];
            assertEquals(50f, h.r, 0f);
            assertTrue("3 s clouds", h.maxLife <= 3.0001f);
            assertEquals("clouds spare her own brood", Entities.CV_HERO, h.variant);
        }
        // eggs: three, each with a 1.0 s ring before it can be hit
        s.hazN = 0;
        s.px = 250;
        b.actT = 0f;
        b.speed = 0f;
        s.update(DT, new Input());
        assertEquals("three pods", 3, countEnemies(s, "BROOD_EGG"));
        int hatched = 0;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (!e.type.def.key.equals("BROOD_EGG")) continue;
            assertTrue("dormant: it cannot be hit while the ring shows", e.dormant);
            assertEquals("1.0 s ring", 1.0f, e.teleMax, 1e-4f);
        }
        Enemy hit = null;
        damageIn(s, 1.1);
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].type.def.key.equals("BROOD_EGG")) hit = s.enemies[i];
        assertNotNull(hit);
        assertFalse(hit.dormant);
        s.hurt(hit, 3f, 0f, 1f, 1f, 0f, 0f);
        damageIn(s, 0.1);
        assertEquals("one hit breaks a pod", 2, countEnemies(s, "BROOD_EGG"));
        assertEquals("a broken pod hatches nothing", 0, countEnemies(s, "SPORE_HOUND"));
        b.actT = 1000f;
        damageIn(s, 5.5);
        assertEquals("the two others hatched, 4 hounds each", 8, countEnemies(s, "SPORE_HOUND"));
        assertEquals(0, countEnemies(s, "BROOD_EGG"));
    }

    @Test
    public void broodMotherBurstsAtHalfHealthInARingAroundTheHeroAndHurtsOnlyInsideARing() throws Exception {
        Sim s = sim("FLATS", 450, 62);
        s.px = 300;
        Enemy b = boss(s, "BROOD_MOTHER", 0, 0);
        b.speed = 0f;
        b.actT = 1000f;
        b.power = 0f;
        b.hp = b.maxHp * 0.49f;
        s.update(DT, new Input());
        assertEquals("six spore bombs", 6, count(s, Entities.HZ_BOMB, Entities.BV_SPORE));
        for (int i = 0; i < s.hazN; i++) {
            Hazard h = s.hazards[i];
            assertEquals(90f, h.r, 0f);
            assertEquals("1.0 s fuse", 1.0f, h.warnMax, 1e-4f);
            assertTrue(h.warn > 0.9f);
        }
        assertEquals("the hero in the middle stands outside every ring: nothing", 0f, damageIn(s, 1.3), 0.001f);
        assertTrue("and the clouds are left behind", count(s, Entities.HZ_CLOUD, -1) >= 6);
        // inside a ring when it goes off: 10 damage
        Sim t = sim("FLATS", 450, 63);
        t.px = 300;
        Enemy c = boss(t, "BROOD_MOTHER", 0, 0);
        c.speed = 0f;
        c.actT = 1000f;
        c.power = 0f;
        c.hp = c.maxHp * 0.49f;
        t.update(DT, new Input());
        Hazard ring = null;
        for (int i = 0; i < t.hazN; i++) if (t.hazards[i].variant == Entities.BV_SPORE) ring = t.hazards[i];
        t.px = ring.x;
        t.py = ring.y;
        float dmg = damageIn(t, 1.1);
        assertTrue("the ring hurt: " + dmg, dmg >= 9.9f && dmg < 40f);
    }

    // ------------------------------------------------------------------ 7 Frozen Welder

    @Test
    public void frozenWelderChainsThreeDashesEachWithItsOwnLineAndEndsInAFrostRing() throws Exception {
        Sim s = sim("FLATS", 750, 71);
        s.px = 300;
        Enemy b = boss(s, "FROZEN_WELDER", 0, 0);
        b.speed = 0f;
        b.power = 0f;
        b.actT = 0f;
        s.update(DT, new Input());
        assertEquals("three warning lines", 3, count(s, Entities.HZ_LANE, -1));
        assertEquals("three frost rings", 3, count(s, Entities.HZ_BOMB, Entities.BV_FROST));
        float[] angs = new float[3];
        int n = 0;
        for (int i = 0; i < s.hazN; i++) {
            Hazard h = s.hazards[i];
            if (h.kind == Entities.HZ_LANE) {
                assertEquals("a marker: it never hurts", 0f, h.dps, 0f);
                assertTrue("every line shows at least 0.8 s: " + h.warnMax, h.warnMax >= 0.8f);
                assertEquals(Entities.LV_MARK, h.variant);
                angs[n++] = h.ang;
            } else {
                assertEquals(140f, h.r, 0f);
                assertTrue("rings warn at least 1.0 s: " + h.warnMax, h.warnMax >= 1.0f);
            }
        }
        float lo = Math.min(angs[0], Math.min(angs[1], angs[2])), hi = Math.max(angs[0], Math.max(angs[1], angs[2]));
        assertTrue("different angles", Math.abs(angs[0] - angs[1]) > 0.5f && Math.abs(angs[1] - angs[2]) > 0.5f);
        assertEquals("pre-warning 0.9 s", 0.9f, b.teleMax, 1e-4f);
        // the dashes happen, leave frost and the boss rests afterwards
        damageIn(s, 0.9 + 3 * 0.45 + 2 * 0.25 + 0.2);
        assertTrue("frost on the floor: " + count(s, Entities.HZ_FROST, -1), count(s, Entities.HZ_FROST, -1) >= 3);
        assertEquals("then 2.0 s of rest", EnemyAi.A_REST, b.ai);
        assertEquals(2.0f, b.aiT, 0.35f);
    }

    @Test
    public void frozenWelderHurtsOnlyInsideALineAndTheRingStunsAfterItsFullWarning() throws Exception {
        // in the first line: nothing for the 0.9 s warning, then the dash hurts
        Sim s = sim("FLATS", 750, 72);
        s.px = 90;
        Enemy b = boss(s, "FROZEN_WELDER", 0, 0);
        b.speed = 0f;
        b.power = 0f;
        b.actT = 0f;
        s.update(DT, new Input());
        Hazard l0 = null;
        for (int i = 0; i < s.hazN; i++) if (s.hazards[i].kind == Entities.HZ_LANE && s.hazards[i].warnMax < 1f) l0 = s.hazards[i];
        assertNotNull(l0);
        s.px = (float) Math.cos(l0.ang) * 100f;
        s.py = (float) Math.sin(l0.ang) * 100f;
        float t = firstDamage(s, 2.5);
        assertTrue("no damage during the warning: " + t, t >= 0.9f);
        assertTrue("the dash hurt: " + t, t < 1.6f);
        // far from every line and ring: untouched through the whole chain
        Sim u = sim("FLATS", 750, 73);
        u.px = 300;
        Enemy c = boss(u, "FROZEN_WELDER", 0, 0);
        c.speed = 0f;
        c.power = 0f;
        c.actT = 0f;
        u.update(DT, new Input());
        u.px = 0;
        u.py = 900;
        assertEquals(0f, damageIn(u, 4.5), 0.001f);
        // the ring: 14 damage and a 0.4 s stun when it goes off
        Sim v = sim("FLATS", 750, 74);
        v.px = 300;
        Enemy d2 = boss(v, "FROZEN_WELDER", 0, 0);
        d2.speed = 0f;
        d2.power = 0f;
        d2.actT = 0f;
        v.update(DT, new Input());
        Hazard first = null;
        for (int i = 0; i < v.hazN; i++) if (v.hazards[i].kind == Entities.HZ_BOMB && (first == null || v.hazards[i].warn < first.warn)) first = v.hazards[i];
        v.px = first.x;
        v.py = first.y + 130f; // inside r 140 but off the lines of the dashes
        float fx = first.x, fy = first.y;
        float stun = 0f, dmg = 0f;
        for (int i = 0; i < 60 * 3; i++) {
            v.hp = v.maxHp;
            v.px = fx;
            v.py = fy + 130f;
            v.update(DT, new Input());
            dmg += v.maxHp - v.hp;
            stun = Math.max(stun, v.heroStunT);
            if (first.warn <= 0f) break;
        }
        assertTrue("the ring hurt: " + dmg, dmg >= 13.9f);
        assertEquals("0.4 s stun", 0.4f, stun, 0.05f);
        assertEquals("the ring leaves frost", true, count(v, Entities.HZ_FROST, -1) >= 1);
    }

    // ------------------------------------------------------------------ 8 Commandant Hale

    @Test
    public void haleCallsEightShellsOverThreeSecondsEachWarningAtLeastASecond() throws Exception {
        Sim s = sim("FLATS", 1050, 81);
        s.px = 300;
        Enemy b = boss(s, "COMMANDANT_HALE", 0, 0);
        b.speed = 0f;
        b.power = 0f;
        b.mt0 = 0f;
        b.mt1 = 1000f;
        s.update(DT, new Input());
        assertEquals("eight impact rings", 8, count(s, Entities.HZ_BOMB, Entities.BV_VOLCANO));
        float minWarn = 99f, maxWarn = 0f;
        for (int i = 0; i < s.hazN; i++) {
            Hazard h = s.hazards[i];
            minWarn = Math.min(minWarn, h.warnMax);
            maxWarn = Math.max(maxWarn, h.warnMax);
        }
        assertEquals(1.0f, minWarn, 1e-3f);
        assertTrue("spread over 3 s: " + maxWarn, maxWarn > 3.4f && maxWarn < 4.1f);
        assertEquals("nothing hurts during the first second", 0f, damageIn(s, 0.9), 0.001f);
        assertTrue("the first shell is on the hero", s.hazN == 8);
        // far away from every ring: untouched all through
        Sim t = sim("FLATS", 1050, 82);
        t.px = 300;
        Enemy c = boss(t, "COMMANDANT_HALE", 0, 0);
        c.speed = 0f;
        c.power = 0f;
        c.mt0 = 0f;
        c.mt1 = 1000f;
        t.update(DT, new Input());
        t.px = 0;
        t.py = 1100;
        assertEquals("outside every ring", 0f, damageIn(t, 4.5), 0.001f);
        // on the spot of the first shell: 14 damage
        Sim u = sim("FLATS", 1050, 83);
        u.px = 300;
        Enemy d2 = boss(u, "COMMANDANT_HALE", 0, 0);
        d2.speed = 0f;
        d2.power = 0f;
        d2.mt0 = 0f;
        d2.mt1 = 1000f;
        u.update(DT, new Input());
        float dmg = damageIn(u, 1.2);
        assertTrue("the first shell lands on the hero: " + dmg, dmg >= 13.9f);
    }

    @Test
    public void haleOrdersSixSergeantsIntoALineThatFiresOneSynchronisedBurstAndHeStandsStill() throws Exception {
        Sim s = sim("FLATS", 1050, 84);
        s.px = 0;
        s.py = 0;
        Enemy b = boss(s, "COMMANDANT_HALE", 400, 0);
        b.speed = 0f;
        b.power = 0f;
        b.mt0 = 1000f;
        b.mt1 = 0f;
        float x0 = b.x, y0 = b.y;
        s.update(DT, new Input());
        assertEquals("six guards", 6, countEnemies(s, "HALE_GUARD"));
        int lanes = 0;
        for (int i = 0; i < s.hazN; i++) {
            if (s.hazards[i].kind != Entities.HZ_LANE) continue;
            lanes++;
            assertTrue("every line of fire shows at least 0.8 s", s.hazards[i].warnMax >= 0.8f);
            assertEquals(0f, s.hazards[i].dps, 0f);
        }
        assertEquals(6, lanes);
        int peak = 0, jumpFrame = -1, before = 0;
        float hpLost = 0f;
        for (int i = 0; i < 60 * 3; i++) {
            s.hp = s.maxHp;
            int pre = s.eprojN;
            s.update(DT, new Input());
            if (s.eprojN - pre >= 12 && jumpFrame < 0) {
                jumpFrame = i;
                before = pre;
            }
            peak = Math.max(peak, s.eprojN);
            assertTrue("never more than 24 live bullets: " + s.eprojN, s.eprojN <= 24);
            if (i < 60 * 1.7) assertEquals("he stands while he orders", x0, b.x, 0.001f);
            assertEquals(y0, b.y, 0.001f);
        }
        assertTrue("the whole line fired in a single frame (frame " + jumpFrame + ")", jumpFrame > 60 * 1.7);
        assertEquals(0, before);
        assertTrue("six guards x three bolts: " + peak, peak >= 12 && peak <= 24);
        assertEquals("the guards are gone after the volley", 0, countEnemies(s, "HALE_GUARD"));
        // a guard killed before the volley does not fire
        Sim t = sim("FLATS", 1050, 85);
        Enemy c = boss(t, "COMMANDANT_HALE", 400, 0);
        c.speed = 0f;
        c.power = 0f;
        c.mt0 = 1000f;
        c.mt1 = 0f;
        t.update(DT, new Input());
        damageIn(t, 1.1);
        for (int i = 0; i < t.enemyN; i++) if (t.enemies[i].type.def.key.equals("HALE_GUARD")) t.killForTest(t.enemies[i]);
        int fired = 0;
        for (int i = 0; i < 60; i++) {
            t.hp = t.maxHp;
            t.update(DT, new Input());
            fired = Math.max(fired, t.eprojN);
        }
        assertEquals("a dead guard shoots nothing", 0, fired);
    }

    // ------------------------------------------------------------------ 9 Overseer Unit 7

    @Test
    public void overseerShowsTheSpiralFor09sThenKeepsUnder25LiveBulletsAlsoEnraged() throws Exception {
        for (boolean enraged : new boolean[]{false, true}) {
            Sim s = sim("FLATS", 1350, enraged ? 92 : 91);
            s.px = 0;
            Enemy b = boss(s, "OVERSEER_UNIT_7", 220, 0);
            b.speed = 0f;
            b.power = 0f;
            b.actT = 0f;
            b.cnt = 0;
            if (enraged) b.hp = b.maxHp * 0.39f;
            s.update(DT, new Input());
            assertEquals(EnemyAi.A_TELE, b.ai);
            assertTrue("the arms show for 0.9 s: " + b.teleMax, b.teleMax >= 0.9f - 1e-4f);
            int peak = 0;
            float firstSpeed = 0f;
            float hp = 0f;
            for (int i = 0; i < 60 * 7; i++) {
                s.hp = s.maxHp;
                s.update(DT, new Input());
                if (i < 60 * 0.8) assertEquals("no bullets during the warning", 0, s.eprojN);
                peak = Math.max(peak, s.eprojN);
                if (firstSpeed == 0f && s.eprojN > 0) firstSpeed = (float) Math.hypot(s.eprojs[0].vx, s.eprojs[0].vy);
                assertTrue("at most 24 live bullets: " + s.eprojN, s.eprojN <= 24);
            }
            assertTrue("a real spiral: " + peak, peak >= 15);
            assertEquals("spiral speed" + (enraged ? " x1.3" : ""), enraged ? 170f * 1.3f : 170f, firstSpeed, 0.5f);
            assertEquals(enraged, b.enraged);
        }
    }

    @Test
    public void overseerLaserHasAThinWarningAndHurtsOnlyInsideItsLaneAndDronesStepOutOfMarkerRings() throws Exception {
        Sim s = sim("FLATS", 1350, 93);
        Enemy b = boss(s, "OVERSEER_UNIT_7", 220, 0);
        b.speed = 0f;
        b.power = 0f;
        b.actT = 0f;
        b.cnt = 1;
        s.update(DT, new Input());
        assertEquals(1, count(s, Entities.HZ_LANE, Entities.LV_LASER));
        Hazard lane = s.hazards[0];
        assertEquals(46f, lane.w, 0f);
        assertEquals("thin warning 1.0 s", 1.0f, lane.warnMax, 1e-4f);
        // stand in the lane's reach but across from it: untouched; in it: hurt only after the warning
        float ux = (float) Math.cos(lane.ang), uy = (float) Math.sin(lane.ang);
        float spin = lane.spin;
        s.px = b.x + ux * 200f;
        s.py = b.y + uy * 200f;
        float t = firstDamage(s, 3.0);
        assertTrue("nothing during the warning: " + t, t >= 1.0f);
        // far away from the whole sweep: never
        Sim u = sim("FLATS", 1350, 94);
        Enemy c = boss(u, "OVERSEER_UNIT_7", 220, 0);
        c.speed = 0f;
        c.power = 0f;
        c.actT = 0f;
        c.cnt = 1;
        u.update(DT, new Input());
        u.px = 220f;
        u.py = 700f;
        assertEquals(0f, damageIn(u, 4), 0.001f);
        // the drones
        Sim v = sim("FLATS", 1350, 95);
        v.px = 220f;
        v.py = 560f;
        Enemy d = boss(v, "OVERSEER_UNIT_7", 220, 0);
        d.speed = 0f;
        d.power = 0f;
        d.actT = 0f;
        d.cnt = 2;
        v.update(DT, new Input());
        assertEquals("four marker rings", 4, count(v, Entities.HZ_BOMB, -1));
        for (int i = 0; i < v.hazN; i++) {
            assertEquals("a marker: no damage", 0f, v.hazards[i].dps, 0f);
            assertTrue(v.hazards[i].warnMax >= 0.8f);
        }
        assertEquals(0, countEnemies(v, "SECURITY_DRONE"));
        damageIn(v, 1.0);
        assertEquals("four drones where the rings were", 4, countEnemies(v, "SECURITY_DRONE"));
    }

    @Test
    public void overseerHoversAroundTwoHundredTwentyUnits() throws Exception {
        Sim s = sim("FLATS", 1350, 96);
        s.px = 0;
        Enemy b = boss(s, "OVERSEER_UNIT_7", 520, 0);
        b.actT = 1000f;
        damageIn(s, 12);
        assertEquals("closes in to its hover distance", 220f, (float) Math.hypot(b.x, b.y), 40f);
        s.px = b.x - 60f;
        s.py = b.y;
        damageIn(s, 6);
        assertTrue("backs off when approached: " + Math.hypot(s.px - b.x, s.py - b.y), Math.hypot(s.px - b.x, s.py - b.y) > 150f);
    }

    // ------------------------------------------------------------------ 10 Governor Kade

    @Test
    public void kadeAnnouncesHisBlinkFor08sAndLeavesThreeDecoysThatExplodeWhenKilledInsideTheirRing() throws Exception {
        Sim s = sim("FLATS", 1650, 101);
        s.px = 0;
        s.py = 0;
        Enemy b = boss(s, "GOVERNOR_KADE", 300, 0);
        b.speed = 0f;
        b.power = 0f;
        b.mt0 = 0f;
        b.mt1 = 1000f;
        s.update(DT, new Input());
        assertEquals(EnemyAi.A_TELE, b.ai);
        assertTrue("0.8 s of warning: " + b.teleMax, b.teleMax >= 0.8f - 1e-4f);
        float bx = b.x;
        damageIn(s, 0.7);
        assertEquals("still where he stood", bx, b.x, 0.001f);
        damageIn(s, 0.3);
        assertEquals("three decoys", 3, countEnemies(s, "KADE_DECOY"));
        assertTrue("he blinked: " + b.x, Math.abs(b.x - bx) > 5f);
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (!e.type.def.key.equals("KADE_DECOY")) continue;
            assertTrue("a ring first", e.dormant || e.tele > 0f);
            assertTrue("one hit kills", e.maxHp <= 2f);
            assertEquals("the ring of its blast", 60f, e.type.def.param("hitR", 0), 0f);
        }
        // kill one with the hero beside it: 12 damage; kill one far from the hero: nothing
        damageIn(s, 1.0);
        Enemy near = null, far = null;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (!e.type.def.key.equals("KADE_DECOY")) continue;
            assertFalse(e.dormant);
            if (near == null) near = e;
            else far = e;
        }
        s.px = far.x + 400f;
        s.py = far.y;
        s.killForTest(far);
        assertEquals("far from the blast", 0f, damageIn(s, 0.3), 0.001f);
        s.px = near.x + 20f;
        s.py = near.y;
        s.killForTest(near);
        assertEquals("inside the ring: 12", 12f, damageIn(s, 0.3), 0.01f);
        // a blink replaces the old decoys silently
        b.mt0 = 0f;
        damageIn(s, 2.0);
        assertEquals("never more than three decoys", 3, countEnemies(s, "KADE_DECOY"));
    }

    @Test
    public void kadesDecreeLightsFourLanesTwoSafeAndOnlyTheUnsafeOnesHurt() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            Sim s = sim("FLATS", 1650, 110 + variant);
            Enemy b = boss(s, "GOVERNOR_KADE", 400, 0);
            b.speed = 0f;
            b.power = 0f;
            b.mt0 = 1000f;
            b.mt1 = 0f;
            s.update(DT, new Input());
            assertEquals(BossAi.BA_DECREE, b.ai);
            assertTrue("1.2 s of warning: " + b.teleMax, b.teleMax >= 1.2f - 1e-4f);
            assertEquals("two lanes are safe", 2, Integer.bitCount(b.cnt));
            int unsafe = -1, safe = -1;
            for (int k = 0; k < 4; k++) {
                if ((b.cnt >> k & 1) == 0 && unsafe < 0) unsafe = k;
                if ((b.cnt >> k & 1) != 0 && safe < 0) safe = k;
            }
            float px = b.ax, py = b.ay; // lane direction; the across axis is (-ay, ax)
            int k = variant == 0 ? unsafe : variant == 1 ? safe : -1;
            float off = k >= 0 ? BossAi.decreeOffset(b, k) : 0f;
            float gap = b.type.def.param("laneGap", 120f), w = b.type.def.param("laneW", 64f);
            if (k < 0) { // between two lanes: halfway across the 56 u gap would still touch, so use the middle of a pair 120 apart minus the width
                off = BossAi.decreeOffset(b, 0) - gap * 0.5f - 0f; // outside the first lane, clear of it (its half width is 32)
                off = BossAi.decreeOffset(b, 0) - w * 0.5f - 30f;
            }
            float cx = b.t2 + -b.ay * off, cy = b.t3 + b.ax * off;
            s.px = cx;
            s.py = cy;
            float tHit = -1f;
            float dmg = 0f;
            for (int i = 0; i < 60 * 3 && b.ai == BossAi.BA_DECREE; i++) {
                s.hp = s.maxHp;
                s.px = cx;
                s.py = cy;
                s.update(DT, new Input());
                if (s.hp < s.maxHp) {
                    dmg += s.maxHp - s.hp;
                    if (tHit < 0) tHit = (i + 1) * DT;
                }
            }
            if (variant == 0) {
                assertEquals("an unsafe lane deals 24 when the warning ends", 24f, dmg, 0.01f);
                assertTrue("after 1.2 s: " + tHit, tHit >= 1.19f);
            } else {
                assertEquals(variant == 1 ? "a safe lane: nothing" : "between the lanes: nothing", 0f, dmg, 0.001f);
            }
        }
    }

    @Test
    public void kadeBringsFourSporeCarriersAtHalfHealth() throws Exception {
        Sim s = sim("FLATS", 1650, 120);
        Enemy b = boss(s, "GOVERNOR_KADE", 400, 0);
        b.speed = 0f;
        b.power = 0f;
        b.mt0 = 1000f;
        b.mt1 = 1000f;
        damageIn(s, 0.2);
        assertEquals(0, countEnemies(s, "SPORE_CARRIER"));
        b.hp = b.maxHp * 0.49f;
        damageIn(s, 0.2);
        assertEquals(4, countEnemies(s, "SPORE_CARRIER"));
        damageIn(s, 0.5);
        assertEquals("only once", 4, countEnemies(s, "SPORE_CARRIER"));
    }

    // ------------------------------------------------------------------ rewards and the pools

    @Test
    public void everyBossPaysAChestOfOneToThreeItemsARareCrateOfItsSlotAndAnXpPileOfOnePointFiveLevels() throws Exception {
        int[] slot = {EquipDef.NECK, EquipDef.RING, EquipDef.HEAD, EquipDef.GLOVES, EquipDef.BODY};
        java.util.Set<Integer> sizes = new java.util.HashSet<>();
        for (int i = 0; i < KEYS.length; i++) {
            for (long seed = 1; seed <= 4; seed++) {
                GameData d = TestData.load();
                Sim s = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>(), null, seed * 13 + i);
                s.noSpawns = true;
                s.enemyN = 0;
                s.time = 900;
                s.pickN = 0;
                Enemy b = boss(s, KEYS[i], 600, 0);
                s.px = 0;
                s.killForTest(b);
                damageIn(s, 0.1);
                int chests = 0, crates = 0;
                float chestValue = 0f, gems = 0f;
                for (int k = 0; k < s.pickN; k++) {
                    Pickup p = s.picks[k];
                    if (p.kind == Entities.K_CHEST) {
                        chests++;
                        chestValue = p.value;
                    } else if (p.kind == Entities.K_EQUIP) {
                        crates++;
                        int code = Math.round(p.value);
                        EquipDef e = d.equipList.get(code >> 2);
                        assertEquals(KEYS[i] + " gear slot", slot[i], e.slot);
                        assertFalse("a base item", e.unique);
                        assertEquals("Rare", Equipment.RARE, code & 3);
                    } else if (p.kind == Entities.K_GEM) {
                        gems += p.value;
                    }
                }
                assertEquals(1, chests);
                assertEquals(KEYS[i] + " drops one gear crate", 1, crates);
                assertTrue("1..3 items: " + chestValue, chestValue >= 1f && chestValue <= 3f);
                sizes.add(Math.round(chestValue));
                float want = s.xpNeed * 1.5f;
                assertTrue(KEYS[i] + " XP pile " + gems + " vs " + want, gems >= want * 0.99f && gems <= want * 1.0f + b.xp * 2f + 1f);
                // opening: as many item lines as the chest says (a weapon at level 1 can always be raised)
                s.inv.add(d.weapons.get("MAGIC_MISSILE"));
                for (int k = 0; k < s.pickN; k++) {
                    if (s.picks[k].kind != Entities.K_CHEST) continue;
                    s.px = s.picks[k].x;
                    s.py = s.picks[k].y;
                }
                damageIn(s, 0.3);
                assertEquals("the chest was picked up", 1, s.pendingChests);
                String[] lines = s.openChest();
                assertEquals(KEYS[i] + " chest lines " + java.util.Arrays.toString(lines), (int) chestValue + 1, lines.length);
            }
        }
        assertTrue("sizes vary over runs: " + sizes, sizes.size() >= 2);
    }

    @Test
    public void aNormalChestStillFollowsTheStageTable() throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>(), null, 5);
        s.noSpawns = true;
        s.enemyN = 0;
        s.time = 300;
        Enemy b = boss(s, "CARGO_LOADER", 600, 0);
        s.killForTest(b);
        damageIn(s, 0.1);
        for (int k = 0; k < s.pickN; k++) if (s.picks[k].kind == Entities.K_CHEST) assertEquals("no item count: the stage table rolls", 0f, s.picks[k].value, 0f);
    }

    @Test
    public void allFiveBossesTogetherRespectTheBulletAndHazardPools() throws Exception {
        for (String stage : new String[]{"FLATS", "MINES"}) {
            Sim s = sim(stage, 1400, 130);
            s.px = 0;
            s.py = 0;
            Enemy[] bs = new Enemy[5];
            for (int i = 0; i < 5; i++) {
                bs[i] = boss(s, KEYS[i], (float) Math.cos(i * 1.2566) * 260f, (float) Math.sin(i * 1.2566) * 260f);
                bs[i].power = 0f;
            }
            int peakHaz = 0, peakBul = 0;
            for (int i = 0; i < 60 * 60; i++) {
                s.hp = s.maxHp;
                s.update(DT, new Input());
                for (int k = 0; k < 5; k++) if (bs[k].hp < bs[k].maxHp * 0.45f) bs[k].hp = bs[k].maxHp * 0.45f;
                peakHaz = Math.max(peakHaz, s.hazN);
                peakBul = Math.max(peakBul, s.eprojN);
                assertTrue(s.eprojN <= Sim.MAX_EPROJ);
                assertTrue(s.hazN <= Sim.MAX_HAZ);
                assertTrue(s.enemyN <= Sim.MAX_ENEMIES);
            }
            assertTrue(stage + " the bosses attacked: haz " + peakHaz + " bullets " + peakBul, peakHaz > 6);
        }
    }

    @Test
    public void everyTelegraphOfEveryNewBossIsAtLeastEightTenthsOfASecond() throws Exception {
        // the shape of the whole fight: record the smallest warning of every hazard any of the bosses puts on the floor
        float worst = 99f;
        for (int i = 0; i < KEYS.length; i++) {
            Sim s = sim("FLATS", 1400, 140 + i);
            s.px = 0;
            Enemy b = boss(s, KEYS[i], 250, 0);
            b.power = 0f;
            for (int f = 0; f < 60 * 40; f++) {
                s.hp = s.maxHp;
                s.update(DT, new Input());
                if (b.hp > b.maxHp * 0.45f && f == 60 * 20) b.hp = b.maxHp * 0.45f; // reach the half-health attacks too
                for (int k = 0; k < s.hazN; k++) {
                    Hazard h = s.hazards[k];
                    if (h.warnMax > 0f && h.kind != Entities.HZ_ASH) worst = Math.min(worst, h.warnMax);
                    assertTrue(KEYS[i] + " hazard kind " + h.kind + " warn " + h.warnMax, h.warnMax <= 0f || h.warnMax >= 0.8f - 1e-4f || h.kind == Entities.HZ_ASH);
                }
                // the Overseer's own drones shoot too (normal enemy bolts); its spiral alone is held to 24 above
                assertTrue(s.eprojN <= (i == 3 ? Sim.MAX_EPROJ : 24));
            }
        }
        assertTrue("the smallest warning is " + worst, worst >= 0.8f - 1e-4f);
    }
}
