package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.game.Entities.EProj;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Entities.Hazard;

/**
 * The doors of {@link Sim} that Meridian Wreck (v1.4 chunk D: {@code game/stage/WreckRules}, {@code game/boss/PillarAi}) needs from
 * outside the {@code game} package. Every method is a one-line forward to a package-private Sim or EnemyAi method, so the stage
 * code can stay in its own packages without widening Sim's API (which the other stage chunks edit at the same time).
 */
public final class WreckOps {
    private WreckOps() {
    }

    public static void fx(Sim s, int kind, float x, float y, float w, float h, float life) {
        s.addFx(kind, x, y, w, h, life, 0, false);
    }

    public static void spark(Sim s, float x, float y, int color, int count) {
        s.spark(x, y, color, count);
    }

    public static void shake(Sim s, float t) {
        s.screenShake(t);
    }

    /** The hero takes {@code taken} (armor already applied by the caller): hurt sound, invulnerability, shake. {@code source} is a {@code Sim.DMG_*}. */
    public static void hurtHero(Sim s, float taken, float inv, int source) {
        s.hurtHero(taken, inv, true, source);
    }

    /** Damage over time from the floor (no armor, no hurt sound). */
    public static void areaDamage(Sim s, float amount) {
        s.areaDamage(amount);
    }

    /** A status-style tick on an enemy: no flash, no number; kills when it empties the enemy. */
    public static void dot(Sim s, Enemy e, float amount) {
        s.statusDamage(e, amount, Status.C_DECAY, false);
    }

    public static Hazard hazard(Sim s, int kind, float x, float y, float r, float life, float dps, float warn) {
        return s.ai.addHazard(kind, x, y, r, life, dps, warn);
    }

    public static EProj shoot(Sim s, float x, float y, float vx, float vy, float dmg, float life) {
        return s.ai.shoot(x, y, vx, vy, dmg, life);
    }

    public static void walkTo(Sim s, Enemy e, float dt, float dx, float dy, float d, float factor) {
        s.ai.walkTo(e, dt, dx, dy, d, factor);
    }

    public static float speedOf(Sim s, Enemy e) {
        return s.ai.speedOf(e);
    }

    public static boolean spawnMinion(Sim s, String key, float x, float y) {
        return s.spawnMinion(key, x, y);
    }

    /** A pinned summon (a decoy): a warning ring, then up; see {@code BossAi.tentacle}. */
    public static Enemy spawnPinned(Sim s, String key, float x, float y) {
        return s.spawnPinned(key, x, y);
    }

    public static void killNow(Sim s, Enemy e) {
        s.killNow(e);
    }

    public static void addPickup(Sim s, int kind, float x, float y, float value) {
        s.addPickup(kind, x, y, value);
    }

    public static void shoveHero(Sim s, float dx, float dy, float dist) {
        s.shoveHero(dx, dy, dist);
    }

    public static void stunHero(Sim s, float seconds) {
        s.stunHero(seconds);
    }

    /** True when the Sim can still take {@code n} more enemies. */
    public static boolean roomFor(Sim s, int n) {
        return s.enemyN + n < Sim.MAX_ENEMIES - 4;
    }
}
