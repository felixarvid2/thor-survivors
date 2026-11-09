package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;

/** v1.5 B4 through the real Engine: run start with modifiers, reroll and banish on the level-up screen, run end (records, tiers, daily, achievements), shop. */
public class EngineB4Test {
    /** The Engine is a JVM-wide singleton: leave it as the other tests expect it. */
    @After
    public void restore() {
        Engine e = Engine.get();
        e.dailyPick = false;
        e.ascTier = 0;
        e.dateOverride = 0;
        e.shopRefund = false;
        e.runMods = RunMods.NONE;
        e.phase = Engine.PH_MENU;
        e.selectStage("FLATS");
    }

    private Engine menu() throws Exception {
        Engine e = Engine.get();
        GameData d = TestData.load();
        MetaProgress fresh = new MetaProgress(d, new MetaProgressTest.MemStore());
        e.init(d, fresh);
        e.meta = fresh;
        fresh.unlockAll = true;
        e.input.clearMovement();
        e.phase = Engine.PH_MENU;
        e.focus = 0;
        e.stageOverride = null;
        e.stageIdx = 0;
        e.ascTier = 0;
        e.dailyPick = false;
        e.dateOverride = 0;
        e.shopRefund = false;
        return e;
    }

    private void press(Engine e, int cmd) {
        e.input.post(cmd, 0, 0, 0);
        e.advance(1f / 60f);
    }

    private void start(Engine e, Long seed) {
        e.nextRunSeed = seed;
        e.focus = Engine.MENU_START;
        press(e, Input.CMD_CONFIRM);
        assertEquals(Engine.PH_PLAYING, e.phase);
        e.sim.noSpawns = true;
    }

    private void levelUp(Engine e) {
        e.sim.pendingLevelUps = 1;
        e.advance(1f / 60f);
        assertEquals(Engine.PH_LEVELUP, e.phase);
    }

    @Test
    public void rerollAndBanishOnTheLevelUpScreen() throws Exception {
        Engine e = menu();
        e.meta.addGold(5000);
        assertTrue(e.meta.buyReroll());
        assertTrue(e.meta.buyReroll());
        assertTrue(e.meta.buyBanish());
        start(e, 5L);
        assertEquals(2, e.rerollsLeft);
        assertEquals(1, e.banishesLeft);
        assertTrue(e.levelUpTools());
        levelUp(e);
        int n = e.offers.size();
        java.util.List<Offer> before = e.offers;
        e.focus = n; // REROLL
        press(e, Input.CMD_CONFIRM);
        assertEquals(1, e.rerollsLeft);
        assertNotSame(before, e.offers);
        assertEquals(Engine.PH_LEVELUP, e.phase);

        e.focus = e.offers.size() + 1; // BANISH, then the first card
        press(e, Input.CMD_CONFIRM);
        assertTrue(e.banishMode);
        Offer struck = e.offers.get(0);
        String key = struck.key();
        e.focus = 0;
        press(e, Input.CMD_CONFIRM);
        assertFalse(e.banishMode);
        assertEquals(0, e.banishesLeft);
        assertEquals("a banish costs no level-up", Engine.PH_LEVELUP, e.phase);
        for (Offer o : e.offers) assertFalse(key.equals(o.key()));
        e.focus = e.offers.size() + 1; // no banish left: refused
        press(e, Input.CMD_CONFIRM);
        assertFalse(e.banishMode);
        for (int i = 0; i < 40 && e.rerollsLeft > 0; i++) {
            e.focus = e.offers.size();
            press(e, Input.CMD_CONFIRM);
            for (Offer o : e.offers) assertFalse(key.equals(o.key()));
        }
        assertEquals(0, e.rerollsLeft);
        e.focus = e.offers.size(); // no reroll left: refused, cards unchanged
        java.util.List<Offer> same = e.offers;
        press(e, Input.CMD_CONFIRM);
        assertEquals(same, e.offers);
        e.focus = 0;
        press(e, Input.CMD_CONFIRM);
        assertEquals(Engine.PH_PLAYING, e.phase);
    }

    @Test
    public void noChargesNoButtons() throws Exception {
        Engine e = menu();
        start(e, 6L);
        assertFalse(e.levelUpTools());
        levelUp(e);
        assertTrue(e.offers.size() >= 3);
        e.focus = 0;
        press(e, Input.CMD_CONFIRM);
        assertEquals(Engine.PH_PLAYING, e.phase);
    }

    @Test
    public void ascensionMenuRowCyclesTheOpenTiersThenTheDaily() throws Exception {
        Engine e = menu();
        String stage = e.stage().key;
        e.meta.recordAscWin(stage, 0);
        e.meta.recordAscWin(stage, 1); // tiers 0..2 open
        assertEquals("OFF", e.ascLabel());
        e.focus = Engine.MENU_ASC;
        press(e, Input.CMD_CONFIRM); // confirm steps +1
        assertEquals(1, e.ascTier);
        assertTrue(e.ascLabel().startsWith("TIER 1"));
        press(e, Input.CMD_CONFIRM);
        press(e, Input.CMD_CONFIRM);
        assertEquals(2, e.ascTier);
        assertTrue(e.dailyPick); // after the open tiers comes the daily challenge
        assertTrue(e.ascLabel().startsWith("DAILY"));
        press(e, Input.CMD_CONFIRM);
        assertFalse(e.dailyPick);
        assertEquals(0, e.ascTier); // wrapped to tier 0
    }

    @Test
    public void aRunOnATierStartsWithItsModifiersAndAWinOpensTheNextTier() throws Exception {
        Engine e = menu();
        String stage = e.stage().key;
        e.meta.recordAscWin(stage, 0);
        e.meta.recordAscWin(stage, 1);
        e.ascTier = 2;
        start(e, 7L);
        assertEquals(2, e.runMods.tier);
        assertEquals(e.runMods, e.sim.mods);
        assertEquals(1.15f, e.sim.mods.hpMult(), 1e-6);
        e.sim.time = 1800;
        e.sim.kills = 1200;
        e.sim.gold = 1000;
        e.sim.ended = 2;
        e.advance(1f / 60f);
        assertEquals(Engine.PH_RESULT, e.phase);
        assertEquals(1300, e.resultGold); // +15% per tier
        assertEquals(3, e.meta.ascMax(stage));
        assertTrue(e.resultRecord);
        assertTrue(e.resultAch.length > 0);
        assertTrue(e.resultAch[0].startsWith("ACHIEVEMENT"));
        assertTrue(e.meta.records.wasRecord());
        assertEquals(1800, e.meta.records.fastestWin(stage, "BASTION"));
    }

    @Test
    public void theDailyChallengeIsTheSameAllDayAndPaysItsBonusOnce() throws Exception {
        Engine e = menu();
        e.dateOverride = 20270102;
        e.dailyPick = true;
        start(e, null);
        long seed1 = e.sim.runSeed;
        String stage1 = e.sim.stage.key, hero1 = e.sim.hero.key;
        assertTrue(e.runMods.daily);
        assertTrue(e.runMods.dailyBonus);
        assertEquals(1, Integer.bitCount(e.runMods.modMask));
        e.sim.gold = 1000;
        e.sim.ended = 1;
        e.advance(1f / 60f);
        assertEquals(1500, e.resultGold);
        assertFalse(e.meta.dailyBonusOpen(20270102));
        e.phase = Engine.PH_MENU;
        e.focus = Engine.MENU_START;
        press(e, Input.CMD_CONFIRM); // the same day again: same challenge, no bonus
        assertEquals(Engine.PH_PLAYING, e.phase);
        assertEquals(seed1, e.sim.runSeed);
        assertEquals(stage1, e.sim.stage.key);
        assertEquals(hero1, e.sim.hero.key);
        assertFalse(e.runMods.dailyBonus);
        e.sim.gold = 1000;
        e.sim.ended = 1;
        e.advance(1f / 60f);
        assertEquals(1000, e.resultGold);
    }

    @Test
    public void shopRefundAndPurchasesThroughTheTerminal() throws Exception {
        Engine e = menu();
        e.meta.addGold(10000);
        int gold = e.meta.gold;
        e.phase = Engine.PH_SHOP;
        e.focus = 0;
        press(e, Input.CMD_CONFIRM); // buys power-up 0
        assertEquals(1, e.meta.rank(e.shopKeys.get(0)));
        e.focus = e.shopKeys.size() + 1; // the REFUND toggle
        press(e, Input.CMD_CONFIRM);
        assertTrue(e.shopRefund);
        e.focus = 0;
        press(e, Input.CMD_CONFIRM);
        assertEquals(0, e.meta.rank(e.shopKeys.get(0)));
        assertEquals(gold, e.meta.gold);
        // a category tab jumps to its first page
        e.input.post(Input.CMD_TAP, Engine.PH_SHOP, e.shopKeys.size() + 4 + ShopCatalog.RUN, 0);
        e.advance(1f / 60f);
        assertEquals(ShopCatalog.RUN, e.catalog.entries.get(e.focus).cat);
        e.shopRefund = false;
        press(e, Input.CMD_CONFIRM); // reroll charge
        assertEquals(1, e.meta.rerollCharges());
        assertNotNull(e.catalog);
        for (int p = 0; p < e.catalog.pages(); p++) assertTrue(e.catalog.pageLen[p] > 0 && e.catalog.pageLen[p] <= Engine.SHOP_PAGE);
        // the shoulder buttons walk through every page, categories included
        e.focus = 0;
        e.shopPage = 0;
        int pages = 1;
        for (int i = 0; i < 20; i++) {
            int before = e.focus;
            e.input.post(Input.CMD_NAV, Input.PAGE_NEXT, 0, 0);
            e.advance(1f / 60f);
            if (e.focus == before) break;
            pages++;
        }
        assertEquals(e.catalog.pages(), pages);
        assertEquals(ShopCatalog.AWARDS, e.catalog.entries.get(e.focus).cat);
        e.phase = Engine.PH_MENU;
    }
}
