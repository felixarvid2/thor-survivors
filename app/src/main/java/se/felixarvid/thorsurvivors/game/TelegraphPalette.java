package se.felixarvid.thorsurvivors.game;

/**
 * v1.5 B6 HOOK for the colorblind telegraph palette (batch 3, options). Every telegraph and modifier colour that passes
 * through {@link #map} can be swapped for a colorblind-safe one without touching the callers; today it returns the colour
 * unchanged. Batch 3 sets {@link #mode} from its Settings class (0 = default) and fills in the table in {@link #map}.
 * Callers so far: {@link Elites#colorOf}. Boss warn rings and lanes are drawn by the renderer: batch 1 and 3 route their
 * telegraph colours through here when the palette lands.
 */
public final class TelegraphPalette {
    /** 0 = the standard colours; other values are reserved for batch 3 (deuteranopia, protanopia, tritanopia). */
    public static int mode = 0;

    private TelegraphPalette() {
    }

    /** The colour (ARGB) to draw for a standard telegraph colour in the current mode. */
    public static int map(int argb) {
        return argb; // TODO(v1.5 B3): colorblind-safe replacements per mode
    }
}
