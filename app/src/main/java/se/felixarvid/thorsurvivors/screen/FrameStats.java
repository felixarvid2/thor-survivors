package se.felixarvid.thorsurvivors.screen;

/**
 * v1.5 B8 (item 80): frame times of the last {@link #N} frames for the debug overlay. Fixed arrays, no allocation per
 * frame; the 95th percentile is recomputed every {@link #EVERY} frames (insertion sort of a copy).
 */
final class FrameStats {
    static final int N = 240, EVERY = 30;
    private final float[] ring = new float[N], sorted = new float[N];
    private int n, head, sinceSort;
    private float p95, last, worst;

    void add(float ms) {
        ring[head] = ms;
        head = (head + 1) % N;
        if (n < N) n++;
        last = ms;
        if (++sinceSort >= EVERY || n < 8) {
            sinceSort = 0;
            recompute();
        }
    }

    private void recompute() {
        System.arraycopy(ring, 0, sorted, 0, n);
        for (int i = 1; i < n; i++) {
            float v = sorted[i];
            int j = i - 1;
            while (j >= 0 && sorted[j] > v) {
                sorted[j + 1] = sorted[j];
                j--;
            }
            sorted[j + 1] = v;
        }
        p95 = n == 0 ? 0f : sorted[Math.min(n - 1, (int) Math.ceil(0.95f * n) - 1)];
        worst = n == 0 ? 0f : sorted[n - 1];
    }

    float p95() {
        return p95;
    }

    float worst() {
        return worst;
    }

    float last() {
        return last;
    }

    int count() {
        return n;
    }
}
