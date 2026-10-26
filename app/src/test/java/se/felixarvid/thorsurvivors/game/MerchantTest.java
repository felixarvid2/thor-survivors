package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.EquipDef;
import se.felixarvid.thorsurvivors.data.GameData;

import java.util.HashMap;

/**
 * The Salvager (merchant): schedule and stay, spawn, own random, the shop phase and its pause, stock, buying and prices.
 * Sim-level tests step the Sim directly; the phase tests go through the real Engine like {@code EquipmentTest}.
 */
public class MerchantTest {
    private static final float DT = 1f / 60f;

    private static Sim sim(String stage, long seed) throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get(stage), "WHIP", new HashMap<String, Double>(), null, seed);
        s.noSpawns = true;
        s.enemyN = 0;
        return s;
    }

    /** Runs the Sim in steps of {@code dt} seconds up to the run time {@code t}, the hero standing still. */
    private static void run(Sim s, double t, float dt) {
        Input in = new Input();
        while (s.time < t) {
            s.hp = s.maxHp;
            s.update(dt, in);
        }
    }

    private static boolean toasted(Sim s, String text) {
        for (int i = 0; i < Sim.MAX_TOASTS; i++) if (text.equals(s.toastText[i]) && s.toastT[i] > 0f) return true;
        return false;
    }

    // ------------------------------------------------------------------ schedule, spawn

    @Test
    public void arrivesAtFourThirtyAndEveryFiveMinutesAndStaysSeventyFiveSeconds() throws Exception {
        for (String stage : new String[]{"FLATS", "MINES"}) {
            Sim s = sim(stage, 3);
            Merchant m = s.merchant;
            double[] arrive = new double[5], leave = new double[5];
            int visit = -1;
            boolean was = false;
            Input in = new Input();
            while (s.time < 1600 && s.ended == 0) {
                s.hp = s.maxHp;
                s.update(0.1f, in);
                if (m.present && !was) {
                    visit++;
                    arrive[visit] = s.time;
                } else if (!m.present && was) {
                    leave[visit] = s.time;
                }
                was = m.present;
            }
            assertEquals(stage, 4, visit);
            for (int i = 0; i < 5; i++) {
                assertEquals(stage + " arrival " + i, 270.0 + 300.0 * i, arrive[i], 0.2);
                assertEquals(stage + " stay " + i, 75.0, leave[i] - arrive[i], 0.2);
            }
            assertEquals(5, m.visits);
        }
    }

    @Test
    public void arrivalAndPackingUpAreToastedAndTheArrivalHasASound() throws Exception {
        Sim s = sim("FLATS", 4);
        run(s, 269.0, 0.25f);
        assertFalse(s.merchant.present);
        int chest = s.sound.emitted[SoundEvents.CHEST_OPEN];
        run(s, 270.3, 0.1f);
        assertTrue(s.merchant.present);
        assertTrue(toasted(s, "A SALVAGER HAS ARRIVED"));
        assertTrue("the chest sound, no new audio", s.sound.emitted[SoundEvents.CHEST_OPEN] > chest);
        assertFalse(toasted(s, "The Salvager is packing up"));
        run(s, 270 + 75 - 14.0, 0.25f);
        assertTrue(toasted(s, "The Salvager is packing up"));
        run(s, 346.0, 0.25f);
        assertFalse(s.merchant.present);
    }

    @Test
    public void spawnsThreeHundredFiftyToFourHundredFiftyUnitsAwayOnAFreeSpot() throws Exception {
        for (String stage : new String[]{"FLATS", "MINES"}) {
            for (long seed = 1; seed <= 12; seed++) {
                Sim s = sim(stage, seed);
                s.px = 120f * seed - 700f;
                s.py = 900f - 130f * seed;
                run(s, 271, 0.25f);
                Merchant m = s.merchant;
                assertTrue(stage + " " + seed, m.present);
                double d = Math.hypot(m.x - s.px, m.y - s.py);
                assertTrue("distance " + d, d >= 349.9 && d <= 450.1);
                assertFalse(s.map.blocked(m.x, m.y));
                assertFalse(s.map.blocked(m.x + 22f, m.y));
                assertFalse(s.map.blocked(m.x, m.y - 22f));
            }
        }
    }

    @Test
    public void theSameSeedGivesTheSameSpotAndStock() throws Exception {
        Sim a = sim("MINES", 21), b = sim("MINES", 21), c = sim("MINES", 22);
        run(a, 271, 0.25f);
        run(b, 271, 0.25f);
        run(c, 271, 0.25f);
        assertEquals(a.merchant.x, b.merchant.x, 0f);
        assertEquals(a.merchant.y, b.merchant.y, 0f);
        assertEquals(a.merchant.name[1], b.merchant.name[1]);
        assertEquals(a.merchant.name[0], b.merchant.name[0]);
        assertTrue("another seed, another place", a.merchant.x != c.merchant.x || a.merchant.y != c.merchant.y);
    }

    @Test
    public void legacyStagesHaveNoMerchant() throws Exception {
        for (String stage : new String[]{"FOREST", "LIBRARY"}) {
            Sim s = sim(stage, 5);
            assertFalse(s.merchant.enabled);
            run(s, 1700, 0.25f);
            assertFalse(s.merchant.present);
            assertEquals(0, s.merchant.visits);
        }
    }

    @Test
    public void theMerchantNeverTouchesTheRunRandom() throws Exception {
        Sim with = sim("FLATS", 9), without = sim("FLATS", 9);
        without.merchant.enabled = false;
        run(with, 700, 0.25f);
        run(without, 700, 0.25f);
        assertTrue(with.merchant.visits > 0);
        assertEquals(without.rng.nextLong(), with.rng.nextLong());
    }

    // ------------------------------------------------------------------ stock and prices

    @Test
    public void stockFollowsTheVisit() throws Exception {
        Sim s = sim("FLATS", 6);
        Merchant m = s.merchant;
        int[] supply = {Merchant.SUP_MEDKIT, Merchant.SUP_VACUUM, Merchant.SUP_MEDKIT, Merchant.SUP_OROLOGION, Merchant.SUP_MEDKIT};
        for (int v = 0; v < 5; v++) {
            run(s, Merchant.ARRIVE[v] + 1, 0.25f);
            assertTrue(m.present);
            assertEquals(v, m.visitIndex());
            assertEquals("supply of visit " + v, supply[v], m.supply);
            assertEquals(v >= 2 ? Equipment.RARE : Equipment.UNCOMMON, m.gearRar);
            assertNotNull(m.gearDef);
            assertFalse("a base item, never a unique", m.gearDef.unique);
            assertNotNull(m.offer);
            assertFalse("no evolutions", m.offer.def != null && m.offer.def.evolution);
            assertTrue("no branches", m.offer.kind != Offer.BRANCH);
            for (int i = 0; i < Merchant.SLOTS; i++) {
                assertFalse(m.sold[i]);
                assertEquals(Merchant.priceOf(i, v), m.price[i]);
                assertEquals(Integer.toString(m.price[i]), m.priceText[i]);
                assertNotNull(m.name[i]);
                assertNotNull(m.desc[i]);
            }
            assertEquals("the card text is the level-up card text", Describe.offer(m.offer), m.desc[0]);
            assertEquals("Field Medkit".equals(m.name[2]), supply[v] == Merchant.SUP_MEDKIT);
            run(s, Merchant.ARRIVE[v] + 80, 0.25f);
            assertFalse(m.present);
        }
    }

    @Test
    public void pricesRiseWithEveryVisit() {
        for (int slot = 0; slot < Merchant.SLOTS; slot++) {
            for (int v = 1; v < 5; v++) assertTrue(Merchant.priceOf(slot, v) > Merchant.priceOf(slot, v - 1));
        }
        assertTrue("the supply is the cheapest, the gear the dearest", Merchant.priceOf(2, 0) < Merchant.priceOf(0, 0)
                && Merchant.priceOf(0, 0) < Merchant.priceOf(1, 0));
    }

    // ------------------------------------------------------------------ buying (Sim level)

    @Test
    public void buyingTheWeaponOrPassiveCardAppliesTheOfferAndTakesTheGold() throws Exception {
        Sim s = sim("FLATS", 8);
        run(s, 271, 0.25f);
        Merchant m = s.merchant;
        Offer o = m.offer;
        int before = o.def == null ? 0 : s.inv.levelOf(o.def.key), price = m.price[0];
        s.gold = 500;
        assertEquals(Merchant.BUY_OK, m.buy(0));
        assertEquals(500 - price, s.gold);
        assertTrue(m.sold[0]);
        assertEquals("SOLD", m.priceText[0]);
        assertEquals(before + 1, s.inv.levelOf(o.def.key));
        assertEquals("a second purchase is refused and free", Merchant.BUY_SOLD, m.buy(0));
        assertEquals(500 - price, s.gold);
    }

    @Test
    public void buyingTheGearAcquiresTheItem() throws Exception {
        Sim s = sim("FLATS", 8);
        run(s, 271, 0.25f);
        Merchant m = s.merchant;
        EquipDef d = m.gearDef;
        s.gold = 500;
        int price = m.price[1];
        assertEquals(Merchant.BUY_OK, m.buy(1));
        assertEquals(500 - price, s.gold);
        assertEquals("an empty slot is worn at once", d, s.equip.def[d.slot]);
        assertEquals(Equipment.UNCOMMON, s.equip.rar[d.slot]);
        assertTrue(toasted(s, "PURCHASED  " + m.name[1]));
    }

    @Test
    public void gearBetterThanWornQueuesTheKeepReplaceChoice() throws Exception {
        Sim s = sim("FLATS", 8);
        for (EquipDef d : s.data.baseItems) s.equip.def[d.slot] = d; // every slot worn at Common
        for (int i = 0; i < EquipDef.SLOTS; i++) s.equip.rar[i] = Equipment.COMMON;
        run(s, 271, 0.25f);
        s.gold = 500;
        assertEquals(Merchant.BUY_OK, s.merchant.buy(1));
        assertEquals("an Uncommon item beats the Common one: it waits for the choice", 1, s.equipN);
    }

    @Test
    public void theMedkitHealsHalfOfMaxHp() throws Exception {
        Sim s = sim("FLATS", 8);
        run(s, 271, 0.25f);
        Merchant m = s.merchant;
        assertEquals(Merchant.SUP_MEDKIT, m.supply);
        s.hp = 10f;
        s.gold = 500;
        float expect = 10f + s.maxHp * 0.5f;
        assertEquals(Merchant.BUY_OK, m.buy(2));
        assertEquals(expect, s.hp, 0.01f);
    }

    @Test
    public void vacuumPullsTheGemsAndOrologionFreezes() throws Exception {
        Sim s = sim("FLATS", 8);
        run(s, 571, 0.25f);
        Merchant m = s.merchant;
        assertEquals(Merchant.SUP_VACUUM, m.supply);
        s.addPickupForTest(Entities.K_GEM, s.px + 500f, s.py, 1f);
        s.gold = 5000;
        assertEquals(Merchant.BUY_OK, m.buy(2));
        boolean pulled = false;
        for (int i = 0; i < s.pickN; i++) if (s.picks[i].kind == Entities.K_GEM && s.picks[i].pulled) pulled = true;
        assertTrue(pulled);
        run(s, 1171, 0.25f);
        assertEquals(Merchant.SUP_OROLOGION, m.supply);
        s.freezeTimer = 0f;
        assertEquals(Merchant.BUY_OK, m.buy(2));
        assertTrue("the Orologion freezes the enemies", s.freezeTimer > 5f);
    }

    @Test
    public void unaffordableCardsAreNotSold() throws Exception {
        Sim s = sim("FLATS", 8);
        run(s, 271, 0.25f);
        Merchant m = s.merchant;
        for (int i = 0; i < Merchant.SLOTS; i++) {
            s.gold = m.price[i] - 1;
            assertEquals(Merchant.BUY_POOR, m.buy(i));
            assertEquals(m.price[i] - 1, s.gold);
            assertFalse(m.sold[i]);
        }
        assertEquals(0, s.equipN);
    }

    @Test
    public void aStaleCardIsRolledAgainWhenTheShopOpens() throws Exception {
        for (long seed = 1; seed <= 30; seed++) {
            Sim s = sim("FLATS", seed);
            run(s, 271, 0.25f);
            Merchant m = s.merchant;
            Offer o = m.offer;
            if (o.kind != Offer.NEW_WEAPON && o.kind != Offer.NEW_PASSIVE) continue;
            s.inv.add(o.def); // a find or a level-up took it first
            m.opened();
            assertTrue("rolled again", m.offer != o);
            assertTrue(s.inv.levelOf(m.offer.def == null ? "" : m.offer.def.key) != 1 || m.offer.kind != Offer.NEW_WEAPON
                    && m.offer.kind != Offer.NEW_PASSIVE);
            assertEquals(Describe.offer(m.offer), m.desc[0]);
            return;
        }
        throw new AssertionError("no seed offered a new weapon or passive");
    }

    // ------------------------------------------------------------------ the shop phase (Engine)

    private static Engine startRun() throws Exception {
        GameData d = TestData.load();
        Engine e = Engine.get();
        MetaProgress fresh = new MetaProgress(d, new MetaProgressTest.MemStore());
        e.init(d, fresh);
        e.meta = fresh;
        e.input.clearMovement();
        e.phase = Engine.PH_MENU;
        e.focus = Engine.MENU_START;
        e.selectStage("FLATS");
        e.nextRunSeed = 4L;
        e.input.post(Input.CMD_START, 0, 0, 0);
        e.advance(DT);
        assertEquals(Engine.PH_PLAYING, e.phase);
        Sim s = e.sim;
        s.noSpawns = true;
        s.enemyN = 0;
        return e;
    }

    private static void frames(Engine e, int n) {
        for (int i = 0; i < n; i++) {
            e.sim.hp = Math.max(e.sim.hp, 1f);
            e.advance(DT);
        }
    }

    /** Jumps to the first arrival and walks the hero onto the Salvager. */
    private static Sim arrive(Engine e) {
        Sim s = e.sim;
        s.time = 269.9;
        frames(e, 12);
        assertTrue(s.merchant.present);
        return s;
    }

    private static void walkOnto(Sim s) {
        s.px = s.merchant.x + 10f;
        s.py = s.merchant.y;
    }

    @Test
    public void walkingUpOpensThePausedShopAndBClosesIt() throws Exception {
        Engine e = startRun();
        Sim s = arrive(e);
        assertEquals("far away: no shop", Engine.PH_PLAYING, e.phase);
        walkOnto(s);
        frames(e, 2);
        assertEquals(Engine.PH_MERCHANT, e.phase);
        assertEquals(0, e.focus);
        double t0 = s.time;
        frames(e, 30);
        assertEquals("paused: no time passes", t0, s.time, 0.0);
        e.input.post(Input.CMD_BACK, 0, 0, 0);
        frames(e, 1);
        assertEquals(Engine.PH_PLAYING, e.phase);
        frames(e, 30);
        assertEquals("it does not reopen while the hero stays", Engine.PH_PLAYING, e.phase);
    }

    @Test
    public void leavingTheRadiusAndComingBackReopensIt() throws Exception {
        Engine e = startRun();
        Sim s = arrive(e);
        walkOnto(s);
        frames(e, 2);
        e.input.post(Input.CMD_BACK, 0, 0, 0);
        frames(e, 1);
        assertEquals(Engine.PH_PLAYING, e.phase);
        s.px = s.merchant.x + Merchant.TALK_R + 10f; // outside the radius but inside the hysteresis
        frames(e, 5);
        s.px = s.merchant.x + 10f;
        frames(e, 5);
        assertEquals("no reopening without leaving the hysteresis band", Engine.PH_PLAYING, e.phase);
        s.px = s.merchant.x + Merchant.TALK_R + Merchant.HYST + 15f;
        frames(e, 5);
        s.px = s.merchant.x + 10f;
        frames(e, 2);
        assertEquals(Engine.PH_MERCHANT, e.phase);
    }

    @Test
    public void thePendingLevelUpComesBeforeTheShop() throws Exception {
        Engine e = startRun();
        Sim s = arrive(e);
        walkOnto(s);
        s.pendingLevelUps = 1;
        frames(e, 2);
        assertEquals(Engine.PH_LEVELUP, e.phase);
        e.input.post(Input.CMD_CONFIRM, 0, 0, 0);
        frames(e, 2);
        assertEquals("the shop opens once nothing is pending", Engine.PH_MERCHANT, e.phase);
    }

    @Test
    public void controlsBuyFocusAndLeave() throws Exception {
        Engine e = startRun();
        Sim s = arrive(e);
        Merchant m = s.merchant;
        walkOnto(s);
        frames(e, 2);
        assertEquals(Engine.PH_MERCHANT, e.phase);
        s.gold = 1000;
        s.hp = 5f;
        // gamepad: right moves focus (wraps over 3 cards + LEAVE), A buys the focused card
        e.input.post(Input.CMD_NAV, Input.RIGHT, 0, 0);
        e.input.post(Input.CMD_NAV, Input.DOWN, 0, 0);
        frames(e, 1);
        assertEquals(2, e.focus);
        e.input.post(Input.CMD_CONFIRM, 0, 0, 0);
        frames(e, 1);
        assertTrue(m.sold[2]);
        assertEquals("the Medkit healed", 5f + s.maxHp * 0.5f, s.hp, 0.5f);
        assertEquals(1000 - m.price[2], s.gold);
        assertEquals("the shop stays open after a purchase", Engine.PH_MERCHANT, e.phase);
        // touch: the first tap on another card only focuses it, the second buys
        int g = s.gold;
        e.input.post(Input.CMD_TAP, Engine.PH_MERCHANT, 0, 0);
        frames(e, 1);
        assertEquals(0, e.focus);
        assertEquals("first tap: no purchase", g, s.gold);
        assertFalse(m.sold[0]);
        e.input.post(Input.CMD_TAP, Engine.PH_MERCHANT, 0, 0);
        frames(e, 1);
        assertTrue(m.sold[0]);
        assertEquals(g - m.price[0], s.gold);
        // sold and unaffordable toast, no charge
        e.input.post(Input.CMD_CONFIRM, 0, 0, 0);
        frames(e, 1);
        assertEquals("Already sold", e.toast);
        s.gold = 0;
        e.input.post(Input.CMD_NAV, Input.RIGHT, 0, 0);
        e.input.post(Input.CMD_CONFIRM, 0, 0, 0);
        frames(e, 1);
        assertEquals("Not enough gold", e.toast);
        assertFalse(m.sold[1]);
        assertEquals(0, s.gold);
        // a tap on LEAVE closes at once
        e.input.post(Input.CMD_TAP, Engine.PH_MERCHANT, Merchant.SLOTS, 0);
        frames(e, 1);
        assertEquals(Engine.PH_PLAYING, e.phase);
    }

    @Test
    public void gearThatNeedsAChoiceChainsIntoTheEquipScreenAfterTheShop() throws Exception {
        Engine e = startRun();
        Sim s = arrive(e);
        for (EquipDef d : s.data.baseItems) s.equip.def[d.slot] = d;
        for (int i = 0; i < EquipDef.SLOTS; i++) s.equip.rar[i] = Equipment.COMMON;
        s.recomputeAll();
        walkOnto(s);
        s.gold = 1000;
        frames(e, 2);
        assertEquals(Engine.PH_MERCHANT, e.phase);
        e.input.post(Input.CMD_NAV, Input.RIGHT, 0, 0);
        e.input.post(Input.CMD_CONFIRM, 0, 0, 0);
        frames(e, 1);
        assertEquals("still in the shop while the choice waits", Engine.PH_MERCHANT, e.phase);
        assertEquals(1, s.equipN);
        e.input.post(Input.CMD_BACK, 0, 0, 0);
        frames(e, 2);
        assertEquals(Engine.PH_EQUIP, e.phase);
        e.input.post(Input.CMD_CONFIRM, 0, 0, 0);
        frames(e, 2);
        assertEquals("Replace was taken, back to the run", Engine.PH_PLAYING, e.phase);
        frames(e, 30);
        assertEquals("and the shop does not reopen under the hero", Engine.PH_PLAYING, e.phase);
    }

    @Test
    public void theMerchantLeavesAndTheShopCannotOpenAfterIt() throws Exception {
        Engine e = startRun();
        Sim s = arrive(e);
        s.time = s.merchant.leaveAt + 0.1;
        frames(e, 2);
        assertFalse(s.merchant.present);
        walkOnto(s);
        frames(e, 5);
        assertEquals(Engine.PH_PLAYING, e.phase);
    }
}
