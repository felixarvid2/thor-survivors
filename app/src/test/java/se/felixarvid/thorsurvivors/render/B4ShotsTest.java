package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Input;
import se.felixarvid.thorsurvivors.game.ShopCatalog;
import se.felixarvid.thorsurvivors.game.Sim;

/** v1.5 B4: the four pages of the salvage terminal, the ascension row, the level-up tools and the results screen with a record and achievements. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class B4ShotsTest {
    private static boolean notBlank(Bitmap b) {
        int first = b.getPixel(0, 0), diff = 0;
        for (int y = 0; y < b.getHeight(); y += 7) for (int x = 0; x < b.getWidth(); x += 7) if (b.getPixel(x, y) != first) diff++;
        return diff > 400;
    }

    @Test
    public void terminalCategories() throws Exception {
        Shots h = new Shots();
        h.eng.meta.addGold(30000);
        h.eng.meta.buy("POWER");
        h.eng.meta.buy("POWER");
        h.eng.meta.buy("ARMOR");
        h.eng.meta.buyReroll();
        h.eng.meta.buyReroll();
        h.eng.meta.buyTint("BASTION", 1);
        h.eng.meta.wearTint("BASTION", 1);
        h.eng.meta.recordAscWin("FLATS", 0);
        h.eng.meta.recordAscWin("FLATS", 1);
        h.eng.meta.recordAscWin("MINES", 0);
        h.eng.phase = Engine.PH_SHOP;
        String[] names = {"stats", "run", "style", "awards"};
        for (int c = 0; c < ShopCatalog.CATS; c++) {
            h.eng.focus = h.eng.catalog.pageStart[h.eng.catalog.catFirstPage[c]];
            h.eng.shopRefund = false;
            h.eng.advance(1f / 60f);
            Bitmap b = h.renderBottom();
            assertTrue(notBlank(b));
            Shots.save(b, "b4_shop_" + names[c] + ".png");
        }
        h.eng.focus = 0;
        h.eng.shopRefund = true;
        h.eng.advance(1f / 60f);
        Shots.save(h.renderBottom(), "b4_shop_refund.png");
        h.eng.shopRefund = false;
        h.eng.phase = Engine.PH_MENU;
        h.eng.advance(1f / 60f);
    }

    @Test
    public void menuAscensionRowAndLevelUpTools() throws Exception {
        Shots h = new Shots();
        h.eng.meta.recordAscWin("FLATS", 0);
        h.eng.meta.recordAscWin("FLATS", 1);
        h.eng.meta.recordAscWin("FLATS", 2);
        h.eng.selectStage("FLATS");
        h.eng.ascTier = 3;
        h.eng.phase = Engine.PH_MENU;
        h.eng.focus = Engine.MENU_ASC;
        h.eng.advance(1f / 60f);
        Shots.save(h.renderBottom(), "b4_menu_ascension.png");
        h.eng.dailyPick = true;
        h.eng.advance(1f / 60f);
        Shots.save(h.renderBottom(), "b4_menu_daily.png");
        h.eng.dailyPick = false;
        h.eng.meta.stepCurse(2); // v1.5 final: the CURSE row at 20 percent
        h.eng.focus = Engine.MENU_CURSE;
        h.eng.advance(1f / 60f);
        Shots.save(h.renderBottom(), "final_menu_curse.png");
        h.eng.dailyPick = false;
        h.eng.meta.addGold(20000);
        h.eng.meta.buyReroll();
        h.eng.meta.buyReroll();
        h.eng.meta.buyBanish();
        Sim s = h.startRun("FLATS", "WHIP", 21);
        s.noSpawnsForTest();
        s.pendingLevelUps = 1;
        h.eng.advance(1f / 60f);
        h.eng.focus = h.eng.offers.size() + 1;
        h.eng.advance(1f / 60f);
        Bitmap b = h.renderBottom();
        Shots.save(b, "b4_levelup_tools.png");
        h.eng.input.post(Input.CMD_CONFIRM, 0, 0, 0); // banish mode on
        h.eng.advance(1f / 60f);
        Shots.save(h.renderBottom(), "b4_levelup_banish.png");
    }

    @Test
    public void resultWithRecordAndAchievements() throws Exception {
        Shots h = new Shots();
        h.startRun("FLATS", "WHIP", 22);
        Sim s = h.eng.sim;
        s.time = 1800;
        s.kills = 1500;
        s.gold = 800;
        s.ended = 2;
        h.eng.advance(1f / 60f);
        assertTrue(h.eng.phase == Engine.PH_RESULT);
        assertTrue(h.eng.resultRecord);
        Shots.save(h.renderBottom(), "b4_result.png");
    }
}
