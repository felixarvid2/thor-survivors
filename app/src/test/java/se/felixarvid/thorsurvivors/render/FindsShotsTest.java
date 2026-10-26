package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.game.Sim;

/** World finds on screen: the edge arrow and minimap arrows at the start of a run, and a cache and a locker in view. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class FindsShotsTest {
    @Test
    public void findsAreSignposted() throws Exception {
        Shots h = new Shots();
        Sim s = h.startRun("FLATS", "WHIP", 7L);
        s.noSpawnsForTest();
        s.enemyN = 0;
        assertTrue(s.findN == Sim.MAX_FINDS);
        for (int i = 0; i < 240; i++) {
            s.hp = s.maxHp;
            h.eng.input.stickX = 0;
            h.eng.input.stickY = 0;
            h.eng.advance(1f / 60f);
        }
        Shots.save(h.renderTop(), "finds_arrow_top.png");
        Shots.save(h.renderBottom(), "finds_minimap_bottom.png");
        int weapon = -1, gear = -1;
        for (int i = 0; i < s.findN; i++) {
            if (s.findKind[i] == Sim.FIND_WEAPON && weapon < 0) weapon = i;
            if (s.findKind[i] == Sim.FIND_GEAR && gear < 0) gear = i;
        }
        s.findX[weapon] = s.px - 160; s.findY[weapon] = s.py - 40;
        s.findX[gear] = s.px + 160; s.findY[gear] = s.py - 40;
        h.eng.advance(1f / 60f);
        Shots.save(h.renderTop(), "finds_in_view.png");
    }
}
