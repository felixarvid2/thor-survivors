package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Entities;
import se.felixarvid.thorsurvivors.game.Input;
import se.felixarvid.thorsurvivors.game.Events;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.ui.PanelRenderer;

import java.lang.reflect.Method;

/**
 * The minute 20-30 events on screen: Signal Tower (world ring and structure, minimap ring with its arc), Collapse and Slag Rain
 * (falling rubble rings, slow patches), the Lumen Surge (glowing enemies and gems, banner), the Beacon (structure, health bar,
 * minimap), The Tide (banner, flicker). Screenshots go to build/screenshots (events_*.png). Also: the world and the minimap draw
 * without allocating with the events active.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class EventsShotsTest {
    private static final float DT = 1f / 60f;

    private static void frames(Shots h, int n) {
        for (int i = 0; i < n; i++) {
            h.eng.sim.hp = h.eng.sim.maxHp;
            h.eng.input.clearMovement();
            if (h.eng.phase == Engine.PH_LEVELUP || h.eng.phase == Engine.PH_CHEST) h.eng.input.post(Input.CMD_CONFIRM, 0, 0, 0);
            else if (h.eng.phase == Engine.PH_MERCHANT || h.eng.phase == Engine.PH_EQUIP) h.eng.input.post(Input.CMD_BACK, 0, 0, 0);
            h.eng.advance(DT);
        }
    }

    /** A run of the stage jumped to just before the event, spawns off, until the event is active. */
    private static Sim at(Shots h, String stage, String id, boolean spawns) {
        Sim s = h.startRun(stage, "WHIP", 7L);
        if (!spawns) s.noSpawnsForTest();
        s.enemyN = 0;
        int when = -1;
        for (int i = 0; i < s.stage.eventId.length; i++) if (s.stage.eventId[i].equals(id)) when = s.stage.eventAt[i];
        s.time = when - 0.05;
        frames(h, 12);
        return s;
    }

    private static void crowd(Sim s, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * 2.399, r = 60 + (i * 37) % 260;
            Entities.Enemy e = s.spawnAt(i % 5 == 0 ? "SERGEANT" : i % 3 == 0 ? "SPORE_HOUND" : "COLONIST",
                    s.px + (float) (Math.cos(a) * r * 1.4), s.py + (float) (Math.sin(a) * r * 0.8), false);
            if (e != null) e.speed = 0f;
        }
        for (int i = 0; i < 25; i++) s.addPickupForTest(Entities.K_GEM, s.px + (i % 5 - 2) * 70f, s.py + (i / 5 - 2) * 55f, i % 7 == 0 ? 12f : i % 3 == 0 ? 4f : 1f);
    }

    @Test
    public void signalTower() throws Exception {
        Shots h = new Shots();
        Sim s = at(h, "FLATS", "SIGNAL_TOWER", false);
        Events ev = s.events;
        assertTrue(ev.towerOn);
        // the tower in view, the hero walking toward it: banner just started, then capturing with half progress
        s.px = ev.tx - 190f;
        s.py = ev.ty + 70f;
        frames(h, 20);
        Shots.save(h.renderTop(), "events_tower_banner.png");
        s.px = ev.tx - 60f;
        s.py = ev.ty + 40f;
        ev.towerProg = 0f;
        frames(h, 400);
        Shots.save(h.renderTop(), "events_tower_capture.png");
        Shots.save(h.renderBottom(), "events_tower_minimap.png");
    }

    @Test
    public void collapseAndSlagRain() throws Exception {
        Shots h = new Shots();
        for (String[] c : new String[][]{{"MINES", "COLLAPSE", "events_collapse.png"}, {"FLATS", "SLAG_RAIN", "events_slag_rain.png"}}) {
            Sim s = at(h, c[0], c[1], false);
            crowd(s, 40);
            for (int i = 0; i < 60 * 9; i++) { // nine seconds in: rings warning, patches lying
                s.invuln = 1e9f;
                frames(h, 1);
                if (i == 40) Shots.save(h.renderTop(), c[2].replace(".png", "_banner.png"));
            }
            Shots.save(h.renderTop(), c[2]);
        }
    }

    @Test
    public void lumenSurge() throws Exception {
        Shots h = new Shots();
        Sim s = at(h, "MINES", "LUMEN_SURGE", false);
        crowd(s, 60);
        frames(h, 40);
        Shots.save(h.renderTop(), "events_surge_banner.png");
        frames(h, 60 * 4);
        Shots.save(h.renderTop(), "events_surge.png");
    }

    @Test
    public void beacon() throws Exception {
        Shots h = new Shots();
        Sim s = at(h, "MINES", "BEACON", false);
        Events ev = s.events;
        assertTrue(ev.beaconOn);
        s.px = ev.bx - 150f;
        s.py = ev.by + 30f;
        crowd(s, 20);
        for (int i = 0; i < 8; i++) {
            Entities.Enemy e = s.spawnAt("COLONIST", ev.bx + 40f + i * 14f, ev.by - 10f + (i % 3) * 18f, false);
            e.toBeacon = true;
            e.speed = 0f;
        }
        frames(h, 60 * 12);
        Shots.save(h.renderTop(), "events_beacon.png");
        Shots.save(h.renderBottom(), "events_beacon_minimap.png");
    }

    @Test
    public void tide() throws Exception {
        Shots h = new Shots();
        Sim s = at(h, "FLATS", "TIDE", true);
        frames(h, 60 * 2);
        Shots.save(h.renderTop(), "events_tide_banner.png");
        frames(h, 60 * 44);
        s.enemyN = 0;
        frames(h, 60 * 2);
        Shots.save(h.renderTop(), "events_tide_lull.png");
    }

    private static final long SPIKE = 8192;

    @Test
    public void worldAndMinimapDrawWithoutAllocatingWithEventsActive() throws Exception {
        Object mx = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
        Method alloc = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", long.class);
        long tid = Thread.currentThread().getId();
        long probe = (Long) alloc.invoke(mx, tid);
        probe = (Long) alloc.invoke(mx, tid) - probe;
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Bitmap bp = Bitmap.createBitmap(1240, 1080, Bitmap.Config.ARGB_8888);
        Canvas cp = new Canvas(bp);
        PanelRenderer panel = new PanelRenderer(false);
        String[][] cases = {{"MINES", "COLLAPSE"}, {"MINES", "LUMEN_SURGE"}, {"MINES", "BEACON"}, {"FLATS", "SIGNAL_TOWER"}, {"FLATS", "TIDE"}};
        for (String[] cs : cases) {
            Shots h = new Shots();
            Sim s = at(h, cs[0], cs[1], false);
            crowd(s, 40);
            if (s.events.beaconOn) {
                s.px = s.events.bx - 120f;
                s.py = s.events.by;
            }
            if (s.events.towerOn) {
                s.px = s.events.tx - 80f;
                s.py = s.events.ty;
            }
            for (int i = 0; i < 80; i++) {
                s.invuln = 1e9f;
                frames(h, 1);
                h.world.draw(c, 1920, 1080, h.eng, true);
                panel.draw(cp, 1240, 1080);
            }
            long bytes = 0;
            int spikes = 0, n = 100;
            for (int i = 0; i < n; i++) {
                s.invuln = 1e9f;
                frames(h, 1);
                long a0 = (Long) alloc.invoke(mx, tid);
                h.world.draw(c, 1920, 1080, h.eng, true);
                panel.draw(cp, 1240, 1080);
                long d = (Long) alloc.invoke(mx, tid) - a0 - probe;
                if (d >= SPIKE) spikes++; else bytes += d;
            }
            System.out.printf("PERF events draw %s %s: %d B/frame (+%d spikes)%n", cs[0], cs[1], bytes / n, spikes);
            assertTrue(cs[1] + ": " + spikes + " spikes", spikes <= 6);
            assertTrue(cs[1] + " allocates " + bytes / n + " B per frame", bytes / n < 512);
        }
    }
}
