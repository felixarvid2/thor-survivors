package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.WeaponDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/** v1.5 batch 7: offer-quality guard (64), early passive weight (62), stand-still penalty (66), spawn-ahead (67), curse (68), late HP ramp (70). */
public class BalanceB7Test {
    @After
    public void reset() {
        CurseMod.set(0);
        CurseMod.setTierHpMul(1);
        OfferGenerator.earlyPassiveMul = 1.0;
    }

    // ------------------------------------------------------------------ 64
    @Test
    public void tenThousandOffersAreNeverDeadLevels() throws Exception {
        GameData d = TestData.load();
        Random rng = new Random(64);
        int checked = 0;
        for (int draw = 0; draw < 10000; draw++) {
            Inventory inv = new Inventory();
            // a random inventory: a few weapons, several passives (many at level 1..4 so passive level-ups are plentiful)
            List<WeaponDef> all = new ArrayList<>(d.weapons.values());
            int tries = 0;
            while (inv.weaponCount < 1 + rng.nextInt(4) && tries++ < 50) {
                WeaponDef w = all.get(rng.nextInt(all.size()));
                if (!w.powerUp && !w.evolution && !w.fusion) inv.add(w);
            }
            tries = 0;
            int passives = rng.nextInt(Inventory.MAX_PASSIVES + 1);
            while (inv.passiveCount < passives && tries++ < 100) {
                WeaponDef w = all.get(rng.nextInt(all.size()));
                if (w.powerUp) inv.add(w);
            }
            for (int i = 0; i < inv.passiveCount; i++) for (int l = rng.nextInt(4); l > 0; l--) inv.levelUp(inv.passives[i].def.key);
            for (int i = 0; i < inv.weaponCount; i++) for (int l = rng.nextInt(9); l > 0; l--) inv.levelUp(inv.weapons[i].def.key);
            List<Offer> pool = OfferGenerator.candidates(inv, d.weapons.values());
            boolean freshExists = false;
            for (Offer o : pool) if (OfferGenerator.isFresh(o)) freshExists = true;
            List<Offer> out = OfferGenerator.generate(inv, d.weapons.values(), rng.nextDouble() * 0.75, rng);
            assertTrue(out.size() >= 1);
            if (pool.size() >= out.size() && freshExists) {
                boolean fresh = false;
                for (Offer o : out) if (OfferGenerator.isFresh(o)) fresh = true;
                assertTrue("draw " + draw + " has no fresh card", fresh);
            }
            for (Offer o : out) {
                String st = OfferGenerator.statOf(o);
                if (st.isEmpty()) continue;
                int n = 0;
                for (Offer q : out) if (st.equals(OfferGenerator.statOf(q))) n++;
                assertTrue("draw " + draw + ": " + n + " passives of " + st, n < 3);
            }
            checked++;
        }
        assertEquals(10000, checked);
    }

    @Test
    public void guardReplacesAnAllPassiveLevelUpOffer() throws Exception {
        GameData d = TestData.load();
        Inventory inv = new Inventory();
        inv.add(d.weapons.get("WHIP"));
        for (WeaponDef w : d.weapons.values()) if (w.powerUp && inv.passiveCount < 4) inv.add(w);
        List<Offer> pool = OfferGenerator.candidates(inv, d.weapons.values());
        List<Offer> out = new ArrayList<>();
        List<Offer> rest = new ArrayList<>(pool);
        for (Offer o : pool) if (o.kind == Offer.PASSIVE_LEVEL && out.size() < 3) {
            out.add(o);
            rest.remove(o);
        }
        assertEquals(3, out.size());
        double[] w = new double[rest.size()];
        java.util.Arrays.fill(w, 1.0);
        OfferGenerator.guard(out, rest, w, rest.size(), false, new Random(1));
        boolean fresh = false;
        for (Offer o : out) if (OfferGenerator.isFresh(o)) fresh = true;
        assertTrue(fresh);
    }

    // ------------------------------------------------------------------ 62
    @Test
    public void passivesWeighLessUntilAWeaponReachesLevelFive() throws Exception {
        GameData d = TestData.load();
        OfferGenerator.earlyPassiveMul = 0.6;
        Inventory inv = new Inventory();
        inv.add(d.weapons.get("WHIP"));
        WeaponDef pass = null;
        for (WeaponDef w : d.weapons.values()) if (w.powerUp && OfferGenerator.dominantMul(w.key) == 1.0) {
            pass = w;
            break;
        }
        Offer o = new Offer(Offer.NEW_PASSIVE, pass, 1);
        double early = OfferGenerator.weightOf(inv, d.weapons.values(), o);
        for (int i = 0; i < 4; i++) inv.levelUp("WHIP");
        double later = OfferGenerator.weightOf(inv, d.weapons.values(), o);
        assertEquals(pass.rarity, later, 1e-9);
        assertEquals(pass.rarity * OfferGenerator.earlyPassiveMul, early, 1e-9);
        assertTrue(early < later);
    }

    @Test
    public void dominantPassivesWeighLessButAreNeverZero() throws Exception {
        GameData d = TestData.load();
        Inventory inv = new Inventory();
        inv.add(d.weapons.get("WHIP"));
        for (int i = 0; i < 4; i++) inv.levelUp("WHIP");
        for (String k : new String[]{"COOLDOWN", "POWER", "AMOUNT"}) {
            WeaponDef def = d.weapons.get(k);
            double w = OfferGenerator.weightOf(inv, d.weapons.values(), new Offer(Offer.NEW_PASSIVE, def, 1));
            assertTrue(k, w > 0 && w < def.rarity);
        }
        assertEquals(1.0, OfferGenerator.dominantMul("GROWTH"), 0);
    }

    // ------------------------------------------------------------------ 66
    @Test
    public void standStillTrackerStartsAtSixSecondsAndResetsOnMovement() {
        WaveDirector.StandStill st = new WaveDirector.StandStill();
        st.update(0, 0, 0f);
        for (int i = 0; i < 5 * 60; i++) st.update(10, 10, 1f / 60f); // 5 s inside 40 units
        assertFalse(st.active());
        assertEquals(1f, st.spawnMul(), 0f);
        for (int i = 0; i < 61; i++) st.update(20, -10, 1f / 60f);
        assertTrue(st.active());
        assertEquals(1.15f, st.spawnMul(), 1e-6f);
        st.update(100, 0, 1f / 60f); // beyond 40 units: reset
        assertFalse(st.active());
        assertEquals(0f, st.seconds(), 1e-6f);
    }

    @Test
    public void standStillDpsIsSmallAndGrowsWithTime() {
        assertEquals(1.5f, WaveDirector.stillDps(0), 1e-5f);
        assertEquals(6.0f, WaveDirector.stillDps(1800), 1e-4f);
    }

    @Test
    public void aHeroThatCampsLosesFasterThanOneThatKeepsMoving() throws Exception {
        GameData d = TestData.load();
        float[] lostStill = new float[1], lostMove = new float[1];
        for (int pass = 0; pass < 2; pass++) {
            Sim s = new Sim(d, d.stages.get("FLATS"), null, new HashMap<>(), "BASTION", 5);
            s.noSpawns = false;
            Input in = new Input();
            for (int i = 0; i < 40 * 60; i++) {
                s.hp = s.maxHp;
                in.stickX = pass == 0 ? 0f : (float) Math.cos(i * 0.02);
                in.stickY = pass == 0 ? 0f : (float) Math.sin(i * 0.02) + 0.6f;
                s.update(1f / 60f, in);
                if (s.ended != 0) break;
            }
            if (pass == 0) lostStill[0] = s.stillDamage;
            else lostMove[0] = s.stillDamage;
        }
        assertTrue("still drift " + lostStill[0], lostStill[0] > 30f); // 34 s of drift at 1.5+ HP/s
        assertEquals(0f, lostMove[0], 1e-6f);
    }

    @Test
    public void stillBotDiesEarlierWithThePenaltyThanWithout() throws Exception {
        GameData d = TestData.load();
        double on = 0, off = 0;
        int n = 3;
        for (int seed = 1; seed <= n; seed++) {
            for (int pass = 0; pass < 2; pass++) {
                Sim s = new Sim(d, d.stages.get("MINES"), "WHIP", new HashMap<>(), "BASTION", seed);
                s.standStillPenalty = pass == 0;
                Input in = new Input();
                while (s.ended == 0 && s.time < 900) s.update(1f / 60f, in);
                if (pass == 0) on += s.time;
                else off += s.time;
            }
        }
        assertTrue("penalty " + on / n + " s vs none " + off / n + " s", on < off);
    }

    // ------------------------------------------------------------------ 67
    @Test
    public void spawnAheadBiasKeepsAnglesInTheCone() {
        float heading = 1.0f;
        int ahead = 0;
        Random r = new Random(9);
        for (int i = 0; i < 10000; i++) {
            float a = WaveDirector.spawnAngle(0.2f, heading, r.nextFloat(), r.nextFloat(), r.nextFloat());
            if (Math.abs(a - heading) <= WaveDirector.AHEAD_SPREAD + 1e-5f) ahead++;
        }
        // 20 % forced into the cone plus the share of the uniform 80 % that lands there by chance
        double expect = 0.2 + 0.8 * (2 * WaveDirector.AHEAD_SPREAD) / (2 * Math.PI);
        assertEquals(expect, ahead / 10000.0, 0.02);
        assertEquals(2.5f, WaveDirector.spawnAngle(0f, heading, 0f, 0f, 2.5f / 6.2831855f), 1e-4f);
    }

    /** Share of newly spawned normal enemies that appear in front (x beyond the hero) of a hero walking right. */
    private static double aheadShare(float bias) throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get("FLATS"), null, new HashMap<>(), "BASTION", 11);
        s.spawnAheadBias = bias;
        Input in = new Input();
        in.stickX = 1f;
        int lastId = -1, n = 0, ahead = 0;
        for (int i = 0; i < 40 * 60; i++) {
            s.hp = s.maxHp;
            s.update(1f / 60f, in);
            int maxId = lastId;
            for (int k = 0; k < s.enemyN; k++) {
                Entities.Enemy e = s.enemies[k];
                if (e.id <= lastId) continue;
                maxId = Math.max(maxId, e.id);
                if (e.boss || e.elite) continue;
                n++;
                if (e.x > s.px) ahead++;
            }
            lastId = maxId;
        }
        assertTrue("spawns seen " + n, n >= 40);
        return ahead / (double) n;
    }

    @Test
    public void simPlacesSpawnsAheadOfTheHeadingWhenTheStageAsksForIt() throws Exception {
        double with = aheadShare(1f), without = aheadShare(0f);
        assertTrue("bias 1: " + with, with >= 0.8); // not 1.0: pack members, summons and horde rings are placed by other code
        assertTrue("no bias: " + without, without < 0.75);
    }

    @Test
    public void stageDataCarriesTheSpawnAheadShareAndTheArmsCrateTimeIsEleven() throws Exception {
        GameData d = TestData.load();
        assertEquals(0.2f, d.stages.get("FLATS").spawnAhead, 1e-6f);
        assertEquals(0.2f, d.stages.get("HYDRO").spawnAhead, 1e-6f);
        assertEquals(0.2f, d.stages.get("REFINERY").spawnAhead, 1e-6f);
        assertEquals(0.2f, d.stages.get("WRECK").spawnAhead, 1e-6f);
        assertEquals("Deep Mines: it cost 17 points, no bias", 0f, d.stages.get("MINES").spawnAhead, 0f);
        assertEquals(660.0, WaveDirector.armsCrateFrom, 0.0);
    }

    // ------------------------------------------------------------------ 68
    @Test
    public void curseFactorsAndTheHpCap() {
        assertEquals(1.0, CurseMod.factor(0), 1e-9);
        assertEquals(1.05, CurseMod.factor(0.1), 1e-9);
        assertEquals(1.10, CurseMod.factor(0.2), 1e-9);
        assertEquals(1.15, CurseMod.factor(0.3), 1e-9);
        assertEquals(1.3, CurseMod.goldMul(0.3), 1e-9);
        assertEquals(1.15, CurseMod.hpMul(0.3, 1.0, 0.0), 1e-9);
        // curse x tier x shop curse multiply, and stop at 3.0
        assertEquals(1.15 * 2.0, CurseMod.hpMul(0.3, 2.0, 0.0), 1e-9);
        assertEquals(3.0, CurseMod.hpMul(0.3, 2.0, 0.5), 1e-9);
        assertEquals(3.0, CurseMod.hpMul(0.3, 9.0, 0.5), 1e-9);
        CurseMod.set(0.55);
        assertEquals(0.3, CurseMod.get(), 1e-9);
        CurseMod.set(-1);
        assertEquals(0.0, CurseMod.get(), 1e-9);
        assertEquals(0.1, CurseMod.next(0.0), 1e-9);
        assertEquals(0.0, CurseMod.next(0.3), 1e-9);
    }

    @Test
    public void aCursedRunHasTougherFasterEnemiesAndMoreGold() throws Exception {
        GameData d = TestData.load();
        Sim plain = new Sim(d, d.stages.get("FLATS"), null, new HashMap<>(), "BASTION", 3);
        CurseMod.set(0.3);
        Sim cursed = new Sim(d, d.stages.get("FLATS"), null, new HashMap<>(), "BASTION", 3);
        assertEquals(0.3, cursed.runCurse, 1e-9);
        Input in = new Input();
        for (int i = 0; i < 600; i++) {
            plain.update(1f / 60f, in);
            cursed.update(1f / 60f, in);
        }
        assertTrue(cursed.enemyN > 0 && plain.enemyN > 0);
        double hpP = 0, hpC = 0, spP = 0, spC = 0;
        for (int i = 0; i < plain.enemyN; i++) if (!plain.enemies[i].boss && plain.enemies[i].type == plain.type("COLONIST")) { hpP = plain.enemies[i].maxHp; spP = plain.enemies[i].speed; break; }
        for (int i = 0; i < cursed.enemyN; i++) if (!cursed.enemies[i].boss && cursed.enemies[i].type == cursed.type("COLONIST")) { hpC = cursed.enemies[i].maxHp; spC = cursed.enemies[i].speed; break; }
        assertTrue(hpP > 0 && hpC > 0);
        assertEquals(1.15, hpC / hpP, 0.02);
        assertEquals(1.15, spC / spP, 0.02);
        assertEquals(1.3, cursed.curseGoldMul, 1e-9);
    }

    // ------------------------------------------------------------------ 70
    @Test
    public void lateHpRampStartsAtMinuteTwentyFive() {
        assertEquals(1.0, WaveDirector.lateHpMul(0), 1e-12);
        assertEquals(1.0, WaveDirector.lateHpMul(25 * 60), 1e-12);
        assertEquals(1.03, WaveDirector.lateHpMul(26 * 60), 1e-9);
        assertEquals(1.15, WaveDirector.lateHpMul(30 * 60), 1e-9);
    }
}
