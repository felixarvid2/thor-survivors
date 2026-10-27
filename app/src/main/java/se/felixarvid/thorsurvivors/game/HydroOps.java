package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Entities.Hazard;

/**
 * The Hydroponic Vaults' door into the package-private parts of the {@link Sim} (hazards, summons, hero damage, pickups, the
 * walking of an enemy), so {@code game/stage/HydroRules} and {@code game/boss/RootmindAi} live in their own packages and the
 * Sim itself gains no stage code. Static, allocation-free wrappers only; they keep the semantics of the callers they wrap.
 */
public final class HydroOps {
    private HydroOps() {
    }

    /** The Arms Crate value of a chest pickup (see {@code Sim.ARMS_CRATE}). */
    public static final float ARMS_CRATE = Sim.ARMS_CRATE;
    /** The hazard slots events leave free (telegraph reserve). */
    public static final int HAZ_RESERVE = Events.HAZ_RESERVE;

    public static Hazard addHazard(Sim s, int kind, float x, float y, float r, float life, float dps, float warn) {
        return s.ai.addHazard(kind, x, y, r, life, dps, warn);
    }

    public static boolean spawnMinion(Sim s, String key, float x, float y) {
        return s.spawnMinion(key, x, y);
    }

    public static Enemy spawnPinned(Sim s, String key, float x, float y) {
        return s.spawnPinned(key, x, y);
    }

    /** A boss-like or tentacle-like enemy placed exactly (tests and the finale's hearts). */
    public static Enemy addEnemy(Sim s, String key, float x, float y, boolean boss) {
        return s.addEnemy(key, x, y, boss);
    }

    public static EnemyType type(Sim s, String key) {
        return s.type(key);
    }

    public static void shoot(Sim s, float x, float y, float vx, float vy, float dmg, float life) {
        s.ai.shoot(x, y, vx, vy, dmg, life);
    }

    /** Damage the hero takes at once (armor already applied by the caller), with the area-damage source. */
    public static void hurtHeroArea(Sim s, float taken) {
        s.hurtHero(taken, 0.5f, true, Sim.DMG_AREA);
    }

    /** Damage over time from the floor (no armor, no hit sound). */
    public static void areaDamage(Sim s, float amount) {
        s.areaDamage(amount);
    }

    public static void spark(Sim s, float x, float y, int color, int count) {
        s.spark(x, y, color, count);
    }

    public static void fx(Sim s, int kind, float x, float y, float w, float life) {
        s.addFx(kind, x, y, w, 0f, life, 0, false);
    }

    public static void shake(Sim s, float t) {
        s.screenShake(t);
    }

    public static void addPickup(Sim s, int kind, float x, float y, float value) {
        s.addPickup(kind, x, y, value);
    }

    /** Walking speed of an enemy in u/s (statuses, events and the stage's own multiplier included). */
    public static float speedOf(Sim s, Enemy e) {
        return s.ai.speedOf(e);
    }

    /** True while a welder-pattern boss is in the middle of a dash. */
    public static boolean isDashing(Enemy e) {
        return e.ai == EnemyAi.A_DASH;
    }

    public static boolean statusDamage(Sim s, Enemy e, float amount, int color) {
        s.statusDamage(e, amount, color, true);
        return e.dead;
    }

    /** Kills an enemy at once (it drops and counts like any kill on the next reap). */
    public static void kill(Sim s, Enemy e) {
        s.killNow(e);
    }

    /** Number of live enemies of one archetype (the Gardener cap). */
    public static int aliveOfArch(Sim s, int arch) {
        int n = 0;
        for (int i = 0; i < s.enemyN; i++) if (!s.enemies[i].dead && s.enemies[i].type.arch == arch) n++;
        return n;
    }
}
