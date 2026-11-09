package se.felixarvid.thorsurvivors.game;

/**
 * v1.5 B6 hook for the colorblind palette, wired to batch 3's "Telegraphs: Shapes" option in the v1.5 integration.
 * Every elite modifier colour that passes through {@link #map} (Elites.colorOf, the elite chips on the bottom screen, the
 * elite outline and floor ring in WorldRenderer) is swapped for an Okabe-Ito colour while {@link #mode} is
 * {@link #MODE_SHAPES}; danger zones themselves take their blue fill and bold orange edge from {@link Settings#telegraphFill}
 * and {@link Settings#telegraphEdge}. {@link Settings} sets the mode when the option loads or changes.
 */
public final class TelegraphPalette {
    public static final int MODE_STANDARD = 0, MODE_SHAPES = 1;
    /** {@link #MODE_STANDARD} or {@link #MODE_SHAPES} (set by {@link Settings#setShapes} and {@link Settings#load}). */
    public static volatile int mode = MODE_STANDARD;

    /** The standard modifier colours (Elites.COLORS order) and their colorblind-safe stand-ins (Okabe-Ito, brightened for a dark floor). */
    private static final int[] FROM = {0xFFFF8A2A, 0xFF40D8FF, 0xFFFF3B1F, 0xFFFFD030, 0xFF3DBE5A, 0xFFB0B8C0, 0xFF5A9BFF, 0xFFC070FF, 0xFFFF6A00};
    public static final int[] TO_COLORS = {0xFFE69F00, 0xFF56B4E9, 0xFFD55E00, 0xFFF0E442, 0xFF00B386, 0xFFB0B8C0, 0xFF2F8FE0, 0xFFCC79A7, 0xFFF4F4F4};

    private TelegraphPalette() {
    }

    /** The colour (ARGB) to draw for a standard modifier colour in the current mode; unknown colours pass unchanged. */
    public static int map(int argb) {
        if (mode != MODE_SHAPES) return argb;
        for (int i = 0; i < FROM.length; i++) if (FROM[i] == argb) return TO_COLORS[i];
        return argb;
    }
}
