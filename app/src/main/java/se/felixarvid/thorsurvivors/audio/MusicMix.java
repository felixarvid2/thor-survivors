package se.felixarvid.thorsurvivors.audio;

/**
 * v1.5 B8 (item 74): how loud the combat layer of the stage music is. The layer fades in with the number of living
 * enemies (nothing below {@link #CALM_BELOW}, full at {@link #FULL_AT}) and a boss, which has its own track, takes the
 * layer away. The value is smoothed over about {@link #SMOOTH} s so a wave arriving does not pump the music; while the
 * layer is up the calm track dips by up to {@link #CALM_DIP}. Pure and allocation-free.
 */
public final class MusicMix {
    public static final int CALM_BELOW = 15, FULL_AT = 300;
    public static final float SMOOTH = 2f, CALM_DIP = 0.35f;

    private float combat;

    /** Where the combat layer wants to be for {@code enemies} living enemies, 0..1 (smoothstep between the two limits). */
    public static float target(int enemies, boolean boss) {
        if (boss || enemies <= CALM_BELOW) return 0f;
        float x = Math.min(1f, (enemies - CALM_BELOW) / (float) (FULL_AT - CALM_BELOW));
        return x * x * (3f - 2f * x);
    }

    /** Advances the smoothing by {@code dt} s and returns the layer level 0..1. */
    public float step(float dt, int enemies, boolean boss) {
        float t = target(enemies, boss);
        float k = 1f - (float) Math.exp(-Math.max(0f, dt) * 3f / SMOOTH); // ~95 % of a step after SMOOTH s
        combat += (t - combat) * k;
        return combat;
    }

    public float combat() {
        return combat;
    }

    /** Gain of the calm layer for a combat level. */
    public static float calmGain(float combat) {
        return 1f - CALM_DIP * Math.max(0f, Math.min(1f, combat));
    }

    public void reset() {
        combat = 0f;
    }
}
