package se.felixarvid.thorsurvivors.ui;

/**
 * v1.5 B2: the registry of {@link PanelScreen}s by Engine phase. A batch that adds a screen writes one line, for example
 * {@code PanelScreens.register(new ShopPanel());}, anywhere that runs once at start-up (the Engine's init). A registered screen
 * wins over the built-in one of the same phase, so it can also replace a built-in screen.
 */
public final class PanelScreens {
    private static final int MAX_PHASES = 64;
    private static final PanelScreen[] BY_PHASE = new PanelScreen[MAX_PHASES];

    private PanelScreens() {
    }

    /** Registers (or replaces) the screen of {@code screen.phase()}. */
    public static synchronized void register(PanelScreen screen) {
        int ph = screen.phase();
        if (ph < 0 || ph >= MAX_PHASES) throw new IllegalArgumentException("phase " + ph);
        BY_PHASE[ph] = screen;
    }

    /** Removes the screen of a phase (tests). */
    public static synchronized void unregister(int phase) {
        if (phase >= 0 && phase < MAX_PHASES) BY_PHASE[phase] = null;
    }

    /** The screen of a phase or null; allocation free, called every frame. */
    public static PanelScreen forPhase(int phase) {
        return phase >= 0 && phase < MAX_PHASES ? BY_PHASE[phase] : null;
    }
}
