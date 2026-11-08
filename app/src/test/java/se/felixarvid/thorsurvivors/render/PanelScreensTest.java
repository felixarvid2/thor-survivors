package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.ui.PanelScreen;
import se.felixarvid.thorsurvivors.ui.PanelScreens;

/** v1.5 B2: a screen registered with one line is drawn instead of the built-in switch, and can post taps. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class PanelScreensTest {
    private static final int PHASE = 50;

    @Test
    public void aRegisteredScreenIsDrawnForItsPhase() throws Exception {
        Shots h = new Shots();
        final int[] calls = new int[1];
        PanelScreen screen = new PanelScreen() {
            @Override public int phase() { return PHASE; }
            @Override public void draw(Canvas c, int w, int h2, Host host) {
                calls[0]++;
                host.skin().plate(c, 100, 100, 600, 400, 0xFFFF0000, 0xFFFFFFFF, 4f);
                host.hit(100, 100, 600, 400, 3, 1);
            }
        };
        assertNull(PanelScreens.forPhase(PHASE));
        PanelScreens.register(screen);
        try {
            assertSame(screen, PanelScreens.forPhase(PHASE));
            int old = h.eng.phase;
            h.eng.phase = PHASE;
            Bitmap b = h.renderBottom();
            h.eng.phase = old;
            assertEquals(1, calls[0]);
            assertTrue("the plug-in drew its red plate", b.getPixel(300, 250) != 0);
        } finally {
            PanelScreens.unregister(PHASE);
        }
        assertNull(PanelScreens.forPhase(PHASE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aPhaseOutOfRangeIsRefused() {
        PanelScreens.register(new PanelScreen() {
            @Override public int phase() { return -1; }
            @Override public void draw(Canvas c, int w, int h, Host host) { }
        });
    }
}
