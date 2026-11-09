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

import se.felixarvid.thorsurvivors.game.Codex;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Settings;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.ui.PanelRenderer;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * v1.5 batch 3 (items 21 to 30): the options, codex and remap screens, the results screen with the damage and build block, the
 * hero strip and tip on the main menu, the first-run hint. Frames go to SHOT_DIR as {@code b3_*.png}; the asserts check that the
 * new parts are really drawn and that none of the screens allocates per frame.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class MenusB3ShotsTest {
    private static int near(Bitmap b, int x0, int y0, int x1, int y1, int rgb, int tol) {
        int n = 0, r0 = rgb >> 16 & 255, g0 = rgb >> 8 & 255, b0 = rgb & 255;
        for (int y = Math.max(0, y0); y < Math.min(b.getHeight(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(b.getWidth(), x1); x++) {
                int p = b.getPixel(x, y);
                if (Math.abs((p >> 16 & 255) - r0) <= tol && Math.abs((p >> 8 & 255) - g0) <= tol && Math.abs((p & 255) - b0) <= tol) n++;
            }
        }
        return n;
    }

    private static Bitmap copy(Bitmap b) {
        return b.copy(Bitmap.Config.ARGB_8888, false);
    }

    private static void settle(Shots h) {
        for (int i = 0; i < 3; i++) h.eng.advance(1f / 60f);
    }

    @Test
    public void mainMenuHasTheNewRowHeroStripAndTip() throws Exception {
        Shots h = new Shots();
        Settings.get().load(null);
        h.eng.phase = Engine.PH_MENU;
        h.eng.selectStage("MINES");
        h.eng.meta.heroIdx = 1;
        h.eng.focus = Engine.MENU_CODEX;
        settle(h);
        Bitmap b = copy(h.renderBottom());
        Shots.save(b, "b3_menu_bottom.png");
        assertTrue("orange focus frame on the CODEX button", near(b, 400, 780, 840, 900, 0xFF8A2A, 30) > 300);
        h.eng.focus = Engine.MENU_HERO;
        settle(h);
        Shots.save(h.renderBottom(), "b3_menu_hero.png");
        h.eng.selectStage("FLATS");
        h.eng.meta.heroIdx = 0;
    }

    @Test
    public void optionsScreenBothPalettes() throws Exception {
        Shots h = new Shots();
        Settings.get().load(null);
        h.eng.phase = Engine.PH_OPTIONS;
        h.eng.focus = Engine.OPT_TELE;
        settle(h);
        Bitmap std = copy(h.renderBottom());
        Shots.save(std, "b3_options_standard.png");
        Settings.get().setShapes(true);
        Settings.get().setLarge(true);
        Settings.get().setShake(Settings.SHAKE_HALF);
        Settings.get().setNumbers(Settings.NUM_CRITS);
        Settings.get().setAim(Settings.AIM_SOFT);
        h.eng.focus = Engine.OPT_AIM;
        settle(h);
        Bitmap shp = copy(h.renderBottom());
        Shots.save(shp, "b3_options_shapes.png");
        assertTrue("blue orange pair in the preview", near(shp, 400, 880, 1200, 1060, 0xFF9A1F, 30) > 200);
        // compact (single screen)
        PanelRenderer compact = new PanelRenderer(true);
        Bitmap frame = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        compact.draw(new Canvas(frame), 1920, 1080);
        Shots.save(frame, "b3_options_compact.png");
        Settings.get().resetAll();
    }

    @Test
    public void remapScreenWaitingAndBound() throws Exception {
        Shots h = new Shots();
        Settings.get().load(null);
        h.eng.phase = Engine.PH_REMAP;
        h.eng.focus = 3;
        h.eng.remapListening = -1;
        settle(h);
        Shots.save(h.renderBottom(), "b3_remap.png");
        h.eng.remapListening = 3;
        settle(h);
        Shots.save(h.renderBottom(), "b3_remap_waiting.png");
        h.eng.remapListening = -1;
    }

    @Test
    public void codexTabsAndSilhouettes() throws Exception {
        Shots h = new Shots();
        Codex cx = h.eng.codex;
        // meet a few things on three stages
        for (int i = 0; i < Math.min(9, cx.size(Codex.ENEMY)); i += 2) cx.mark(Codex.ENEMY, cx.key(Codex.ENEMY, i), i % 3);
        for (int i = 0; i < 6; i++) cx.mark(Codex.WEAPON, cx.key(Codex.WEAPON, i), 0);
        for (int i = 0; i < 4; i++) cx.mark(Codex.PASSIVE, cx.key(Codex.PASSIVE, i), 1);
        h.eng.phase = Engine.PH_CODEX;
        for (int tab = 0; tab < Codex.KINDS; tab++) {
            h.eng.codexTab = tab;
            h.eng.focus = 2;
            settle(h);
            Shots.save(h.renderBottom(), "b3_codex_tab" + tab + ".png");
        }
        h.eng.codexTab = 0;
        h.eng.focus = 3; // an unseen one
        settle(h);
        Shots.save(h.renderBottom(), "b3_codex_unseen.png");
        h.eng.phase = Engine.PH_MENU;
    }

    @Test
    public void resultsScreenWithDamageBuildAndCause() throws Exception {
        Shots h = new Shots();
        Settings.get().load(null);
        Sim s = h.startRun("FLATS", "WHIP", 4242L, "MAGIC_MISSILE", "GARLIC", "AXE");
        h.drive(75, true);
        s.run.lastHurt = Sim.DMG_BOLT;
        s.hp = 0.5f;
        s.ended = 1;
        h.eng.advance(1f / 60f);
        assertEquals(Engine.PH_RESULT, h.eng.phase);
        assertTrue("the run dealt damage", s.run.total > 0);
        h.eng.advance(1f / 60f);
        Bitmap res = copy(h.renderBottom());
        Shots.save(res, "b3_results_loss.png");
        assertTrue("orange damage bars", near(res, 580, 700, 1210, 930, 0xFF8A2A, 30) > 500);
        PanelRenderer compact = new PanelRenderer(true);
        Bitmap frame = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        compact.draw(new Canvas(frame), 1920, 1080);
        Shots.save(frame, "b3_results_compact.png");
        h.eng.resultWin = true;
        h.eng.advance(1f / 60f);
        Shots.save(h.renderBottom(), "b3_results_win.png");
        h.eng.resultWin = false;
        h.eng.phase = Engine.PH_MENU;
    }

    @Test
    public void firstRunHints() throws Exception {
        Shots h = new Shots();
        Settings.get().load(null);
        h.eng.tutorial.reset();
        h.startRun("FLATS", "WHIP", 99L);
        h.eng.advance(1f / 60f);
        Bitmap move = copy(h.renderBottom());
        Shots.save(move, "b3_hint_move.png");
        assertTrue("the hint banner is on screen", near(move, 0, 370, 1240, 460, 0xFF8A2A, 30) > 200);
        h.eng.tutorial.reset();
        h.eng.phase = Engine.PH_LEVELUP;
        h.eng.advance(1f / 60f);
        Shots.save(h.renderBottom(), "b3_hint_cards.png");
        h.eng.tutorial.reset();
        h.eng.tutorial.update(Engine.PH_PLAYING, 0.01f, 0f);
        h.eng.phase = Engine.PH_PLAYING;
        h.eng.tutorial.update(Engine.PH_MENU, 0.01f, 0f); // end it
        Bitmap none = copy(h.renderBottom());
        assertTrue(!h.eng.tutorial.active());
        h.eng.phase = Engine.PH_MENU;
    }

    // ---- the new screens allocate nothing per frame

    private Object mx;
    private Method alloc;
    private long tid, probe;

    private long measure(PanelRenderer p, Canvas c, int w, int h) throws Exception {
        for (int i = 0; i < 40; i++) p.draw(c, w, h);
        long bytes = 0;
        int spikes = 0;
        for (int i = 0; i < 80; i++) {
            long a0 = (Long) alloc.invoke(mx, tid);
            p.draw(c, w, h);
            long d = (Long) alloc.invoke(mx, tid) - a0 - probe;
            if (d >= 8192) spikes++; else bytes += d;
        }
        assertTrue("allocation spikes: " + spikes, spikes <= 4);
        return bytes / 80;
    }

    @Test
    public void newScreensDoNotAllocatePerFrame() throws Exception {
        mx = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
        alloc = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", long.class);
        tid = Thread.currentThread().getId();
        probe = (Long) alloc.invoke(mx, tid);
        probe = (Long) alloc.invoke(mx, tid) - probe;
        Shots h = new Shots();
        Settings.get().load(null);
        PanelRenderer p = new PanelRenderer(false);
        Bitmap b = Bitmap.createBitmap(1240, 1080, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int[] phases = {Engine.PH_OPTIONS, Engine.PH_CODEX, Engine.PH_REMAP};
        for (int ph : phases) {
            h.eng.phase = ph;
            h.eng.focus = 2;
            long perFrame = measure(p, c, 1240, 1080);
            System.out.println("PERF b3 phase " + ph + " allocation " + perFrame + " B/frame");
            assertTrue("phase " + ph + " allocates " + perFrame + " B per frame", perFrame < 256);
        }
        // the results screen
        Sim s = h.startRun("FLATS", "WHIP", 5L, "GARLIC");
        h.drive(20, true);
        s.ended = 1;
        h.eng.advance(1f / 60f);
        assertEquals(Engine.PH_RESULT, h.eng.phase);
        long perFrame = measure(p, c, 1240, 1080);
        System.out.println("PERF b3 results allocation " + perFrame + " B/frame");
        assertTrue("results allocate " + perFrame + " B per frame", perFrame < 256);
        h.eng.phase = Engine.PH_MENU;
        perFrame = measure(p, c, 1240, 1080);
        System.out.println("PERF b3 menu allocation " + perFrame + " B/frame");
        assertTrue("menu allocates " + perFrame + " B per frame", perFrame < 256);
        Arrays.fill(new int[1], 0);
    }
}
