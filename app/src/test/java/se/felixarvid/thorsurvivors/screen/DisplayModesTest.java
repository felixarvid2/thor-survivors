package se.felixarvid.thorsurvivors.screen;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** v1.5 B8 (72): the refresh-rate pick (same size only, no pointless switch). */
public class DisplayModesTest {
    private final int[] w = {1920, 1920, 1280, 1920};
    private final int[] h = {1080, 1080, 720, 1080};
    private final float[] hz = {60f, 120f, 144f, 90f};

    @Test
    public void picksTheFastestModeAtTheCurrentSize() {
        assertEquals(1, DisplayModes.fastest(w, h, hz, 1920, 1080, 60f));
        assertEquals(120f, DisplayModes.maxHz(w, h, hz, 1920, 1080, 60f), 0f);
    }

    @Test
    public void neverChangesTheResolution() {
        assertEquals(2, DisplayModes.fastest(w, h, hz, 1280, 720, 60f));
        assertEquals(-1, DisplayModes.fastest(w, h, hz, 3840, 2160, 60f));
        assertEquals(60f, DisplayModes.maxHz(w, h, hz, 3840, 2160, 60f), 0f);
    }

    @Test
    public void noSwitchWhenAlreadyFastest() {
        assertEquals(-1, DisplayModes.fastest(w, h, hz, 1920, 1080, 120f));
        assertEquals(-1, DisplayModes.fastest(new int[]{1920}, new int[]{1080}, new float[]{60f}, 1920, 1080, 59.94f));
        assertEquals(-1, DisplayModes.fastest(new int[0], new int[0], new float[0], 1920, 1080, 60f));
    }
}
