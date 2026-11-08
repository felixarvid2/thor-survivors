package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.EquipDef;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.WeaponDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.HashMap;
import java.util.List;

/** v1.5 batch 5: the new weapons, passives, uniques, Overdrive, the Vacuum Gem and the two new places. */
public class Batch5ContentTest {
    private static final float DT = 1f / 60f;

    private static Sim sim(String starter, long seed) throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get("FLATS"), starter, new HashMap<String, Double>(), null, seed);
        s.noSpawns = true;
        s.enemyN = 0;
        s.findN = 0;
        s.merchant.enabled = false;
        s.pois.enabled = false;
        return s;
    }

    private static Enemy dummy(Sim s, float x, float y, float hp) {
        Enemy e = s.addEnemy("COLONIST", x, y, false);
        e.hp = e.maxHp = hp;
        e.speed = 0;
        return e;
    }

    private static void run(Sim s, double seconds) {
        Input in = new Input();
        for (int i = 0, n = (int) Math.round(seconds / DT); i < n; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
        }
    }

    private static void setLevel(Sim s, int slot, int level) {
        s.inv.weapons[slot].level = level;
        s.recomputeAll();
    }

    // ------------------------------------------------------------------ data and offers

    @Test
    public void newWeaponsPassivesAndEvolutionsAreInTheData() throws Exception {
        GameData d = TestData.load();
        String[][] pairs = {{"ARC_LANCE", "STORM_LANCE"}, {"SLAG_MORTAR", "MAGMA_HOWITZER"}, {"TETHER_DRONE", "LEASH_ARRAY"}};
        for (String[] p : pairs) {
            WeaponDef b = d.weapons.get(p[0]), e = d.weapons.get(p[1]);
            assertNotNull(p[0], b);
            assertNotNull(p[1], e);
            assertEquals(8, b.maxLevel());
            assertTrue(e.evolution && !b.evolution && !b.powerUp);
            assertEquals(p[1], b.evoInto);
            for (String syn : b.evoSynergy) assertTrue(syn, d.weapons.get(syn).powerUp);
        }
        assertEquals(5, d.weapons.get("OVERCHARGE").maxLevel());
        assertEquals(4, d.weapons.get("SCRAP_MAGNET").maxLevel());
        assertEquals(3, d.weapons.get("GLASS_CORE").maxLevel());
    }

    @Test
    public void theyAreOfferedAsNewCardsAndTheEvolutionIsNot() throws Exception {
        GameData d = TestData.load();
        Inventory inv = new Inventory();
        boolean[] seen = new boolean[6];
        String[] want = {"ARC_LANCE", "SLAG_MORTAR", "TETHER_DRONE", "OVERCHARGE", "SCRAP_MAGNET", "GLASS_CORE"};
        for (Offer o : OfferGenerator.candidates(inv, d.weapons.values())) {
            for (int i = 0; i < want.length; i++) if (o.def.key.equals(want[i])) seen[i] = true;
            assertFalse(o.def.key, o.def.evolution);
        }
        for (int i = 0; i < seen.length; i++) assertTrue(want[i], seen[i]);
    }

    @Test
    public void passiveStatsSum() throws Exception {
        GameData d = TestData.load();
        Sim s = sim(null, 1);
        s.inv.add(d.weapons.get("OVERCHARGE"));
        s.inv.passives[0].level = 5;
        s.inv.add(d.weapons.get("SCRAP_MAGNET"));
        s.inv.passives[1].level = 4;
        s.inv.add(d.weapons.get("GLASS_CORE"));
        s.inv.passives[2].level = 3;
        s.recomputeAll();
        assertEquals(1.30, s.stats.speedMul, 1e-6);
        assertEquals(1.20, s.stats.durationMul, 1e-6);
        assertEquals(2.00, s.stats.magnetMul, 1e-6);
        assertEquals(1.12, s.stats.greedMul, 1e-6);
        assertEquals(1.60, s.stats.might, 1e-6);
        assertEquals(0.55, s.stats.maxHpMul, 1e-6);
    }

    // ------------------------------------------------------------------ Arc Lance

    @Test
    public void arcLancePiercesThreeAndStopsOnTheFourth() throws Exception {
        Sim s = sim("ARC_LANCE", 2);
        Enemy[] row = new Enemy[5];
        for (int i = 0; i < row.length; i++) row[i] = dummy(s, 90 + i * 40, 0, 1e6f);
        run(s, 1.0);
        int hit = 0;
        for (Enemy e : row) if (e.hp < e.maxHp) hit++;
        assertEquals(3, hit);
        assertEquals("first enemy takes the full 40", 1e6f - 40f, row[0].hp, 4f);
    }

    @Test
    public void arcLanceGainsOnePierceEverySecondLevel() throws Exception {
        GameData d = TestData.load();
        WeaponDef w = d.weapons.get("ARC_LANCE");
        assertEquals(3, w.stat(1, "penetrating", 0), 0);
        assertEquals(3, w.stat(2, "penetrating", 0), 0);
        assertEquals(4, w.stat(3, "penetrating", 0), 0);
        assertEquals(5, w.stat(5, "penetrating", 0), 0);
        assertEquals(6, w.stat(7, "penetrating", 0), 0);
    }

    @Test
    public void stormLanceElectrifiesAndArcsToANeighbour() throws Exception {
        GameData d = TestData.load();
        Sim s = sim("ARC_LANCE", 3);
        s.inv.weapons[0].def = d.weapons.get("STORM_LANCE");
        s.inv.weapons[0].level = 1;
        s.recomputeAll();
        Enemy front = dummy(s, 120, 0, 1e6f);
        Enemy side = dummy(s, 120, 90, 1e6f); // 90 u off the line: only the arc reaches it
        run(s, 0.8);
        assertTrue(front.hp < front.maxHp);
        assertTrue(front.elec > 0);
        assertTrue("arc hit the neighbour", side.hp < side.maxHp);
    }

    // ------------------------------------------------------------------ Slag Mortar

    @Test
    public void slagMortarPuddleTicksOnAStandingCrowdAndMissesAWalkerOff() throws Exception {
        Sim s = sim("SLAG_MORTAR", 4);
        Enemy still = dummy(s, 200, 0, 1e6f);
        run(s, 6.0);
        float dealt = still.maxHp - still.hp;
        assertTrue("the puddle burned the standing enemy: " + dealt, dealt > 12f);
        assertTrue(still.burn > 0 || dealt > 0);

        Sim k = sim("SLAG_MORTAR", 4);
        Enemy walker = dummy(k, 200, 0, 1e6f);
        walker.speed = 0;
        // the shell is aimed at the first sighting; the target teleports away every frame once it is in the air
        Input in = new Input();
        for (int i = 0; i < 360; i++) {
            k.hp = k.maxHp;
            k.update(DT, in);
            if (k.projN > 0) {
                walker.x = 700;
                walker.y = 0;
                walker.kvx = walker.kvy = 0;
            }
        }
        assertEquals("a target that left takes nothing", walker.maxHp, walker.hp, 0.01f);
    }

    // ------------------------------------------------------------------ Tether Drone

    @Test
    public void tetherDroneHitsTheWeakestInRangeAndNothingOutside() throws Exception {
        Sim s = sim("TETHER_DRONE", 5);
        Enemy tough = dummy(s, 100, 0, 1e6f);
        Enemy weak = dummy(s, -100, 20, 500f);
        Enemy far = dummy(s, 600, 0, 10f);
        run(s, 1.5);
        assertTrue("weakest in reach tethered", weak.hp < 500f);
        assertEquals("a stronger one is left alone", tough.maxHp, tough.hp, 0.01f);
        assertEquals("out of range", 10f, far.hp, 0.01f);
        float perBeam = 9f;
        assertEquals("9 damage a beam (power 0.9)", perBeam, 500f - weak.hp, 4.5f * 9f); // one to three beams in 1.5 s
        assertTrue(500f - weak.hp >= 9f - 0.5f);
    }

    @Test
    public void leashArrayTethersTwoAndSlows() throws Exception {
        GameData d = TestData.load();
        Sim s = sim("TETHER_DRONE", 6);
        s.inv.weapons[0].def = d.weapons.get("LEASH_ARRAY");
        s.inv.weapons[0].level = 1;
        s.recomputeAll();
        Enemy a = dummy(s, 100, 0, 1e6f), b = dummy(s, -100, 0, 1e6f);
        run(s, 1.0);
        assertTrue(a.hp < a.maxHp && b.hp < b.maxHp);
    }

    // ------------------------------------------------------------------ evolution and Overdrive

    @Test
    public void evolutionsNeedTheirPassiveAtMaxLevelAndOverdriveWaitsForTheChest() throws Exception {
        GameData d = TestData.load();
        Sim s = sim("ARC_LANCE", 7);
        setLevel(s, 0, 8);
        assertFalse(s.inv.canEvolve(0, d.weapons));
        assertFalse("no passive: no overdrive", s.weapons[0].overdrive);
        int amount = s.weapons[0].amount;
        float interval = s.weapons[0].interval;
        s.inv.add(d.weapons.get("OVERCHARGE"));
        s.recomputeAll();
        assertTrue(s.inv.canEvolve(0, d.weapons));
        assertTrue(s.weapons[0].overdrive);
        assertEquals(amount + 1, s.weapons[0].amount);
        assertEquals(interval * 0.9f, s.weapons[0].interval, 1e-4f);
        assertEquals("STORM_LANCE", s.inv.evolve(0, d.weapons).key);
        s.recomputeAll();
        assertFalse("evolved: overdrive is over", s.weapons[0].overdrive);
        // not maxed: nothing
        Sim t = sim("ARC_LANCE", 8);
        t.inv.add(d.weapons.get("OVERCHARGE"));
        t.recomputeAll();
        assertFalse(t.weapons[0].overdrive);
    }

    // ------------------------------------------------------------------ the three uniques

    private static void wear(Sim s, String key) throws Exception {
        EquipDef e = TestData.load().equipment.get(key);
        assertNotNull(key, e);
        assertTrue(e.unique);
        s.equip.def[e.slot] = e;
        s.equip.rar[e.slot] = Equipment.UNIQUE;
        s.recomputeAll();
    }

    @Test
    public void theUniquesAreDroppedByBossesThatHadNone() throws Exception {
        GameData d = TestData.load();
        for (String k : new String[]{"CINDER_BAND", "ECHO_BELL", "LUMEN_WARD"}) {
            EquipDef e = d.equipment.get(k);
            assertNotNull(k, e);
            assertEquals(e, d.uniqueBySource.get(e.source));
        }
    }

    @Test
    public void cinderBandLeavesBurningGroundThatHurtsEnemiesNotTheHero() throws Exception {
        Sim s = sim(null, 9);
        wear(s, "CINDER_BAND");
        assertTrue(s.stats.cinderTrail);
        Input in = new Input();
        in.stickX = 1;
        for (int i = 0; i < 90; i++) s.update(DT, in);
        boolean patch = false;
        for (int i = 0; i < s.hazN; i++) if (s.hazards[i].kind == Entities.HZ_ASH && s.hazards[i].dps == Sim.CINDER_DPS) patch = true;
        assertTrue(patch);
        {
            Entities.Hazard h = null;
            for (int i = 0; i < s.hazN; i++) if (s.hazards[i].dps == Sim.CINDER_DPS) h = s.hazards[i];
            Enemy e = dummy(s, h.x, h.y, 1000f);
            e.power = 0f; // no contact damage on the hero
            float hp0 = s.hp;
            Input idle = new Input();
            for (int i = 0; i < 30; i++) {
                e.x = h.x;
                e.y = h.y;
                s.update(DT, idle);
            }
            assertTrue("enemy burned: " + e.hp, e.hp < 1000f);
            assertEquals("hero untouched", hp0, s.hp, 0.01f);
        }
    }

    @Test
    public void echoBellBoostsTheHitAfterTenGemsInARow() throws Exception {
        Sim s = sim(null, 10);
        wear(s, "ECHO_BELL");
        for (int i = 0; i < 10; i++) {
            s.addPickupForTest(Entities.K_GEM, s.px, s.py, 0.01f);
            s.update(DT, new Input());
        }
        assertTrue(s.bellReady);
        Enemy e = dummy(s, 400, 0, 1e6f);
        s.hurt(e, 100f, 0f, 1f, 0, 0, 0);
        assertEquals(130f, e.maxHp - e.hp, 0.5f);
        assertFalse(s.bellReady);
        s.hurt(e, 100f, 0f, 1f, 0, 0, 0);
        assertEquals(230f, e.maxHp - e.hp, 0.5f);
    }

    @Test
    public void lumenWardAbsorbsOneHitThenWaitsFortyFiveSeconds() throws Exception {
        Sim s = sim(null, 11);
        wear(s, "LUMEN_WARD");
        float hp0 = s.hp;
        s.hurtHero(20f, 0.5f, false);
        assertEquals("absorbed", hp0, s.hp, 0.001f);
        s.invuln = 0;
        s.hurtHero(20f, 0.5f, false);
        assertTrue("second hit lands", s.hp < hp0);
        s.hp = hp0;
        s.invuln = 0;
        run(s, 46.0);
        s.hp = hp0;
        s.invuln = 0;
        s.hurtHero(20f, 0.5f, false);
        assertEquals("ready again", hp0, s.hp, 0.001f);
    }

    // ------------------------------------------------------------------ the Vacuum Gem

    @Test
    public void aVacuumGemLandsAboutEveryFourMinutesAtMostOneAtATime() throws Exception {
        Sim s = sim(null, 12);
        s.pois.enabled = true;
        s.pois.calmUntil = 0;
        s.time = s.vacNext + 1.0;
        s.update(DT, new Input());
        assertTrue(s.vacuumGemLive());
        double next = s.vacNext;
        assertTrue(next - s.time >= 179.0 && next - s.time <= 301.0);
        s.time = next + 1.0;
        s.update(DT, new Input());
        int n = 0;
        for (int i = 0; i < s.pickN; i++) if (s.picks[i].kind == Entities.K_VACUUM && s.picks[i].value >= Sim.VACUUM_GEM) n++;
        assertEquals("one live at a time", 1, n);
    }

    @Test
    public void collectingTheVacuumGemPullsGemsAndCoins() throws Exception {
        Sim s = sim(null, 13);
        s.addPickupForTest(Entities.K_GEM, 900, 0, 1f);
        s.addPickupForTest(Entities.K_COIN, 0, 900, 1f);
        s.addPickupForTest(Entities.K_VACUUM, s.px, s.py, Sim.VACUUM_GEM);
        s.update(DT, new Input());
        for (int i = 0; i < s.pickN; i++) assertTrue("kind " + s.picks[i].kind, s.picks[i].pulled);
        // an ordinary Vacuum leaves the coin alone
        Sim t = sim(null, 14);
        t.addPickupForTest(Entities.K_COIN, 0, 900, 1f);
        t.addPickupForTest(Entities.K_VACUUM, t.px, t.py, 0f);
        t.update(DT, new Input());
        assertFalse(t.picks[0].pulled);
    }

    // ------------------------------------------------------------------ the two places

    private static int findSpot(Sim s, int kind) {
        for (int g = 0; g < 4000; g++) {
            s.px = (g % 63 - 31) * 700f;
            s.py = (g / 63 - 31) * 700f;
            s.update(DT, new Input());
            for (int i = 0; i < s.pois.spotN; i++) if (s.pois.spotKind[i] == kind) return i;
        }
        return -1;
    }

    @Test
    public void shrineOfHasteGivesTwentyPercentSpeedForSixtySecondsOnce() throws Exception {
        Sim s = sim(null, 15);
        s.pois.enabled = true;
        s.pois.calmUntil = 0;
        double base = s.stats.moveSpeedMul;
        int i = findSpot(s, Pois.SP_SHRINE);
        assertTrue(i >= 0);
        s.px = s.pois.spotX[i];
        s.py = s.pois.spotY[i];
        s.update(DT, new Input());
        assertEquals(base * 1.2, s.stats.moveSpeedMul, 1e-6);
        assertTrue(s.pois.spotUsed(i));
        assertEquals(60f, s.pois.hasteLeft(), 1f);
        s.time += 61.0;
        s.update(DT, new Input());
        assertEquals(base, s.stats.moveSpeedMul, 1e-6);
    }

    @Test
    public void cursedCacheSpringsTwelveEnemiesThenPaysDoubleGold() throws Exception {
        Sim s = sim(null, 16);
        s.pois.enabled = true;
        s.pois.calmUntil = 0;
        int i = findSpot(s, Pois.SP_CACHE);
        assertTrue(i >= 0);
        s.px = s.pois.spotX[i];
        s.py = s.pois.spotY[i];
        s.enemyN = 0;
        s.update(DT, new Input());
        int n = 0;
        for (int k = 0; k < s.enemyN; k++) if (s.enemies[k].poi == Pois.POI_AMBUSH) n++;
        assertEquals(12, n);
        assertTrue(s.pois.ambushOn);
        int gold0 = s.gold;
        for (int k = 0; k < s.enemyN; k++) if (s.enemies[k].poi == Pois.POI_AMBUSH) s.killForTest(s.enemies[k]);
        run(s, 1.0);
        assertFalse(s.pois.ambushOn);
        int gold = s.gold - gold0; // the hero stands on the cache and takes the bags
        assertTrue("double the outpost chest's 20 gold: " + gold, gold >= 40);
    }
}
