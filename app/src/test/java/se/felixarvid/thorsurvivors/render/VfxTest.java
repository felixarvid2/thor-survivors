package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** v1.5 B1: the headless rules of Vfx (hit-stop, trauma shake, pools, damage numbers, easing curves). */
public class VfxTest {
    @Test
    public void hitStopNeverExceeds100ms() {
        Vfx v = new Vfx();
        for (int i = 0; i < 20; i++) v.hitStop(Vfx.STOP_BOSS);
        assertEquals(Vfx.STOP_MAX, v.stopLeft(), 1e-6f);
    }

    @Test
    public void filterDtEatsTheFrontOfTheFrame() {
        Vfx v = new Vfx();
        v.hitStop(Vfx.STOP_ELITE);
        float total = 0f;
        int frozen = 0;
        for (int i = 0; i < 6; i++) {
            float d = v.filterDt(1f / 60f);
            if (d == 0f) frozen++;
            total += d;
        }
        assertEquals(2, frozen);
        assertEquals(6f / 60f - Vfx.STOP_ELITE, total, 1e-5f); // the stop is paid exactly once
        assertEquals(0f, v.stopLeft(), 0f);
    }

    @Test
    public void traumaDecaysMonotonicallyAndShakeStaysInBounds() {
        Vfx v = new Vfx();
        v.addTrauma(5f);
        assertEquals(1f, v.trauma, 0f);
        float prev = v.trauma;
        float max = 0f;
        for (int i = 0; i < 100; i++) {
            v.update(1f / 60f);
            assertTrue(v.trauma <= prev);
            prev = v.trauma;
            max = Math.max(max, Math.max(Math.abs(v.shakeX), Math.abs(v.shakeY)));
            assertTrue(Math.abs(v.shakeX) <= Vfx.SHAKE_PX + 1e-4f && Math.abs(v.shakeY) <= Vfx.SHAKE_PX + 1e-4f);
        }
        assertEquals(0f, v.trauma, 0f); // 1 / 1.6 s = 0.63 s, gone well before 1.6 s
        assertEquals(0f, v.shakeX, 1e-6f);
        assertTrue("the shake did move the camera: " + max, max > 1f);
        assertEquals(Vfx.SHAKE_PX, Vfx.shakeAmount(1f), 1e-6f);
        assertEquals(2.5f, Vfx.shakeAmount(0.5f), 1e-6f); // trauma squared
    }

    @Test
    public void noiseIsSmooth() {
        float prev = Vfx.noise(0f, 1);
        for (int i = 1; i < 2000; i++) {
            float n = Vfx.noise(i * 0.01f, 1);
            assertTrue(Math.abs(n) <= 1f);
            assertTrue("a jump of " + Math.abs(n - prev), Math.abs(n - prev) < 0.1f);
            prev = n;
        }
    }

    @Test
    public void sparksAreAPoolOf256AndLiveFor018s() {
        Vfx v = new Vfx();
        for (int i = 0; i < 1000; i++) v.sparks(i, i, 0xFFFFFF);
        assertTrue(v.livePart() <= Vfx.MAX_PART);
        assertEquals(Vfx.MAX_PART, v.livePart());
        v.update(0.19f);
        assertEquals(0, v.livePart());
        v.sparks(0, 0, 0xFFFFFF);
        int n = v.livePart();
        assertTrue("3 to 5 per hit: " + n, n >= 3 && n <= 5);
    }

    @Test
    public void poolsAllocateNothing() throws Exception {
        Vfx v = new Vfx();
        Object mx = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
        java.lang.reflect.Method alloc = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", long.class);
        long tid = Thread.currentThread().getId();
        for (int i = 0; i < 3000; i++) { // warm up the JIT and the reflection path
            v.sparks(1, 1, 0xFFFFFF);
            v.addNumber(1, 1, 5, i % 7 == 0, Vfx.N_HIT, 0, i % 5);
            v.update(0.016f);
        }
        long a0 = (Long) alloc.invoke(mx, tid);
        long probe = (Long) alloc.invoke(mx, tid) - a0;
        a0 = (Long) alloc.invoke(mx, tid);
        for (int i = 0; i < 5000; i++) {
            v.sparks(i, i, 0xFFAA00);
            v.shards(i, i, 0xFFAA00);
            v.addNumber(i, i, i, i % 7 == 0, Vfx.N_HIT, 0, i % 5);
            v.levelUp(0, 0);
            v.chestOpen(0, 0, i % 2 == 0);
            v.heroHit(0.1f);
            v.update(0.016f);
        }
        long d = (Long) alloc.invoke(mx, tid) - a0 - probe;
        assertTrue("allocated " + d + " B", d < 4096);
    }

    @Test
    public void hitsOnOneEnemyWithin150msSumIntoOneNumber() {
        Vfx v = new Vfx();
        v.addNumber(0, 0, 10, false, Vfx.N_HIT, 0, 7);
        v.update(0.05f);
        v.addNumber(0, 0, 15, false, Vfx.N_HIT, 0, 7);
        assertEquals(1, v.liveNum());
        int s = 0;
        assertEquals(25, v.nval[s]);
        assertEquals(2, v.nlen[s]);
        assertEquals('2', v.ndig[s][0]);
        assertEquals('5', v.ndig[s][1]);
        v.update(0.2f); // older than 0.15 s: a new number
        v.addNumber(0, 0, 4, false, Vfx.N_HIT, 0, 7);
        assertEquals(2, v.liveNum());
        v.addNumber(0, 0, 4, false, Vfx.N_HIT, 0, 8); // another enemy
        assertEquals(3, v.liveNum());
    }

    @Test
    public void mergedCritTurnsTheNumberIntoACrit() {
        Vfx v = new Vfx();
        int a = v.addNumber(0, 0, 10, false, Vfx.N_HIT, 0, 3);
        v.update(0.1f);
        int b = v.addNumber(0, 0, 30, true, Vfx.N_HIT, 0, 3);
        assertEquals(a, b);
        assertTrue(v.ncrit[a]);
        assertEquals(0f, v.nage[a], 0f);
        assertEquals(Vfx.CRIT_LIFE, v.nlife[a], 0f);
    }

    @Test
    public void capIs24AndTheOldestGoes() {
        Vfx v = new Vfx();
        for (int i = 0; i < 100; i++) v.addNumber(i, 0, i + 1, false, Vfx.N_HIT, 0, 0);
        assertEquals(Vfx.MAX_NUM, v.liveNum());
        for (int i = 0; i < Vfx.MAX_NUM; i++) assertTrue("kept the newest 24, got " + v.nval[i], v.nval[i] > 100 - Vfx.MAX_NUM);
    }

    @Test
    public void numbersExpire() {
        Vfx v = new Vfx();
        v.addNumber(0, 0, 3, false, Vfx.N_HIT, 0, 0);
        v.addNumber(0, 0, 3, true, Vfx.N_HIT, 0, 0);
        v.update(0.61f);
        assertEquals(1, v.liveNum()); // the crit lives 0.8 s
        v.update(0.3f);
        assertEquals(0, v.liveNum());
    }

    @Test
    public void critPopOvershootsThenSettles() {
        assertEquals(1f, Vfx.popScale(false, 0.03f), 0f);
        assertEquals(1.4f, Vfx.popScale(true, 0f), 1e-6f);
        assertEquals(1.4f * 1.25f, Vfx.popScale(true, 0.06f), 1e-5f);
        assertEquals(1.4f, Vfx.popScale(true, 0.12f), 1e-5f);
        assertEquals(1.4f, Vfx.popScale(true, 0.5f), 1e-6f);
    }

    @Test
    public void ringAndPillarCurves() {
        assertEquals(0f, Vfx.ringRadius(0f), 0f);
        assertEquals(140f, Vfx.ringRadius(0.4f), 1e-4f);
        assertEquals(140f, Vfx.ringRadius(2f), 1e-4f);
        assertTrue(Vfx.ringRadius(0.2f) > 70f); // eases out
        assertEquals(1f, Vfx.pillarAlpha(1.5f), 0f);
        assertEquals(0f, Vfx.pillarAlpha(3f), 0f);
        assertTrue(Vfx.pillarAlpha(0.1f) < 1f);
    }

    @Test
    public void levelUpAndChestMakeTheirParticles() {
        Vfx v = new Vfx();
        v.levelUp(0, 0);
        assertEquals(12, v.livePart());
        assertEquals(Vfx.FLASH_TIME, v.flash, 0f);
        assertTrue(v.effectLive(0));
        v = new Vfx();
        v.chestOpen(0, 0, true);
        assertEquals(20, v.livePart());
        assertTrue(v.effectLive(0) && v.effectLive(1));
        assertEquals(Vfx.E_PILLAR, v.ekind[1]);
        v.update(3.1f);
        assertTrue(!v.effectLive(0) && !v.effectLive(1));
    }

    @Test
    public void edgePulseIsBoundedAndBeatsBelowQuarterHealth() {
        Vfx v = new Vfx();
        assertEquals(0f, v.edgeAlpha(1f), 0f);
        v.heroHit(5f);
        assertEquals(Vfx.PULSE_MAX, v.edgeAlpha(1f), 1e-5f);
        v.update(1f);
        assertEquals(0f, v.edgeAlpha(0.9f), 0f);
        float lo = 1f, hi = 0f;
        for (int i = 0; i < 100; i++) {
            v.update(0.01f);
            float a = v.edgeAlpha(0.1f);
            assertTrue(a >= 0f && a <= Vfx.PULSE_MAX);
            lo = Math.min(lo, a);
            hi = Math.max(hi, a);
        }
        assertTrue("it beats: " + lo + " .. " + hi, hi - lo > 0.1f);
    }
}
