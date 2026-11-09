package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Entities;
import se.felixarvid.thorsurvivors.game.Entities.Corpse;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.SoundEvents;

/** v1.5 B1: the juice on screen (build/screenshots/vfx_*.png, cropped around the hero) and the Sim-to-Vfx wiring. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class VfxShotsTest {
    private static Shots calm(Sim[] out) throws Exception {
        Shots h = new Shots();
        Sim s = h.startRun("FOREST", "WHIP", 11);
        s.noSpawnsForTest();
        s.enemyN = 0;
        h.eng.sim.hp = h.eng.sim.maxHp;
        h.eng.input.clearMovement();
        h.eng.advance(1f / 60f);
        h.world.testDt = 0.016f;
        h.renderTop(); // the first look only takes the snapshot
        out[0] = s;
        return h;
    }

    private static void crop(Shots h, String name) throws Exception {
        Bitmap t = h.renderTop();
        Shots.save(Bitmap.createBitmap(t, 480, 190, 960, 700), name);
    }

    private static Enemy spawn(Sim s, String type, float dx, float dy) {
        Enemy e = s.spawnAt(type, s.px + dx, s.py + dy, false);
        if (e != null) e.speed = 0f;
        return e;
    }

    @Test
    public void hitSparksCritNumbersAndMerging() throws Exception {
        Sim[] o = new Sim[1];
        Shots h = calm(o);
        Sim s = o[0];
        Enemy a = spawn(s, "COLONIST", 140, -40), b = spawn(s, "SERGEANT", -120, 30), c = spawn(s, "SPORE_HOUND", 20, 110);
        a.burn = 2;
        b.frost = 2;
        h.renderTop();
        a.flash = b.flash = c.flash = 0.1f;
        // two hits on a, 0.1 s apart (the Sim merged them into one text): one number; a crit on b
        s.texts[0].life = s.texts[0].maxLife = 0.5f; s.texts[0].owner = a.id; s.texts[0].total = 12;
        s.texts[0].x = a.x; s.texts[0].y = a.y - 20; s.texts[0].len = 2; s.texts[0].digits[0] = '1'; s.texts[0].digits[1] = '2';
        s.texts[1].life = s.texts[1].maxLife = 0.5f; s.texts[1].owner = b.id; s.texts[1].total = 87; s.texts[1].crit = true;
        s.texts[1].x = b.x; s.texts[1].y = b.y - 20; s.texts[1].len = 2; s.texts[1].digits[0] = '8'; s.texts[1].digits[1] = '7';
        s.texts[2].life = s.texts[2].maxLife = 0.5f; s.texts[2].owner = c.id; s.texts[2].total = 9;
        s.texts[2].x = c.x; s.texts[2].y = c.y - 20; s.texts[2].len = 1; s.texts[2].digits[0] = '9';
        h.world.testDt = 0.05f;
        h.renderTop();
        assertEquals("one number per text", 3, h.world.vfx.liveNum());
        assertTrue("sparks from the three hits: " + h.world.vfx.livePart(), h.world.vfx.livePart() >= 9);
        s.texts[0].total = 30; // the Sim merged another hit into it
        h.world.testDt = 0.04f;
        h.renderTop();
        assertEquals("the merge sums into the same number", 3, h.world.vfx.liveNum());
        h.world.testDt = 0.02f;
        crop(h, "vfx_sparks_crit.png");
        h.world.testDt = 0.2f;
        h.renderTop();
        assertEquals("sparks are gone after 0.18 s", 0, h.world.vfx.livePart());
    }

    @Test
    public void eliteAndBossKillsFreezeTheWorldBriefly() throws Exception {
        Sim[] o = new Sim[1];
        Shots h = calm(o);
        Sim s = o[0];
        Corpse k = s.corpses[5];
        k.type = spawn(s, "COLONIST", 300, 0).type;
        s.enemyN = 0;
        k.active = true; k.elite = true; k.boss = false; k.age = 0f; k.x = s.px + 100; k.y = s.py; k.radius = 12f; k.id = 777;
        h.renderTop();
        assertEquals(Vfx.STOP_ELITE, h.world.vfx.stopLeft(), 1e-6f);
        assertEquals(0.01f, h.world.filterDt(0.05f), 1e-5f);
        k = s.corpses[6];
        k.type = s.corpses[5].type;
        k.active = true; k.boss = true; k.age = 0f; k.x = s.px; k.y = s.py; k.radius = 40f; k.id = 778;
        h.renderTop();
        h.world.vfx.hitStop(1f);
        assertEquals(Vfx.STOP_MAX, h.world.vfx.stopLeft(), 1e-6f);
        assertTrue("the boss kill shook the camera", h.world.vfx.trauma > 0.3f);
    }

    @Test
    public void levelUpBurst() throws Exception {
        Sim[] o = new Sim[1];
        Shots h = calm(o);
        Sim s = o[0];
        s.level++;
        h.world.testDt = 0.01f;
        h.renderTop();
        assertEquals(12, h.world.vfx.livePart());
        h.world.testDt = 0.12f;
        crop(h, "vfx_levelup.png");
        h.world.testDt = 0.1f;
        h.renderTop();
        assertTrue(h.world.vfx.effectLive(0));
    }

    @Test
    public void chestFanfareAndEvolutionPillar() throws Exception {
        Sim[] o = new Sim[1];
        Shots h = calm(o);
        h.eng.chestLines = new String[]{"EVOLUTION!", "Whip -> Bloody Tear", "+40 gold"};
        h.eng.phase = Engine.PH_CHEST;
        h.world.testDt = 0.01f;
        h.renderTop();
        assertEquals(20, h.world.vfx.livePart());
        assertEquals(Vfx.E_PILLAR, h.world.vfx.ekind[1]);
        h.world.testDt = 0.3f;
        crop(h, "vfx_chest_evolution_t03.png");
        h.world.testDt = 1.2f;
        crop(h, "vfx_chest_evolution_t15.png");
    }

    @Test
    public void heroHitFlashAndEdgePulse() throws Exception {
        Sim[] o = new Sim[1];
        Shots h = calm(o);
        Sim s = o[0];
        s.hp -= s.maxHp * 0.3f;
        s.hitShake = 0.15f;
        h.world.testDt = 0.03f;
        h.renderTop();
        assertTrue(h.world.vfx.heroFlash > 0f);
        assertTrue(h.world.vfx.trauma > 0.3f);
        Bitmap t = h.renderTop();
        Shots.save(t, "vfx_hero_hit.png");
        s.hp = s.maxHp * 0.15f;
        h.world.testDt = 0.2f;
        for (int i = 0; i < 4; i++) h.renderTop();
        Shots.save(h.renderTop(), "vfx_low_hp.png");
    }

    @Test
    public void largeEnemiesDissolveSmallOnesPop() throws Exception {
        Sim[] o = new Sim[1];
        Shots h = calm(o);
        Sim s = o[0];
        Enemy big = spawn(s, "SMELTER", 90, 0);
        Enemy small = spawn(s, "COLONIST", -90, 0);
        h.renderTop();
        float bigR = big.radius;
        s.hurt(big, 1e6f, 0, 1, 0, 0, 0);
        s.hurt(small, 1e6f, 0, 1, 0, 0, 0);
        h.eng.advance(1f / 60f);
        h.world.testDt = 0.05f;
        h.renderTop();
        assertEquals(bigR >= Vfx.LARGE_R ? 6 : 0, h.world.vfx.livePart() - (small.radius >= Vfx.LARGE_R ? 6 : 0));
        h.world.testDt = 0.05f;
        crop(h, "vfx_dissolve.png");
    }

    @Test
    public void gemBlipsFollowThePickupChimeAndMagnetsLeaveTrails() throws Exception {
        Sim[] o = new Sim[1];
        Shots h = calm(o);
        Sim s = o[0];
        for (int i = 0; i < 8; i++) s.addPickupForTest(Entities.K_GEM, s.px + 90 + i * 22, s.py - 30 + (i % 3) * 25, 1f);
        for (int i = 0; i < s.pickN; i++) s.picks[i].pulled = true;
        h.renderTop();
        for (int i = 0; i < 3; i++) {
            h.eng.sound.emit(SoundEvents.GEM, i);
            h.eng.sound.advance(0.11f); // past the per-window rate limit
            h.world.testDt = 0.05f;
            h.renderTop();
        }
        assertTrue("blips: " + h.world.vfx.liveNum(), h.world.vfx.liveNum() >= 2);
        crop(h, "vfx_gem_trail.png");
    }
}
