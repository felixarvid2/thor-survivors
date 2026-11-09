package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.WeaponDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.HashMap;
import java.util.Map;

/** v1.5 B2: the pure HUD logic (cooldown sweeps, status strip, boss panel, elite tags, XP chest tick, contrast) and the Describe texts. */
public class HudInfoTest {
    private GameData data;

    private Sim sim(String stage) throws Exception {
        data = TestData.load();
        Sim s = new Sim(data, data.stages.get(stage), null, new HashMap<String, Double>());
        s.noSpawns = true;
        s.enemyN = 0;
        s.rng.setSeed(5);
        return s;
    }

    // ---- item 11
    @Test
    public void cooldownFractionRunsFromZeroToReady() throws Exception {
        Sim s = sim("FLATS");
        s.inv.add(data.weapons.get("AXE"));
        s.recomputeAll();
        Sim.WeaponState w = s.weapons[0];
        assertNotNull(w);
        float period = HudInfo.period(w);
        assertTrue("the axe has a long volley wait", period > 1f);
        assertFalse(HudInfo.continuous(w));
        w.cd = period;
        assertEquals(0f, HudInfo.ready(w), 1e-4);
        w.cd = period / 2f;
        assertEquals(0.5f, HudInfo.ready(w), 1e-4);
        w.cd = -0.2f;
        assertEquals(1f, HudInfo.ready(w), 0f);
    }

    @Test
    public void aurasAndVeryFastWeaponsShowASteadyRing() throws Exception {
        Sim s = sim("FLATS");
        s.inv.add(data.weapons.get("GARLIC"));
        s.recomputeAll();
        assertTrue(HudInfo.continuous(s.weapons[0]));
        s.weapons[0].behavior = Sim.B_WHIP;
        s.weapons[0].interval = 0.2f;
        assertTrue("faster than 0.45 s: a sweep would only flicker", HudInfo.continuous(s.weapons[0]));
        s.weapons[0].interval = 1.4f;
        assertFalse(HudInfo.continuous(s.weapons[0]));
    }

    // ---- item 12
    @Test
    public void statusStripListsActiveStatusesInOrderWithBars() throws Exception {
        Sim s = sim("FLATS");
        int[] kinds = new int[HudInfo.HS_COUNT];
        assertEquals(0, HudInfo.active(s, kinds));
        s.heroSlowT = 1f;
        s.heroBurnT = 3f;
        s.guardT = 2f;
        assertEquals(3, HudInfo.active(s, kinds));
        assertEquals(HudInfo.HS_BURN, kinds[0]);
        assertEquals(HudInfo.HS_SLOW, kinds[1]);
        assertEquals(HudInfo.HS_GUARD, kinds[2]);
        assertEquals(1f, HudInfo.fraction(s, HudInfo.HS_BURN), 1e-4);
        assertEquals(0.5f, HudInfo.fraction(s, HudInfo.HS_SLOW), 1e-4);
        assertEquals(0.5f, HudInfo.fraction(s, HudInfo.HS_GUARD), 1e-4);
        s.heroStunT = 9f;
        s.heroDecayT = 0.5f;
        s.dodgeT = 0.1f;
        assertEquals("all six", 6, HudInfo.active(s, kinds));
        assertEquals("a bar never overflows", 1f, HudInfo.fraction(s, HudInfo.HS_STUN), 0f);
        assertTrue(HudInfo.beneficial(HudInfo.HS_GUARD) && !HudInfo.beneficial(HudInfo.HS_BURN));
    }

    @Test
    public void aStatusEndingWithinASecondBlinks() {
        int off = 0, on = 0;
        for (int i = 0; i < 80; i++) { // 1 s in 1/80 steps
            if (HudInfo.blinkOff(0.9f, i / 80.0)) off++; else on++;
        }
        assertTrue("it blinks: both phases occur", off > 20 && on > 20);
        for (int i = 0; i < 80; i++) assertFalse("a long status never blinks", HudInfo.blinkOff(2.5f, i / 80.0));
        assertFalse("an ended status is not drawn at all", HudInfo.blinkOff(0f, 0.0));
    }

    // ---- item 13
    @Test
    public void bossTicksComeFromTheBossData() throws Exception {
        Sim s = sim("FLATS");
        float[] t = new float[4];
        Enemy first = s.spawnAt("THE_FIRST", s.px + 300, s.py, true);
        assertEquals(2, HudInfo.bossTicks(first, t));
        assertEquals(0.66f, t[0], 1e-4);
        assertEquals(0.33f, t[1], 1e-4);
        Enemy slag = s.spawnAt("SLAGBORN", s.px + 300, s.py, true);
        assertEquals(1, HudInfo.bossTicks(slag, t));
        assertEquals("the enrage threshold of the data", 0.5f, t[0], 1e-4);
        Enemy loader = s.spawnAt("CARGO_LOADER", s.px + 300, s.py, true);
        assertEquals("a boss without phases has no tick", 0, HudInfo.bossTicks(loader, t));
        Enemy foreman = s.spawnAt("SMELTER_FOREMAN", s.px + 300, s.py, true);
        assertEquals(0.25f, (HudInfo.bossTicks(foreman, t) == 1 ? t[0] : -1f), 1e-4);
    }

    @Test
    public void enrageAndNextAttackReadTheBossState() throws Exception {
        Sim s = sim("FLATS");
        Enemy b = s.spawnAt("SLAGBORN", s.px + 300, s.py, true);
        assertFalse(HudInfo.enraged(b));
        b.enraged = true;
        assertTrue(HudInfo.enraged(b));
        b.ai = 0;
        b.tele = 0f;
        assertEquals(HudInfo.NA_NONE, HudInfo.nextAttack(b));
        b.ai = BossAi.BA_WINDUP;
        b.tele = 0.8f;
        b.teleLen = 0f;
        assertEquals("a ring or bomb mat", HudInfo.NA_AREA, HudInfo.nextAttack(b));
        b.teleLen = 300f;
        assertEquals("a charge has a length", HudInfo.NA_LINE, HudInfo.nextAttack(b));
        b.tele = 0f;
        assertEquals(HudInfo.NA_NONE, HudInfo.nextAttack(b));
    }

    // ---- item 14
    @Test
    public void eliteCountsPerModifierSkipDeadAndDormant() throws Exception {
        Sim s = sim("FLATS");
        s.time = 600;
        int[] per = new int[Elites.COUNT];
        assertEquals(0, HudInfo.eliteCounts(s, per));
        Enemy a = s.spawnEliteForTest("SERGEANT", Elites.M_FIRE | Elites.M_ARMORED);
        Enemy b = s.spawnEliteForTest("SERGEANT", Elites.M_FIRE);
        assertEquals(2, HudInfo.eliteCounts(s, per));
        assertEquals(2, per[0]);
        assertEquals(1, per[5]);
        assertEquals(0, per[1]);
        b.dead = true;
        assertEquals(1, HudInfo.eliteCounts(s, per));
        assertEquals(1, per[0]);
        a.dormant = true;
        assertEquals(0, HudInfo.eliteCounts(s, per));
    }

    // ---- item 20
    @Test
    public void chestTickWhenAChestOrABossIsClose() throws Exception {
        Sim s = sim("FLATS");
        s.time = 10;
        s.pendingChests = 1;
        assertTrue(HudInfo.chestSoon(s));
        s.pendingChests = 0;
        s.addPickupForTest(Entities.K_CHEST, s.px + 50, s.py, 0);
        assertTrue("a chest on the ground", HudInfo.chestSoon(s));
        s.pickN = 0;
        if (s.stage.timedAt.length > 0) {
            s.time = s.stage.timedAt[0] - 30;
            assertTrue("a timed boss within 60 s brings its chest", HudInfo.chestSoon(s));
            s.time = Math.max(0, s.stage.timedAt[0] - 200);
            assertFalse(HudInfo.chestSoon(s));
        }
    }

    // ---- item 19
    @Test
    public void contrastHelperReaches4Point5() {
        int bg = 0xFF15110F;
        assertTrue(HudInfo.contrast(0xFFFFFFFF, bg) > 15);
        int dim = 0xFF5A5048; // the "empty slot" grey of the panel
        assertTrue("the grey fails the target", HudInfo.contrast(dim, bg) < 4.5);
        int fixed = HudInfo.ensureContrast(dim, bg, 4.5);
        assertTrue(HudInfo.contrast(fixed, bg) >= 4.5);
        assertEquals("a colour that already reads is kept", 0xFFEFE8DC, HudInfo.ensureContrast(0xFFEFE8DC, bg, 4.5));
        assertEquals(0xFF000000, HudInfo.ensureContrast(0xFF000000, 0xFFFFFFFF, 1.0) & 0xFF000000);
    }

    // ---- item 16
    @Test
    public void pauseStatsAreSignedPercentsAndFlatNumbers() throws Exception {
        PlayerStats p = new PlayerStats();
        p.might = 1.25;
        p.areaMul = 1.1;
        p.cooldownMul = 0.9;
        p.moveSpeedMul = 1.0;
        p.armor = 2;
        p.luck = 0.15;
        p.growthMul = 1.0;
        p.greedMul = 1.3;
        String[] v = Describe.statValues(p, 1);
        assertEquals(Describe.STAT_LABELS.length, v.length);
        assertEquals("+25%", v[0]);
        assertEquals("+10%", v[1]);
        assertEquals("-10%", v[2]);
        assertEquals("0%", v[3]);
        assertEquals("2", v[4]);
        assertEquals("+15%", v[5]);
        assertEquals("0%", v[6]);
        assertEquals("+30%", v[7]);
        assertEquals("1", v[8]);
        assertEquals("0", Describe.statValues(p, -3)[8]);
    }

    // ---- items 17 and 18
    private Sim buildWith(String... keys) throws Exception {
        Sim s = sim("FLATS");
        for (String k : keys) s.inv.add(data.weapons.get(k));
        s.recomputeAll();
        return s;
    }

    @Test
    public void evolutionTrackerHasThreeStates() throws Exception {
        Sim s = buildWith("WHIP");
        Map<String, WeaponDef> defs = data.weapons;
        WeaponDef whip = defs.get("WHIP");
        String need = defs.get(whip.evoSynergy.get(0)).name;
        s.inv.weapons[0].level = 3;
        assertEquals(Describe.EVO_NOT_MAX, Describe.evoState(s.inv, 0, defs));
        String l1 = Describe.evoLine(s.inv, 0, defs);
        assertTrue(l1, l1.startsWith("Lv 3/" + whip.maxLevel() + ", passive missing: " + need));
        assertTrue(l1.endsWith("needs boss chest"));
        s.inv.weapons[0].level = whip.maxLevel();
        assertEquals(Describe.EVO_MISSING_PASSIVE, Describe.evoState(s.inv, 0, defs));
        String l2 = Describe.evoLine(s.inv, 0, defs);
        assertEquals("Lv " + whip.maxLevel() + "/" + whip.maxLevel() + ", passive missing: " + need + ", needs boss chest", l2);
        s.inv.add(defs.get(whip.evoSynergy.get(0)));
        assertEquals(Describe.EVO_READY, Describe.evoState(s.inv, 0, defs));
        String l3 = Describe.evoLine(s.inv, 0, defs);
        assertTrue(l3, l3.contains(need + " owned") && l3.contains("ready"));
        assertTrue(Describe.evoCompact(s.inv, defs).contains("ready"));
    }

    @Test
    public void evolvedWeaponsAndPassivesHaveNoTrackerLine() throws Exception {
        Sim s = buildWith("WHIP", "MAXHEALTH");
        s.inv.weapons[0].level = s.inv.weapons[0].def.maxLevel();
        assertEquals(0, s.inv.firstEvolvable(data.weapons));
        s.inv.evolve(0, data.weapons);
        assertEquals(Describe.EVO_NONE, Describe.evoState(s.inv, 0, data.weapons));
        assertNull(Describe.evoLine(s.inv, 0, data.weapons));
        assertEquals(Describe.EVO_NONE, Describe.evoState(s.inv, 5, data.weapons));
        assertNull(Describe.evoCompact(s.inv, data.weapons));
    }

    @Test
    public void compactTrackerPicksTheWeaponClosestToEvolving() throws Exception {
        Sim s = buildWith("WHIP", "KNIFE");
        s.inv.weapons[0].level = 2;
        s.inv.weapons[1].level = 6;
        String c = Describe.evoCompact(s.inv, data.weapons);
        assertTrue(c, c.startsWith(s.inv.weapons[1].def.name + " 6/"));
        assertTrue(c, c.contains("needs "));
    }

    @Test
    public void tooltipShowsNowAndNextAndNewWeaponsShowTheirEnd() throws Exception {
        Sim s = buildWith("WHIP");
        WeaponDef whip = data.weapons.get("WHIP");
        String[] t = Describe.tooltip(new Offer(Offer.WEAPON_LEVEL, whip, 3), data.weapons);
        assertTrue(t[0], t[0].startsWith("NOW  LV 2: "));
        assertTrue(t[1], t[1].startsWith("NEXT  LV 3: "));
        assertTrue("numbers, not the delta", t[0].contains("Damage") && t[0].contains("Cooldown"));
        assertFalse("the two levels differ", t[0].equals(t[1].replace("NEXT  LV 3", "NOW  LV 2")));
        WeaponDef knife = data.weapons.get("KNIFE");
        String[] n = Describe.tooltip(new Offer(Offer.NEW_WEAPON, knife, 1), data.weapons);
        assertTrue(n[0], n[0].startsWith("NEW  LV 1: "));
        assertTrue(n[1], n[1].startsWith("MAX  LV " + knife.maxLevel()) && n[1].contains("EVOLVES WITH"));
        WeaponDef hp = data.weapons.get("MAXHEALTH");
        String[] p = Describe.tooltip(new Offer(Offer.PASSIVE_LEVEL, hp, 2), data.weapons);
        assertTrue(p[0], p[0].startsWith("NOW  LV 1: "));
        assertNull(Describe.tooltip(new Offer(Offer.HEAL, null, 0), data.weapons));
    }

    @Test
    public void evolvesBannerOnTheCardThatCompletesTheRequirement() throws Exception {
        Sim s = buildWith("WHIP", "MAXHEALTH");
        WeaponDef whip = data.weapons.get("WHIP");
        int max = whip.maxLevel();
        s.inv.weapons[0].level = max - 1;
        assertTrue("the last level with the passive owned", Describe.completesEvolution(new Offer(Offer.WEAPON_LEVEL, whip, max), s.inv, data.weapons));
        assertFalse("not the last level", Describe.completesEvolution(new Offer(Offer.WEAPON_LEVEL, whip, max - 1), s.inv, data.weapons));
        Sim t = buildWith("WHIP");
        t.inv.weapons[0].level = max;
        WeaponDef hp = data.weapons.get("MAXHEALTH");
        assertTrue("the passive a maxed weapon waits for", Describe.completesEvolution(new Offer(Offer.NEW_PASSIVE, hp, 1), t.inv, data.weapons));
        assertFalse("another passive", Describe.completesEvolution(new Offer(Offer.NEW_PASSIVE, data.weapons.get("POWER"), 1), t.inv, data.weapons));
        Sim u = buildWith("WHIP");
        u.inv.weapons[0].level = 3;
        assertFalse("the weapon is not maxed yet", Describe.completesEvolution(new Offer(Offer.NEW_PASSIVE, hp, 1), u.inv, data.weapons));
        assertFalse(Describe.completesEvolution(new Offer(Offer.HEAL, null, 0), u.inv, data.weapons));
    }
}
