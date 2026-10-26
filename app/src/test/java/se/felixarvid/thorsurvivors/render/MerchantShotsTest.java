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
import se.felixarvid.thorsurvivors.game.Merchant;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.ui.PanelRenderer;

import java.lang.reflect.Method;

/**
 * The Salvager on screen: in the world (baked technician, ring, label), the edge arrow and minimap marker, the shop on the
 * bottom screen and its banner on the top screen. Screenshots go to build/screenshots (merchant_*.png). Also the allocation rule:
 * with the Salvager on screen the world and the shop panel draw without allocating, like every other screen.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class MerchantShotsTest {
    private static final float DT = 1f / 60f;

    private static void frames(Shots h, int n) {
        for (int i = 0; i < n; i++) {
            h.eng.sim.hp = h.eng.sim.maxHp;
            h.eng.input.clearMovement();
            h.eng.advance(DT);
        }
    }

    /** A FLATS run at the first arrival with the Salvager on the map. */
    private static Sim arrived(Shots h) {
        Sim s = h.startRun("FLATS", "WHIP", 7L);
        s.noSpawnsForTest();
        s.enemyN = 0;
        s.time = 269.9;
        frames(h, 12);
        assertTrue(s.merchant.present);
        return s;
    }

    @Test
    public void theSalvagerOnBothScreens() throws Exception {
        Shots h = new Shots();
        Sim s = arrived(h);
        Merchant m = s.merchant;
        // far away: the amber arrow on the top edge, the amber marker on the minimap
        m.x = s.px + 760f;
        m.y = s.py - 120f;
        frames(h, 3);
        Shots.save(h.renderTop(), "merchant_arrow_top.png");
        Shots.save(h.renderBottom(), "merchant_minimap_bottom.png");
        // in view: the technician with its ring, light and label
        m.x = s.px + 170f;
        m.y = s.py + 30f;
        frames(h, 3);
        Shots.save(h.renderTop(), "merchant_world.png");
        // beside it: the shop opens, the run pauses
        s.px = m.x + 12f;
        s.py = m.y;
        s.gold = 95; // the Medkit (40) and the card (70) are affordable, the gear (100) is not
        frames(h, 2);
        assertEquals(Engine.PH_MERCHANT, h.eng.phase);
        Shots.save(h.renderBottom(), "merchant_panel.png");
        Shots.save(h.renderTop(), "merchant_top_phase.png");
        // buy the Medkit with two taps, look at the SOLD card and the toast
        h.eng.input.post(Input.CMD_TAP, Engine.PH_MERCHANT, 2, 0);
        h.eng.input.post(Input.CMD_TAP, Engine.PH_MERCHANT, 2, 0);
        h.eng.advance(DT);
        assertTrue(m.sold[2]);
        h.renderBottom();
        Shots.save(h.renderBottom(), "merchant_panel_bought.png");
    }

    // ------------------------------------------------------------------ allocation

    private static final long SPIKE = 8192;

    @Test
    public void worldAndShopPanelDrawWithoutAllocatingWithTheSalvagerOnScreen() throws Exception {
        Object mx = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
        Method alloc = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", long.class);
        long tid = Thread.currentThread().getId();
        long probe = (Long) alloc.invoke(mx, tid);
        probe = (Long) alloc.invoke(mx, tid) - probe;
        Shots h = new Shots();
        Sim s = arrived(h);
        s.merchant.x = s.px + 150f;
        s.merchant.y = s.py + 20f;
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Bitmap bp = Bitmap.createBitmap(1240, 1080, Bitmap.Config.ARGB_8888);
        Canvas cp = new Canvas(bp);
        PanelRenderer panel = new PanelRenderer(false), compact = new PanelRenderer(true);
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
                if (d >= SPIKE) spikes++; else bytes += d;
            }
            System.out.printf("PERF merchant world lighting=%d: allocation %d B/frame (+%d one-off spikes)%n", mode, bytes / n, spikes);
            assertTrue("world with the Salvager, mode " + mode + ": " + spikes + " spikes", spikes <= 6);
            assertTrue("world with the Salvager allocates " + bytes / n + " B per frame", bytes / n < 256);
        }
        // the shop panel (full and single-screen overlay) and the top banner
        s.px = s.merchant.x + 10f;
        s.py = s.merchant.y;
        frames(h, 2);
        assertEquals(Engine.PH_MERCHANT, h.eng.phase);
        for (int pass = 0; pass < 3; pass++) {
            PanelRenderer p = pass == 1 ? compact : panel;
            Canvas cc = pass == 1 ? c : cp;
            int w = pass == 1 ? 1920 : 1240;
            if (pass == 2) h.eng.focus = Merchant.SLOTS; // the LEAVE button focused
            for (int i = 0; i < 60; i++) p.draw(cc, w, 1080);
            long bytes = 0;
            int spikes = 0, n = 120;
            for (int i = 0; i < n; i++) {
                long a0 = (Long) alloc.invoke(mx, tid);
                p.draw(cc, w, 1080);
                long d = (Long) alloc.invoke(mx, tid) - a0 - probe;
                if (d >= SPIKE) spikes++; else bytes += d;
            }
            System.out.printf("PERF merchant panel pass %d: allocation %d B/frame (+%d one-off spikes)%n", pass, bytes / n, spikes);
            assertTrue("panel pass " + pass + ": " + spikes + " spikes", spikes <= 6);
            assertTrue("panel allocates " + bytes / n + " B per frame", bytes / n < 256);
        }
        for (int i = 0; i < 60; i++) h.world.draw(c, 1920, 1080, h.eng, true); // the top screen in the shop phase
        long a0 = (Long) alloc.invoke(mx, tid);
        for (int i = 0; i < 60; i++) h.world.draw(c, 1920, 1080, h.eng, true);
        long per = ((Long) alloc.invoke(mx, tid) - a0 - 60 * probe) / 60;
        assertTrue("top banner of the shop allocates " + per + " B per frame", per < 256);
    }
}
