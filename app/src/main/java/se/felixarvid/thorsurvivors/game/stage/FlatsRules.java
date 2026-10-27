package se.felixarvid.thorsurvivors.game.stage;

/**
 * Cinder Flats: the ash storms at 12:00 and 22:00 (design 9.1): 20 s long; the light radius shrinks by 40% and spawns grow by
 * 20% at the height of a storm. Moved here from {@code Sim} unchanged; {@code Sim.stormLevel()} and friends ask this object.
 */
public final class FlatsRules extends StageRules {
    public static final float STORM_LEN = 20f, STORM_FADE = 1.5f, STORM_LIGHT = 0.4f, STORM_SPAWNS = 0.2f;
    static final float[] STORM_AT = {720f, 1320f};

    /** 0..1: how strongly the ash storm blows at run time {@code time} (ramps in and out over {@link #STORM_FADE}); 0 outside the storms. */
    public float stormLevel(double time) {
        for (float at : STORM_AT) {
            float t = (float) time - at;
            if (t < 0f || t > STORM_LEN) continue;
            return Math.min(1f, Math.min(t, STORM_LEN - t) / STORM_FADE);
        }
        return 0f;
    }

    /** Seconds since the current ash storm began, or -1 outside the storms (the banner shows for the first seconds). */
    public float stormElapsed(double time) {
        for (float at : STORM_AT) {
            float t = (float) time - at;
            if (t >= 0f && t <= STORM_LEN) return t;
        }
        return -1f;
    }

    @Override
    public float lightMul() {
        return 1f - STORM_LIGHT * stormLevel(s.time);
    }

    @Override
    public float spawnMul() {
        return stormLevel(s.time) > 0f ? 1f + STORM_SPAWNS : 1f;
    }
}
