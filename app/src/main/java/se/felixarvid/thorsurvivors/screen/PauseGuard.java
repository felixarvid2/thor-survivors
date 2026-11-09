package se.felixarvid.thorsurvivors.screen;

/**
 * v1.5 B8 (item 80): when the run pauses by itself. The Activity pausing (another app, the system shade over the app,
 * the screen turning off) or the second screen going away pauses the run; it stays paused until the player gives input
 * (the game already waits in {@code PH_PAUSED} for a button), so coming back never resumes it. Pure state, no android.*.
 *
 * <p>v1.5 integration: losing window focus alone does NOT pause. On the dual-screen AYN Thor a tap on the bottom screen
 * moves focus to the panel window (Presentation or SecondaryActivity) while the game keeps running, so a focus-based
 * pause would stop the run on every touch of the bottom screen.
 */
final class PauseGuard {
    private boolean dual, dualKnown, held;

    /** The main window gained or lost focus. Never pauses (see the class note); always false. */
    boolean focusChanged(boolean hasFocus) {
        return false;
    }

    /** The Activity went to onPause. True = post an auto-pause now (held until the player presses something). */
    boolean activityPaused() {
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
