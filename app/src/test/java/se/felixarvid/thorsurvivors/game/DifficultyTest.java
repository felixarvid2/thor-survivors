package se.felixarvid.thorsurvivors.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.StageDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.HashMap;

/** Design phase 13, difficulty: the HP / damage curves of wave enemies, the horde bursts, the minimum per minute. */
public class DifficultyTest {
    private static final float DT = 1f / 60f;

    private static Sim sim(String stage, long seed, boolean spawns) throws Exception {
        GameData d = TestData.load();
        Sim s = new Sim(d, d.stages.get(stage), null, new HashMap<String, Double>(), null, seed);
        s.noSpawns = !spawns;
        s.enemyN = 0;
        return s;
    }

    @Test
    public void theCurvesInterpolateAndHoldTheLastValue() throws Exception {
        GameData d = TestData.load();
        for (String k : new String[]{"FLATS", "MINES"}) {
            StageDef st = d.stages.get(k);
            boolean f = k.equals("FLATS"); // v1.5 final: Flats has its own HP, damage and speed curves (survival re-tune), Mines keep the v1.4 HP and damage
            assertEquals(1.0, st.hpMul(0), 1e-9);
            assertEquals(f ? 0.9 : 1.2, st.hpMul(10 * 60), 1e-9);
            assertEquals(f ? 1.2 : 1.6, st.hpMul(15 * 60), 1e-9);
            assertEquals(f ? 1.8 : 2.4, st.hpMul(20 * 60), 1e-9);
            assertEquals(f ? 2.775 : 3.7, st.hpMul(22.5 * 60), 1e-9);
            assertEquals(f ? 6.75 : 9.0, st.hpMul(30 * 60), 1e-9);
            assertEquals("held after the last point", f ? 6.75 : 9.0, st.hpMul(99 * 60), 1e-9);
            assertEquals("v1.4: damage ends at 7.5, not 18", f ? 5.625 : 7.5, st.dmgMul(30 * 60), 1e-9);
            assertEquals(f ? 3.75 : 5.0, st.dmgMul(24 * 60), 1e-9);
            assertEquals(f ? 2.625 : 3.5, st.dmgMul(20 * 60), 1e-9);
            assertEquals("speed: 1 at the start", 1.0, st.speedMul(0), 1e-9);
            assertEquals(f ? 1.035 : 1.05, st.speedMul(5 * 60), 1e-9);
            // v1.5 B7 item 63 and the final re-tune: the speed excess over 1.0 is scaled per stage (Flats x0.35, Mines x0.5 of the v1.4 curve)
            assertEquals(f ? 1.114 : 1.325, st.speedMul(20 * 60), 1e-9);
            assertEquals(f ? 1.219 : 1.625, st.speedMul(30 * 60), 1e-9);
            assertEquals("Mines start harder (stage knob)", f ? 1.35 : 1.4, st.dmgMul(0), 1e-9);
        }
        assertEquals("the old stages have no speed curve", 1.0, d.stages.get("FOREST").speedMul(25 * 60), 1e-9);
        assertEquals("the old stages have no curve", 1.0, d.stages.get("FOREST").hpMul(25 * 60), 1e-9);
    }

    @Test
    public void aWaveEnemyGetsTheCurveButABossAndATentacleDoNot() throws Exception {
        Sim s = sim("FLATS", 1, false);
        Enemy early = s.addEnemy("COLONIST", 0, 0, false);
        s.time = 30 * 60;
        Enemy late = s.addEnemy("COLONIST", 0, 0, false);
        assertEquals(early.maxHp * 6.75f * 1.15f, late.maxHp, 0.01f); // curve x6.75 (v1.5 final Flats curve) and the v1.5 B7 late ramp (+3 %/min past 25:00 = x1.15)
        assertEquals("1.35 at the start, 5.625 at 30:00", early.power * 5.625f / 1.35f, late.power, 0.01f);
        assertEquals("speed x1.219 at 30:00 (v1.5 final: Flats eased from 2.25)", early.speed * 1.219f, late.speed, 0.01f);
        s.time = 0;
        Enemy bossEarly = s.addEnemy("SLAGBORN", 0, 0, true);
        s.time = 20 * 60;
        Enemy bossLate = s.addEnemy("SLAGBORN", 0, 0, true);
        // the boss HP follows its own ramp (hpRamp), not hpCurve: at 20 min the ramp is over, at 0 min it is 20%
        assertEquals(bossEarly.maxHp / 0.2f, bossLate.maxHp, bossLate.maxHp * 0.001f);
    }

    @Test
    public void aHordeBurstFillsTheFieldFasterThanTheRowMinute() throws Exception {
        GameData d = TestData.load();
        StageDef st = d.stages.get("FLATS");
        double at = st.hordes[st.hordes.length - 2][0]; // 1420 s on Flats (shifted off the Surge), minimum 290
        Sim calm = sim("FLATS", 4, true);
        calm.time = at - 40;
        for (int i = 0; i < 2 * 60; i++) calm.update(DT, new Input());
        Sim horde = sim("FLATS", 4, true);
        horde.time = at;
        for (int i = 0; i < 2 * 60; i++) horde.update(DT, new Input());
        assertTrue("calm " + calm.enemyN, calm.enemyN < 240);
        assertTrue("horde " + horde.enemyN, horde.enemyN >= 270);
    }

    @Test
    public void everyMinuteFromFiveHasAtLeastFortyEnemiesAsMinimum() throws Exception {
        GameData d = TestData.load();
        for (String k : new String[]{"FLATS", "MINES"}) {
            StageDef st = d.stages.get(k);
            int last = 0;
            for (int m = 5; m < 30; m++) {
                int min = st.rowAt(m * 60).minimum;
                assertTrue(k + " minute " + m + " minimum " + min, min >= 40 && min <= WaveDirector.ENEMY_CAP);
                assertTrue(k + " minimum falls only on a boss row", min >= last * 0.8);
                last = min;
            }
        }
    }
}
