package se.felixarvid.thorsurvivors.screen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** v1.5 B8 (80): when the run pauses by itself and what lets it go. */
public class PauseGuardTest {
    @Test
    public void focusLossPausesAndRegainingFocusDoesNotResume() {
        PauseGuard g = new PauseGuard();
        assertFalse(g.held());
        assertTrue(g.focusChanged(false));
        assertTrue(g.held());
        assertFalse(g.focusChanged(true));
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
