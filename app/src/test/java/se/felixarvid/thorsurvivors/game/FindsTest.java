package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.WeaponDef;

import java.util.HashMap;

/** Weapon Caches and Gear Lockers on the Ashfall stages: placement, rewards, none on the legacy stages. */
public class FindsTest {
    private static final float DT = 1f / 60f;

    private Sim sim(String stage, long seed) throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get(stage), "WHIP", new HashMap<String, Double>(), null, seed);
        s.noSpawns = true;
        s.enemyN = 0;
        return s;
    }

    private static int firstOfKind(Sim s, int kind) {
        for (int i = 0; i < s.findN; i++) if (s.findKind[i] == kind) return i;
        return -1;
    }

    private static void walkTo(Sim s, int i) {
        s.px = s.findX[i];
        s.py = s.findY[i];
        s.update(DT, new Input());
    }

    @Test
    public void placementIsDeterministicAndValid() throws Exception {
        for (String stage : new String[]{"FLATS", "MINES"}) {
            for (long seed = 1; seed <= 20; seed++) {
                Sim a = sim(stage, seed), b = sim(stage, seed);
                assertEquals(6, a.findN);
                int weapons = 0, near = 0;
                float nd = Float.MAX_VALUE;
                for (int i = 0; i < a.findN; i++) {
                    assertEquals(a.findX[i], b.findX[i], 0f);
                    assertEquals(a.findY[i], b.findY[i], 0f);
                    assertEquals(a.findKind[i], b.findKind[i]);
                    assertFalse(a.map.blocked(a.findX[i], a.findY[i]));
                    if (a.findKind[i] == Sim.FIND_WEAPON) weapons++;
                    float d0 = (float) Math.hypot(a.findX[i], a.findY[i]);
                    assertTrue("far out: " + d0, d0 >= 1290f && d0 <= 5000f + 20 * 50f);
                    float[] oc = {a.findX[i] + 22f, a.findX[i] - 22f, a.findX[i], a.findX[i]};
                    float[] od = {a.findY[i], a.findY[i], a.findY[i] + 22f, a.findY[i] - 22f};
                    for (int q = 0; q < 4; q++) assertFalse(a.map.blocked(oc[q], od[q]));
                    if (d0 < nd) {
                        nd = d0;
                        near = i;
                    }
                    for (int j = i + 1; j < a.findN; j++) {
                        assertTrue(Math.hypot(a.findX[i] - a.findX[j], a.findY[i] - a.findY[j]) >= 150.0);
                    }
                }
                assertEquals(4, weapons);
                assertEquals(Sim.FIND_WEAPON, a.findKind[near]);
            }
        }
    }

    @Test
    public void theNearestIsAboutTwiceAsFarAsBefore() throws Exception {
        for (long seed = 1; seed <= 10; seed++) {
            Sim a = sim("FLATS", seed);
            float nd = Float.MAX_VALUE, fd = 0;
            for (int i = 0; i < a.findN; i++) {
                float d0 = (float) Math.hypot(a.findX[i], a.findY[i]);
                nd = Math.min(nd, d0);
                fd = Math.max(fd, d0);
            }
            assertTrue("nearest " + nd, nd >= 1290f && nd < 1600f);
            assertTrue("farthest " + fd, fd >= 4990f);
        }
    }

    @Test
    public void theWorldWorksAtFiveThousandUnits() throws Exception {
        Sim s = sim("FLATS", 3);
        int open = 0;
        for (int i = 0; i < 24; i++) {
            double ang = i * Math.PI / 12;
            float x = (float) Math.cos(ang) * 5000f, y = (float) Math.sin(ang) * 5000f;
            if (!s.map.blocked(x, y)) open++;
        }
        assertTrue("open ground out there", open > 6);
        s.px = 5000f;
        s.py = -3000f;
        for (int i = 0; i < 120; i++) s.update(DT, new Input());
        assertFalse(Float.isNaN(s.px));
    }

    @Test
    public void legacyStagesHaveNone() throws Exception {
        assertEquals(0, sim("FOREST", 5).findN);
        assertEquals(0, sim("LIBRARY", 5).findN);
    }

    @Test
    public void weaponCacheGivesANewWeapon() throws Exception {
        Sim s = sim("FLATS", 7);
        int i = firstOfKind(s, Sim.FIND_WEAPON), before = s.inv.weaponCount;
        walkTo(s, i);
        assertTrue(s.findTaken[i]);
        assertEquals(before + 1, s.inv.weaponCount);
        assertTrue(s.nearestFind() != i);
    }

    @Test
    public void weaponCacheWithFullSlotsLevelsOneUp() throws Exception {
        Sim s = sim("FLATS", 7);
        GameData d = TestData.load();
        for (WeaponDef w : d.weapons.values()) {
            if (s.inv.weaponCount >= Inventory.MAX_WEAPONS) break;
            if (!w.evolution && !w.powerUp) s.inv.add(w);
        }
        assertEquals(Inventory.MAX_WEAPONS, s.inv.weaponCount);
        int sum = 0;
        for (int k = 0; k < s.inv.weaponCount; k++) sum += s.inv.weapons[k].level;
        walkTo(s, firstOfKind(s, Sim.FIND_WEAPON));
        int after = 0;
        for (int k = 0; k < s.inv.weaponCount; k++) after += s.inv.weapons[k].level;
        assertEquals(Inventory.MAX_WEAPONS, s.inv.weaponCount);
        assertEquals(sum + 1, after);
    }

    @Test
    public void gearLockerGivesAnItem() throws Exception {
        Sim s = sim("MINES", 11);
        int i = firstOfKind(s, Sim.FIND_GEAR);
        int worn = 0;
        walkTo(s, i);
        assertTrue(s.findTaken[i]);
        boolean toasted = false;
        for (int k = 0; k < Sim.MAX_TOASTS; k++) if ("GEAR LOCKER".equals(s.toastText[k])) toasted = true;
        assertTrue(toasted);
        for (int k = 0; k < s.equip.def.length; k++) if (s.equip.def[k] != null) worn++;
        assertTrue("worn or waiting for the Keep / Replace choice", worn > 0 || s.equipN > 0);
    }

    @Test
    public void ownRandomLeavesTheRunUntouched() throws Exception {
        GameData d = TestData.load();
        Sim a = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>(), null, 9L);
        Sim b = new Sim(d, d.stages.get("FLATS"), "WHIP", new HashMap<String, Double>(), null, 9L);
        assertEquals(a.rng.nextLong(), b.rng.nextLong());
        a.update(DT, new Input());
        assertTrue(a.time > 0);
    }
}
