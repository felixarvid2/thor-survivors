package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;

import java.util.HashMap;

/** Salvage terminal texts, tap-to-select, and the HP display rounding. */
public class ShopTextTest {
    private static Describe.PowerUpText t(String key) throws Exception {
        return Describe.powerUp(TestData.load().powerUps.get(key));
    }

    @Test
    public void powerText() throws Exception {
        Describe.PowerUpText p = t("POWER");
        assertEquals("+5% DAMAGE / RANK", p.perRank);
        assertEquals("NOW -  NEXT +5%", p.totals[0]);
        assertEquals("NOW +10%  NEXT +15%", p.totals[2]);
        assertEquals("MAXED: +25%", p.totals[5]);
    }

    @Test
    public void unitsAndSpecialCases() throws Exception {
        assertEquals("-1 DAMAGE TAKEN / RANK", t("ARMOR").perRank);
        assertEquals("NOW -1  NEXT -2", t("ARMOR").totals[1]);
        assertEquals("+0.1 HP/S / RANK", t("REGEN").perRank);
        assertEquals("-2.5% COOLDOWN / RANK", t("COOLDOWN").perRank);
        assertEquals("+1 PROJECTILE", t("AMOUNT").perRank);
        assertEquals("REVIVE ONCE AT 50% HP", t("REVIVAL").perRank);
        assertEquals("MAXED: +1 REVIVE", t("REVIVAL").totals[1]);
        assertEquals("+10% ENEMY HP, SPEED & SPAWNS / RANK", t("CURSE").perRank);
    }

    @Test
    public void tapFocusesThenBuys() throws Exception {
        Engine e = Engine.get();
        GameData d = TestData.load();
        MetaProgress meta = new MetaProgress(d, new MetaProgressTest.MemStore());
        e.init(d, meta);
        e.meta = meta;
        meta.addGold(1000);
        e.input.clearMovement();
        e.phase = Engine.PH_SHOP;
        e.focus = 5;
        int i = e.shopKeys.indexOf("POWER");
        e.input.post(Input.CMD_TAP, Engine.PH_SHOP, i, 0);
        e.advance(1f / 60f);
        assertEquals(i, e.focus);
        assertEquals(0, meta.rank("POWER"));
        e.input.post(Input.CMD_TAP, Engine.PH_SHOP, i, 0);
        e.advance(1f / 60f);
        assertEquals(1, meta.rank("POWER"));
    }

    @Test
    public void shownHpNeverExceedsShownMax() {
        float max = 118.4f;
        assertEquals(118, Sim.shownMaxHp(max));
        assertEquals(118, Sim.shownHp(max, max)); // ceil(118.4) = 119 would exceed the max
        assertEquals(118, Sim.shownHp(max - 0.2f, max));
        assertEquals(60, Sim.shownHp(59.5f, max));
        float m2 = 118.6f;
        assertEquals(119, Sim.shownMaxHp(m2));
        assertTrue(Sim.shownHp(m2, m2) <= Sim.shownMaxHp(m2));
    }

    @Test
    public void maxHpDropClampsHp() throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get("FLATS"), null, new HashMap<String, Double>(), null, 5);
        s.maxHp = 1000f;
        s.hp = 1000f;
        s.recomputeAll();
        assertTrue(s.maxHp < 1000f);
        assertEquals(s.maxHp, s.hp, 1e-4);
    }
}
