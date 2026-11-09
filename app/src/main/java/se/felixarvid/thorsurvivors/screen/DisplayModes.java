package se.felixarvid.thorsurvivors.screen;

/**
 * v1.5 B8 (item 72): which display mode to ask for. Pure arrays in, index out, so the choice is unit-testable; the
 * caller reads {@code Display.getSupportedModes()} (API 23+) and hands over sizes and refresh rates.
 */
final class DisplayModes {
    private DisplayModes() {
    }

    /**
     * Index of the mode with the highest refresh rate among those with the current resolution (a mode switch must not
     * change the size), or -1 when nothing beats {@code curHz}. Ties keep the first.
     */
    static int fastest(int[] w, int[] h, float[] hz, int curW, int curH, float curHz) {
        int best = -1;
        float bestHz = curHz + 0.5f; // 59.94 vs 60 is no gain
        for (int i = 0; i < hz.length; i++) {
            if (w[i] != curW || h[i] != curH) continue;
            if (hz[i] > bestHz) {
                bestHz = hz[i];
                best = i;
            }
        }
        return best;
    }

    /** Highest refresh rate of any mode at the current size (or {@code curHz}); the rate asked of the Surface. */
    static float maxHz(int[] w, int[] h, float[] hz, int curW, int curH, float curHz) {
        float m = curHz;
        for (int i = 0; i < hz.length; i++) if (w[i] == curW && h[i] == curH) m = Math.max(m, hz[i]);
        return m;
    }
}
