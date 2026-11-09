package se.felixarvid.thorsurvivors.screen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** v1.5 B8 (73): damage -> rumble strength. The vibrator call itself is device-only. */
public class HapticsTest {
    @Test
    public void noDamageNoRumble() {
        assertEquals(0f, Haptics.intensity(0f, 100f), 0f);
        assertEquals(0f, Haptics.intensity(-5f, 100f), 0f);
        assertEquals(0f, Haptics.intensity(5f, 0f), 0f);
        assertEquals(0f, Haptics.intensity(Float.NaN, 100f), 0f);
        assertEquals(0, Haptics.amplitude(0f));
        assertEquals(0L, Haptics.millis(0f));
    }

    @Test
    public void strengthRisesWithTheShareOfMaxHp() {
        float a = Haptics.intensity(1f, 100f), b = Haptics.intensity(10f, 100f), c = Haptics.intensity(25f, 100f);
        assertTrue(a >= Haptics.MIN_INTENSITY && a < b && b < c);
        assertEquals(1f, c, 1e-6f);
        assertEquals(1f, Haptics.intensity(90f, 100f), 1e-6f);
    }

    @Test
    public void amplitudeAndDurationStayInRange() {
        for (float d = 0.1f; d < 200f; d *= 1.7f) {
            float i = Haptics.intensity(d, 100f);
            int amp = Haptics.amplitude(i);
            long ms = Haptics.millis(i);
            assertTrue(amp >= 1 && amp <= 255);
            assertTrue(ms >= 20 && ms <= 90);
        }
        assertEquals(255, Haptics.amplitude(1f));
        assertEquals(90L, Haptics.millis(1f));
    }

    @Test
    public void callsAreNoOpsWithoutADevice() {
        Haptics.hit(10f, 100f); // not attached: nothing happens, nothing throws
        Haptics.levelUp();
    }
}
