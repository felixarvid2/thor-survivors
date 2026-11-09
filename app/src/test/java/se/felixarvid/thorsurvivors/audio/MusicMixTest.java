package se.felixarvid.thorsurvivors.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** v1.5 B8 (74): enemy count -> combat layer level, smoothed over about 2 s. */
public class MusicMixTest {
    @Test
    public void densityMapsToTheLayerLevel() {
        assertEquals(0f, MusicMix.target(0, false), 0f);
        assertEquals(0f, MusicMix.target(MusicMix.CALM_BELOW, false), 0f);
        assertEquals(1f, MusicMix.target(300, false), 1e-6f);
        assertEquals(1f, MusicMix.target(900, false), 1e-6f);
        float prev = -1;
        for (int n = 0; n <= 320; n += 5) {
            float t = MusicMix.target(n, false);
            assertTrue("monotonic at " + n, t >= prev);
            prev = t;
        }
        assertEquals(0.5f, MusicMix.target((MusicMix.CALM_BELOW + 300) / 2, false), 0.02f);
    }

    @Test
    public void aBossTakesTheLayerAway() {
        assertEquals(0f, MusicMix.target(300, true), 0f);
    }

    @Test
    public void smoothingReachesAboutNinetyFivePercentInTwoSeconds() {
        MusicMix m = new MusicMix();
        float v = 0;
        for (int i = 0; i < 120; i++) v = m.step(1f / 60f, 300, false);
        assertEquals(0.95f, v, 0.02f);
        assertTrue("not instant", m.step(0f, 300, false) < 1f);
        for (int i = 0; i < 600; i++) v = m.step(1f / 60f, 300, false);
        assertEquals(1f, v, 0.001f);
        for (int i = 0; i < 120; i++) v = m.step(1f / 60f, 0, false);
        assertEquals(0.05f, v, 0.02f);
    }

    @Test
    public void oneLongFrameDoesNotJump() {
        MusicMix m = new MusicMix();
        assertTrue(m.step(0.25f, 300, false) < 0.5f);
    }

    @Test
    public void theCalmTrackDipsOnlyAsFarAsTheLimit() {
        assertEquals(1f, MusicMix.calmGain(0f), 0f);
        assertEquals(1f - MusicMix.CALM_DIP, MusicMix.calmGain(1f), 1e-6f);
        assertEquals(1f - MusicMix.CALM_DIP, MusicMix.calmGain(5f), 1e-6f);
    }
}
