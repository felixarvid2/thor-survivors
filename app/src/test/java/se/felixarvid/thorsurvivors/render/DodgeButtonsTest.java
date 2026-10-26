package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.screen.InputRouter;

/** While playing, A, B, X, Y and the shoulder buttons dodge (B no longer pauses); in the pause menu B still goes back. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class DodgeButtonsTest {
    private static void press(int code) {
        InputRouter.onKey(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        InputRouter.onKey(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    @Test
    public void faceAndShoulderButtonsDodgeWhilePlaying() throws Exception {
        Shots h = new Shots();
        h.startRun("FLATS", "WHIP", 3L);
        Engine eng = h.eng;
        int[] codes = {KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2};
        for (int code : codes) {
            assertEquals(Engine.PH_PLAYING, eng.phase);
            eng.input.dodgeReq = false;
            press(code);
            assertTrue(KeyEvent.keyCodeToString(code) + " dodges", eng.input.dodgeReq);
            eng.input.dodgeReq = false;
            eng.advance(1f / 60f);
            assertEquals(KeyEvent.keyCodeToString(code) + " does not pause", Engine.PH_PLAYING, eng.phase);
        }
        press(KeyEvent.KEYCODE_BUTTON_START);
        eng.advance(1f / 60f);
        assertEquals(Engine.PH_PAUSED, eng.phase);
        eng.input.dodgeReq = false;
        press(KeyEvent.KEYCODE_BUTTON_B);
        assertFalse(eng.input.dodgeReq);
        eng.advance(1f / 60f);
        assertEquals("B resumes from the pause menu", Engine.PH_PLAYING, eng.phase);
    }
}
