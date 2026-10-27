package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Entities;
import se.felixarvid.thorsurvivors.game.Input;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.WorldGen;
import se.felixarvid.thorsurvivors.game.layout.DomesLayout;
import se.felixarvid.thorsurvivors.game.stage.HydroRules;

/**
 * Hydroponic Vaults on screen (v1.4 chunk B): the domes, a growth patch with hearts (and the minimap), a flooded dome, Sporefall, the
 * Gardener's pulse and the Rootmind in each phase. Screenshots go to build/screenshots (hydro_*.png).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class HydroShotsTest {
    private static final float DT = 1f / 60f;

    private static void frames(Shots h, int n) {
        for (int i = 0; i < n; i++) {
            h.eng.sim.hp = h.eng.sim.maxHp;
            h.eng.sim.invuln = Math.max(h.eng.sim.invuln, 1f);
            h.eng.input.clearMovement();
            if (h.eng.phase == Engine.PH_LEVELUP || h.eng.phase == Engine.PH_CHEST) h.eng.input.post(Input.CMD_CONFIRM, 0, 0, 0);
            else if (h.eng.phase == Engine.PH_MERCHANT || h.eng.phase == Engine.PH_EQUIP) h.eng.input.post(Input.CMD_BACK, 0, 0, 0);
            h.eng.advance(DT);
        }
    }

    private static Sim run(Shots h) {
        Sim s = h.startRun("HYDRO", "WHIP", 7L);
        s.noSpawnsForTest();
        s.enemyN = 0;
        frames(h, 5);
        return s;
    }

    private static void crowd(Sim s, int n, float spread) {
        for (int i = 0; i < n; i++) {
            double a = i * 2.399, r = 60 + (i * 37) % spread;
            Entities.Enemy e = s.spawnAt(i % 6 == 0 ? "SERGEANT" : i % 4 == 0 ? "SPORE_HOUND" : i % 9 == 0 ? "SPORE_CARRIER" : "COLONIST",
                    s.px + (float) (Math.cos(a) * r * 1.4), s.py + (float) (Math.sin(a) * r * 0.8), false);
            if (e != null) e.speed = 0f;
        }
    }

    /** Hero x, y in world units at the south corridor of the dome with cell (cx, cy) (tile coordinates of its centre + 9). */
    private static float[] domeFront(boolean flooded) {
        for (int cy = 1; cy < 30; cy++) {
            for (int cx = 1; cx < 30; cx++) {
                if (flooded ? DomesLayout.flooded(cx, cy) : DomesLayout.dome(cx, cy) && DomesLayout.dome(cx + 1, cy) && DomesLayout.dome(cx, cy + 1)) {
                    return new float[]{DomesLayout.centerX(cx) * WorldGen.TILE, DomesLayout.centerX(cy) * WorldGen.TILE};
                }
            }
        }
        throw new AssertionError("no dome");
    }

    @Test
    public void domes() throws Exception {
        Shots h = new Shots();
        Sim s = run(h);
        float[] d = domeFront(false);
        s.px = d[0];
        s.py = d[1] + 6.5f * WorldGen.TILE; // on the south gap of a dome, the ring and the corridor in view
        crowd(s, 14, 280f);
        frames(h, 30);
        Shots.save(h.renderTop(), "hydro_domes.png");
        s.px = d[0];
        s.py = d[1]; // inside
        frames(h, 20);
        Shots.save(h.renderTop(), "hydro_dome_inside.png");
    }

    @Test
    public void patchAndHearts() throws Exception {
        Shots h = new Shots();
        Sim s = run(h);
        HydroRules r = (HydroRules) s.rules;
        r.heartsOn = false;
        float[] d = domeFront(false);
        s.px = d[0];
        s.py = d[1];
        s.time = 600;
        r.addHeart(s.px + 230f, s.py - 30f, true);
        r.hAge[0] = 70f;
        r.addHeart(s.px - 330f, s.py + 150f, true);
        r.hAge[1] = 20f;
        r.hHp[1] = r.hMax[1] * 0.6f;
        crowd(s, 40, 330f);
        for (int i = 0; i < s.enemyN; i++) if (i % 3 == 0) s.enemies[i].hp = s.enemies[i].maxHp * 0.5f;
        frames(h, 40);
        Shots.save(h.renderTop(), "hydro_patch_hearts.png");
        Shots.save(h.renderBottom(), "hydro_patch_minimap.png");
        s.px += 220f; // standing in the patch
        frames(h, 20);
        Shots.save(h.renderTop(), "hydro_patch_hero.png");
        assertTrue(r.inPatch(s.px, s.py));
    }

    @Test
    public void floodedDome() throws Exception {
        Shots h = new Shots();
        Sim s = run(h);
        float[] d = domeFront(true);
        s.px = d[0] - 60f;
        s.py = d[1] + 20f;
        crowd(s, 22, 220f);
        frames(h, 40);
        Shots.save(h.renderTop(), "hydro_flooded_dome.png");
        HydroRules r = (HydroRules) s.rules;
        r.destBroken(Sim.DK_TANKER, s.px + 160f, s.py - 40f, 5L);
        frames(h, 30);
        Shots.save(h.renderTop(), "hydro_tanker_flood.png");
    }

    @Test
    public void sporefall() throws Exception {
        Shots h = new Shots();
        Sim s = run(h);
        HydroRules r = (HydroRules) s.rules;
        float[] d = domeFront(false);
        s.px = d[0];
        s.py = d[1] + 6.5f * WorldGen.TILE;
        crowd(s, 30, 300f);
        r.startSporefall(20f, false);
        frames(h, 60 * 5 + 20);
        Shots.save(h.renderTop(), "hydro_sporefall.png");
        frames(h, 60 * 2);
        Shots.save(h.renderTop(), "hydro_sporefall_clouds.png");
    }

    @Test
    public void gardenerPulse() throws Exception {
        Shots h = new Shots();
        Sim s = run(h);
        float[] d = domeFront(false);
        s.px = d[0];
        s.py = d[1];
        crowd(s, 24, 160f);
        for (int i = 0; i < s.enemyN; i++) s.enemies[i].hp = s.enemies[i].maxHp * 0.5f;
        Entities.Enemy g = s.spawnAt("GARDENER", s.px + 120f, s.py - 20f, false);
        g.speed = 0f;
        Entities.Enemy g2 = s.spawnAt("GARDENER", s.px - 160f, s.py + 40f, false);
        g2.speed = 0f;
        frames(h, 100);
        int i = 0;
        while (((HydroRules) s.rules).gpN == 0 && i++ < 200) frames(h, 1);
        frames(h, 8);
        Shots.save(h.renderTop(), "hydro_gardener_pulse.png");
    }

    @Test
    public void rootmindEachPhase() throws Exception {
        Shots h = new Shots();
        Sim s = run(h);
        s.time = 1680;
        float[] d = domeFront(false);
        s.px = d[0] - 450f;
        s.py = d[1];
        Entities.Enemy b = s.spawnAt("ROOTMIND", d[0], d[1], true);
        HydroRules r = (HydroRules) s.rules;
        s.px = r.rmBossX - 330f;
        s.py = r.rmBossY + 90f;
        frames(h, 60 * 3);
        Shots.save(h.renderTop(), "hydro_rootmind_phase1.png");
        frames(h, 60 * 3);
        Shots.save(h.renderTop(), "hydro_rootmind_phase1_vines.png");
        Shots.save(h.renderBottom(), "hydro_rootmind_minimap.png");
        b.hp = b.maxHp * 0.5f;
        frames(h, 60 * 6);
        Shots.save(h.renderTop(), "hydro_rootmind_phase2.png");
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].type.def.key.equals("ROOT_HEART")) s.killForTest(s.enemies[i]);
        frames(h, 40);
        Shots.save(h.renderTop(), "hydro_rootmind_exposed.png");
        b.hp = b.maxHp * 0.2f;
        frames(h, 60 * 7);
        Shots.save(h.renderTop(), "hydro_rootmind_phase3.png");
    }
}
