package se.felixarvid.thorsurvivors.render.stagefx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** v1.5 B8 (71): the baked heat vignette bitmap and its heat -> alpha curve. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class RefineryVignetteTest {
    @Test
    public void bitmapIsClearInTheMiddleAndOpaqueAtTheCorners() {
        Bitmap b = RefineryFx.bakeVignette();
        assertEquals(256, b.getWidth());
        assertEquals(256, b.getHeight());
        assertEquals(0, Color.alpha(b.getPixel(128, 128)));
        assertEquals(0, Color.alpha(b.getPixel(128, 40)));
        int edge = Color.alpha(b.getPixel(128, 1)), corner = Color.alpha(b.getPixel(1, 1));
        assertTrue("edge " + edge, edge > 40);
        assertTrue("corner " + corner + " stronger than the edge", corner > edge);
        // monotonic from the centre to the border: a smooth ramp, no rings
        int prev = 0;
        for (int y = 128; y >= 0; y--) {
            int a = Color.alpha(b.getPixel(128, y));
            assertTrue("ramp at y=" + y, a >= prev - 1);
            prev = a;
        }
    }

    @Test
    public void alphaFollowsHeat() {
        assertEquals(0, RefineryFx.vignetteAlpha(0f, 100f, false, 0f));
        assertEquals(0, RefineryFx.vignetteAlpha(50f, 100f, false, 0f));
        int mid = RefineryFx.vignetteAlpha(75f, 100f, false, 0f), full = RefineryFx.vignetteAlpha(100f, 100f, false, 0f);
        assertEquals(128, mid, 2);
        assertEquals(255, full);
        for (float t = 0; t < 3; t += 0.1f) {
            int a = RefineryFx.vignetteAlpha(100f, 100f, true, t);
            assertTrue("overheated pulses between 60 % and 100 %: " + a, a >= 150 && a <= 255);
        }
    }
}
