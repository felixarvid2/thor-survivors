package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;

import java.util.HashMap;

/** v1.5 B4: ascension chain, run modifiers, charges, refunds, tints, records, achievements and the daily challenge. */
public class MetaB4Test {
    private static MetaProgress meta(MetaProgressTest.MemStore st) throws Exception {
        return new MetaProgress(TestData.load(), st);
    }

    // ------------------------------------------------------------------ 31, 32: ascension

    @Test
    public void tiersOpenOneByOneByWinningTheStageOnTheTierBelow() throws Exception {
        MetaProgressTest.MemStore st = new MetaProgressTest.MemStore();
        MetaProgress m = meta(st);
        assertEquals(0, m.ascMax("FLATS")); // never won: only tier 0
        m.recordAscWin("FLATS", 0);
        assertEquals(1, m.ascMax("FLATS"));
        m.recordAscWin("FLATS", 0); // winning tier 0 again opens nothing more
        assertEquals(1, m.ascMax("FLATS"));
        m.recordAscWin("FLATS", 1);
        m.recordAscWin("FLATS", 2);
        assertEquals(3, m.ascMax("FLATS"));
        m.recordAscWin("FLATS", 3);
        m.recordAscWin("FLATS", 4);
        m.recordAscWin("FLATS", 5);
        assertEquals(5, m.ascMax("FLATS")); // capped
        assertEquals(0, m.ascMax("MINES")); // per stage
        assertEquals(5, meta(st).ascMax("FLATS")); // persists
    }

    @Test
    public void anOldSaveWithTheStageClearedOpensTierOne() throws Exception {
        MetaProgressTest.MemStore st = new MetaProgressTest.MemStore();
        st.putInt("cleared_MINES", 1);
        assertEquals(1, meta(st).ascMax("MINES"));
    }

    @Test
    public void eachTierAddsOneModifierAndTheyStack() {
        assertEquals(1f, RunMods.NONE.hpMult(), 0);
        assertEquals(1f, RunMods.NONE.goldMult(), 0);
        for (int t = 0; t <= 5; t++) {
            RunMods r = RunMods.ascension(t, 0, 0);
            assertEquals(t, Integer.bitCount(r.modMask));
            assertEquals(1f + 0.15f * t, r.goldMult(), 1e-5);
        }
        RunMods t1 = RunMods.ascension(1, 0, 0), t3 = RunMods.ascension(3, 0, 0), t5 = RunMods.ascension(5, 0, 0);
        assertEquals(1.15f, t1.hpMult(), 1e-6);
        assertEquals(1f, t1.speedMult(), 0);
        assertEquals(1.08f, t3.speedMult(), 1e-6);
        assertEquals(120f, t3.eliteEarlySec(), 0);
        assertEquals(1f, t3.healMult(), 0);
        assertEquals(0.7f, t5.healMult(), 1e-6);
        assertEquals(1.25f, t5.merchantMult(), 1e-6);
        assertEquals(1.15f, t5.hpMult(), 1e-6); // earlier modifiers stay
    }

    @Test
    public void ascensionMakesWaveEnemiesTougherAndGoldWorthMore() throws Exception {
        GameData d = TestData.load();
        double[] hp = new double[2];
        for (int run = 0; run < 2; run++) {
            Sim s = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>(), null, 11L);
            s.applyMods(RunMods.ascension(run == 0 ? 0 : 3, 0, 0));
            s.noSpawns = false;
            Input in = new Input();
            for (int i = 0; i < 600; i++) {
                s.hp = s.maxHp;
                s.update(1f / 60f, in);
            }
            double sum = 0;
            for (int i = 0; i < s.enemyN; i++) sum += s.enemies[i].maxHp;
            hp[run] = sum / Math.max(1, s.enemyN);
            s.gold = 1000;
            assertEquals(run == 0 ? 1000 : 1450, s.endGold());
        }
        assertTrue("tier 3 enemies have more HP on average", hp[1] > hp[0] * 1.05);
    }

    @Test
    public void theFirstEliteComesTwoMinutesEarlierAndTheSalvagerCostsMore() throws Exception {
        GameData d = TestData.load();
        Sim a = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>(), null, 2L);
        Sim b = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>(), null, 2L);
        b.applyMods(RunMods.ascension(5, 0, 0));
        assertEquals(Math.max(30f, a.nextEliteAt - 120f), b.nextEliteAt, 1e-3);
        Sim c = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>(), null, 2L);
        c.applyMods(RunMods.ascension(5, 0, 0));
        // the Salvager's visit-2 card: +25%, rounded to 5 like the base prices
        assertEquals(Math.round(Merchant.priceOf(0, 2) * 1.25f / 5f) * 5, Math.round(Merchant.priceOf(0, 2) * c.mods.merchantMult() / 5f) * 5);
        assertEquals(1.25f, c.mods.merchantMult(), 1e-6);
    }

    // ------------------------------------------------------------------ 33, 34, 40: charges, refunds, tints

    @Test
    public void rerollAndBanishPriceChains() throws Exception {
        MetaProgress m = meta(new MetaProgressTest.MemStore());
        m.addGold(500 + 1000 + 2000 + 4000 + 8000 + 1500 + 3000 + 6000);
        int[] want = {500, 1000, 2000, 4000, 8000};
        for (int i = 0; i < 5; i++) {
            assertEquals(want[i], m.nextRerollPrice());
            assertTrue(m.buyReroll());
        }
        assertFalse(m.buyReroll());
        assertEquals(0, m.nextRerollPrice());
        int[] wantB = {1500, 3000, 6000};
        for (int i = 0; i < 3; i++) {
            assertEquals(wantB[i], m.nextBanishPrice());
            assertTrue(m.buyBanish());
        }
        assertFalse(m.buyBanish());
        assertEquals(0, m.gold);
        assertEquals(5, m.rerollCharges());
        RunMods r = RunMods.ascension(0, m.rerollCharges(), m.banishCharges());
        assertEquals(5, r.extraRerolls());
        assertEquals(3, r.extraBanishes());
    }

    @Test
    public void refundsReturnExactlyWhatWasPaid() throws Exception {
        MetaProgressTest.MemStore st = new MetaProgressTest.MemStore();
        MetaProgress m = meta(st);
        m.addGold(100000);
        int before = m.gold;
        assertEquals(0, m.totalSpent());
        assertTrue(m.buy("POWER"));
        assertTrue(m.buy("POWER"));
        assertTrue(m.buy("ARMOR"));
        assertTrue(m.buyReroll());
        assertTrue(m.buyReroll());
        assertTrue(m.buyBanish());
        assertTrue(m.buyTint("BASTION", 1));
        assertEquals(before - m.gold, m.totalSpent());
        assertTrue(m.totalSpent() > 0);
        while (m.refund("POWER") > 0) { }
        m.refund("ARMOR");
        while (m.refundReroll() > 0) { }
        m.refundBanish();
        assertTrue(m.refundTint("BASTION", 1) > 0);
        assertEquals("gold before equals gold after", before, m.gold);
        assertEquals(0, m.totalSpent());
        assertEquals(0, m.rank("POWER"));
        assertEquals(0, meta(st).rank("POWER")); // and in the save
        assertEquals(before, meta(st).gold);
        assertEquals(0, m.refund("POWER")); // nothing left to refund
    }

    @Test
    public void tintsAreBoughtWornAndAchievementTintsCannotBeRefunded() throws Exception {
        MetaProgressTest.MemStore st = new MetaProgressTest.MemStore();
        MetaProgress m = meta(st);
        assertEquals(0, m.tint("BASTION"));
        assertFalse(m.buyTint("BASTION", 0)); // no gold
        m.addGold(2000);
        assertTrue(m.buyTint("BASTION", 0));
        assertEquals(1200, m.gold);
        assertTrue(m.wearTint("BASTION", 0));
        assertEquals(MetaProgress.tintColor("BASTION", 0), m.tint("BASTION"));
        assertEquals(0, m.tint("SPECTRE")); // per hero
        assertEquals(MetaProgress.tintColor("BASTION", 0), meta(st).tint("BASTION"));
        assertFalse(m.wearTint("BASTION", 2)); // not owned
        assertNotEquals(MetaProgress.tintColor("BASTION", 0), MetaProgress.tintColor("BASTION", 1));
        m.grantTint("SPECTRE", 3);
        assertTrue(m.tintOwned("SPECTRE", 3));
        assertEquals(0, m.refundTint("SPECTRE", 3));
        assertTrue(m.tintOwned("SPECTRE", 3));
        assertEquals(800, m.refundTint("BASTION", 0));
        assertEquals(0, m.tint("BASTION")); // refunded and no longer worn
        assertEquals(2000, m.gold);
    }

    // ------------------------------------------------------------------ 35, 36: achievements

    private static Achievements.Facts win(String stage, String hero) {
        Achievements.Facts f = new Achievements.Facts();
        f.stage = stage;
        f.hero = hero;
        f.win = true;
        f.timeSec = 1800;
        f.kills = 500;
        f.level = 30;
        f.passives = 3;
        return f;
    }

    @Test
    public void achievementsUnlockOnScriptedCountersAndPayOnce() throws Exception {
        MetaProgress m = meta(new MetaProgressTest.MemStore());
        assertEquals(24, Achievements.COUNT);
        for (int i = 0; i < Achievements.COUNT; i++) {
            assertTrue(Achievements.NAME[i], Achievements.REWARD[i] >= 250 && Achievements.REWARD[i] <= 2000);
        }
        Achievements.Facts f = win("FLATS", "BASTION");
        int[] got = Achievements.check(m, f);
        // win Flats, win with Bastion, survive 10:00, survive 20:00
        assertEquals(4, got.length);
        int paid = 0;
        for (int id : got) paid += Achievements.REWARD[id];
        assertEquals(paid, m.gold);
        assertTrue(m.tintOwned("BASTION", 3)); // the hero achievement gives the 4th tint
        assertFalse(m.tintOwned("SPECTRE", 3));
        int gold = m.gold;
        assertEquals("a reward is claimed once", 0, Achievements.check(m, f).length);
        assertEquals(gold, m.gold);
        assertEquals(4, m.achCount());

        Achievements.Facts g = new Achievements.Facts();
        g.stage = "MINES";
        g.hero = "XENOMANT";
        g.timeSec = 1600;
        g.kills = 3200;
        g.evolutions = 3;
        g.passives = 0;
        g.level = 45;
        got = Achievements.check(m, g);
        // 1000 and 3000 kills, evolve one and three, 25 min without passives, level 40
        assertEquals(6, got.length);
        assertEquals(10, m.achCount());
    }

    @Test
    public void ascensionAndDailyAchievementsNeedAWin() throws Exception {
        MetaProgress m = meta(new MetaProgressTest.MemStore());
        Achievements.Facts f = new Achievements.Facts();
        f.stage = "FLATS";
        f.hero = "BASTION";
        f.tier = 5;
        f.daily = true;
        f.timeSec = 100;
        assertEquals(0, Achievements.check(m, f).length); // lost: nothing
        f.win = true;
        assertEquals(1 + 1 + 1 + 1 + 1 + 1, Achievements.check(m, f).length); // Flats, Bastion, tier 1, 3, 5, daily
    }

    @Test
    public void shopAchievementsFollowTheGoldInvested() throws Exception {
        MetaProgress m = meta(new MetaProgressTest.MemStore());
        m.addGold(20000);
        assertEquals(0, Achievements.checkShop(m).length);
        for (int i = 0; i < 5; i++) m.buyReroll();
        int[] got = Achievements.checkShop(m);
        assertEquals(1, got.length); // 500+1000+2000+4000 >= 5000
        assertEquals(0, Achievements.checkShop(m).length);
    }

    // ------------------------------------------------------------------ 39: records

    @Test
    public void recordsPerStageAndHeroAndTheNewRecordFlag() throws Exception {
        MetaProgressTest.MemStore st = new MetaProgressTest.MemStore();
        MetaProgress m = meta(st);
        Records r = m.records;
        assertTrue(r.submit("FLATS", "BASTION", 600, 200, false));
        assertTrue(r.wasRecord());
        assertTrue(r.wasTimeRecord() && r.wasKillsRecord() && !r.wasWinRecord());
        assertFalse(r.submit("FLATS", "BASTION", 500, 100, false)); // nothing beaten
        assertFalse(r.wasRecord());
        assertTrue(r.submit("FLATS", "BASTION", 550, 300, false)); // more kills only
        assertTrue(r.wasKillsRecord() && !r.wasTimeRecord());
        assertTrue(r.submit("FLATS", "SPECTRE", 100, 1, false)); // another hero has its own
        assertTrue(r.submit("FLATS", "BASTION", 1800, 400, true));
        assertTrue(r.wasWinRecord());
        assertEquals(1800, r.fastestWin("FLATS", "BASTION"));
        assertTrue(r.submit("FLATS", "BASTION", 1700, 100, true)); // a faster win
        assertEquals(1700, r.fastestWin("FLATS", "BASTION"));
        assertFalse(r.submit("FLATS", "BASTION", 1750, 100, true));
        assertEquals(1800, r.bestTime("FLATS", "BASTION"));
        assertEquals(400, meta(st).records.bestKills("FLATS", "BASTION"));
        assertEquals(0, r.fastestWin("MINES", "BASTION"));
    }

    // ------------------------------------------------------------------ 38: daily challenge

    @Test
    public void theSameDateGivesTheSameChallenge() {
        RunMods.Daily a = RunMods.dailyOf(20261108, 5, 3), b = RunMods.dailyOf(20261108, 5, 3);
        assertEquals(a.seed, b.seed);
        assertEquals(a.stage, b.stage);
        assertEquals(a.hero, b.hero);
        assertEquals(a.mod, b.mod);
        boolean differs = false;
        for (int d = 20261109; d < 20261140; d++) {
            RunMods.Daily o = RunMods.dailyOf(d, 5, 3);
            assertTrue(o.stage >= 0 && o.stage < 5 && o.hero >= 0 && o.hero < 3 && o.mod >= 0 && o.mod < 5);
            if (o.seed != a.seed) differs = true;
        }
        assertTrue(differs);
    }

    @Test
    public void theDailyBonusPaysOncePerDay() throws Exception {
        MetaProgress m = meta(new MetaProgressTest.MemStore());
        assertTrue(m.dailyBonusOpen(20261108));
        RunMods r = RunMods.daily(2, true, 0, 0);
        assertEquals(1.5f, r.goldMult(), 1e-6);
        assertTrue(r.has(RunMods.MOD_ELITE));
        assertEquals(1, Integer.bitCount(r.modMask));
        m.claimDaily(20261108);
        assertFalse(m.dailyBonusOpen(20261108));
        assertTrue(m.dailyBonusOpen(20261109));
        assertEquals(1f, RunMods.daily(2, false, 0, 0).goldMult(), 1e-6);
    }
}
