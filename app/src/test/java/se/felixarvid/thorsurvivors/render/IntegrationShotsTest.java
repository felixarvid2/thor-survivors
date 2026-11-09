package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.game.Elites;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Entities;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Pois;
import se.felixarvid.thorsurvivors.game.Settings;
import se.felixarvid.thorsurvivors.game.Sim;

/**
 * v1.5 integration: frames of what the eight batches only show together - the boss panel with B6's phase shield and
 * enrage, a Shielded elite's arc, B5's new weapons, B7's stand-still frame, the minimap marks of B5's places and B3's
 * Shapes telegraphs on B6 hazards. Saved as {@code int_*.png}; each frame is checked for content where it matters.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class IntegrationShotsTest {
    @After
    public void resetSettings() {
        Settings.get().setShapes(false);
    }

    private static Sim scene(Shots h, String stage, long seed) {
        Sim s = h.startRun(stage, "WHIP", seed);
        s.noSpawnsForTest();
        s.enemyN = 0;
        s.inv.weaponCount = 0;
        s.recomputeAll();
        s.hp = s.maxHp;
        return s;
    }

    private static void still(Shots h, int frames) {
        Sim s = h.eng.sim;
        for (int i = 0; i < frames; i++) {
            s.hp = s.maxHp;
            s.invuln = 1f;
            h.eng.input.stickX = 0;
            h.eng.input.stickY = 0;
            if (h.eng.phase == Engine.PH_LEVELUP || h.eng.phase == Engine.PH_CHEST) h.eng.input.post(se.felixarvid.thorsurvivors.game.Input.CMD_CONFIRM, 0, 0, 0);
            h.eng.advance(1f / 60f);
        }
    }

    private static Bitmap crop(Bitmap b) {
        return Bitmap.createBitmap(b, 480, 270, 960, 540);
    }

    /** Pixels within a small distance of a colour, sampled every 2nd pixel. */
    private static int near(Bitmap b, int rgb, int tol) {
        int n = 0, r0 = rgb >> 16 & 255, g0 = rgb >> 8 & 255, b0 = rgb & 255;
        for (int y = 0; y < b.getHeight(); y += 2)
            for (int x = 0; x < b.getWidth(); x += 2) {
                int p = b.getPixel(x, y);
                if (Math.abs((p >> 16 & 255) - r0) + Math.abs((p >> 8 & 255) - g0) + Math.abs((p & 255) - b0) < tol) n++;
            }
        return n;
    }

    @Test
    public void bossPhaseShieldAndEnrageOnBothScreens() throws Exception {
        Shots h = new Shots();
        Sim s = scene(h, "FLATS", 9101);
        Enemy b = s.spawnAt("SLAGBORN", s.px + 150f, s.py - 20f, true);
        still(h, 2);
        b.shieldT = 0.8f; // B6 phase shield
        h.eng.advance(0f);
        Bitmap top = h.renderTop().copy(Bitmap.Config.ARGB_8888, false);
        Shots.save(crop(top), "int_boss_shield_top.png");
        Shots.save(h.renderBottom(), "int_boss_shield_bottom.png");
        assertTrue("phase-shield ring in lumen green", near(crop(top), 0x39FF88, 60) > 40);
        b.shieldT = 0f;
        b.bossAge = 75f; // 15 s to the enrage: the countdown on the panel
        Shots.save(h.renderBottom(), "int_boss_enrage_soon_bottom.png");
        b.timeEnrage = true;
        b.bossAge = 95f;
        Shots.save(crop(h.renderTop()), "int_boss_enraged_top.png");
        Shots.save(h.renderBottom(), "int_boss_enraged_bottom.png");
    }

    @Test
    public void shieldedEliteShowsItsArc() throws Exception {
        Shots h = new Shots();
        Sim s = scene(h, "MINES", 9102);
        s.time = 600;
        Enemy e = s.spawnEliteForTest("WELDER", Elites.M_SHIELDED);
        e.x = s.px + 170f;
        e.y = s.py + 10f;
        e.eshT = 3f;
        e.faceA = (float) Math.PI; // facing the hero on its left
        h.eng.advance(0f);
        Bitmap c = crop(h.renderTop());
        Shots.save(c, "int_shielded_elite.png");
        Shots.save(h.renderBottom(), "int_shielded_elite_bottom.png");
        assertTrue("the arc in the Shielded colour", near(c, Elites.COLORS[6], 50) > 30);
    }

    @Test
    public void newWeaponsInAction() throws Exception {
        String[][] sets = {{"ARC_LANCE", "SLAG_MORTAR", "TETHER_DRONE"}, {"STORM_LANCE", "MAGMA_HOWITZER", "LEASH_ARRAY"}};
        for (int k = 0; k < sets.length; k++) {
            Shots h = new Shots();
            Sim s = scene(h, "FLATS", 9103 + k);
            for (String w : sets[k]) s.inv.add(h.eng.data.weapons.get(w));
            s.recomputeAll();
            for (int i = 0; i < 26; i++) {
                double a = i * 0.7;
                float r = 140f + (i % 5) * 45f;
                s.spawnAt(i % 3 == 0 ? "SERGEANT" : "COLONIST", s.px + (float) Math.cos(a) * r, s.py + (float) Math.sin(a) * r * 0.8f, false);
            }
            for (int i = 0; i < s.enemyN; i++) s.enemies[i].speed = 0f;
            still(h, 100);
            Shots.save(crop(h.renderTop()), k == 0 ? "int_weapons_new.png" : "int_weapons_evolved.png");
        }
    }

    @Test
    public void standStillFrameAndPlaceMarks() throws Exception {
        Shots h = new Shots();
        Sim s = scene(h, "FLATS", 9105);
        still(h, 60 * 7); // B7: six seconds without moving
        assertTrue("stand-still penalty active", s.standStillActive());
        Bitmap top = h.renderTop();
        Shots.save(top, "int_standstill_top.png");
        // B5 places on the minimap: one shrine and one cache close by (render only, the Pois step is not run)
        Pois p = s.pois;
        p.spotN = 2;
        p.spotX[0] = s.px + 500f;
        p.spotY[0] = s.py - 300f;
        p.spotKind[0] = Pois.SP_SHRINE;
        p.spotX[1] = s.px - 600f;
        p.spotY[1] = s.py + 250f;
        p.spotKind[1] = Pois.SP_CACHE;
        Shots.save(h.renderBottom(), "int_minimap_places.png");
    }

    @Test
    public void shapesTelegraphsOnHazardsAndElites() throws Exception {
        Shots h = new Shots();
        Sim s = scene(h, "FLATS", 9106);
        Settings.get().setShapes(true);
        s.hazardForTest(Entities.HZ_BOMB, s.px + 160f, s.py + 40f, 70f, 1f, 10f, 1.2f);
        s.hazardForTest(Entities.HZ_BOMB, s.px - 180f, s.py - 60f, 55f, 1f, 10f, 0.9f);
        s.time = 900;
        Enemy e = s.spawnEliteForTest("SERGEANT", Elites.M_RAISER | Elites.M_FIRE);
        e.x = s.px + 40f;
        e.y = s.py - 160f;
        still(h, 6);
        Bitmap c = crop(h.renderTop());
        Shots.save(c, "int_shapes_telegraph.png");
        assertTrue("bold orange Shapes edge", near(c, Settings.SHAPES_EDGE & 0xFFFFFF, 60) > 30);
        Settings.get().setShapes(false);
    }
}
