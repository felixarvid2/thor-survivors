package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Entities.Hazard;

/**
 * The package-private doors of the {@link Sim} that the Refinery District (chunk C: {@code game/stage/RefineryRules},
 * {@code game/boss/IronheartAi}) needs from outside {@code game}: hazards, hero damage, destructibles, silent kills, effects.
 * Thin wrappers only (no rules in here), so the Sim keeps its encapsulation and the stage code stays in its own packages.
 */
public final class RefineryOps {
    private RefineryOps() {
    }

    // ---- hazards and effects

    /** A hazard of the pool (null when full); callers keep the reserve themselves (see {@link #hazardRoom}). */
    public static Hazard hazard(Sim s, int kind, float x, float y, float r, float life, float dps, float warn) {
        return s.ai.addHazard(kind, x, y, r, life, dps, warn);
    }

    /** True while the hazard pool still has {@code reserve} slots free for telegraphs. */
    public static boolean hazardRoom(Sim s, int reserve) {
        return s.hazN < Sim.MAX_HAZ - reserve;
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

    // ---- hero

    public static void hurtHero(Sim s, float dmg, float inv, int source) {
        s.hurtHero(dmg, inv, true, source);
    }

    /** Floor damage over time (no armor, no invulnerability). */
    public static void areaDamage(Sim s, float amount) {
        s.areaDamage(amount);
    }

    public static void stunHero(Sim s, float seconds) {
        s.stunHero(seconds);
    }

    public static void shoveHero(Sim s, float dx, float dy, float dist) {
        s.shoveHero(dx, dy, dist);
    }

    // ---- enemies

    /** Enemies in the circle: fills {@code s.qbuf}, returns the count; {@link #queried} reads an entry. Needs the grid of this step. */
    public static int query(Sim s, float x, float y, float r) {
        return s.query(x, y, r);
    }

    public static Enemy queried(Sim s, int k) {
        return s.enemies[s.qbuf[k]];
    }

    public static void statusDamage(Sim s, Enemy e, float amount, int color) {
        s.statusDamage(e, amount, color, false);
    }

    /**
     * Removes a fodder enemy without a kill drop: counted as a kill, a gem worth {@code gemFrac} of its XP, nothing else
     * (the furnace gates). The enemy leaves through the silent path of the reaper.
     */
    public static void gateKill(Sim s, Enemy e, float gemFrac) {
        if (e.dead) return;
        s.addPickup(Entities.K_GEM, e.x, e.y, e.xp * gemFrac * s.events.xpMul());
        s.kills++;
        s.spark(e.x, e.y - 8f, 0xFFFF8A30, 6);
        e.dead = true;
        e.silent = true;
        e.hp = 0;
    }

    /** The charger move of a welder (telegraph, dash, rest) for a stage archetype that dashes like one. */
    public static void charger(Sim s, Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        s.ai.charger(e, dt, moving, dx, dy, d);
    }

    public static void walk(Sim s, Enemy e, float dt, float dx, float dy, float d, float factor) {
        s.ai.walkTo(e, dt, dx, dy, d, factor);
    }

    public static float speedOf(Sim s, Enemy e) {
        return s.ai.speedOf(e);
    }

    // ---- destructibles, pickups, run

    public static void addDest(Sim s, float x, float y, int kind, long key) {
        s.addDest(x, y, kind, key);
    }

    public static void hitDest(Sim s, float x, float y, float r) {
        s.hitDest(x, y, r);
    }

    public static void addPickup(Sim s, int kind, float x, float y, float value) {
        s.addPickup(kind, x, y, value);
    }
}
