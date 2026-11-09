package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.data.WeaponDef;
import se.felixarvid.thorsurvivors.game.BossAi;
import se.felixarvid.thorsurvivors.game.Elites;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Equipment;
import se.felixarvid.thorsurvivors.game.Offer;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.ui.PanelRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.5 batch 2, bottom-screen HUD and UX (items 11-20): cooldown arcs, status strip, boss panel, elite tags, build strip with evolve
 * pips, pause with stats, offer tooltips, evolution tracker, large-text mode and the XP bar. Frames go to SHOT_DIR (b2_*.png); the asserts
 * compare pixels of a frame with and without the feature, so a feature that silently stops drawing fails here.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class Batch2ShotsTest {
    private static final float DT = 1f / 60f;

    @After
    public void reset() {
        PanelRenderer.forceLargeText = false;
    }

    private static Bitmap copy(Bitmap b) {
        return b.copy(Bitmap.Config.ARGB_8888, false);
    }

    /** Pixels in the rectangle that differ between two frames by more than a little. */
    private static int diff(Bitmap a, Bitmap b, int x0, int y0, int x1, int y1) {
        int n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int p = a.getPixel(x, y), q = b.getPixel(x, y);
                int d = Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255)) + Math.abs((p & 255) - (q & 255));
                if (d > 40) n++;
            }
        }
        return n;
    }

    private static int near(Bitmap b, int x0, int y0, int x1, int y1, int rgb, int tol) {
        int n = 0, r0 = rgb >> 16 & 255, g0 = rgb >> 8 & 255, b0 = rgb & 255;
        for (int y = Math.max(0, y0); y < Math.min(b.getHeight(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(b.getWidth(), x1); x++) {
                int p = b.getPixel(x, y);
                if (Math.abs((p >> 16 & 255) - r0) <= tol && Math.abs((p >> 8 & 255) - g0) <= tol && Math.abs((p & 255) - b0) <= tol) n++;
            }
        }
        return n;
    }

    /** A built hero in a quiet scene: six weapons, six passives, six items; the Whip is maxed without its passive. */
    private Sim loaded(Shots h) {
        h.panel.dtOverride = DT;
        Sim s = h.startRun("FLATS", "WHIP", 21, "MAGIC_MISSILE", "KNIFE", "AXE", "GARLIC", "HOLYBOOK", "POWER", "ARMOR", "COOLDOWN", "AREA",
                "MAXHEALTH", "MAGNET");
        s.noSpawnsForTest();
        s.enemyN = 0;
        s.time = 14 * 60 + 12;
        s.level = 17;
        s.kills = 1342;
        s.gold = 5120;
        s.xp = s.xpNeed * 0.62f;
        for (int i = 0; i < s.inv.weaponCount; i++) s.inv.weapons[i].level = 1 + (i * 3) % 8;
        s.inv.weapons[1].level = s.inv.weapons[1].def.maxLevel();
        for (int i = 0; i < s.inv.passiveCount; i++) s.inv.passives[i].level = 1 + i % 4;
        s.giveEquipment("COMBAT_HELM", Equipment.COMMON);
        s.giveEquipment("PLATED_VEST", Equipment.UNCOMMON);
        s.recomputeAll();
        s.hp = s.maxHp * 0.7f;
        h.eng.advance(DT);
        s.hp = s.maxHp * 0.7f;
        return s;
    }

    @Test
    public void cooldownArcsSweepTheWeaponSlots() throws Exception {
        Shots h = new Shots();
        Sim s = loaded(h);
        Sim.WeaponState w = s.weapons[2]; // the Axe: a long wait
        w.cd = 0f;
        Bitmap ready = copy(h.renderBottom());
        w.cd = w.interval * 0.5f;
        Bitmap half = copy(h.renderBottom());
        w.cd = w.interval * 0.95f;
        Bitmap early = copy(h.renderBottom());
        Shots.save(ready, "b2_cooldown_ready.png");
        Shots.save(half, "b2_cooldown_half.png");
        Shots.save(early, "b2_cooldown_early.png");
        int x0 = 30 + 2 * 96, y0 = 376; // slot 2 of the weapon row (88 px tiles, 8 px gap)
        int dHalf = diff(ready, half, x0, y0, x0 + 88, y0 + 88), dEarly = diff(ready, early, x0, y0, x0 + 88, y0 + 88);
        assertTrue("half the wait left darkens about half the tile (" + dHalf + ")", dHalf > 800);
        assertTrue("nearly all of it left darkens more (" + dEarly + " vs " + dHalf + ")", dEarly > dHalf);
        assertEquals("the other slots do not change", 0, diff(ready, half, 30, y0, 30 + 88, y0 + 88));
    }

    @Test
    public void statusStripShowsBarsAndBlinks() throws Exception {
        Shots h = new Shots();
        Sim s = loaded(h);
        Bitmap none = copy(h.renderBottom());
        s.heroBurnT = 2.6f;
        s.heroSlowT = 1.8f;
        s.heroDecayT = 1.2f;
        s.heroStunT = 0.6f;
        s.guardT = 3f;
        s.dodgeT = 0.2f;
        s.time = 100.0;
        Bitmap all = copy(h.renderBottom());
        Shots.save(all, "b2_status_strip.png");
        assertTrue("six chips with bars", diff(none, all, 880, 212, 1210, 252) > 900);
        // the ones ending within a second blink: two frames 1/8 s apart differ
        s.time = 100.0 + 1.0 / 8.0;
        Bitmap later = copy(h.renderBottom());
        assertTrue("a status in its last second blinks", diff(all, later, 880, 212, 1210, 252) > 150);
    }

    @Test
    public void bossPanelHasTicksAttackGlyphAndEnrage() throws Exception {
        Shots h = new Shots();
        Sim s = loaded(h);
        Enemy boss = s.spawnAt("THE_FIRST", s.px + 300, s.py, true);
        boss.speed = 0;
        h.eng.advance(DT);
        s.hp = s.maxHp * 0.7f;
        boss.hp = boss.maxHp * 0.8f;
        boss.phase = 0;
        boss.ai = 0;
        boss.tele = 0;
        Bitmap idle = copy(h.renderBottom());
        boss.ai = BossAi.BA_WINDUP;
        boss.tele = 0.9f;
        boss.teleMax = 1.5f;
        boss.teleLen = 0f;
        Bitmap ring = copy(h.renderBottom());
        boss.teleLen = 400f;
        Bitmap line = copy(h.renderBottom());
        Shots.save(idle, "b2_boss_idle.png");
        Shots.save(ring, "b2_boss_windup_area.png");
        Shots.save(line, "b2_boss_windup_line.png");
        int gx = 642 + 60 + 14, gy = 516 + 30; // the glyph slot under the name (the zone starts at x 642)
        assertTrue("an area windup draws its glyph", diff(idle, ring, gx, gy, gx + 200, gy + 40) > 300);
        assertTrue("a charge is told apart from an area", diff(ring, line, gx, gy, gx + 26, gy + 28) > 40);
        // at 80 % both phase ticks (66 and 33 %) are still ahead of the bar: white
        int tick66 = 30 + (int) (1180 * 0.66);
        assertTrue("a white tick at 66 % (bar 607..625)", near(idle, tick66 - 3, 607, tick66 + 3, 625, 0xFFF4E6, 30) > 20);
        int cut = 30 + (int) (1180 * 0.33);
        assertTrue("a tick at 33 %", near(idle, cut - 3, 607, cut + 3, 625, 0xFFF4E6, 30) > 20);
        // enraged: the badge appears
        Enemy slag = s.spawnAt("SLAGBORN", s.px + 300, s.py, true);
        slag.speed = 0;
        boss.dead = true;
        h.eng.advance(DT);
        s.hp = s.maxHp * 0.7f;
        Bitmap calm = copy(h.renderBottom());
        slag.enraged = true;
        slag.hp = slag.maxHp * 0.4f;
        Bitmap mad = copy(h.renderBottom());
        Shots.save(mad, "b2_boss_enraged.png");
        assertTrue("ENRAGED replaces the percent line", diff(calm, mad, 900, 516, 1210, 548) > 80);
    }

    @Test
    public void eliteChipsCountModifiersAndCapAtFour() throws Exception {
        Shots h = new Shots();
        Sim s = loaded(h);
        s.time = 600;
        s.spawnEliteForTest("SERGEANT", Elites.M_FIRE).speed = 0;
        h.eng.advance(DT);
        s.hp = s.maxHp * 0.7f;
        Bitmap one = copy(h.renderBottom());
        Enemy b = s.spawnEliteForTest("SERGEANT", Elites.M_FIRE | Elites.M_FROST | Elites.M_ARMORED);
        b.speed = 0;
        Enemy c = s.spawnEliteForTest("SERGEANT", Elites.M_BARRAGE | Elites.M_VOLCANO | Elites.M_RAISER);
        c.speed = 0;
        Bitmap many = copy(h.renderBottom());
        Shots.save(one, "b2_elite_one.png");
        Shots.save(many, "b2_elite_many.png");
        assertTrue("more modifiers, more chips", diff(one, many, 580, 516, 1210, 560) > 500);
        // six modifiers are alive: four chips and a "+2" (v1.5 integration: B6 made it nine modifiers in all)
        assertEquals(9, Elites.COUNT);
    }

    @Test
    public void buildStripShowsEvolvePips() throws Exception {
        Shots h = new Shots();
        Sim s = loaded(h);
        WeaponDef whip = s.inv.weapons[0].def;
        s.inv.weapons[0].level = whip.maxLevel();
        Bitmap hollow = copy(h.renderBottom()); // maxed, passive missing (the Whip needs Max Health: owned? remove it)
        for (int i = 0; i < s.inv.passiveCount; i++) if (whip.evoSynergy.contains(s.inv.passives[i].def.key)) s.inv.passives[i].level = 1;
        Bitmap ready = copy(h.renderBottom());
        Shots.save(ready, "b2_build_evolve_ready.png");
        Shots.save(hollow, "b2_build_evolve_state_a.png");
        // the tracker line in the WEAPONS row is gold when something is ready
        assertTrue("the gold evolve star on the Whip tile", near(ready, 30 + 60, 376, 30 + 88, 376 + 30, 0xFFD84A, 40) > 30);
    }

    @Test
    public void pauseScreenShowsStatsBuildAndEvolutionLines() throws Exception {
        Shots h = new Shots();
        Sim s = loaded(h);
        h.eng.phase = Engine.PH_PAUSED;
        h.eng.focus = 0;
        Bitmap p = copy(h.renderBottom());
        Shots.save(p, "b2_pause.png");
        assertTrue("stats, build and tracker fill the plate", near(p, 40, 300, 1200, 1000, 0xEFE8DC, 40) > 3000);
        h.eng.focus = 1;
        Bitmap q = copy(h.renderBottom());
        assertTrue("the focus moves between RESUME and QUIT", diff(p, q, 0, 900, 1240, 1060) > 800);
        PanelRenderer compact = new PanelRenderer(true);
        Bitmap cb = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        compact.draw(new android.graphics.Canvas(cb), 1920, 1080);
        Shots.save(cb, "b2_pause_compact.png");
        h.eng.phase = Engine.PH_PLAYING;
    }

    @Test
    public void offerCardTooltipAndEvolvesBanner() throws Exception {
        Shots h = new Shots();
        Sim s = loaded(h);
        WeaponDef whip = s.inv.weapons[0].def;
        s.inv.weapons[0].level = whip.maxLevel() - 1;
        List<Offer> offers = new ArrayList<>();
        offers.add(new Offer(Offer.WEAPON_LEVEL, whip, whip.maxLevel()));
        offers.add(new Offer(Offer.WEAPON_LEVEL, s.inv.weapons[2].def, s.inv.weapons[2].level + 1));
        offers.add(new Offer(Offer.PASSIVE_LEVEL, s.inv.passives[0].def, s.inv.passives[0].level + 1));
        h.eng.offers = offers;
        h.eng.phase = Engine.PH_LEVELUP;
        h.eng.focus = 0;
        Bitmap a = copy(h.renderBottom());
        Shots.save(a, "b2_offer_tooltip_evolves.png");
        h.eng.focus = 1;
        Bitmap b = copy(h.renderBottom());
        Shots.save(b, "b2_offer_tooltip_plain.png");
        assertTrue("the Whip's last level carries the gold EVOLVES! banner", near(a, 700, 170, 1200, 260, 0xFFD84A, 40) > 200);
        assertTrue("the second card does not", near(b, 700, 170, 1200, 260, 0xFFD84A, 40) < near(a, 700, 170, 1200, 260, 0xFFD84A, 40));
        assertTrue("the focus moves the tooltip", diff(a, b, 0, 170, 1240, 700) > 3000);
        h.eng.phase = Engine.PH_PLAYING;
    }

    @Test
    public void largeTextIsBiggerOutlinedAndStillFits() throws Exception {
        Shots h = new Shots();
        Sim s = loaded(h);
        s.heroBurnT = 2f;
        Bitmap normal = copy(h.renderBottom());
        PanelRenderer.forceLargeText = true;
        Bitmap large = copy(h.renderBottom());
        PanelRenderer.forceLargeText = false;
        Shots.save(normal, "b2_text_normal.png");
        Shots.save(large, "b2_text_large.png");
        int bright = 0, brightL = 0;
        for (int y = 20; y < 110; y++) {
            for (int x = 40; x < 1200; x++) {
                if ((normal.getPixel(x, y) & 255) > 150) bright++;
                if ((large.getPixel(x, y) & 255) > 150) brightL++;
            }
        }
        assertTrue("the header text covers more pixels at +20 % (" + bright + " -> " + brightL + ")", brightL > bright * 1.15);
        h.eng.phase = Engine.PH_PAUSED;
        Bitmap pauseNormal = copy(h.renderBottom());
        PanelRenderer.forceLargeText = true;
        Bitmap pauseLarge = copy(h.renderBottom());
        Shots.save(pauseLarge, "b2_pause_large.png");
        assertTrue(diff(pauseNormal, pauseLarge, 0, 0, 1240, 1080) > 5000);
        h.eng.phase = Engine.PH_PLAYING;
    }

    @Test
    public void xpBarEasesPulsesAndMarksAChest() throws Exception {
        Shots h = new Shots();
        Sim s = loaded(h);
        h.panel.dtOverride = DT;
        s.xp = s.xpNeed * 0.2f;
        h.renderBottom();
        h.renderBottom();
        int lumen0 = near(h.renderBottom(), 100, 180, 1200, 200, 0x7CFFB4, 30);
        s.xp = s.xpNeed * 0.8f; // a jump: the bar must not follow in one frame
        int lumen1 = near(h.renderBottom(), 100, 180, 1200, 200, 0x7CFFB4, 30);
        for (int i = 0; i < 30; i++) h.renderBottom(); // 0.5 s
        int lumen2 = near(h.renderBottom(), 100, 180, 1200, 200, 0x7CFFB4, 30);
        assertTrue("one frame later the fill has barely moved (" + lumen0 + " -> " + lumen1 + ")", lumen1 < lumen0 + (lumen2 - lumen0) * 0.6);
        assertTrue("after half a second it has arrived (" + lumen2 + ")", lumen2 > lumen0 + (lumen2 - lumen0) * 0.9 && lumen2 > lumen0 * 2);
        // level-up: the LV label pulses (bigger gold text)
        Bitmap calm = copy(h.renderBottom());
        s.level++;
        s.xp = 0;
        Bitmap pulse = copy(h.renderBottom());
        Shots.save(pulse, "b2_xp_levelup_pulse.png");
        int goldCalm = near(calm, 100, 176, 260, 206, 0xFFD84A, 40), goldPulse = near(pulse, 100, 176, 260, 206, 0xFFD84A, 40);
        assertTrue("the label flashes gold on a level-up (" + goldCalm + " -> " + goldPulse + ")", goldPulse > goldCalm + 20);
        // a chest is due: the tick and the CHEST label
        s.pendingChests = 1;
        Bitmap chest = copy(h.renderBottom());
        Shots.save(chest, "b2_xp_chest_tick.png");
        s.pendingChests = 0;
        int goldish = 0; // the label is anti-aliased and pulses: count any gold-ish pixel, not an exact colour
        for (int y = 170; y < 210; y++) for (int x = 1050; x < 1215; x++) {
            int p = chest.getPixel(x, y);
            if ((p >> 16 & 255) > 120 && (p >> 8 & 255) > 100 && (p & 255) < 100) goldish++;
        }
        assertTrue("CHEST marks the bar end (" + goldish + ")", goldish > 60);
    }
}
