package se.felixarvid.thorsurvivors.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** v1.5 B8 (77): per-sound and global voice caps, the quietest voice goes first. */
public class VoiceLimiterTest {
    private static int play(VoiceLimiter l, int id, float vol, double now, int handle) {
        int slot = l.admit(id, 4, vol, now, 10f);
        if (slot != VoiceLimiter.DROP) l.setHandle(slot, handle);
        return slot;
    }

    @Test
    public void aSoundNeverHasMoreThanFourVoices() {
        VoiceLimiter l = new VoiceLimiter();
        for (int i = 0; i < 10; i++) play(l, 3, 0.5f + i * 0.01f, 0.0, 100 + i);
        assertEquals(4, l.active(3, 0.0));
    }

    @Test
    public void theQuietestVoiceOfTheSoundIsCut() {
        VoiceLimiter l = new VoiceLimiter();
        play(l, 1, 0.9f, 0, 11);
        play(l, 1, 0.2f, 0, 12);
        play(l, 1, 0.7f, 0, 13);
        play(l, 1, 0.8f, 0, 14);
        int slot = l.admit(1, 4, 0.6f, 0, 10f); // louder than the 0.2 voice: that one is stolen
        assertTrue(slot != VoiceLimiter.DROP);
        assertEquals(12, l.stolenHandle());
        assertEquals(4, l.active(1, 0));
    }

    @Test
    public void aNewVoiceQuieterThanAllIsDropped() {
        VoiceLimiter l = new VoiceLimiter();
        for (int i = 0; i < 4; i++) play(l, 2, 0.8f, 0, 20 + i);
        assertEquals(VoiceLimiter.DROP, l.admit(2, 4, 0.3f, 0, 5f));
        assertEquals(0, l.stolenHandle());
        assertEquals(4, l.active(2, 0));
    }

    @Test
    public void theGlobalCapIs24AndAnEqualVolumeNewcomerReplacesTheOldest() {
        VoiceLimiter l = new VoiceLimiter();
        int h = 1;
        for (int id = 0; id < 8; id++) for (int k = 0; k < 3; k++) play(l, id, 0.5f, k, h++); // 24 voices of 8 sounds
        assertEquals(24, l.active());
        int slot = l.admit(9, 4, 0.5f, 3.0, 10f);
        assertTrue(slot != VoiceLimiter.DROP);
        assertEquals("the oldest equally quiet voice", 1, l.stolenHandle());
        assertEquals(24, l.active());
        assertEquals(VoiceLimiter.DROP, l.admit(10, 4, 0.1f, 3.0, 10f));
    }

    @Test
    public void finishedVoicesFreeTheirSlots() {
        VoiceLimiter l = new VoiceLimiter();
        for (int i = 0; i < 4; i++) l.setHandle(l.admit(5, 4, 0.5f, 0, 0.5f), 30 + i);
        assertEquals(4, l.active(5, 0.4));
        assertEquals(0, l.active(5, 0.6));
        int slot = l.admit(5, 4, 0.1f, 1.0, 0.5f);
        assertTrue(slot != VoiceLimiter.DROP);
        assertEquals(0, l.stolenHandle());
    }

    @Test
    public void aCapOfOneKeepsOneVoice() {
        VoiceLimiter l = new VoiceLimiter();
        l.setHandle(l.admit(7, 1, 0.5f, 0, 5f), 77);
        int s = l.admit(7, 1, 0.6f, 0.1, 5f);
        assertTrue(s != VoiceLimiter.DROP);
        assertEquals(77, l.stolenHandle());
        assertEquals(1, l.active(7, 0.1));
    }

    @Test
    public void aSwarmOfSoundsStaysBounded() {
        VoiceLimiter l = new VoiceLimiter();
        java.util.Random r = new java.util.Random(5);
        double t = 0;
        for (int i = 0; i < 20000; i++) {
            t += 0.002;
            int slot = l.admit(r.nextInt(59), 1 + r.nextInt(4), r.nextFloat(), t, 0.05f + r.nextFloat());
            if (slot != VoiceLimiter.DROP) l.setHandle(slot, i + 1);
            assertTrue(l.active() <= VoiceLimiter.GLOBAL);
        }
    }
}
