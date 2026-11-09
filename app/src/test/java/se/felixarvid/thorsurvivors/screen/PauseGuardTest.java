package se.felixarvid.thorsurvivors.screen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** v1.5 B8 (80): when the run pauses by itself and what lets it go. */
public class PauseGuardTest {
    @Test
    public void focusLossNeverPauses() {
        // v1.5 integration: on the dual-screen Thor a tap on the bottom screen takes the focus from the game window
        PauseGuard g = new PauseGuard();
        assertFalse(g.focusChanged(false));
        assertFalse("a bottom-screen tap must not hold the run", g.held());
        assertFalse(g.focusChanged(true));
        assertFalse(g.held());
    }

    @Test
    public void activityPausePausesAndOnlyInputReleasesIt() {
        PauseGuard g = new PauseGuard();
        assertTrue(g.activityPaused());
        assertTrue(g.held());
        assertFalse("focus coming back does not resume", g.focusChanged(true));
        assertTrue("still waiting for the player", g.held());
        g.userInput();
        assertFalse(g.held());
    }

    @Test
    public void losingTheSecondScreenPausesOnce() {
        PauseGuard g = new PauseGuard();
        assertFalse("first look at the screens", g.screensChanged(true));
        assertFalse(g.screensChanged(true));
        assertTrue(g.screensChanged(false));
        assertTrue(g.held());
        assertFalse("already single", g.screensChanged(false));
        g.userInput();
        assertFalse("coming back never pauses", g.screensChanged(true));
        assertFalse(g.held());
    }

    @Test
    public void startingSingleScreenIsNotALoss() {
        PauseGuard g = new PauseGuard();
        assertFalse(g.screensChanged(false));
        assertFalse(g.held());
    }
}
