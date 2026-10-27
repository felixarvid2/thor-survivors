package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertTrue;

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
import se.felixarvid.thorsurvivors.game.layout.StreetsLayout;
import se.felixarvid.thorsurvivors.game.stage.RefineryRules;

import java.lang.reflect.Method;

/**
 * The Refinery District on screen (v1.4 chunk C): streets with belts and the arrows before a flip, a furnace gate, the heat bar (world
 * and bottom screen) with the vignette, the Heat Wave warning and band, a molten channel with its bridge, the Ironheart in its three
 * phases, the minimap. Screenshots go to build/screenshots (refinery_*.png). Also: the Refinery drawing allocates nothing in steady state.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class RefineryShotsTest {
    private static final float T = WorldGen.TILE;

    private static void frames(Shots h, int n) {
        for (int i = 0; i < n; i++) {
            h.eng.sim.hp = h.eng.sim.maxHp;
            h.eng.input.clearMovement();
            if (h.eng.phase == Engine.PH_LEVELUP || h.eng.phase == Engine.PH_CHEST) h.eng.input.post(Input.CMD_CONFIRM, 0, 0, 0);
            else if (h.eng.phase == Engine.PH_MERCHANT || h.eng.phase == Engine.PH_EQUIP) h.eng.input.post(Input.CMD_BACK, 0, 0, 0);
            h.eng.advance(1f / 60f);
        }
    }

    private static Sim start(Shots h) {
        Sim s = h.startRun("REFINERY", "WHIP", 7L);
        s.noSpawnsForTest();
        s.enemyN = 0;
        return s;
    }

    private static int[] find(int mask, boolean vertical) {
        for (int ty = -80; ty <= 80; ty++) {
            for (int tx = -80; tx <= 80; tx++) {
                if (vertical ? StreetsLayout.local(tx) != 2 : StreetsLayout.local(ty) != 2) continue;
                boolean ok = true;
                for (int k = -6; k <= 6 && ok; k++) ok = (StreetsLayout.surfaceAt(vertical ? tx : tx + k, vertical ? ty + k : ty) & mask) != 0;
                if (ok) return new int[]{tx, ty};
            }
        }
        throw new AssertionError("no tile");
    }

    private static void crowd(Sim s, float cx, float cy, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * 2.399, r = 60 + (i * 37) % 240;
            Enemy e = s.spawnAt(i % 6 == 0 ? "STOKER" : i % 5 == 0 ? "IRONCLAD" : i % 4 == 0 ? "SECURITY_DRONE" : "COLONIST",
                    cx + (float) (Math.cos(a) * r * 1.3), cy + (float) (Math.sin(a) * r * 0.8), false);
            if (e != null) e.speed = 0f;
        }
    }

    @Test
    public void streetsBeltsAndTheFlipWarning() throws Exception {
        Shots h = new Shots();
        Sim s = start(h);
        int[] b = find(WorldGen.SURF_BELT_N | WorldGen.SURF_BELT_S, true);
        s.px = (b[0] - 3.5f) * T;
        s.py = (b[1] + 0.5f) * T;
        s.time = 5;
        frames(h, 20);
        Shots.save(h.renderTop(), "refinery_streets.png");
        s.time = 18.7;
        frames(h, 6);
        Shots.save(h.renderTop(), "refinery_belt_flip_warning.png");
        Shots.save(h.renderBottom(), "refinery_minimap.png");
    }

    @Test
    public void furnaceGateWithAnApproachingCrowd() throws Exception {
        Shots h = new Shots();
        Sim s = start(h);
        int[] g = null;
        search:
        for (int ty = -80; ty <= 80; ty++) {
            for (int tx = -80; tx <= 80; tx++) {
                if (StreetsLayout.local(tx) == 2 && (StreetsLayout.surfaceAt(tx, ty) & WorldGen.SURF_FURNACE_GATE) != 0
                        && (StreetsLayout.surfaceAt(tx, ty - 1) & StreetsLayout.surfaceAt(tx, ty + 1) & WorldGen.SURF_FURNACE_GATE) != 0) {
                    g = new int[]{tx, ty};
                    break search;
                }
            }
        }
        s.px = (g[0] + 0.5f) * T + 260f;
        s.py = (g[1] + 0.5f) * T;
        crowd(s, (g[0] + 0.5f) * T - 60f, (g[1] + 0.5f) * T, 30);
        for (int i = 0; i < 25; i++) s.addPickupForTest(Entities.K_GEM, s.px - 90f + (i % 5) * 20f, s.py - 60f + (i / 5) * 25f, 2f);
        s.time = 3;
        frames(h, 30);
        Shots.save(h.renderTop(), "refinery_gate.png");
    }

    @Test
    public void heatBarVignetteAndOverheat() throws Exception {
        Shots h = new Shots();
        Sim s = start(h);
        RefineryRules r = (RefineryRules) s.rules;
        s.time = 30;
        r.heat = 72f;
        frames(h, 3);
        r.heat = 72f;
        Shots.save(h.renderTop(), "refinery_heat_top.png");
        Shots.save(h.renderBottom(), "refinery_heat_bottom.png");
        r.heat = 100f;
        frames(h, 3);
        r.heat = 100f;
        frames(h, 1);
        Shots.save(h.renderBottom(), "refinery_overheat_bottom.png");
        Shots.save(h.renderTop(), "refinery_overheat_top.png");
        r.heat = 15f;
        frames(h, 1);
        Shots.save(h.renderBottom(), "refinery_heat_low_bottom.png");
    }

    @Test
    public void heatWaveWarningAndBand() throws Exception {
        Shots h = new Shots();
        Sim s = start(h);
        RefineryRules r = (RefineryRules) s.rules;
        s.time = 118.55;
        crowd(s, s.px, s.py, 25);
        frames(h, 10);
        assertTrue(r.wLive[0]);
        Shots.save(h.renderTop(), "refinery_heatwave_warning.png");
        Shots.save(h.renderBottom(), "refinery_heatwave_minimap.png");
        frames(h, 60 * 2 + 40); // the band is in the middle of the screen about 2.6 s after the start
        Shots.save(h.renderTop(), "refinery_heatwave_band.png");
    }

    @Test
    public void moltenChannelAndItsBridge() throws Exception {
        Shots h = new Shots();
        Sim s = start(h);
        int[] m = null, br = null;
        search:
        for (int ty = -120; ty <= 120; ty++) {
            for (int tx = -120; tx <= 120; tx++) {
                if (StreetsLayout.bridge(tx, ty)) {
                    m = new int[]{tx, ty};
                    break search;
                }
            }
        }
        s.px = (m[0] + 0.5f) * T - 120f;
        s.py = (m[1] + 0.5f) * T + 160f;
        s.time = 3;
        frames(h, 20);
        Shots.save(h.renderTop(), "refinery_molten.png");
    }

    @Test
    public void ironheartInThreePhases() throws Exception {
        Shots h = new Shots();
        Sim s = start(h);
        RefineryRules r = (RefineryRules) s.rules;
        s.px = 0f;
        s.py = 0f;
        Enemy e = s.spawnAt("IRONHEART", 380f, 90f, true);
        s.time = 1690;
        s.px = e.x - 330f;
        s.py = e.y + 20f;
        e.speed = 0f;
        s.invuln = 1e9f;
        crowd(s, s.px, s.py, 18);
        frames(h, 40);
        Shots.save(h.renderTop(), "refinery_ironheart_p1.png");
        Shots.save(h.renderBottom(), "refinery_ironheart_p1_bottom.png");
        e.hp = e.maxHp * 0.6f;
        frames(h, 60 * 3 + 20);
        Shots.save(h.renderTop(), "refinery_ironheart_p2.png");
        e.hp = e.maxHp * 0.3f;
        frames(h, 40);
        assertTrue(r.vgN > 0);
        s.px = e.x - 250f;
        s.py = e.y + 10f;
        frames(h, 20);
        Shots.save(h.renderTop(), "refinery_ironheart_p3.png");
        r.thermalShock(e);
        frames(h, 10);
        Shots.save(h.renderTop(), "refinery_ironheart_shock.png");
    }

    @Test
    public void coolantFogAndTower() throws Exception {
        Shots h = new Shots();
        Sim s = start(h);
        RefineryRules r = (RefineryRules) s.rules;
        s.time = 1259.95;
        frames(h, 30);
        s.px = s.events.tx - 90f;
        s.py = s.events.ty + 40f;
        r.destBroken(RefineryRules.DK_COOLANT, s.px + 60f, s.py - 30f, 0L);
        crowd(s, s.px + 60f, s.py - 30f, 12);
        frames(h, 40);
        Shots.save(h.renderTop(), "refinery_tower_fog.png");
    }

    private static long allocated() throws Exception {
        Object mx = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
        Method m = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", long.class);
        m.setAccessible(true);
        return (Long) m.invoke(mx, Thread.currentThread().getId());
    }

    @Test
    public void refineryDrawingAllocatesNothingInSteadyState() throws Exception {
        Shots h = new Shots();
        Sim s = start(h);
        RefineryRules r = (RefineryRules) s.rules;
        int[] b = find(WorldGen.SURF_BELT_N | WorldGen.SURF_BELT_S, true);
        s.px = (b[0] - 3.5f) * T;
        s.py = (b[1] + 0.5f) * T;
        s.time = 118.55; // a wave warning, then the band
        r.heat = 80f;
        crowd(s, s.px, s.py, 30);
        for (int i = 0; i < 80; i++) { // warm-up: Robolectric binds call sites on first use
            h.renderTop();
            h.renderBottom();
            frames(h, 1);
            r.heat = 80f;
        }
        int spikes = 0;
        long total = 0;
        for (int i = 0; i < 120; i++) {
            long a0 = allocated();
            h.renderTop();
            h.renderBottom();
            long used = allocated() - a0;
            total += Math.min(used, 8192);
            if (used > 8192) spikes++;
            frames(h, 1);
            r.heat = 80f;
        }
        assertTrue("allocation spikes: " + spikes, spikes <= 12);
        assertTrue("steady allocation per frame pair: " + total / 120, total / 120 < 2048);
    }
}
