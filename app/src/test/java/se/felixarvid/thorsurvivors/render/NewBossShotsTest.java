package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Sim;

/**
 * v1.3 timed bosses on screen: each one mid-attack with its telegraph (frames go to $SHOT_DIR as newboss_*.png) and the banner
 * with its portrait. The asserts check what can be measured: a red-orange telegraph is on the screen while it warns.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class NewBossShotsTest {
    private Sim scene(Shots h, String stage, long seed, String boss, float dx, float dy) {
        Sim s = h.startRun(stage, "WHIP", seed);
        s.noSpawnsForTest();
        s.enemyN = 0;
        s.inv.weaponCount = 0;
        s.recomputeAll();
        s.time = 1000;
        s.hp = s.maxHp;
        Enemy b = s.spawnAt(boss, s.px + dx, s.py + dy, true);
        b.power = 0f;
        b.speed = 0f;
        s.bannerT = 0f;
        return s;
    }

    private void step(Shots h, int frames) {
        Sim s = h.eng.sim;
        for (int i = 0; i < frames; i++) {
            s.hp = s.maxHp;
            h.eng.input.stickX = 0;
            h.eng.input.stickY = 0;
            int ph = h.eng.phase;
            if (ph == Engine.PH_LEVELUP || ph == Engine.PH_CHEST) h.eng.input.post(se.felixarvid.thorsurvivors.game.Input.CMD_CONFIRM, 0, 0, 0);
            h.eng.advance(1f / 60f);
        }
    }

    private static int danger(Bitmap b) {
        int n = 0;
        for (int y = 0; y < b.getHeight(); y += 2) {
            for (int x = 0; x < b.getWidth(); x += 2) {
                int p = b.getPixel(x, y);
                int r = p >> 16 & 255, g = p >> 8 & 255, bl = p & 255;
                if (r > 215 && g > 30 && g < 110 && bl < 70) n++;
            }
        }
        return n;
    }

    private int shot(Shots h, String name, int minDanger) throws Exception {
        Bitmap f = h.renderTop().copy(Bitmap.Config.ARGB_8888, false);
        Shots.save(f, "newboss_" + name + ".png");
        int d = danger(f);
        assertTrue(name + ": red-orange telegraph visible: " + d, d >= minDanger);
        return d;
    }

    @Test
    public void broodMother() throws Exception {
        Shots h = new Shots();
        Sim s = scene(h, "FLATS", 701, "BROOD_MOTHER", 260, -30);
        Enemy b = s.enemies[0];
        b.actT = 0f;
        step(h, 40);
        shot(h, "brood_eggs", 150);
        b.hp = b.maxHp * 0.49f;
        step(h, 70);
        shot(h, "brood_burst", 400);
        Shots.save(h.renderBottom(), "newboss_hud_brood.png");
    }

    @Test
    public void frozenWelder() throws Exception {
        Shots h = new Shots();
        Sim s = scene(h, "MINES", 702, "FROZEN_WELDER", 220, -30);
        Enemy b = s.enemies[0];
        b.actT = 0f;
        step(h, 30);
        shot(h, "welder_chain", 400);
        step(h, 60 + 14);
        Shots.save(h.renderTop().copy(Bitmap.Config.ARGB_8888, false), "newboss_welder_dash.png");
    }

    @Test
    public void commandantHale() throws Exception {
        Shots h = new Shots();
        Sim s = scene(h, "FLATS", 703, "COMMANDANT_HALE", 330, -20);
        Enemy b = s.enemies[0];
        b.mt0 = 0f;
        b.mt1 = 1e6f;
        step(h, 100);
        shot(h, "hale_barrage", 300);
        Sim t = scene(h = new Shots(), "FLATS", 704, "COMMANDANT_HALE", 330, -20);
        Enemy c = t.enemies[0];
        c.mt0 = 1e6f;
        c.mt1 = 0f;
        step(h, 55);
        shot(h, "hale_order", 150);
    }

    @Test
    public void overseer() throws Exception {
        Shots h = new Shots();
        Sim s = scene(h, "MINES", 705, "OVERSEER_UNIT_7", 230, -20);
        Enemy b = s.enemies[0];
        b.actT = 0f;
        b.cnt = 0;
        step(h, 35);
        shot(h, "overseer_spiral_warning", 100);
        step(h, 150);
        Shots.save(h.renderTop().copy(Bitmap.Config.ARGB_8888, false), "newboss_overseer_spiral.png");
        Shots h2 = new Shots();
        Sim t = scene(h2, "MINES", 706, "OVERSEER_UNIT_7", 230, -20);
        t.enemies[0].actT = 0f;
        t.enemies[0].cnt = 1;
        step(h2, 70);
        shot(h2, "overseer_laser_warning", 100);
        step(h2, 60);
        Shots.save(h2.renderTop().copy(Bitmap.Config.ARGB_8888, false), "newboss_overseer_laser.png");
    }

    @Test
    public void governorKade() throws Exception {
        Shots h = new Shots();
        Sim s = scene(h, "FLATS", 707, "GOVERNOR_KADE", 330, -20);
        Enemy b = s.enemies[0];
        b.mt0 = 1e6f;
        b.mt1 = 0f;
        step(h, 50);
        shot(h, "kade_decree", 400);
        Shots h2 = new Shots();
        Sim t = scene(h2, "FLATS", 708, "GOVERNOR_KADE", 330, -20);
        t.enemies[0].mt0 = 0f;
        t.enemies[0].mt1 = 1e6f;
        step(h2, 95);
        Shots.save(h2.renderTop().copy(Bitmap.Config.ARGB_8888, false), "newboss_kade_decoys.png");
        step(h2, 30);
        Shots.save(h2.renderTop().copy(Bitmap.Config.ARGB_8888, false), "newboss_kade_decoys2.png");
    }

    @Test
    public void banners() throws Exception {
        String[] keys = {"BROOD_MOTHER", "FROZEN_WELDER", "COMMANDANT_HALE", "OVERSEER_UNIT_7", "GOVERNOR_KADE"};
        for (String k : keys) {
            Shots h = new Shots();
            Sim s = h.startRun("FLATS", "WHIP", 710);
            s.noSpawnsForTest();
            s.enemyN = 0;
            s.time = 900;
            s.spawnAt(k, s.px + 400, s.py, true);
            s.bannerT = Sim.BANNER_LIFE;
            s.bannerKey = k;
            s.bannerName = s.data.enemies.get(k).name.toUpperCase();
            step(h, 20);
            Shots.save(h.renderBottom(), "newboss_banner_" + k.toLowerCase() + ".png");
        }
    }
}
