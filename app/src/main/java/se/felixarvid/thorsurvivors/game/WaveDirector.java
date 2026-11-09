package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.WaveRow;

/** Pure spawn-count policy for one spawn tick. */
public final class WaveDirector {
    public static final int ENEMY_CAP = 300;
    public static final int MAX_PER_TICK = 30;

    private WaveDirector() {
    }

    /** Enemies spawned per tick regardless of the field: one of each type in the row (Vampire Survivors), up to the cap. */
    public static int eachCount(WaveRow row, int alive) {
        return Math.max(0, Math.min(row.enemies.length, ENEMY_CAP - alive));
    }

    /** After the per-type spawns: how many more (random types) bring the field up to `minimum`, bounded per tick. */
    public static int topUpCount(WaveRow row, int alive) {
        return topUpCount(row.minimum, row.enemies.length, alive);
    }

    /** The same with an explicit minimum (a horde burst raises it above the row's). */
    public static int topUpCount(int minimum, int types, int alive) {
        if (alive >= ENEMY_CAP || types == 0 || alive >= minimum) return 0;
        return Math.min(Math.min(minimum - alive, MAX_PER_TICK), ENEMY_CAP - alive);
    }

    // ------------------------------------------------------------------ v1.5 B7

    /** Item 66: seconds standing inside {@link #STILL_RADIUS} before the penalty starts. */
    public static final float STILL_SECONDS = 6f;
    /** Item 66: radius (world units) the hero may wander in and still count as standing still. */
    public static final float STILL_RADIUS = 40f;
    /** Item 66: spawn rate multiplier while the penalty is on (+15 percent). */
    public static final float STILL_SPAWN_MUL = 1.15f;

    /**
     * Item 66: damage per second of the drift while the penalty is on: {@code 1.5 + 0.15 * minute} (1.5 at 0:00, 6 at 30:00;
     * ignores armor, like the floor hazards). Small against a hero of 100+ HP, but a hero that camps for a minute loses
     * most of a health bar late.
     */
    public static float stillDps(double seconds) {
        return 1.5f + 0.15f * (float) (seconds / 60.0);
    }

    /** Item 66: tracks how long the hero has stayed within {@link #STILL_RADIUS} of an anchor. No allocation per update. */
    public static final class StandStill {
        private float ax, ay, still;
        private boolean started;

        /** Call once per step with the hero's position. */
        public void update(float x, float y, float dt) {
            if (!started) {
                started = true;
                ax = x;
                ay = y;
                still = 0f;
                return;
            }
            float dx = x - ax, dy = y - ay;
            if (dx * dx + dy * dy > STILL_RADIUS * STILL_RADIUS) {
                ax = x;
                ay = y;
                still = 0f;
            } else {
                still += dt;
            }
        }

        public float seconds() {
            return still;
        }

        /** True once the hero has stood still for {@link #STILL_SECONDS}. */
        public boolean active() {
            return still >= STILL_SECONDS;
        }

        public float spawnMul() {
            return active() ? STILL_SPAWN_MUL : 1f;
        }

        public void reset() {
            started = false;
            still = 0f;
        }
    }

    /** Item 70: minute (of run time) from which normal enemies gain extra HP. */
    public static final double LATE_FROM_MIN = 25.0;
    /** Item 70: extra HP of normal enemies per minute past {@link #LATE_FROM_MIN} (3 percent). */
    public static final double LATE_HP_PER_MIN = 0.03;

    /** Item 70: HP multiplier of a normal enemy spawned at {@code seconds}: 1 until 25:00, then +3 percent per minute (1.15 at 30:00). */
    public static double lateHpMul(double seconds) {
        double over = seconds / 60.0 - LATE_FROM_MIN;
        return over <= 0 ? 1.0 : 1.0 + LATE_HP_PER_MIN * over;
    }

    /**
     * Item 67: angle (radians) of a spawn that is biased ahead of the hero. With probability {@code bias} the spawn angle is
     * drawn within +-{@link #AHEAD_SPREAD} of the movement heading, otherwise anywhere ({@code u2} in [0,1) covers the full
     * circle). {@code u0} chooses the branch, {@code u1} the offset inside the cone.
     */
    public static float spawnAngle(float bias, float heading, float u0, float u1, float u2) {
        if (bias > 0f && u0 < bias) return heading + (u1 * 2f - 1f) * AHEAD_SPREAD;
        return u2 * 6.2831855f;
    }

    /** Item 67: half width of the spawn-ahead cone (about 50 degrees). */
    public static final float AHEAD_SPREAD = 0.9f;

    /**
     * Item 62: run time (seconds) from which an elite drops an Arms Crate (fusion, evolution, levels; from the 2nd elite killed). It was 300 and
     * was the main source of the first evolution (measured: first evolution at 7:47 to 9:00 in half of the skilled runs). Not final so a soak can
     * try values ({@code ARMS_FROM}); the game never changes it.
     */
    public static double armsCrateFrom = 300.0;
}
