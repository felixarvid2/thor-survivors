package se.felixarvid.thorsurvivors.screen;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Input;
import se.felixarvid.thorsurvivors.game.Sim;

import java.io.File;
import java.io.FileOutputStream;

/** v1.5 B8 (80): the hidden debug overlay draws its lines (shot: build/screenshots/debug_overlay.png) and allocates nothing. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class DebugOverlayShotTest {
    @Test
    public void overlayShowsFrameTimeAndCounts() throws Exception {
        Engine eng = Engine.get();
        eng.init(TestData.load(), new se.felixarvid.thorsurvivors.game.MetaProgress(TestData.load(), new se.felixarvid.thorsurvivors.game.MetaProgress.Store() {
            public int getInt(String k, int d) {
                return d;
            }

            public void putInt(String k, int v) {
            }
        }));
        eng.nextRunSeed = 4L;
        eng.phase = Engine.PH_MENU;
        eng.focus = Engine.MENU_START;
        eng.input.post(Input.CMD_START, 0, 0, 0);
        eng.advance(1f / 60f);
        for (int i = 0; i < 120; i++) eng.advance(1f / 60f);
        Sim s = eng.sim;

        FrameStats st = new FrameStats();
        for (int i = 0; i < 240; i++) st.add(i % 12 == 0 ? 17.4f : 8.3f);
        Bitmap bm = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        c.drawColor(0xFF223322);
        DebugOverlay o = new DebugOverlay();
        o.draw(c, 960, 540, st, s, false);
        File dir = new File(System.getenv("SHOT_DIR") != null ? System.getenv("SHOT_DIR") : "build/screenshots");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, "debug_overlay.png"))) {
            bm.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        int bright = 0;
        for (int y = 8; y < 100; y++) for (int x = 8; x < 300; x++) if ((bm.getPixel(x, y) & 0xFF) > 0xB0) bright++;
        assertTrue("text pixels drawn: " + bright, bright > 200);
        assertTrue("p95 is the slow frame time", st.p95() > 17f);
        o.draw(c, 960, 540, st, s, true); // held variant must not throw
    }
}
