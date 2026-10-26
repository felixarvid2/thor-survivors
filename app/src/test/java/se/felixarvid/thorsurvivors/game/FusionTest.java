package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.Treasure;
import se.felixarvid.thorsurvivors.data.WeaponDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/** Weapon fusions (design v1.3 section 1): data, the Inventory rules, the offers, the Arms Crate, the Fuse Kit and each behaviour. */
public class FusionTest {
    private static final float DT = 1f / 60f;

    private static GameData d() throws Exception {
        return TestData.load();
    }

    private static Sim sim(long seed) throws Exception {
        GameData d = d();
        Sim s = new Sim(d, d.stages.get("FLATS"), null, new HashMap<String, Double>(), null, seed);
        s.noSpawns = true;
        s.enemyN = 0;
        s.merchant.enabled = false;
        return s;
    }

    /** Puts a base weapon (max level) or its evolved form in the inventory. */
    private static void master(Sim s, String baseKey, boolean evolved) throws Exception {
        GameData d = d();
        WeaponDef b = d.weapons.get(baseKey);
        if (evolved) {
            s.inv.add(d.weapons.get(b.evoInto));
        } else {
            s.inv.add(b);
            s.inv.weapons[s.inv.weaponCount - 1].level = b.maxLevel();
        }
    }

    /** The first synergy passive of a base weapon. */
    private static WeaponDef passiveFor(GameData d, String baseKey) {
        return d.weapons.get(d.weapons.get(baseKey).evoSynergy.get(0));
    }

    private static void run(Sim s, double seconds) {
        Input in = new Input();
        for (int i = 0, n = (int) Math.round(seconds / DT); i < n; i++) {
            s.hp = s.maxHp;
            s.update(DT, in);
        }
    }

    // ------------------------------------------------------------------ data

    @Test
    public void sevenFusionsWithSixLevelsAndTwoBaseParentsAreInTheData() throws Exception {
        GameData d = d();
        assertEquals(7, d.fusions.length);
        for (WeaponDef f : d.fusions) {
            assertTrue(f.key, f.fusion && !f.evolution && !f.powerUp);
            assertEquals(f.key, 6, f.maxLevel());
            assertEquals(2, f.parents.size());
            for (String p : f.parents) {
                WeaponDef b = d.weapons.get(p);
                assertNotNull(p, b);
                assertFalse(p, b.fusion || b.evolution || b.powerUp);
            }
            assertTrue(f.key, d.weapons.get(f.key) == f);
            assertTrue("power grows 8% a level", f.stat(6, "power", 0) > f.stat(1, "power", 0) * 1.35);
        }
    }

    @Test
    public void aFusionIsLevelableButNeverOfferedAsANewWeapon() throws Exception {
        GameData d = d();
        Sim s = sim(1);
        for (Offer o : OfferGenerator.candidates(s.inv, d.weapons.values())) assertFalse(o.def.fusion);
        master(s, "WHIP", false);
        master(s, "LIGHTNING", false);
        assertTrue(s.fuseNow(d.weapons.get("STORMLASH")));
        boolean levelCard = false;
        for (Offer o : OfferGenerator.candidates(s.inv, d.weapons.values())) {
            if (o.def.fusion) {
                assertEquals(Offer.WEAPON_LEVEL, o.kind);
                levelCard = true;
            }
        }
        assertTrue(levelCard);
    }

    // ------------------------------------------------------------------ the Inventory rules

    @Test
    public void eachFusionIsReachableFromTwoMasteredParents() throws Exception {
        GameData d = d();
        for (WeaponDef f : d.fusions) {
            Sim s = sim(2);
            master(s, f.parents.get(0), false);
            master(s, f.parents.get(1), false);
            assertTrue(f.key, s.inv.canFuse(f, d.weapons));
            assertEquals(f.key, f, s.inv.readyFusion(d.fusions, d.weapons) == null ? null : f); // some fusion is ready (maybe an earlier one)
            assertTrue(s.fuseNow(f));
            assertEquals(f.key, 1, s.inv.weaponCount);
            assertEquals(f.key, f, s.inv.weapons[0].def);
            assertEquals("starts at level 1 with base parents", 1, s.inv.weapons[0].level);
        }
    }

    @Test
    public void fusingFreesExactlyOneSlotAndTheFusionTakesTheLowerOne() throws Exception {
        GameData d = d();
        Sim s = sim(3);
        s.inv.add(d.weapons.get("KNIFE"));
        master(s, "AXE", false);
        s.inv.add(d.weapons.get("HOLYWATER"));
        master(s, "CROSS", false);
        s.inv.add(d.weapons.get("MAGIC_MISSILE"));
        assertEquals(5, s.inv.weaponCount);
        assertTrue(s.fuseNow(d.weapons.get("RICOCHET_ORDNANCE")));
        assertEquals(4, s.inv.weaponCount);
        assertEquals("KNIFE", s.inv.weapons[0].def.key);
        assertEquals("the fusion sits where the lower parent was", "RICOCHET_ORDNANCE", s.inv.weapons[1].def.key);
        assertEquals("HOLYWATER", s.inv.weapons[2].def.key);
        assertEquals("MAGIC_MISSILE", s.inv.weapons[3].def.key);
        assertNull("the freed slot is empty", s.inv.weapons[4].def);
        assertTrue("and can take a new weapon", s.inv.add(d.weapons.get("FIREBALL")));
    }

    @Test
    public void parentsBelowMaxLevelCannotFuse() throws Exception {
        GameData d = d();
        Sim s = sim(4);
        master(s, "WHIP", false);
        s.inv.add(d.weapons.get("LIGHTNING")); // level 1
        assertFalse(s.inv.canFuse(d.weapons.get("STORMLASH"), d.weapons));
        s.inv.weapons[1].level = d.weapons.get("LIGHTNING").maxLevel() - 1;
        assertFalse(s.inv.canFuse(d.weapons.get("STORMLASH"), d.weapons));
        assertFalse(s.fuseNow(d.weapons.get("STORMLASH")));
        assertEquals(2, s.inv.weaponCount);
        s.inv.weapons[1].level = d.weapons.get("LIGHTNING").maxLevel();
        assertTrue(s.inv.canFuse(d.weapons.get("STORMLASH"), d.weapons));
    }

    @Test
    public void everyEvolvedParentAddsOneStartLevel() throws Exception {
        GameData d = d();
        WeaponDef f = d.weapons.get("STORMLASH");
        Sim a = sim(5);
        master(a, "WHIP", true);
        master(a, "LIGHTNING", false);
        assertTrue(a.fuseNow(f));
        assertEquals(2, a.inv.weapons[0].level);
        Sim b = sim(5);
        master(b, "WHIP", true);
        master(b, "LIGHTNING", true);
        assertTrue(b.fuseNow(f));
        assertEquals(3, b.inv.weapons[0].level);
        Sim c = sim(5);
        master(c, "WHIP", false);
        master(c, "LIGHTNING", true);
        assertTrue(c.fuseNow(f));
        assertEquals(2, c.inv.weapons[0].level);
    }

    @Test
    public void aFusionIsNeverAParentAndCannotBeMadeTwice() throws Exception {
        GameData d = d();
        Sim s = sim(6);
        master(s, "WHIP", false);
        master(s, "LIGHTNING", false);
        master(s, "HOLYBOOK", false);
        assertTrue(s.fuseNow(d.weapons.get("STORMLASH")));
        s.inv.weapons[0].level = 6;
        assertFalse("the fusion is not a parent of Faraday Halo", s.inv.canFuse(d.weapons.get("FARADAY_HALO"), d.weapons));
        assertFalse(s.inv.canFuse(d.weapons.get("STORMLASH"), d.weapons));
    }

    // ------------------------------------------------------------------ behaviours

    private static double damageToField(String fusionKey, int level, long seed) throws Exception {
        return FusionBalance.dps(d(), fusionKey, level, seed, 6f);
    }

    @Test
    public void eachFusionDamagesASeededTwoHundredEnemyField() throws Exception {
        for (WeaponDef f : d().fusions) {
            double dps = damageToField(f.key, 1, 11);
            assertTrue(f.key + " dps " + dps, dps > 100);
            assertEquals("seeded: the same field gives the same result", dps, damageToField(f.key, 1, 11), 1e-6);
            assertTrue(f.key + " grows with its levels", damageToField(f.key, 6, 11) > dps);
        }
    }

    @Test
    public void stormlashChainsAtMostSixTimesPerSlashToEnemiesOutsideTheSlash() throws Exception {
        GameData d = d();
        Sim s = sim(7);
        master(s, "WHIP", false);
        master(s, "LIGHTNING", false);
        s.fuseNow(d.weapons.get("STORMLASH"));
        s.facingRight = true;
        Enemy hit = s.addEnemy("COLONIST", 80, -6, false); // inside the slash
        List<Enemy> near = new ArrayList<>();
        for (int i = 0; i < 8; i++) near.add(s.addEnemy("COLONIST", 80 + (i - 3) * 22f, 100 + (i % 2) * 20f, false)); // out of the slash, within 140 of the hit
        Enemy far = s.addEnemy("COLONIST", 80, 400, false);
        for (Enemy e : near) { e.hp = e.maxHp = 1e5f; e.speed = 0; }
        hit.hp = hit.maxHp = 1e5f; hit.speed = 0;
        far.hp = far.maxHp = 1e5f; far.speed = 0;
        run(s, 0.47f); // the first slash at 0.4 s
        int chained = 0;
        for (Enemy e : near) if (e.hp < e.maxHp) chained++;
        assertTrue("the hit enemy is hurt", hit.hp < hit.maxHp);
        assertTrue("2 nearest chained: " + chained, chained == 2);
        assertEquals("beyond 140 u nothing", far.maxHp, far.hp, 0f);
    }

    @Test
    public void needleChoirFiresAFanOfHomingNeedlesWithinFortyDegrees() throws Exception {
        GameData d = d();
        Sim s = sim(8);
        master(s, "MAGIC_MISSILE", false);
        master(s, "KNIFE", false);
        s.fuseNow(d.weapons.get("NEEDLE_CHOIR"));
        s.inv.weapons[0].level = 5; // 5 needles
        s.recomputeAll();
        Enemy t = s.addEnemy("COLONIST", 300, 0, false);
        t.hp = t.maxHp = 1e5f;
        t.speed = 0;
        int needles = 0;
        for (int i = 0; i < 90 && needles == 0; i++) {
            run(s, DT);
            for (int k = 0; k < s.projN; k++) if (s.projs[k].kind == Entities.P_KNIFE) needles++;
        }
        assertEquals(5, needles);
        double lo = 1e9, hi = -1e9;
        for (int k = 0; k < s.projN; k++) {
            Entities.Projectile p = s.projs[k];
            double a = Math.toDegrees(Math.atan2(p.vy, p.vx));
            lo = Math.min(lo, a);
            hi = Math.max(hi, a);
            assertTrue(p.fusion == Fusions.NEEDLE && p.targetId == t.id);
        }
        assertTrue("a 40 degree cone (homing has already bent the first frame): " + (hi - lo), hi - lo > 28 && hi - lo <= 40.5);
        run(s, 1.2f);
        assertTrue("the fan homes in on the target", t.hp < t.maxHp);
    }

    @Test
    public void censersAndDronesOrbitAndHaveTheirAuraOrBolts() throws Exception {
        GameData d = d();
        Sim s = sim(9);
        master(s, "GARLIC", false);
        master(s, "HOLYBOOK", false);
        s.fuseNow(d.weapons.get("MIASMA_CENSER"));
        Enemy e = s.addEnemy("COLONIST", 90, 0, false);
        e.hp = e.maxHp = 1e5f;
        e.speed = 0;
        run(s, 0.6f);
        int books = 0;
        float minR = 1e9f, maxR = 0;
        for (int i = 0; i < 360; i++) {
            run(s, DT);
            for (int k = 0; k < s.projN; k++) {
                Entities.Projectile p = s.projs[k];
                if (p.kind != Entities.P_BOOK) continue;
                float r = (float) Math.hypot(p.x - s.px, p.y - s.py);
                minR = Math.min(minR, r);
                maxR = Math.max(maxR, r);
            }
        }
        for (int k = 0; k < s.projN; k++) if (s.projs[k].kind == Entities.P_BOOK) books++;
        assertEquals("3 censers, endless", 3, books);
        assertEquals("the orbit grows 20%", 1.2f, maxR / minR, 0.04f);
        assertTrue("the aura hurts and decays", e.hp < e.maxHp);

        Sim h = sim(10);
        master(h, "HOLYBOOK", false);
        master(h, "LIGHTNING", false);
        h.fuseNow(d.weapons.get("FARADAY_HALO"));
        Enemy t = h.addEnemy("COLONIST", 130, 0, false);
        t.hp = t.maxHp = 1e5f;
        t.speed = 0;
        int bolts = h.sound.emitted[SoundEvents.LIGHTNING];
        run(h, 2f);
        assertTrue("drones call lightning on an enemy within 150 u", h.sound.emitted[SoundEvents.LIGHTNING] > bolts);
        assertTrue(t.hp < t.maxHp);
    }

    @Test
    public void ordnanceDetonatesAtTheTurnAndOnTheReturn() throws Exception {
        GameData d = d();
        Sim s = sim(11);
        master(s, "AXE", false);
        master(s, "CROSS", false);
        s.fuseNow(d.weapons.get("RICOCHET_ORDNANCE"));
        // the nearest enemy sets the launch line (+x); the other two stand 60 u off it, so only a blast (r 70+) can reach them
        Enemy aim = s.addEnemy("COLONIST", 50, 0, false);
        Enemy back = s.addEnemy("COLONIST", 20, 60, false); // near the hero: the return blast
        Enemy turn = s.addEnemy("COLONIST", 430, 60, false); // at the turn point: the first blast
        for (Enemy e : new Enemy[]{aim, back, turn}) {
            e.hp = e.maxHp = 1e6f;
            e.speed = 0;
        }
        boolean turnedBeforeBack = false;
        for (int i = 0; i < 300; i++) {
            run(s, DT);
            if (turn.hp < turn.maxHp && back.hp == back.maxHp) turnedBeforeBack = true;
        }
        assertTrue("the turn blast came first", turnedBeforeBack);
        assertTrue("and the return blast too", back.hp < back.maxHp);
        assertTrue(turn.hp < turn.maxHp);
    }

    @Test
    public void foundryPoolLobsFireBurstsAndKilnBreathSendsARing() throws Exception {
        GameData d = d();
        Sim s = sim(12);
        master(s, "FIREBALL", false);
        master(s, "HOLYWATER", false);
        s.fuseNow(d.weapons.get("FOUNDRY_SPILL"));
        for (int i = 0; i < 12; i++) {
            Enemy e = s.addEnemy("COLONIST", 40 + i * 14f, 30 - i * 5f, false);
            e.hp = e.maxHp = 1e6f;
            e.speed = 0;
        }
        run(s, 5f);
        boolean burning = false;
        for (int i = 0; i < s.enemyN; i++) burning |= s.enemies[i].burn > 0;
        assertTrue("the pool burns what stands in it", burning);

        Sim k = sim(13);
        master(k, "GARLIC", false);
        master(k, "FIREBALL", false);
        k.fuseNow(d.weapons.get("KILN_BREATH"));
        Enemy far = k.addEnemy("COLONIST", 170, 0, false); // outside the aura, inside the ring (r 200)
        far.hp = far.maxHp = 1e6f;
        far.speed = 0;
        run(k, 3.8f);
        assertTrue("the ring reaches 170 u", far.hp < far.maxHp);
    }

    // ------------------------------------------------------------------ chests, caches

    @Test
    public void aChestFusesFirstAndTheLineNamesBothParents() throws Exception {
        GameData d = d();
        Sim s = sim(14);
        master(s, "WHIP", false);
        master(s, "LIGHTNING", false);
        s.pendingChests = 1;
        String[] lines = s.openChest(new Treasure(new double[]{0, 0, 100}, 1, new String[]{Treasure.FUSION, Treasure.EVOLUTION}));
        assertEquals("FUSION! Vibro Lash + Arc Coil -> Stormlash", lines[0]);
        assertEquals(1, s.inv.weaponCount);
        assertTrue(s.sound.emitted[SoundEvents.EVOLUTION] > 0);
        assertEquals(d.weapons.get("STORMLASH"), s.lastFusedTo);
    }

    @Test
    public void aFusionPrizeWithNoPairFallsBackToAnEvolution() throws Exception {
        GameData d = d();
        Sim s = sim(15);
        master(s, "WHIP", false);
        s.inv.add(passiveFor(d, "WHIP"));
        s.pendingChests = 1;
        String[] lines = s.openChest(new Treasure(new double[]{0, 0, 100}, 1, new String[]{Treasure.FUSION, Treasure.EVOLUTION}));
        assertEquals("EVOLUTION!", lines[0]);
        assertTrue(s.inv.weapons[0].def.evolution);
    }

    @Test
    public void everyBossChestTableHasFusionThenEvolutionFirst() throws Exception {
        GameData d = d();
        for (String st : new String[]{"FLATS", "MINES"}) {
            int n = 0;
            for (se.felixarvid.thorsurvivors.data.WaveRow r : d.stages.get(st).rows) {
                if (r.treasure == null) continue;
                n++;
                assertEquals("FUSION", r.treasure.prizeAt(0));
                assertEquals("EVOLUTION", r.treasure.prizeAt(1));
            }
            assertEquals(6, n);
        }
    }

    @Test
    public void aWeaponCacheFusesAReadyPairElseEvolves() throws Exception {
        GameData d = d();
        Sim s = sim(16);
        master(s, "KNIFE", false);
        master(s, "MAGIC_MISSILE", false);
        s.collectFindForTest(Sim.FIND_WEAPON);
        assertEquals(1, s.inv.weaponCount);
        assertEquals("NEEDLE_CHOIR", s.inv.weapons[0].def.key);
        Sim e = sim(17);
        master(e, "WHIP", false);
        e.inv.add(passiveFor(d, "WHIP"));
        e.collectFindForTest(Sim.FIND_WEAPON);
        assertEquals("VAMPIRICA", e.inv.weapons[0].def.key);
    }

    // ------------------------------------------------------------------ the FUSE card

    private static List<Offer> three(Sim s) throws Exception {
        return OfferGenerator.generate(s.inv, d().weapons.values(), 0, new Random(3));
    }

    @Test
    public void theFuseCardNeedsNinetySecondsAndAppearsAtMostOnceInTwoLevelUps() throws Exception {
        GameData d = d();
        Sim s = sim(18);
        master(s, "WHIP", false);
        master(s, "LIGHTNING", false);
        run(s, 1.0); // the pair is noticed
        assertNotNull(s.fusionReady);
        List<Offer> o = three(s);
        s.addFuseCard(o);
        for (Offer x : o) assertFalse("not after 1 s", x.kind == Offer.FUSE);
        run(s, 95.0);
        List<Offer> a = three(s);
        int size = a.size();
        s.addFuseCard(a);
        assertEquals(size, a.size());
        int fuse = 0;
        for (Offer x : a) if (x.kind == Offer.FUSE) fuse++;
        assertEquals(1, fuse);
        assertEquals("it replaces the last card", Offer.FUSE, a.get(size - 1).kind);
        Offer f = a.get(size - 1);
        assertEquals("Stormlash", f.def.name);
        assertEquals("Vibro Lash", f.partA.name);
        assertEquals("Arc Coil", f.partB.name);
        List<Offer> b = three(s);
        s.addFuseCard(b);
        for (Offer x : b) assertFalse("not at the next level-up", x.kind == Offer.FUSE);
        List<Offer> c = three(s);
        s.addFuseCard(c);
        boolean again = false;
        for (Offer x : c) again |= x.kind == Offer.FUSE;
        assertTrue("again after two level-ups when declined", again);
        // taking it fuses and frees a slot; the other cards decline it
        s.applyOffer(f);
        assertEquals(1, s.inv.weaponCount);
        assertEquals("STORMLASH", s.inv.weapons[0].def.key);
    }

    @Test
    public void thePartnerOfAMasteredWeaponIsTaggedAndDrawnMoreOften() throws Exception {
        GameData d = d();
        Sim s = sim(19);
        master(s, "WHIP", false);
        List<Offer> pool = OfferGenerator.candidates(s.inv, d.weapons.values());
        Offer lightning = null, knife = null;
        for (Offer o : pool) {
            OfferGenerator.weightOf(s.inv, d.weapons.values(), o);
            if (o.def.key.equals("LIGHTNING")) lightning = o;
            if (o.def.key.equals("KNIFE")) knife = o;
        }
        assertTrue("Arc Coil is the partner of a mastered Vibro Lash", lightning.partner);
        assertFalse(knife.partner);
        assertEquals(1.5, OfferGenerator.weightOf(s.inv, d.weapons.values(), lightning) / lightning.def.rarity, 1e-9);
        assertEquals(1.0, OfferGenerator.weightOf(s.inv, d.weapons.values(), knife) / knife.def.rarity, 1e-9);
        // the synergy passive of a maxed weapon counts x3
        Offer pass = null;
        for (Offer o : pool) if (o.def.key.equals("MAXHEALTH")) pass = o;
        assertEquals(3.0, OfferGenerator.weightOf(s.inv, d.weapons.values(), pass) / pass.def.rarity, 1e-9);
    }

    // ------------------------------------------------------------------ the Arms Crate

    private static Entities.Pickup armsCrate(Sim s) {
        for (int i = 0; i < s.pickN; i++) if (s.picks[i].kind == Entities.K_CHEST && s.picks[i].value < -0.5f) return s.picks[i];
        return null;
    }

    @Test
    public void anEliteDropsAnArmsCrateFromTheSecondElite() throws Exception {
        Sim s = sim(20);
        s.time = 200;
        Enemy e1 = s.spawnEliteForTest("COLONIST", 0);
        s.killForTest(e1);
        run(s, 0.5);
        assertNull("the first elite has no Arms Crate", armsCrate(s));
        assertEquals(1, s.eliteKilled);
        s.time = 400;
        Enemy e2 = s.spawnEliteForTest("COLONIST", 0);
        s.killForTest(e2);
        run(s, 0.5);
        assertEquals(2, s.eliteKilled);
        assertNotNull("the second elite does", armsCrate(s));
        boolean gear = false;
        for (int i = 0; i < s.pickN; i++) gear |= s.picks[i].kind == Entities.K_EQUIP;
        assertTrue("the gear crate still drops next to it", gear);
    }

    @Test
    public void theArmsCrateIsBeforeFiveMinutesOnlyAfterTheSecondElite() throws Exception {
        Sim s = sim(21);
        s.eliteKilled = 1;
        s.time = 250; // second elite but before 5:00
        Enemy e = s.spawnEliteForTest("COLONIST", 0);
        s.killForTest(e);
        run(s, 0.5);
        assertNull(armsCrate(s));
    }

    @Test
    public void anArmsCrateOpensWithFusionEvolutionAndWeaponLevels() throws Exception {
        GameData d = d();
        Sim s = sim(22);
        master(s, "WHIP", false);
        master(s, "LIGHTNING", false);
        s.inv.add(d.weapons.get("KNIFE"));
        s.addPickupForTest(Entities.K_CHEST, s.px, s.py, Sim.ARMS_CRATE);
        run(s, 0.5);
        assertEquals(1, s.pendingChests);
        assertEquals(1, s.pendingArms);
        String[] lines = s.openChest();
        assertEquals(0, s.pendingArms);
        assertTrue(lines[0], lines[0].startsWith("FUSION!"));
        assertEquals("the pair became one weapon", 2, s.inv.weaponCount);
        assertEquals("the weapon level prize raised the knife", 2, s.inv.weaponLevel("KNIFE") >= 2 ? 2 : 0);
    }

    // ------------------------------------------------------------------ the Fuse Kit

    @Test
    public void theFuseKitAppearsOnlyWhileAPairIsReady() throws Exception {
        GameData d = d();
        Sim s = sim(23);
        s.merchant.enabled = true;
        run(s, 271);
        assertTrue(s.merchant.present);
        s.merchant.opened();
        assertFalse(s.merchant.kit);
        assertEquals(Merchant.SLOTS, s.merchant.cards());
        master(s, "GARLIC", false);
        master(s, "HOLYBOOK", false);
        s.merchant.opened();
        assertTrue(s.merchant.kit);
        assertEquals(4, s.merchant.cards());
        assertEquals(Merchant.priceOf(Merchant.SLOT_FUSE, 0), s.merchant.price[Merchant.SLOT_FUSE]);
        assertEquals("250 gold on the first visit", 250, s.merchant.price[Merchant.SLOT_FUSE]);
        s.gold = 100;
        assertEquals(Merchant.BUY_POOR, s.merchant.buy(Merchant.SLOT_FUSE));
        s.gold = 1000;
        assertEquals(Merchant.BUY_OK, s.merchant.buy(Merchant.SLOT_FUSE));
        assertEquals(750, s.gold);
        assertEquals(1, s.inv.weaponCount);
        assertTrue(s.inv.weapons[0].def.fusion);
    }

    @Test
    public void theFuseKitPriceRisesAQuarterPerVisit() {
        assertEquals(250, Merchant.priceOf(Merchant.SLOT_FUSE, 0));
        assertEquals(315, Merchant.priceOf(Merchant.SLOT_FUSE, 1));
        assertEquals(375, Merchant.priceOf(Merchant.SLOT_FUSE, 2));
    }

    // ------------------------------------------------------------------ allocation

    @Test
    public void theFusionLoopAllocatesNothing() throws Exception {
        GameData d = d();
        for (WeaponDef f : d.fusions) {
            Sim s = sim(30);
            master(s, f.parents.get(0), false);
            master(s, f.parents.get(1), false);
            assertTrue(s.fuseNow(f));
            s.inv.weapons[0].level = 6;
            s.recomputeAll();
            Random r = new Random(4);
            for (int i = 0; i < 120; i++) {
                double a = r.nextDouble() * 6.28, rad = 30 + r.nextDouble() * 230;
                Enemy e = s.addEnemy("COLONIST", (float) (Math.cos(a) * rad), (float) (Math.sin(a) * rad), false);
                e.hp = e.maxHp = 1e7f;
                e.speed = 0;
            }
            run(s, 4f); // warm up
            long before = Alloc.bytes();
            int steps = 1500;
            Input in = new Input();
            for (int i = 0; i < steps; i++) {
                s.hp = s.maxHp;
                s.update(DT, in);
            }
            long bytes = Alloc.bytes() - before;
            assertTrue(f.key + " allocated " + bytes + " bytes in " + steps + " frames", bytes < steps * 16L);
        }
    }
}
