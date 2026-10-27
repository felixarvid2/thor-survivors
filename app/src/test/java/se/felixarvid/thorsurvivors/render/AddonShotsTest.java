package se.felixarvid.thorsurvivors.render;

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
import se.felixarvid.thorsurvivors.game.stage.FlatsRules;
import se.felixarvid.thorsurvivors.game.stage.MinesRules;
import se.felixarvid.thorsurvivors.ui.PanelRenderer;

import java.lang.reflect.Method;

/**
 * The Flats and Mines add-ons on screen (chunk E): storm lightning, the convoy, a Lumen Bloom, the cart lane warning and the cart,
 * firedamp, Dark Lurkers in and out of the light, and the minimap markers. Screenshots go to build/screenshots (addon_*.png). Also:
 * the world and the minimap draw without allocating with the add-ons active.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class AddonShotsTest {
    private static final float DT = 1f / 60f;

    private static void frames(Shots h, int n) {
        for (int i = 0; i < n; i++) {
            h.eng.sim.hp = h.eng.sim.maxHp;
            h.eng.sim.invuln = Math.max(h.eng.sim.invuln, 0.5f);
            h.eng.input.clearMovement();
            if (h.eng.phase == Engine.PH_LEVELUP || h.eng.phase == Engine.PH_CHEST) h.eng.input.post(Input.CMD_CONFIRM, 0, 0, 0);
            else if (h.eng.phase == Engine.PH_MERCHANT || h.eng.phase == Engine.PH_EQUIP) h.eng.input.post(Input.CMD_BACK, 0, 0, 0);
            h.eng.advance(DT);
        }
    }

    private static Sim at(Shots h, String stage, double time) {
        Sim s = h.startRun(stage, "WHIP", 7L);
        s.noSpawnsForTest();
        s.pois.enabled = false;
        s.rules.spawnWhenQuiet = true;
        s.enemyN = 0;
        s.time = time;
        frames(h, 6);
        return s;
    }

    private static void crowd(Sim s, int n, float spread) {
        for (int i = 0; i < n; i++) {
            double a = i * 2.399, r = 80 + (i * 37) % 300;
            Enemy e = s.spawnAt(i % 6 == 0 ? "SERGEANT" : "COLONIST", s.px + (float) (Math.cos(a) * r * 1.4f * spread),
                    s.py + (float) (Math.sin(a) * r * 0.8f * spread), false);
            if (e != null) e.speed = 0f;
        }
    }

    @Test
    public void stormLightning() throws Exception {
        Shots h = new Shots();
        Sim s = at(h, "FLATS", 724.0);
        FlatsRules r = (FlatsRules) s.rules;
        crowd(s, 25, 1f);
        for (int i = 0; i < s.destN; i++) { /* the generated barrels stay */ }
        // two rings warning (one on the hero, one off to the side) and a barrel cluster the second will light
        r.strikeN = 2;
        r.strikeX[0] = s.px + 20f; r.strikeY[0] = s.py + 10f; r.strikeT[0] = 0.55f;
        r.strikeX[1] = s.px + 250f; r.strikeY[1] = s.py - 90f; r.strikeT[1] = 0.9f;
        frames(h, 2);
        Shots.save(h.renderTop(), "addon_storm_warning.png");
        r.strikeT[0] = 0.01f;
        frames(h, 3);
        Shots.save(h.renderTop(), "addon_storm_strike.png");
    }

    @Test
    public void convoy() throws Exception {
        Shots h = new Shots();
        Sim s = at(h, "FLATS", 495.0);
        FlatsRules r = (FlatsRules) s.rules;
        frames(h, 4);
        Enemy t = r.tanker;
        assertNotNull(t);
        // bring the convoy into view, coming from the left
        float dx = (s.px - 330f) - t.x, dy = (s.py + 20f) - t.y;
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].poi == se.felixarvid.thorsurvivors.game.AddonOps.POI_ESCORT || s.enemies[i] == t) {
            s.enemies[i].x += dx;
            s.enemies[i].y += dy;
            s.enemies[i].stun = 100f;
        }
        frames(h, 20);
        Shots.save(h.renderTop(), "addon_convoy.png");
        Shots.save(h.renderBottom(), "addon_convoy_minimap.png");
    }

    @Test
    public void bloom() throws Exception {
        Shots h = new Shots();
        Sim s = at(h, "FLATS", 100.0);
        FlatsRules r = (FlatsRules) s.rules;
        float[] o = new float[2];
        float[] site = null;
        for (int j = -6; j <= 6 && site == null; j++) for (int i = -6; i <= 6 && site == null; i++) if (r.siteOf(i, j, o)) site = new float[]{o[0], o[1]};
        assertNotNull(site);
        s.px = site[0] - 200f;
        s.py = site[1] + 40f;
        frames(h, 4);
        Shots.save(h.renderTop(), "addon_bloom.png");
        s.px = site[0] + 10f;
        s.py = site[1] + 10f;
        frames(h, 100); // about half of the hold
        Shots.save(h.renderTop(), "addon_bloom_harvest.png");
        Shots.save(h.renderBottom(), "addon_bloom_minimap.png");
    }

    @Test
    public void cartWarningAndPass() throws Exception {
        Shots h = new Shots();
        Sim s = at(h, "MINES", 179.9);
        crowd(s, 18, 0.9f);
        s.py = 0f;
        frames(h, 40); // warning running
        Shots.save(h.renderTop(), "addon_cart_warning.png");
        Shots.save(h.renderBottom(), "addon_cart_minimap.png");
        MinesRules r = (MinesRules) s.rules;
        int guard = 0;
        while (guard++ < 900 && (r.cartT < MinesRules.CART_WARN || Math.abs(r.cartX - s.px) > 150f)) frames(h, 1);
        Shots.save(h.renderTop(), "addon_cart_pass.png");
    }

    @Test
    public void firedamp() throws Exception {
        Shots h = new Shots();
        Sim s = at(h, "MINES", 100.0);
        MinesRules r = (MinesRules) s.rules;
        float[] o = new float[2];
        float[] p = null;
        for (int j = -6; j <= 6 && p == null; j++) for (int i = -6; i <= 6 && p == null; i++) if (r.pocketOf(i, j, o)) p = new float[]{o[0], o[1]};
        assertNotNull(p);
        s.px = p[0] - 230f;
        s.py = p[1] + 30f;
        frames(h, 4);
        Shots.save(h.renderTop(), "addon_firedamp_haze.png");
        Enemy e = s.spawnAt("COLONIST", p[0] + 30f, p[1], false);
        e.speed = 0f;
        for (int i = 0; i < r.pocketN; i++) if (Math.abs(r.pocketX[i] - p[0]) < 1f) r.ignite(i);
        frames(h, 150); // the fuse flashes
        Shots.save(h.renderTop(), "addon_firedamp_fuse.png");
        Shots.save(h.renderBottom(), "addon_firedamp_minimap.png");
        frames(h, 28);
        Shots.save(h.renderTop(), "addon_firedamp_blast.png");
    }

    @Test
    public void lurkersInAndOutOfTheLight() throws Exception {
        Shots h = new Shots();
        Sim s = at(h, "MINES", 400.0);
        MinesRules r = (MinesRules) s.rules;
        r.addonsEnabled = true;
        s.px = 0f;
        s.py = 0f;
        // an x with no lane lamp near, so only the hero's light counts
        s.px = 4000f;
        float[] lamp = new float[2];
        for (float cand = 4000f; cand < 7000f; cand += 20f) {
            boolean clear = true;
            for (int k = Math.floorDiv((int) (cand / WorldGen.TILE), 14) - 3; k <= Math.floorDiv((int) (cand / WorldGen.TILE), 14) + 3; k++) {
                MinesRules.lamp(k, lamp);
                if (Math.abs(lamp[0] - cand) < 650) clear = false;
            }
            if (clear) { s.px = cand; break; }
        }
        float[][] spots = {{90, 20}, {-130, -40}, {170, 100}, {-60, 110}, {230, -80}, {340, 40}, {-380, 60}, {300, -150}, {-290, 120}, {420, 110}};
        for (float[] sp : spots) {
            Enemy e = s.spawnAt("DARK_LURKER", s.px + sp[0], s.py + sp[1], false);
            if (e != null) { e.speed = 0f; e.stun = 1e5f; }
        }
        s.noSpawnsForTest();
        frames(h, 6);
        Shots.save(h.renderTop(), "addon_lurkers_light.png");
        // a headlamp widens the light: more of them show
        s.stats.lightRadius = 1.6;
        frames(h, 3);
        Shots.save(h.renderTop(), "addon_lurkers_headlamp.png");
    }

    private static final long SPIKE = 8192;

    @Test
    public void worldAndMinimapDrawWithoutAllocatingWithTheAddonsActive() throws Exception {
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
        String[] cases = {"storm", "convoy", "cart", "firedamp"};
        for (String cs : cases) {
            Shots h = new Shots();
            Sim s;
            switch (cs) {
                case "storm":
                    s = at(h, "FLATS", 730.0);
                    break;
                case "convoy":
                    s = at(h, "FLATS", 495.0);
                    break;
                case "cart":
                    s = at(h, "MINES", 180.2);
                    break;
                default:
                    s = at(h, "MINES", 400.0);
                    MinesRules mr = (MinesRules) s.rules;
                    float[] o = new float[2];
                    for (int j = -6; j <= 6; j++) for (int i = -6; i <= 6; i++) if (mr.pocketOf(i, j, o) && mr.pocketN == 0) { s.px = o[0] - 150f; s.py = o[1]; }
                    frames(h, 3);
                    if (mr.pocketN > 0) mr.ignite(0);
            }
            crowd(s, 30, 1f);
            for (int i = 0; i < 60; i++) {
                frames(h, 1);
                h.world.draw(c, 1920, 1080, h.eng, true);
                panel.draw(cp, 1240, 1080);
            }
            long bytes = 0;
            int spikes = 0, n = 100;
            for (int i = 0; i < n; i++) {
                frames(h, 1);
                long a0 = (Long) alloc.invoke(mx, tid);
                h.world.draw(c, 1920, 1080, h.eng, true);
                panel.draw(cp, 1240, 1080);
                long d = (Long) alloc.invoke(mx, tid) - a0 - probe;
                if (d >= SPIKE) spikes++; else bytes += d;
            }
            System.out.printf("PERF addon draw %s: %d B/frame (+%d spikes)%n", cs, bytes / n, spikes);
            assertTrue(cs + ": " + spikes + " spikes", spikes <= 6);
            assertTrue(cs + " allocates " + bytes / n + " B per frame", bytes / n < 512);
        }
    }
}
