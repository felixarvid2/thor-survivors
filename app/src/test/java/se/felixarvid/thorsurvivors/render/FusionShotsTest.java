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

import se.felixarvid.thorsurvivors.data.WeaponDef;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Offer;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.ui.PanelRenderer;

import java.lang.reflect.Method;

/**
 * Weapon fusions on screen: the gold FUSE level-up card with both parents, the chest line, the "F" pip on the fused slot, the Salvager's
 * Fuse Kit, and each fusion fighting on the top screen. Screenshots go to build/screenshots (fusion_*.png). Also the allocation rule for
 * the FUSE card and the chest line.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class FusionShotsTest {
    private static final float DT = 1f / 60f;

    private static void master(Sim s, String key) {
        WeaponDef d = s.data.weapons.get(key);
        s.inv.add(d);
        for (int i = 0; i < s.inv.weaponCount; i++) if (s.inv.weapons[i].def == d) s.inv.weapons[i].level = d.maxLevel();
        s.recomputeAll();
    }

    /** Empties the weapon slots, then masters the given base weapons (the starter list has no Slag Cannon or Toxic Vent). */
    private static void only(Sim s, String... keys) {
        s.inv.weaponCount = 0;
        for (String k : keys) master(s, k);
    }

    private static void frames(Shots h, int n) {
        for (int i = 0; i < n; i++) {
            h.eng.sim.hp = h.eng.sim.maxHp;
            h.eng.input.clearMovement();
            h.eng.advance(DT);
        }
    }

    /** A run with a ready pair (Vibro Lash + Arc Coil) that has been waiting 100 s, and a level-up pending: the FUSE card is on the bottom screen. */
    private static Sim fuseCard(Shots h) {
        Sim s = h.startRun("FLATS", "WHIP", 7L);
        s.noSpawnsForTest();
        s.enemyN = 0;
        s.inv.weapons[0].level = s.inv.weapons[0].def.maxLevel();
        master(s, "LIGHTNING");
        frames(h, 40);
        s.time = 300.0;
        s.fuseReadySince = 200.0; // ready for 100 s
        s.pendingLevelUps = 1;
        frames(h, 2);
        return s;
    }

    @Test
    public void theFuseCardAndTheChestLine() throws Exception {
        Shots h = new Shots();
        Sim s = fuseCard(h);
        assertEquals(Engine.PH_LEVELUP, h.eng.phase);
        boolean fuse = false;
        for (Offer o : h.eng.offers) fuse |= o.kind == Offer.FUSE;
        assertTrue("the FUSE card is among the offers", fuse);
        h.eng.focus = h.eng.offers.size() - 1;
        Shots.save(h.renderBottom(), "fusion_card.png");
        // take it: the slot shows the F pip
        h.eng.input.post(se.felixarvid.thorsurvivors.game.Input.CMD_CONFIRM, 0, 0, 0);
        frames(h, 2);
        assertEquals(1, s.inv.weaponCount);
        Shots.save(h.renderBottom(), "fusion_slot_pip.png");
        // a chest that fuses
        Sim s2 = h.startRun("FLATS", "WHIP", 8L);
        s2.noSpawnsForTest();
        s2.enemyN = 0;
        s2.inv.weapons[0].level = s2.inv.weapons[0].def.maxLevel();
        master(s2, "HOLYBOOK");
        master(s2, "LIGHTNING");
        s2.pendingChests = 1;
        frames(h, 2);
        assertEquals(Engine.PH_CHEST, h.eng.phase);
        Shots.save(h.renderBottom(), "fusion_chest.png");
    }

    @Test
    public void theFuseKitAtTheSalvager() throws Exception {
        Shots h = new Shots();
        Sim s = h.startRun("FLATS", "WHIP", 9L);
        s.noSpawnsForTest();
        s.enemyN = 0;
        only(s, "GARLIC", "HOLYBOOK");
        s.time = 269.9;
        frames(h, 12);
        assertTrue(s.merchant.present);
        s.px = s.merchant.x + 12f;
        s.py = s.merchant.y;
        s.gold = 300;
        frames(h, 2);
        assertEquals(Engine.PH_MERCHANT, h.eng.phase);
        assertTrue(s.merchant.kit);
        h.eng.focus = 3;
        Shots.save(h.renderBottom(), "fusion_kit.png");
    }

    @Test
    public void eachFusionInAction() throws Exception {
        String[][] pairs = {{"WHIP", "LIGHTNING"}, {"FIREBALL", "HOLYWATER"}, {"MAGIC_MISSILE", "KNIFE"}, {"GARLIC", "HOLYBOOK"},
                {"AXE", "CROSS"}, {"HOLYBOOK", "LIGHTNING"}, {"GARLIC", "FIREBALL"}};
        for (int i = 0; i < pairs.length; i++) {
            Shots h = new Shots();
            Sim s = h.startRun("FLATS", "WHIP", 20L + i);
            only(s, pairs[i][0], pairs[i][1]);
            assertTrue(pairs[i][0], s.fuseNow(s.inv.readyFusion(s.data.fusions, s.data.weapons)));
            s.inv.weapons[0].level = 4;
            s.recomputeAll();
            h.drive(70, true); // the real waves, auto-picked level-ups
            Shots.save(h.renderTop(), "fusion_action_" + s.inv.weapons[0].def.key.toLowerCase() + ".png");
            if (i == 0) Shots.save(h.renderBottom(), "fusion_hud.png");
        }
    }

    // ------------------------------------------------------------------ allocation

    @Test
    public void theFuseCardAndTheChestLineDrawWithoutAllocating() throws Exception {
        Object mx = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
        Method alloc = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", long.class);
        long tid = Thread.currentThread().getId();
        long probe = (Long) alloc.invoke(mx, tid);
        probe = (Long) alloc.invoke(mx, tid) - probe;
        Shots h = new Shots();
        fuseCard(h);
        Bitmap bp = Bitmap.createBitmap(1240, 1080, Bitmap.Config.ARGB_8888);
        Canvas cp = new Canvas(bp);
        PanelRenderer panel = new PanelRenderer(false);
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                Sim s = h.eng.sim;
                h.eng.input.post(se.felixarvid.thorsurvivors.game.Input.CMD_CONFIRM, 0, 0, 0);
                frames(h, 2);
                master(s, "HOLYBOOK");
                master(s, "KNIFE");
                s.pendingChests = 1;
                frames(h, 2);
                assertEquals(Engine.PH_CHEST, h.eng.phase);
            } else {
                h.eng.focus = h.eng.offers.size() - 1;
            }
            for (int i = 0; i < 60; i++) panel.draw(cp, 1240, 1080);
            long bytes = 0;
            int n = 100, spikes = 0;
            for (int i = 0; i < n; i++) {
                long a0 = (Long) alloc.invoke(mx, tid);
                panel.draw(cp, 1240, 1080);
                long d = (Long) alloc.invoke(mx, tid) - a0 - probe;
                if (d >= 8192) spikes++; else bytes += d;
            }
            assertTrue("pass " + pass + ": " + spikes + " spikes", spikes <= 6);
            assertTrue("pass " + pass + " allocates " + bytes / n + " B per frame", bytes / n < 256);
        }
    }
}
