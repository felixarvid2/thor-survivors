package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Input;
import se.felixarvid.thorsurvivors.game.Pois;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.ui.PanelRenderer;

import java.lang.reflect.Method;

/**
 * The places on screen (poi_*.png in build/screenshots): an outpost asleep and awake, a nest with its hounds, a relay terminal in
 * the world and its choice screen, a slag vent warning and bursting, a steam jet, the vault sealed and open, and the minimap with
 * every marker. Also the allocation rule for the world and the terminal screen.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class PoisShotsTest {
    private static final float DT = 1f / 60f;

    private static void frames(Shots h, int n) {
        for (int i = 0; i < n; i++) {
            h.eng.sim.hp = h.eng.sim.maxHp;
            h.eng.input.clearMovement();
            h.eng.advance(DT);
        }
    }

    /** Frames until the terminal screen is up (level-ups and chests that the walk collects are waved away). */
    private static void untilTerminal(Shots h) {
        for (int i = 0; i < 600 && h.eng.phase != Engine.PH_TERMINAL; i++) {
            h.eng.sim.pendingLevelUps = 0;
            h.eng.sim.pendingChests = 0;
            if (h.eng.phase == Engine.PH_LEVELUP || h.eng.phase == Engine.PH_CHEST) h.eng.input.post(Input.CMD_CONFIRM, 0, 0, 0);
            frames(h, 1);
        }
    }

    private static Sim run(Shots h, String stage, long seed) {
        Sim s = h.startRun(stage, "WHIP", seed);
        s.noSpawnsForTest();
        s.enemyN = 0;
        s.pois.calmUntil = 0;
        return s;
    }

    /** Where a nest / terminal / vent first shows on a coarse grid. kind 0 vent, 1 nest, 2 terminal. */
    private static float[] find(Shots h, int kind) {
        Sim s = h.eng.sim;
        for (int gy = -12; gy <= 12; gy++) {
            for (int gx = -12; gx <= 12; gx++) {
                s.px = gx * 500f;
                s.py = gy * 500f;
                frames(h, 1);
                if (kind == 0 && s.pois.ventN > 0) return new float[]{s.pois.ventX[0], s.pois.ventY[0]};
                if (kind == 2 && s.pois.termN > 0) return new float[]{s.pois.termX[0], s.pois.termY[0]};
                if (kind == 1) for (int i = 0; i < s.destN; i++) if (s.destKind[i] == Sim.DK_NEST) return new float[]{s.destX[i], s.destY[i]};
            }
        }
        throw new AssertionError("nothing of kind " + kind);
    }

    private static int vi(Sim s, float[] v) {
        for (int i = 0; i < s.pois.ventN; i++) if (Math.abs(s.pois.ventX[i] - v[0]) < 0.01f && Math.abs(s.pois.ventY[i] - v[1]) < 0.01f) return i;
        return 0;
    }

    @Test
    public void outpostAsleepAndAwake() throws Exception {
        Shots h = new Shots();
        Sim s = run(h, "FLATS", 7L);
        Pois p = s.pois;
        s.px = p.outX[2] + 330f;
        s.py = p.outY[2] + 40f;
        frames(h, 20);
        Shots.save(h.renderTop(), "poi_outpost_asleep.png");
        s.px = p.outX[2] + 200f;
        frames(h, 40);
        assertTrue(p.outWoken[2]);
        Shots.save(h.renderTop(), "poi_outpost_awake.png");
        for (int i = 0; i < s.enemyN; i++) s.killForTest(s.enemies[i]);
        frames(h, 30);
        s.px = p.outX[2];
        s.py = p.outY[2] + 90f;
        frames(h, 5);
        Shots.save(h.renderTop(), "poi_outpost_cleared.png");
    }

    @Test
    public void nestWithItsHounds() throws Exception {
        Shots h = new Shots();
        Sim s = run(h, "MINES", 9L);
        float[] n = find(h, 1);
        s.px = n[0] + 230f;
        s.py = n[1] + 60f;
        frames(h, 60 * 5);
        Shots.save(h.renderTop(), "poi_nest.png");
        for (int i = 0; i < 4; i++) s.hitDestForTest(n[0], n[1], 10f); // damaged: shows its health bar
        frames(h, 2);
        Shots.save(h.renderTop(), "poi_nest_hit.png");
    }

    @Test
    public void terminalInTheWorldAndItsScreen() throws Exception {
        Shots h = new Shots();
        Sim s = run(h, "FLATS", 2L);
        float[] t = find(h, 2);
        s.px = t[0] + 160f;
        s.py = t[1] + 50f;
        frames(h, 20);
        Shots.save(h.renderTop(), "poi_terminal_world.png");
        s.px = t[0];
        s.py = t[1];
        frames(h, 70); // 1.2 s of the 2 s hold
        Shots.save(h.renderTop(), "poi_terminal_hold.png");
        untilTerminal(h);
        assertEquals(Engine.PH_TERMINAL, h.eng.phase);
        Shots.save(h.renderBottom(), "poi_terminal_panel.png");
        Shots.save(h.renderTop(), "poi_terminal_top.png");
        h.eng.input.post(Input.CMD_NAV, Input.DOWN, 0, 0);
        h.eng.advance(DT);
        h.eng.input.post(Input.CMD_TAP, Engine.PH_TERMINAL, 1, 0); // first tap focuses
        h.eng.advance(DT);
        assertEquals(Engine.PH_TERMINAL, h.eng.phase);
        h.eng.input.post(Input.CMD_TAP, Engine.PH_TERMINAL, 1, 0); // second picks
        h.eng.advance(DT);
        assertEquals(Engine.PH_PLAYING, h.eng.phase);
        assertTrue(s.pois.buff >= 0);
        frames(h, 5);
        Shots.save(h.renderTop(), "poi_terminal_used.png");
    }

    @Test
    public void terminalPadAAndBAnswer() throws Exception {
        Shots h = new Shots();
        Sim s = run(h, "FLATS", 2L);
        float[] t = find(h, 2);
        s.px = t[0];
        s.py = t[1];
        untilTerminal(h);
        assertEquals(Engine.PH_TERMINAL, h.eng.phase);
        h.eng.input.post(Input.CMD_BACK, 0, 0, 0); // B declines
        h.eng.advance(DT);
        assertEquals(Engine.PH_PLAYING, h.eng.phase);
        assertEquals(-1, s.pois.buff);
        s.px = t[0] + 200f;
        frames(h, 30);
        s.px = t[0];
        untilTerminal(h);
        assertEquals(Engine.PH_TERMINAL, h.eng.phase);
        h.eng.input.post(Input.CMD_CONFIRM, 0, 0, 0); // A picks the focused card
        h.eng.advance(DT);
        assertEquals(Engine.PH_PLAYING, h.eng.phase);
        assertTrue(s.pois.buff >= 0);
    }

    @Test
    public void hazardFields() throws Exception {
        Shots h = new Shots();
        Sim s = run(h, "FLATS", 2L);
        float[] v = find(h, 0);
        s.px = v[0] - 170f;
        s.py = v[1] + 50f;
        for (int i = 0; i < 60 * 12 && !(s.pois.ventPhase(vi(s, v)) > 3.4f); i++) frames(h, 1);
        Shots.save(h.renderTop(), "poi_vent_warn.png");
        for (int i = 0; i < 60 * 12 && !(s.pois.ventBurst[vi(s, v)] > 0.45f); i++) frames(h, 1);
        Shots.save(h.renderTop(), "poi_vent_burst.png");
        Shots h2 = h;
        Sim m = run(h2, "MINES", 2L);
        float[] j = find(h2, 0);
        m.px = j[0] - 150f;
        m.py = j[1] + 60f;
        frames(h2, 40);
        Shots.save(h2.renderTop(), "poi_jet.png");
    }

    @Test
    public void vaultBeforeAndAfter() throws Exception {
        Shots h = new Shots();
        Sim s = run(h, "FLATS", 7L);
        Pois p = s.pois;
        s.px = p.vaultX - 40f;
        s.py = p.vaultY + 260f;
        frames(h, 30);
        Shots.save(h.renderTop(), "poi_vault_sealed.png");
        Shots.save(h.renderBottom(), "poi_minimap.png");
        s.hitDestForTest(p.vbX[1], p.vbY[1], 5f);
        frames(h, 8);
        assertEquals(Pois.V_OPEN, p.vaultState);
        Shots.save(h.renderTop(), "poi_vault_blast.png");
        frames(h, 50);
        Shots.save(h.renderTop(), "poi_vault_open.png");
    }

    // ------------------------------------------------------------------ allocation

    @Test
    public void worldAndTerminalPanelDrawWithoutAllocatingWithPlacesOnScreen() throws Exception {
        Object mx = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
        Method alloc = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", long.class);
        long tid = Thread.currentThread().getId();
        long probe = (Long) alloc.invoke(mx, tid);
        probe = (Long) alloc.invoke(mx, tid) - probe;
        Shots h = new Shots();
        Sim s = run(h, "FLATS", 7L);
        Pois p = s.pois;
        s.px = p.outX[2] + 200f;
        s.py = p.outY[2] + 40f;
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        for (int mode = 2; mode >= 0; mode--) {
            h.eng.meta.lighting = mode;
            for (int i = 0; i < 80; i++) {
                frames(h, 1);
                h.world.draw(c, 1920, 1080, h.eng, true);
            }
            long bytes = 0;
            int spikes = 0, n = 100;
            for (int i = 0; i < n; i++) {
                frames(h, 1);
                long a0 = (Long) alloc.invoke(mx, tid);
                h.world.draw(c, 1920, 1080, h.eng, true);
                long d = (Long) alloc.invoke(mx, tid) - a0 - probe;
                if (d >= 8192) spikes++; else bytes += d;
            }
            System.out.printf("PERF pois world lighting=%d: %d B/frame (+%d spikes)%n", mode, bytes / n, spikes);
            assertTrue("spikes " + spikes, spikes <= 6);
            assertTrue("world with an outpost allocates " + bytes / n + " B per frame", bytes / n < 256);
        }
        float[] t = find(h, 2);
        s.px = t[0];
        s.py = t[1];
        s.pendingLevelUps = 0;
        s.pendingChests = 0;
        untilTerminal(h);
        assertEquals(Engine.PH_TERMINAL, h.eng.phase);
        Bitmap bp = Bitmap.createBitmap(1240, 1080, Bitmap.Config.ARGB_8888);
        Canvas cp = new Canvas(bp);
        PanelRenderer panel = new PanelRenderer(false);
        for (int i = 0; i < 60; i++) panel.draw(cp, 1240, 1080);
        long a0 = (Long) alloc.invoke(mx, tid);
        for (int i = 0; i < 60; i++) panel.draw(cp, 1240, 1080);
        long per = ((Long) alloc.invoke(mx, tid) - a0 - 60 * probe) / 60;
        assertTrue("terminal panel allocates " + per + " B per frame", per < 256);
    }
}
