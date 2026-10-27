package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Input;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.WorldGen;
import se.felixarvid.thorsurvivors.game.layout.DebrisLayout;
import se.felixarvid.thorsurvivors.game.stage.WreckRules;
import se.felixarvid.thorsurvivors.ui.PanelRenderer;

import java.lang.reflect.Method;

/**
 * Meridian Wreck on screen (v1.4 chunk D): the debris field (hull plates and void), pads (ready, channelling, resting, the arrival
 * shockwave), gravity wells on, flickering and off with the crowd pulled in, the Vacuum Breach warning and bubble, the Pillar Gate, the
 * Pillar in its three phases, the Rift Hound and Breacher telegraphs, the minimap. Screenshots go to build/screenshots (wreck_*.png).
 * Also: the world and the minimap draw without allocating with everything active.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class WreckShotsTest {
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

    /** A Wreck run with the waves off and an empty field, the rules' tables built. */
    private static Sim wreck(Shots h) {
        Sim s = h.startRun("WRECK", "WHIP", 7L);
        s.noSpawnsForTest();
        s.enemyN = 0;
        s.events.enabled = false;
        frames(h, 3);
        return s;
    }

    private static void crowd(Sim s, float cx, float cy, int n, float r0, float r1) {
        for (int i = 0; i < n; i++) {
            double a = i * 2.399, r = r0 + (i * 37) % (int) Math.max(1, r1 - r0);
            Enemy e = s.spawnAt(i % 5 == 0 ? "SERGEANT" : i % 3 == 0 ? "SPORE_HOUND" : "COLONIST", cx + (float) (Math.cos(a) * r), cy + (float) (Math.sin(a) * r * 0.8), false);
            if (e != null) e.speed = 0f;
        }
    }

    private static float[] firstPad() {
        float[] p = new float[4];
        float best = 1e30f;
        float[] q = new float[4];
        for (int cx = -2; cx <= 2; cx++) {
            for (int cy = -2; cy <= 2; cy++) {
                if (!WorldGen.of("WRECK").pad(cx, cy, q)) continue;
                float d = q[0] * q[0] + q[1] * q[1];
                if (d < best) {
                    best = d;
                    System.arraycopy(q, 0, p, 0, 4);
                }
            }
        }
        assertTrue("a pad pair near the start", best < 1e30f);
        return p;
    }

    /** The nearest well of the table to a point. */
    private static int nearestWell(WreckRules wr, float x, float y) {
        int best = -1;
        float bd = 1e30f;
        for (int i = 0; i < wr.wellN; i++) {
            float dx = wr.wellX[i] - x, dy = wr.wellY[i] - y, d = dx * dx + dy * dy;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        assertTrue("a well in the table", best >= 0);
        return best;
    }

    /** A clock value at which well i is {@code into} seconds into its cycle (cycle 16 s, on for the first 10). */
    private static double wellClock(WreckRules wr, int i, double into) {
        return Math.ceil((200 + wr.wellPhase[i]) / 16.0) * 16.0 - wr.wellPhase[i] + into;
    }

    @Test
    public void debrisFieldHullPlatesAndVoid() throws Exception {
        Shots h = new Shots();
        Sim s = wreck(h);
        Shots.save(h.renderTop(), "wreck_start.png");
        // a spot where the island edge crosses the view: about half void in a 46 x 26 tile window
        WorldGen g = WorldGen.of("WRECK");
        float bx = 0, by = 0, bf = 9f;
        for (int a = 0; a < 360; a += 5) {
            for (int rt = 30; rt <= 90; rt += 6) {
                int tx = (int) (Math.cos(Math.toRadians(a)) * rt), ty = (int) (Math.sin(Math.toRadians(a)) * rt), v = 0;
                for (int i = -23; i < 23; i += 2) for (int j = -13; j < 13; j += 2) if ((g.surface(tx + i, ty + j) & WorldGen.SURF_VOID) != 0) v++;
                float f = Math.abs(v / (23f * 13f) - 0.45f);
                if (f < bf) {
                    bf = f;
                    bx = tx * WorldGen.TILE;
                    by = ty * WorldGen.TILE;
                }
            }
        }
        s.px = bx;
        s.py = by;
        frames(h, 4);
        Shots.save(h.renderTop(), "wreck_debris_field.png");
        // the same place with a crowd and the hull's props
        crowd(s, s.px, s.py, 40, 90, 380);
        frames(h, 2);
        Shots.save(h.renderTop(), "wreck_debris_crowd.png");
        // the Pillar and its arena from the edge
        s.px = DebrisLayout.pillarX() + 20f;
        s.py = DebrisLayout.pillarY() + 330f;
        frames(h, 4);
        Shots.save(h.renderTop(), "wreck_pillar_spire.png");
    }

    @Test
    public void padsReadyChannellingRestingAndTheArrivalShockwave() throws Exception {
        Shots h = new Shots();
        Sim s = wreck(h);
        WreckRules wr = WreckRules.of(s);
        float[] p = firstPad();
        s.px = p[0] - 190f;
        s.py = p[1] + 120f;
        frames(h, 4);
        Shots.save(h.renderTop(), "wreck_pad_ready.png");
        s.px = p[0];
        s.py = p[1];
        frames(h, 15); // 0.25 s of 0.5
        assertTrue("channelling", wr.padChanIdx >= 0);
        Shots.save(h.renderTop(), "wreck_pad_channel.png");
        crowd(s, p[2], p[3], 50, 40, 300);
        frames(h, 20);
        assertEquals("sent to the twin (a well may pull a little)", p[2], s.px, 120f);
        frames(h, 6);
        Shots.save(h.renderTop(), "wreck_pad_arrival_shock.png");
        frames(h, 100);
        Shots.save(h.renderTop(), "wreck_pad_resting.png");
        // the minimap with the pair, the wells and the Pillar
        Shots.save(h.renderBottom(), "wreck_minimap.png");
        s.px = p[0] * 0.5f + p[2] * 0.5f;
        s.py = p[1] * 0.5f + p[3] * 0.5f;
        frames(h, 3);
        Shots.save(h.renderBottom(), "wreck_minimap_pair.png");
    }

    @Test
    public void gravityWellsOnFlickeringAndOffWithTheCrowdPulledIn() throws Exception {
        Shots h = new Shots();
        Sim s = wreck(h);
        WreckRules wr = WreckRules.of(s);
        s.px = 1100f;
        s.py = 700f;
        frames(h, 3);
        int w = nearestWell(wr, s.px, s.py);
        float wx = wr.wellX[w], wy = wr.wellY[w];
        s.px = wx + 300f;
        s.py = wy + 40f;
        crowd(s, wx, wy, 45, 40, 200);
        for (int i = 0; i < 18; i++) s.addPickupForTest(Entities.K_GEM, wx + (float) Math.cos(i) * (60 + i * 6), wy + (float) Math.sin(i) * (50 + i * 5), 1f);
        s.time = wellClock(wr, w, 2.0);
        frames(h, 2);
        assertTrue(wr.wellOn(w));
        Shots.save(h.renderTop(), "wreck_well_on.png");
        s.px = wx + 90f;
        s.py = wy + 20f;
        frames(h, 20);
        Shots.save(h.renderTop(), "wreck_well_on_close.png");
        s.px = wx + 300f;
        s.py = wy + 40f;
        s.time = wellClock(wr, w, 9.4);
        frames(h, 12); // the last second before it goes off: it flickers
        Shots.save(h.renderTop(), "wreck_well_flicker.png");
        s.time = wellClock(wr, w, 12.0);
        frames(h, 2);
        assertTrue(!wr.wellOn(w));
        Shots.save(h.renderTop(), "wreck_well_off.png");
        // the minimap with the wells
        Shots.save(h.renderBottom(), "wreck_minimap_wells.png");
        // a held-off well and a short-lived one
        wr.holdOff(wx, wy, 60.0);
        wr.addTempWell(s.px - 160f, s.py + 20f, 160f, 6f);
        frames(h, 3);
        Shots.save(h.renderTop(), "wreck_well_held_and_temp.png");
    }

    @Test
    public void vacuumBreachWarningAndBubble() throws Exception {
        Shots h = new Shots();
        Sim s = wreck(h);
        WreckRules wr = WreckRules.of(s);
        s.time = 538.0;
        crowd(s, s.px, s.py, 60, 150, 420);
        frames(h, 20);
        assertTrue(wr.breachWarn > 0f);
        Shots.save(h.renderTop(), "wreck_breach_warning.png");
        frames(h, 130);
        assertTrue("open", wr.bubbleOn);
        Shots.save(h.renderTop(), "wreck_breach_bubble.png");
        // the hero outside it: the red edge grows
        s.px = wr.bubbleX + wr.bubbleR + 160f;
        s.py = wr.bubbleY;
        frames(h, 90);
        Shots.save(h.renderTop(), "wreck_breach_outside.png");
        Shots.save(h.renderBottom(), "wreck_minimap_bubble.png");
    }

    @Test
    public void pillarGateAndTheThreePillarPhases() throws Exception {
        Shots h = new Shots();
        Sim s = wreck(h);
        WreckRules wr = WreckRules.of(s);
        s.time = 1649.5;
        frames(h, 90);
        assertTrue(wr.gateOn);
        Shots.save(h.renderTop(), "wreck_gate_banner.png");
        s.px = wr.gateX - 200f;
        s.py = wr.gateY + 90f;
        frames(h, 60);
        Shots.save(h.renderTop(), "wreck_gate.png");
        Shots.save(h.renderBottom(), "wreck_minimap_gate.png");

        s.time = 1700;
        Enemy p = s.spawnAt("THE_PILLAR", 0f, 0f, true);
        assertNotNull(p);
        s.px = DebrisLayout.pillarX() + 20f;
        s.py = DebrisLayout.pillarY() + 300f;
        frames(h, 4 * 60); // 2.5 s to the first beams, 1 s warning, then they sweep
        assertTrue("phase 1 beams", p.phase == 0);
        Shots.save(h.renderTop(), "wreck_pillar_phase1.png");
        p.hp = p.maxHp * 0.6f;
        frames(h, 90);
        assertEquals(1, p.phase);
        Shots.save(h.renderTop(), "wreck_pillar_phase2.png");
        frames(h, 150);
        Shots.save(h.renderTop(), "wreck_pillar_phase2_echo.png");
        p.hp = p.maxHp * 0.3f;
        s.px = DebrisLayout.pillarX() + 250f;
        s.py = DebrisLayout.pillarY() + 60f;
        frames(h, 150);
        assertEquals(2, p.phase);
        assertTrue(wr.bubbleOn);
        Shots.save(h.renderTop(), "wreck_pillar_phase3.png");
        frames(h, 130);
        Shots.save(h.renderTop(), "wreck_pillar_phase3_pulse.png");
        Shots.save(h.renderBottom(), "wreck_minimap_pillar.png");
    }

    @Test
    public void riftHoundBreacherAlphaAndWardenOnScreen() throws Exception {
        Shots h = new Shots();
        Sim s = wreck(h);
        WreckRules wr = WreckRules.of(s);
        wr.packs = false;
        Enemy r1 = s.spawnAt("RIFT_HOUND", s.px + 170f, s.py - 60f, false), r2 = s.spawnAt("RIFT_HOUND", s.px - 200f, s.py + 40f, false);
        for (Enemy e : new Enemy[]{r1, r2}) {
            e.speed = 0f;
            e.ai = 3;
            e.tele = e.teleMax = 0.3f;
            e.aiT = 5f;
        }
        Enemy b = s.spawnAt("BREACHER", s.px - 330f, s.py, false), b2 = s.spawnAt("BREACHER", s.px + 60f, s.py - 220f, false);
        b.speed = b2.speed = 0f;
        b.ai = b2.ai = 3;
        b.tele = b.teleMax = b2.tele = b2.teleMax = 1f;
        b.aiT = b2.aiT = 5f;
        b.ax = 1f;
        b.ay = 0f;
        b2.ax = 0f;
        b2.ay = 1f;
        b.teleLen = b2.teleLen = 300f;
        b.teleW = b2.teleW = 14f;
        b.tele = 0.45f;
        Shots.save(h.renderTop(), "wreck_rift_breacher.png");
        s.enemyN = 0;
        Enemy a = s.spawnAt("HULL_BREACHER_ALPHA", s.px + 190f, s.py - 30f, true);
        a.hp = a.maxHp = 1e6f;
        frames(h, 150);
        Shots.save(h.renderTop(), "wreck_alpha_well.png");
        s.enemyN = 0;
        s.hazN = 0;
        Enemy w = s.spawnAt("SALVAGE_WARDEN", s.px + 230f, s.py - 40f, true);
        w.hp = w.maxHp = 1e6f;
        frames(h, 200);
        Shots.save(h.renderTop(), "wreck_warden.png");
    }

    private static final long SPIKE = 8192;

    @Test
    public void worldAndMinimapDrawWithoutAllocatingWithWellsPadsBubbleAndPillar() throws Exception {
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
        for (String scene : new String[]{"wells", "bubble", "pillar"}) {
            Shots h = new Shots();
            Sim s = wreck(h);
            WreckRules wr = WreckRules.of(s);
            s.px = 1100f;
            s.py = 700f;
            frames(h, 3);
            int w = nearestWell(wr, s.px, s.py);
            if (scene.equals("wells")) {
                s.px = wr.wellX[w] + 150f;
                s.py = wr.wellY[w];
                s.time = wellClock(wr, w, 2.0);
            } else if (scene.equals("bubble")) {
                s.time = 541.0;
            } else {
                s.time = 1700;
                Enemy p = s.spawnAt("THE_PILLAR", 0f, 0f, true);
                p.hp = p.maxHp * 0.3f;
                s.px = DebrisLayout.pillarX() + 250f;
                s.py = DebrisLayout.pillarY() + 60f;
            }
            crowd(s, s.px, s.py, 40, 60, 380);
            for (int i = 0; i < 80; i++) {
                s.invuln = 1e9f;
                frames(h, 1);
                h.world.draw(c, 1920, 1080, h.eng, true);
                panel.draw(cp, 1240, 1080);
            }
            long bytes = 0;
            int n = 60, spikes = 0;
            for (int i = 0; i < n; i++) {
                s.invuln = 1e9f;
                frames(h, 1);
                long a0 = (Long) alloc.invoke(mx, tid);
                h.world.draw(c, 1920, 1080, h.eng, true);
                panel.draw(cp, 1240, 1080);
                long d = (Long) alloc.invoke(mx, tid) - a0 - probe;
                if (d >= SPIKE) spikes++; else bytes += d;
            }
            System.out.printf("PERF wreck draw %s: %d B/frame (+%d spikes)%n", scene, bytes / n, spikes);
            assertTrue(scene + ": " + spikes + " spikes", spikes <= 6);
            assertTrue(scene + " allocates " + bytes / n + " B per frame", bytes / n < 512);
        }
    }
}
