package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;

/** v1.5 final: the CURSE row of the main menu cycles 0/10/20/30 percent, is stored, and a run start hands it to the Sim. */
public class CurseMenuTest {
    @After
    public void restore() {
        CurseMod.set(0.0);
        Engine e = Engine.get();
        e.phase = Engine.PH_MENU;
        e.selectStage("FLATS");
    }

    private Engine menu(MetaProgress.Store st) throws Exception {
        Engine e = Engine.get();
        GameData d = TestData.load();
        MetaProgress mp = new MetaProgress(d, st);
        e.init(d, mp);
        e.meta = mp;
        mp.unlockAll = true;
        e.input.clearMovement();
        e.phase = Engine.PH_MENU;
        e.focus = 0;
        e.stageOverride = null;
        e.stageIdx = 0;
        e.ascTier = 0;
        e.dailyPick = false;
        e.dateOverride = 0;
        return e;
    }

    private void press(Engine e, int cmd) {
        e.input.post(cmd, 0, 0, 0);
        e.advance(1f / 60f);
    }

    @Test
    public void rowCyclesAndIsStored() throws Exception {
        MetaProgressTest.MemStore st = new MetaProgressTest.MemStore();
        Engine e = menu(st);
        assertEquals("OFF", e.curseLabel());
        e.focus = Engine.MENU_CURSE;
        assertTrue(Engine.isCycleRow(Engine.MENU_CURSE));
        press(e, Input.CMD_CONFIRM);
        assertEquals("10%  GOLD +10%", e.curseLabel());
        press(e, Input.CMD_CONFIRM);
        press(e, Input.CMD_CONFIRM);
        assertEquals("30%  GOLD +30%", e.curseLabel());
        press(e, Input.CMD_CONFIRM); // wraps
        assertEquals("OFF", e.curseLabel());
        press(e, Input.CMD_CONFIRM); // 10 percent
        e.meta.stepCurse(2); // 30 percent
        assertEquals(3, e.meta.curseStep);
        assertEquals(0.3, new MetaProgress(TestData.load(), st).curse(), 1e-9); // survives a restart
    }

    @Test
    public void runStartGivesTheSimTheCurse() throws Exception {
        Engine e = menu(new MetaProgressTest.MemStore());
        e.meta.stepCurse(-1); // wraps to 30 percent
        assertEquals(0.3, e.meta.curse(), 1e-9);
        e.nextRunSeed = 5L;
        e.focus = Engine.MENU_START;
        press(e, Input.CMD_CONFIRM);
        assertEquals(Engine.PH_PLAYING, e.phase);
        assertEquals(0.3, e.sim.runCurse, 1e-9);
        assertEquals(1.15, e.sim.curseHpExtra(), 1e-9);
        assertEquals(1.3, e.sim.curseGoldMul, 1e-9);
    }
}
