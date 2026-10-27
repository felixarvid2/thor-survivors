package se.felixarvid.thorsurvivors.game.boss;

import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Sim;

/**
 * Boss pattern 13 (chunk D): THE PILLAR, the Meridian Wreck finale (28:00). Called from {@code BossAi.init} and {@code BossAi.update} (the marked v1.4 blocks) for an enemy
 * whose {@code pattern} param is 13. Placeholder: a boss with this pattern stands still and does nothing yet.
 */
public final class PillarAi {
    private PillarAi() {
    }

    /** First timers of a freshly spawned boss of this pattern. */
    public static void init(Sim s, Enemy e) {
    }

    /** One step of the boss ({@code moving} is false while staggered; (dx, dy) points at the hero, {@code d} is the distance). */
    public static void update(Sim s, Enemy e, float dt, boolean moving, float dx, float dy, float d) {
    }
}
