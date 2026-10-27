package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.ui.PanelRenderer;

/** The stage select of the main menu (v1.4): an open card with its record and star, locked cards with padlock and unlock line, the refusal shake. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class StageSelectShotsTest {
    private static Bitmap copy(Bitmap b) {
        return b.copy(Bitmap.Config.ARGB_8888, false);
    }

    private static void settle(Shots h, String key) {
        h.eng.selectStage(key);
        h.eng.focus = Engine.MENU_STAGE;
        for (int i = 0; i < 10; i++) h.eng.advance(1f / 60f);
    }

    @Test
    public void stageSelectOpenAndLockedCards() throws Exception {
        Shots h = new Shots();
        h.eng.meta.unlockAll = false;
        h.eng.meta.recordRun(754, 900, "FLATS"); // 12:34 on the Flats opens the Mines
        h.eng.meta.markCleared("FLATS");
        h.eng.meta.recordRun(680, 500, "MINES"); // 11:20 on the Mines: the Vaults need 15:00
        settle(h, "FLATS");
        Bitmap open = copy(h.renderBottom());
        Shots.save(open, "stageselect_flats_open.png");
        settle(h, "MINES");
        Shots.save(copy(h.renderBottom()), "stageselect_mines_open.png");
        settle(h, "HYDRO");
        Bitmap locked = copy(h.renderBottom());
        Shots.save(locked, "stageselect_hydro_locked.png");
        settle(h, "WRECK");
        Shots.save(copy(h.renderBottom()), "stageselect_wreck_locked.png");
        // the refusal: A on START RUN with a locked stage shakes the card and shows the toast
        settle(h, "REFINERY");
        h.eng.focus = Engine.MENU_START;
        h.eng.input.post(se.felixarvid.thorsurvivors.game.Input.CMD_CONFIRM, 0, 0, 0);
        h.eng.advance(0.1f);
        assertTrue(h.eng.lockShake > 0f);
        Shots.save(copy(h.renderBottom()), "stageselect_refused.png");
        // the single-screen overlay
        PanelRenderer compact = new PanelRenderer(true);
        Bitmap frame = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas fc = new android.graphics.Canvas(frame);
        h.world.draw(fc, 1920, 1080, h.eng, false);
        compact.draw(fc, 1920, 1080);
        Shots.save(frame, "stageselect_compact.png");
        // the locked card is darker than the open one in the painting area
        assertTrue(brightness(locked, 40, 400, 560, 560) < brightness(open, 40, 400, 560, 560) * 0.9);
    }

    private static double brightness(Bitmap b, int x0, int y0, int x1, int y1) {
        long sum = 0;
        int n = 0;
        for (int y = y0; y < y1; y += 3) {
            for (int x = x0; x < x1; x += 3) {
                int p = b.getPixel(x, y);
                sum += ((p >> 16) & 255) + ((p >> 8) & 255) + (p & 255);
                n++;
            }
        }
        return sum / (3.0 * n);
    }
}
