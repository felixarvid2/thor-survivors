package se.felixarvid.thorsurvivors.screen;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** v1.5 B8 (80): the p95 of the debug overlay. */
public class FrameStatsTest {
    @Test
    public void p95IgnoresRareSpikesButSeesFrequentOnes() {
        FrameStats s = new FrameStats();
        for (int i = 0; i < 240; i++) s.add(i % 100 == 0 ? 40f : 8.3f); // 3 spikes in 240 frames
        assertEquals(8.3f, s.p95(), 0.01f);
        assertEquals(40f, s.worst(), 0.01f);
        FrameStats t = new FrameStats();
        for (int i = 0; i < 240; i++) t.add(i % 10 == 0 ? 33f : 8.3f); // 10 % slow frames
        assertEquals(33f, t.p95(), 0.01f);
    }

    @Test
    public void windowSlides() {
        FrameStats s = new FrameStats();
        for (int i = 0; i < 240; i++) s.add(50f);
        for (int i = 0; i < 300; i++) s.add(8f); // the p95 refreshes every 30 frames
        assertEquals(8f, s.p95(), 0.01f);
        assertEquals(240, s.count());
    }

    @Test
    public void textHelpersFormatWithoutAllocatingStrings() {
        char[] b = new char[32];
        int n = DebugOverlay.fixed1(b, 0, 16.67f);
        assertEquals("16.7", new String(b, 0, n));
        n = DebugOverlay.num(b, 0, 305);
        assertEquals("305", new String(b, 0, n));
        n = DebugOverlay.num(b, 0, 0);
        assertEquals("0", new String(b, 0, n));
    }
}
