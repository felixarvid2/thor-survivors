package se.felixarvid.thorsurvivors.audio;

/**
 * v1.5 B8 (item 77): which sound effect voices may play. At most {@link #GLOBAL} voices at once and, per sound, at
 * most its own cap (never more than {@link #PER_SOUND}); when a cap is reached the quietest voice of that group is
 * cut (the oldest of equally quiet ones) unless the new one is itself the quietest, which is then not played at all.
 * SoundPool has no completion callback, so every voice carries the time it is expected to end. Fixed arrays, no
 * allocation, no android.*.
 */
public final class VoiceLimiter {
    public static final int PER_SOUND = 4, GLOBAL = 24;
    /** {@link #admit} result: the new voice is not played. */
    public static final int DROP = -1;

    private final int[] id = new int[GLOBAL], handle = new int[GLOBAL];
    private final float[] vol = new float[GLOBAL];
    private final double[] end = new double[GLOBAL];
    private int n;
    private int stolen;

    public int active() {
        return n;
    }

    /** Voices of {@code sound} still sounding at {@code now}. */
    public int active(int sound, double now) {
        expire(now);
        int c = 0;
        for (int i = 0; i < n; i++) if (id[i] == sound) c++;
        return c;
    }

    /** SoundPool stream handle of the voice that {@link #admit} cut for the last accepted request (0 = none was cut). */
    public int stolenHandle() {
        return stolen;
    }

    /**
     * Asks to start a voice of {@code sound} with loudness {@code volume} lasting {@code dur} s. Returns {@link #DROP},
     * or a slot to pass to {@link #setHandle}; the cut voice's handle, if any, is in {@link #stolenHandle}.
     */
    public int admit(int sound, int cap, float volume, double now, float dur) {
        stolen = 0;
        expire(now);
        cap = Math.max(1, Math.min(PER_SOUND, cap));
        int same = 0, quietSame = -1;
        for (int i = 0; i < n; i++) {
            if (id[i] != sound) continue;
            same++;
            if (quietSame < 0 || vol[i] < vol[quietSame] || (vol[i] == vol[quietSame] && end[i] < end[quietSame])) quietSame = i;
        }
        int slot;
        if (same >= cap) {
            if (vol[quietSame] > volume) return DROP;
            slot = quietSame;
        } else if (n >= GLOBAL) {
            int q = 0;
            for (int i = 1; i < n; i++) if (vol[i] < vol[q] || (vol[i] == vol[q] && end[i] < end[q])) q = i;
            if (vol[q] > volume) return DROP;
            slot = q;
        } else {
            slot = n++;
            handle[slot] = 0;
        }
        stolen = handle[slot];
        id[slot] = sound;
        vol[slot] = volume;
        end[slot] = now + Math.max(0.05f, dur);
        handle[slot] = 0;
        return slot;
    }

    public void setHandle(int slot, int h) {
        if (slot >= 0 && slot < n) handle[slot] = h;
    }

    private void expire(double now) {
        for (int i = n - 1; i >= 0; i--) {
            if (end[i] > now) continue;
            n--;
            id[i] = id[n];
            handle[i] = handle[n];
            vol[i] = vol[n];
            end[i] = end[n];
        }
    }
}
