package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.HashMap;

/** v1.5 batch 3: settings, button map, run stats, aim assist, tips, tutorial, codex and the menu flow (items 21 to 30). */
public class MenusB3Test {
    // ------------------------------------------------------------------ item 21: settings

    @Test
    public void defaultsKeepTheOldBehaviour() {
        Settings st = new Settings();
        // a fresh instance is not loaded: the same defaults as a load from an empty store
        MetaProgressTest.MemStore ms = new MetaProgressTest.MemStore();
        Settings.get().load(ms);
        st = Settings.get();
        assertEquals(1f, st.shake(), 0f);
        assertEquals(Settings.NUM_ALL, st.damageNumbers());
        assertTrue(st.showDamageNumber(false) && st.showDamageNumber(true));
        assertFalse(st.largeText());
        assertEquals(1f, st.textScale(), 0f);
        assertFalse(st.colorblindShapes());
        assertFalse(st.hatched());
        assertEquals(0xFF123456, st.telegraphFill(0xFF123456));
        assertEquals(0xFF123456, st.telegraphEdge(0xFF123456));
        assertEquals(Settings.AIM_OFF, st.autoAim());
        assertTrue(st.haptics());
        assertTrue(st.buttons.isDefault());
    }

    @Test
    public void settingsRoundTripThroughTheStore() {
        MetaProgressTest.MemStore ms = new MetaProgressTest.MemStore();
        Settings st = Settings.get();
        st.load(ms);
        st.stepShake(-1);
        assertEquals(0.5f, st.shake(), 0f);
        st.stepShake(-1);
        assertEquals(0f, st.shake(), 0f);
        st.stepShake(-1); // wraps
        assertEquals(1f, st.shake(), 0f);
        st.setShake(Settings.SHAKE_HALF);
        st.setNumbers(Settings.NUM_CRITS);
        st.setShapes(true);
        st.setLarge(true);
        st.setAim(Settings.AIM_FULL);
        st.setHaptics(false);
        assertTrue(st.buttons.bind(ButtonMap.CONFIRM, ButtonMap.X) == ButtonMap.BIND_OK);
        // a second instance of the app reads the same store
        st.load(null); // forget (memory only)
        st.load(ms);
        assertEquals(0.5f, st.shake(), 0f);
        assertEquals(Settings.NUM_CRITS, st.damageNumbers());
        assertTrue(st.showDamageNumber(true));
        assertFalse(st.showDamageNumber(false));
        assertTrue(st.colorblindShapes() && st.hatched());
        assertEquals(Settings.SHAPES_FILL, st.telegraphFill(0xFF000000));
        assertEquals(Settings.SHAPES_EDGE, st.telegraphEdge(0xFF000000));
        assertEquals(2f, st.telegraphEdgeMul(), 0f);
        assertTrue(st.largeText());
        assertEquals(Settings.LARGE_TEXT, st.textScale(), 0f);
        assertEquals(Settings.AIM_FULL, st.autoAim());
        assertFalse(st.haptics());
        assertEquals(ButtonMap.X, st.buttons.code(ButtonMap.CONFIRM));
        st.resetAll();
        st.load(ms);
        assertEquals(1f, st.shake(), 0f);
        assertTrue(st.buttons.isDefault());
        assertTrue(st.haptics());
    }

    @Test
    public void volumeGettersFollowTheMenuSteps() throws Exception {
        Engine e = Engine.get();
        GameData d = TestData.load();
        MetaProgress mp = new MetaProgress(d, new MetaProgressTest.MemStore());
        e.init(d, mp);
        e.meta = mp;
        mp.musicVol = 0;
        mp.sfxVol = 3;
        assertEquals(0f, Settings.get().musicVol(), 0f);
        assertEquals(1f, Settings.get().sfxVol(), 0f);
        mp.musicVol = 2;
        assertEquals(SoundEvents.stepGain(2), Settings.get().musicVol(), 0f);
    }

    // ------------------------------------------------------------------ item 29: button map

    @Test
    public void buttonMapDefaultsAndLookups() {
        ButtonMap bm = new ButtonMap();
        bm.load(null);
        assertEquals(ButtonMap.CONFIRM, bm.action(ButtonMap.A, false));
        assertEquals(ButtonMap.BACK, bm.action(ButtonMap.B, false));
        assertEquals(ButtonMap.PAUSE, bm.action(ButtonMap.START, false));
        assertEquals(ButtonMap.PAUSE, bm.action(ButtonMap.START, true));
        assertEquals(-1, bm.action(ButtonMap.X, false));
        // while playing every face and shoulder button dashes, as before the remap
        for (int c : new int[]{ButtonMap.A, ButtonMap.B, ButtonMap.X, ButtonMap.Y, ButtonMap.L1, ButtonMap.R1, ButtonMap.L2, ButtonMap.R2}) {
            assertEquals(ButtonMap.DASH, bm.action(c, true));
        }
        assertEquals(-1, bm.action(ButtonMap.SELECT, true));
        assertTrue(bm.legacyDash());
    }

    @Test
    public void buttonMapRejectsConflictsAndAcceptsFreeButtons() {
        ButtonMap bm = new ButtonMap();
        bm.load(null);
        // confirm on B clashes with back (same menu screen)
        assertEquals(ButtonMap.BACK, bm.bind(ButtonMap.CONFIRM, ButtonMap.B));
        assertEquals(ButtonMap.A, bm.code(ButtonMap.CONFIRM));
        // pause clashes with confirm and with dash alike
        assertEquals(ButtonMap.CONFIRM, bm.bind(ButtonMap.PAUSE, ButtonMap.A));
        assertEquals(ButtonMap.BIND_BAD, bm.bind(ButtonMap.DASH, 12345));
        // dash and confirm may share A (menu versus play)
        assertEquals(ButtonMap.BIND_OK, bm.bind(ButtonMap.DASH, ButtonMap.A));
        // a free button
        assertEquals(ButtonMap.BIND_OK, bm.bind(ButtonMap.DASH, ButtonMap.R2));
        assertFalse(bm.legacyDash());
        assertEquals(ButtonMap.DASH, bm.action(ButtonMap.R2, true));
        assertEquals(-1, bm.action(ButtonMap.A, true)); // A no longer dashes
        assertEquals(ButtonMap.CONFIRM, bm.action(ButtonMap.A, false));
        // dash on the start button clashes with pause
        assertEquals(ButtonMap.PAUSE, bm.bind(ButtonMap.DASH, ButtonMap.START));
        // moving pause away frees START
        assertEquals(ButtonMap.BIND_OK, bm.bind(ButtonMap.PAUSE, ButtonMap.SELECT));
        assertEquals(ButtonMap.PAUSE, bm.action(ButtonMap.SELECT, false));
        assertEquals(-1, bm.action(ButtonMap.START, false));
        bm.reset();
        assertTrue(bm.isDefault());
        assertTrue(bm.legacyDash());
    }

    @Test
    public void buttonMapPersistsAndResets() {
        MetaProgressTest.MemStore ms = new MetaProgressTest.MemStore();
        ButtonMap a = new ButtonMap();
        a.load(ms);
        assertEquals(ButtonMap.BIND_OK, a.bind(ButtonMap.BACK, ButtonMap.Y));
        assertEquals(ButtonMap.BIND_OK, a.bind(ButtonMap.DASH, ButtonMap.L1));
        ButtonMap b = new ButtonMap();
        b.load(ms);
        assertEquals(ButtonMap.Y, b.code(ButtonMap.BACK));
        assertEquals(ButtonMap.L1, b.code(ButtonMap.DASH));
        b.reset();
        ButtonMap c = new ButtonMap();
        c.load(ms);
        assertTrue(c.isDefault());
        // a hand-edited store with a clash falls back to the defaults
        ms.putInt("btn_0", ButtonMap.X);
        ms.putInt("btn_1", ButtonMap.X);
        ButtonMap d = new ButtonMap();
        d.load(ms);
        assertTrue(d.isDefault());
    }

    // ------------------------------------------------------------------ item 24: run stats

    @Test
    public void damagePerSlotSumsToTheTotal() throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>());
        Settings.get().load(null);
        Enemy e = s.spawnAt("SERGEANT", s.px + 400, s.py, false);
        assertNotNull(e);
        e.hp = 100;
        s.run.src = 0;
        s.hurt(e, 30f, 0f, 1f, 0, 0, 0);
        assertEquals(30 * RunStats.SCALE, s.run.dmg[0]);
        s.run.src = RunStats.MAIN;
        s.hurt(e, 500f, 0f, 1f, 0, 0, 0); // only the 70 that were left count
        assertEquals(70 * RunStats.SCALE, s.run.dmg[RunStats.MAIN]);
        assertEquals(100 * RunStats.SCALE, s.run.total);
        assertEquals(s.run.total, s.run.sum());
        assertEquals(100, s.run.points(0) + s.run.points(RunStats.MAIN));
    }

    @Test
    public void aRealRunAttributesEveryHitAndTheSumMatches() throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>(), "BASTION", 77L);
        Input in = new Input();
        for (int i = 0; i < 60 * 40 && s.ended == 0; i++) {
            s.hp = s.maxHp;
            in.stickX = (float) Math.cos(i / 60.0);
            in.stickY = (float) Math.sin(i / 60.0);
            s.update(1f / 60f, in);
            while (s.pendingLevelUps > 0) s.pendingLevelUps--;
            while (s.pendingChests > 0) s.openChest();
        }
        assertTrue(s.kills > 0);
        assertTrue("damage was recorded", s.run.total > 0);
        assertEquals(s.run.total, s.run.sum());
        long weaponOrMain = 0;
        for (int i = 0; i <= RunStats.MAIN; i++) weaponOrMain += s.run.dmg[i];
        assertTrue("most damage is attributed to a weapon, not to 'other'", weaponOrMain > s.run.dmg[RunStats.OTHER]);
    }

    @Test
    public void causeOfDeathFollowsTheLastSource() {
        RunStats r = new RunStats();
        assertEquals("", r.deathText());
        r.lastHurt = Sim.DMG_BOLT;
        assertEquals("Shot down", r.deathText());
        r.lastHurt = Sim.DMG_BLAST;
        assertEquals("Blown up", r.deathText());
    }

    // ------------------------------------------------------------------ item 28: aim assist

    @Test
    public void aimAssistPicksTheNearestEnemyInTheCone() throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>(), "SPECTRE", 5L);
        s.enemyN = 0;
        Enemy off = s.spawnAt("SERGEANT", s.px, s.py + 200, false); // straight down
        Enemy on = s.spawnAt("SERGEANT", s.px + 300, s.py + 60, false); // right, a little down
        assertNotNull(off);
        assertNotNull(on);
        float[] out = new float[2];
        // off changes nothing
        assertFalse(AimAssist.pick(Settings.AIM_OFF, s, 1f, 0f, true, out));
        assertEquals(1f, out[0], 0f);
        // soft pulls a manual aim to the enemy 11 degrees off the line, not to the one 90 degrees off
        assertTrue(AimAssist.pick(Settings.AIM_SOFT, s, 1f, 0f, true, out));
        float dx = on.x - s.px, dy = on.y - s.py, m = (float) Math.sqrt(dx * dx + dy * dy);
        assertEquals(dx / m, out[0], 1e-4f);
        assertEquals(dy / m, out[1], 1e-4f);
        // soft does nothing without a manual aim; full aims at the nearest enemy
        assertFalse(AimAssist.pick(Settings.AIM_SOFT, s, 1f, 0f, false, out));
        assertTrue(AimAssist.pick(Settings.AIM_FULL, s, 1f, 0f, false, out));
        assertEquals(0f, out[0], 1e-4f);
        assertEquals(1f, out[1], 1e-4f); // the enemy straight down is nearer (200 against 306)
        // an aim 90 degrees away from everything is left alone by soft
        assertFalse(AimAssist.pick(Settings.AIM_SOFT, s, -1f, 0f, true, out));
        assertEquals(-1f, out[0], 0f);
    }

    @Test
    public void simAimFollowsTheSetting() throws Exception {
        GameData d = TestData.load();
        Settings.get().load(null);
        Sim s = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>(), "SPECTRE", 5L);
        s.enemyN = 0;
        s.spawnAt("SERGEANT", s.px, s.py + 250, false);
        Input in = new Input();
        s.update(1f / 60f, in);
        float fx = s.aimX, fy = s.aimY;
        Settings.get().setAim(Settings.AIM_FULL);
        s.update(1f / 60f, in);
        assertNotEquals("full aim turns toward the enemy", fy, s.aimY, 0.05f);
        Settings.get().setAim(Settings.AIM_OFF);
        s.update(1f / 60f, in);
        assertEquals(fx, s.aimX, 1e-3f);
        assertEquals(fy, s.aimY, 1e-3f);
    }

    // ------------------------------------------------------------------ item 26: tips

    @Test
    public void everyTipIsShortAndNotEmpty() {
        assertEquals(30, Tips.GENERAL.length);
        for (String t : Tips.GENERAL) {
            assertFalse(t.trim().isEmpty());
            assertTrue(t + " is " + t.length(), t.length() < Tips.MAX_LEN);
        }
        for (int st = 0; st < Tips.stages(); st++) {
            for (int i = 0; i < 3; i++) {
                String t = Tips.stageTip(st, i);
                assertFalse(t.trim().isEmpty());
                assertTrue(t + " is " + t.length(), t.length() < Tips.MAX_LEN);
            }
        }
    }

    @Test
    public void stageTipsAreWeightedFiftyPercent() {
        int stage = 0, n = 4000;
        java.util.Set<String> stageLines = new java.util.HashSet<>();
        for (int i = 0; i < 3; i++) stageLines.add(Tips.stageTip(1, i)); // MINES
        for (int seed = 0; seed < n; seed++) {
            String t = Tips.pick("MINES", seed);
            assertNotNull(t);
            if (stageLines.contains(t)) stage++;
        }
        assertTrue("about half of the tips are for the stage: " + stage, stage > n * 0.42 && stage < n * 0.58);
        // an unknown stage only gets general tips, and the seed decides the line
        for (int seed = 0; seed < 200; seed++) assertTrue(java.util.Arrays.asList(Tips.GENERAL).contains(Tips.pick("NOWHERE", seed)));
        assertEquals(Tips.pick("WRECK", 12), Tips.pick("WRECK", 12));
    }

    // ------------------------------------------------------------------ item 27: tutorial

    @Test
    public void tutorialHintsInOrderAndOnlyOnce() {
        MetaProgressTest.MemStore ms = new MetaProgressTest.MemStore();
        Tutorial t = new Tutorial();
        t.load(ms);
        assertTrue(t.active());
        assertEquals(-1, t.hint(Engine.PH_MENU));
        // standing still keeps the move hint up
        for (int i = 0; i < 120; i++) t.update(Engine.PH_PLAYING, 1f / 60f, 0f);
        assertEquals(Tutorial.H_MOVE, t.hint(Engine.PH_PLAYING));
        // a second of steering dismisses it
        for (int i = 0; i < 61; i++) t.update(Engine.PH_PLAYING, 1f / 60f, 1f);
        assertEquals(-1, t.hint(Engine.PH_PLAYING));
        assertTrue(t.done(Tutorial.H_MOVE));
        // the level-up hint shows while the cards are open, goes when a card is picked
        t.update(Engine.PH_LEVELUP, 0.016f, 0f);
        assertEquals(Tutorial.H_CARDS, t.hint(Engine.PH_LEVELUP));
        t.update(Engine.PH_PLAYING, 0.016f, 0f);
        assertTrue(t.done(Tutorial.H_CARDS));
        assertEquals(-1, t.hint(Engine.PH_LEVELUP)); // a second level-up shows no hint
        // the chest hint, then the tutorial is over and stored
        assertTrue(t.active());
        t.update(Engine.PH_CHEST, 0.016f, 0f);
        assertEquals(Tutorial.H_CHEST, t.hint(Engine.PH_CHEST));
        t.update(Engine.PH_PLAYING, 0.016f, 0f);
        assertFalse(t.active());
        Tutorial again = new Tutorial();
        again.load(ms);
        assertFalse("never again after the first run", again.active());
    }

    @Test
    public void theFirstRunEndingEndsTheTutorial() {
        MetaProgressTest.MemStore ms = new MetaProgressTest.MemStore();
        Tutorial t = new Tutorial();
        t.load(ms);
        t.update(Engine.PH_PLAYING, 0.016f, 0f);
        assertTrue(t.active());
        t.update(Engine.PH_RESULT, 0.016f, 0f); // died before the hints were all seen
        assertFalse(t.active());
        assertEquals(1, ms.getInt("tut_done", 0));
        assertEquals(-1, t.hint(Engine.PH_CHEST));
        t.reset();
        assertTrue(t.active());
    }

    // ------------------------------------------------------------------ item 25: codex

    @Test
    public void codexUnlocksOnFirstEncounterAndPersists() throws Exception {
        GameData d = TestData.load();
        java.util.List<String> keys = new java.util.ArrayList<>(java.util.Arrays.asList(GameData.STAGE_ORDER));
        MetaProgressTest.MemStore ms = new MetaProgressTest.MemStore();
        Codex cx = new Codex(d, keys);
        cx.load(ms);
        assertTrue(cx.size(Codex.ENEMY) > 10);
        assertTrue(cx.size(Codex.WEAPON) > 10);
        assertTrue(cx.size(Codex.PASSIVE) > 5);
        assertEquals(0, cx.seenCount());
        Sim s = new Sim(d, d.stages.get("MINES"), "WHIP", new HashMap<String, Double>(), "BASTION", 3L);
        s.enemyN = 0;
        String ek = cx.key(Codex.ENEMY, 0);
        Enemy e = s.spawnAt(ek, s.px + 300, s.py, false);
        assertNotNull(e);
        int n = cx.scan(s, 1);
        assertTrue("the starter weapon and the enemy are new", n >= 2);
        assertEquals(0, cx.scan(s, 1)); // nothing new the second time
        assertTrue(cx.seen(Codex.ENEMY, 0));
        assertEquals("MINES", cx.firstStageKey(Codex.ENEMY, 0));
        // a second Codex over the same store sees the same entries; the stage stays the first one
        Codex c2 = new Codex(d, keys);
        c2.load(ms);
        assertEquals(cx.seenCount(), c2.seenCount());
        assertFalse(c2.mark(Codex.ENEMY, ek, 0));
        assertEquals("MINES", c2.firstStageKey(Codex.ENEMY, 0));
        assertFalse(c2.mark(Codex.ENEMY, "NO_SUCH_ENEMY", 0));
    }

    // ------------------------------------------------------------------ menu flow (items 21, 25, 29, 30)

    private Engine menu() throws Exception {
        Engine e = Engine.get();
        GameData d = TestData.load();
        MetaProgress fresh = new MetaProgress(d, new MetaProgressTest.MemStore());
        e.init(d, fresh);
        e.meta = fresh;
        fresh.unlockAll = true;
        Settings.get().load(new MetaProgressTest.MemStore());
        e.input.clearMovement();
        e.phase = Engine.PH_MENU;
        e.focus = 0;
        e.remapListening = -1;
        e.sound.clear();
        return e;
    }

    private void press(Engine e, int type, int a, int b, int c) {
        e.input.post(type, a, b, c);
        e.advance(1f / 60f);
    }

    @Test
    public void theThreeButtonRowIsReachedWithTheDpad() throws Exception {
        Engine e = menu();
        e.focus = Engine.MENU_WEAPON;
        press(e, Input.CMD_NAV, Input.DOWN, 0, 0);
        assertEquals(Engine.MENU_SHOP, e.focus);
        press(e, Input.CMD_NAV, Input.RIGHT, 0, 0);
        assertEquals(Engine.MENU_CODEX, e.focus);
        press(e, Input.CMD_NAV, Input.RIGHT, 0, 0);
        assertEquals(Engine.MENU_OPTIONS, e.focus);
        press(e, Input.CMD_NAV, Input.RIGHT, 0, 0);
        assertEquals(Engine.MENU_OPTIONS, e.focus);
        press(e, Input.CMD_NAV, Input.DOWN, 0, 0);
        assertEquals(Engine.MENU_MUSIC, e.focus);
        press(e, Input.CMD_NAV, Input.UP, 0, 0);
        assertEquals(Engine.MENU_SHOP, e.focus);
        press(e, Input.CMD_NAV, Input.UP, 0, 0);
        assertEquals(Engine.MENU_WEAPON, e.focus);
        // the old settings rows still cycle in the menu
        e.focus = Engine.MENU_LIGHT;
        int l0 = e.meta.lighting;
        press(e, Input.CMD_NAV, Input.RIGHT, 0, 0);
        assertNotEquals(l0, e.meta.lighting);
    }

    @Test
    public void optionsScreenChangesTheSharedSettings() throws Exception {
        Engine e = menu();
        e.focus = Engine.MENU_OPTIONS;
        press(e, Input.CMD_CONFIRM, 0, 0, 0);
        assertEquals(Engine.PH_OPTIONS, e.phase);
        assertEquals(0, e.focus);
        // the first rows are the menu's own music / SFX / lighting
        int m0 = e.meta.musicVol;
        press(e, Input.CMD_NAV, Input.RIGHT, 0, 0);
        assertEquals((m0 + 1) % (MetaProgress.VOL_MAX + 1), e.meta.musicVol);
        e.focus = Engine.OPT_SHAKE;
        press(e, Input.CMD_NAV, Input.LEFT, 0, 0);
        assertEquals(0.5f, Settings.get().shake(), 0f);
        e.focus = Engine.OPT_NUMBERS;
        press(e, Input.CMD_NAV, Input.LEFT, 0, 0);
        assertEquals(Settings.NUM_CRITS, Settings.get().damageNumbers());
        e.focus = Engine.OPT_TELE;
        press(e, Input.CMD_CONFIRM, 0, 0, 0);
        assertTrue(Settings.get().colorblindShapes());
        e.focus = Engine.OPT_TEXT;
        press(e, Input.CMD_CONFIRM, 0, 0, 0);
        assertTrue(Settings.get().largeText());
        e.focus = Engine.OPT_AIM;
        press(e, Input.CMD_NAV, Input.RIGHT, 0, 0);
        assertEquals(Settings.AIM_SOFT, Settings.get().autoAim());
        e.focus = Engine.OPT_HAPTICS;
        press(e, Input.CMD_TAP, Engine.PH_OPTIONS, Engine.OPT_HAPTICS, 1);
        assertFalse(Settings.get().haptics());
        // a tap on the left half steps backwards
        press(e, Input.CMD_TAP, Engine.PH_OPTIONS, Engine.OPT_AIM, -1);
        assertEquals(Settings.AIM_OFF, Settings.get().autoAim());
        // down wraps, B goes back to the menu on the OPTIONS button
        e.focus = Engine.OPT_BACK;
        press(e, Input.CMD_NAV, Input.DOWN, 0, 0);
        assertEquals(0, e.focus);
        press(e, Input.CMD_BACK, 0, 0, 0);
        assertEquals(Engine.PH_MENU, e.phase);
        assertEquals(Engine.MENU_OPTIONS, e.focus);
        assertTrue(Engine.isMenuPhase(Engine.PH_OPTIONS) && Engine.isMenuPhase(Engine.PH_CODEX) && Engine.isMenuPhase(Engine.PH_REMAP));
    }

    @Test
    public void remapFlowBindsRejectsAndResets() throws Exception {
        Engine e = menu();
        e.phase = Engine.PH_OPTIONS;
        e.focus = Engine.OPT_REMAP;
        press(e, Input.CMD_CONFIRM, 0, 0, 0);
        assertEquals(Engine.PH_REMAP, e.phase);
        // listen for DASH
        e.focus = ButtonMap.DASH;
        press(e, Input.CMD_CONFIRM, 0, 0, 0);
        assertEquals(ButtonMap.DASH, e.remapListening);
        press(e, Input.CMD_NAV, Input.DOWN, 0, 0);
        assertEquals("the pad is ignored while waiting", ButtonMap.DASH, e.focus);
        press(e, Input.CMD_BIND, ButtonMap.START, 0, 0); // clashes with pause
        assertEquals(ButtonMap.DASH, e.remapListening);
        assertTrue(e.toast.contains("PAUSE"));
        assertTrue(Settings.get().buttons.legacyDash());
        press(e, Input.CMD_BIND, ButtonMap.Y, 0, 0);
        assertEquals(-1, e.remapListening);
        assertEquals(ButtonMap.Y, Settings.get().buttons.code(ButtonMap.DASH));
        // B while waiting cancels the wait only
        e.focus = ButtonMap.CONFIRM;
        press(e, Input.CMD_CONFIRM, 0, 0, 0);
        assertEquals(ButtonMap.CONFIRM, e.remapListening);
        press(e, Input.CMD_BACK, 0, 0, 0);
        assertEquals(-1, e.remapListening);
        assertEquals(Engine.PH_REMAP, e.phase);
        // reset restores everything, back leaves
        e.focus = Engine.REMAP_RESET;
        press(e, Input.CMD_CONFIRM, 0, 0, 0);
        assertTrue(Settings.get().buttons.isDefault());
        e.focus = Engine.REMAP_BACK;
        press(e, Input.CMD_CONFIRM, 0, 0, 0);
        assertEquals(Engine.PH_OPTIONS, e.phase);
        assertEquals(Engine.OPT_REMAP, e.focus);
        // a bind that arrives when nobody waits does nothing
        press(e, Input.CMD_BIND, ButtonMap.X, 0, 0);
        assertTrue(Settings.get().buttons.isDefault());
    }

    @Test
    public void codexScreenNavigatesTabsAndGrid() throws Exception {
        Engine e = menu();
        e.focus = Engine.MENU_CODEX;
        press(e, Input.CMD_CONFIRM, 0, 0, 0);
        assertEquals(Engine.PH_CODEX, e.phase);
        assertEquals(Codex.ENEMY, e.codexTab);
        int n = e.codex.size(Codex.ENEMY);
        press(e, Input.CMD_NAV, Input.RIGHT, 0, 0);
        assertEquals(1, e.focus);
        press(e, Input.CMD_NAV, Input.DOWN, 0, 0);
        assertEquals(1 + Engine.CODEX_COLS, e.focus);
        press(e, Input.CMD_NAV, Input.UP, 0, 0);
        press(e, Input.CMD_NAV, Input.LEFT, 0, 0);
        assertEquals(0, e.focus);
        press(e, Input.CMD_NAV, Input.PAGE_NEXT, 0, 0);
        assertEquals(Codex.WEAPON, e.codexTab);
        assertEquals(0, e.focus);
        press(e, Input.CMD_NAV, Input.PAGE_PREV, 0, 0);
        press(e, Input.CMD_NAV, Input.PAGE_PREV, 0, 0);
        assertEquals(Codex.PASSIVE, e.codexTab);
        press(e, Input.CMD_TAP, Engine.PH_CODEX, 1000 + Codex.ENEMY, 0);
        assertEquals(Codex.ENEMY, e.codexTab);
        // down from the last row reaches BACK, BACK leaves
        e.focus = n - 1;
        press(e, Input.CMD_NAV, Input.DOWN, 0, 0);
        assertEquals(n, e.focus);
        press(e, Input.CMD_CONFIRM, 0, 0, 0);
        assertEquals(Engine.PH_MENU, e.phase);
        assertEquals(Engine.MENU_CODEX, e.focus);
    }

    @Test
    public void aRunFillsTheCodexAndRollsAnotherTip() throws Exception {
        Engine e = menu();
        Settings.get().setNumbers(Settings.NUM_ALL);
        e.codex.load(new MetaProgressTest.MemStore());
        int before = e.codex.seenCount();
        e.nextRunSeed = 9L;
        e.focus = Engine.MENU_START;
        press(e, Input.CMD_START, 0, 0, 0);
        assertEquals(Engine.PH_PLAYING, e.phase);
        for (int i = 0; i < 60 * 20 && e.phase == Engine.PH_PLAYING; i++) {
            e.sim.hp = e.sim.maxHp;
            e.advance(1f / 60f);
        }
        assertTrue("the starter weapon and the first enemies are in the codex", e.codex.seenCount() >= before + 2);
        assertNotNull(e.tip());
        assertTrue(e.tip().length() < Tips.MAX_LEN);
    }

    @Test
    public void tutorialRunsInTheEngineOnTheFirstRun() throws Exception {
        Engine e = menu();
        e.tutorial.load(new MetaProgressTest.MemStore());
        e.nextRunSeed = 11L;
        e.focus = Engine.MENU_START;
        press(e, Input.CMD_START, 0, 0, 0);
        e.advance(1f / 60f);
        assertEquals(Tutorial.H_MOVE, e.tutorial.hint(Engine.PH_PLAYING));
        e.input.stickX = 1f;
        for (int i = 0; i < 90; i++) {
            e.sim.hp = e.sim.maxHp;
            e.advance(1f / 60f);
        }
        assertEquals(-1, e.tutorial.hint(Engine.PH_PLAYING));
        // the run ends: no more hints, ever
        e.sim.ended = 1;
        e.advance(1f / 60f);
        assertEquals(Engine.PH_RESULT, e.phase);
        assertFalse(e.tutorial.active());
        e.tutorial.reset();
    }
}
