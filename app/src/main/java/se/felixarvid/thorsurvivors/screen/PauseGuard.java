package se.felixarvid.thorsurvivors.screen;

/**
 * v1.5 B8 (item 80): when the run pauses by itself. Losing window focus (a system shade, another app) or the second
 * screen going away pauses the run; it stays paused until the player gives input (the game already waits in
 * {@code PH_PAUSED} for a button), so regaining focus never resumes it. Pure state, no android.*.
 */
final class PauseGuard {
    private boolean dual, dualKnown, held;

    /** The main window gained or lost focus. True = post an auto-pause now. */
    boolean focusChanged(boolean hasFocus) {
        if (hasFocus) return false;
        held = true;
        return true;
    }

    /** The screen setup was re-evaluated. True = the second screen just disappeared: post an auto-pause. */
    boolean screensChanged(boolean nowDual) {
        boolean lost = dualKnown && dual && !nowDual;
        dual = nowDual;
        dualKnown = true;
        if (lost) held = true;
        return lost;
    }

    /** The player pressed something: a held pause may be left. */
    void userInput() {
        held = false;
    }

    /** True while an automatic pause waits for the player. */
    boolean held() {
        return held;
    }
}
