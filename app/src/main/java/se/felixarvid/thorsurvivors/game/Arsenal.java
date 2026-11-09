package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Entities.Projectile;

/**
 * v1.5 B5: the behaviours of the three new base weapons and their evolutions (ashfall_data.json "weapons"). Each reuses an
 * existing projectile kind and renderer path, so no drawing code changed:
 * <ul>
 * <li>Arc Lance / Storm Lance: a straight piercing {@code P_MISSILE} at the nearest enemy (Storm Lance electrifies and arcs half
 * the damage to a neighbour on every hit).</li>
 * <li>Slag Mortar / Magma Howitzer: a lobbed {@code P_PUDDLE} that lands where the target stood {@link #LOB_TIME} s ago, burns
 * for its duration and does nothing against an enemy that has walked on (the Howitzer also blasts on landing).</li>
 * <li>Tether Drone / Leash Array: orbiting {@code P_BOOK} drones (inert: no contact damage) that tether the weakest enemy within
 * a short range every 0.6 s (the Array: two targets, a slow, every 0.48 s).</li>
 * </ul>
 * The Sim calls in at a few marked places (behaviorOf, fire, spawnBooks, updateProjectiles). Everything works on preallocated
 * state; nothing here allocates while a run is stepping.
 */
final class Arsenal {
    static final int NONE = 0, LANCE = 1, STORM = 2, MORTAR = 3, HOWITZER = 4, TETHER = 5, LEASH = 6;

    static int idOf(String key) {
        switch (key) {
            case "ARC_LANCE": return LANCE;
            case "STORM_LANCE": return STORM;
            case "SLAG_MORTAR": return MORTAR;
            case "MAGMA_HOWITZER": return HOWITZER;
            case "TETHER_DRONE": return TETHER;
            case "LEASH_ARRAY": return LEASH;
            default: return NONE;
        }
    }

    /** The {@code Sim.B_*} behaviour a new weapon reuses, or -1 for none of them. */
    static int behaviorOf(int id) {
        switch (id) {
            case LANCE: case STORM: return Sim.B_MISSILE;
            case MORTAR: case HOWITZER: return Sim.B_WATER;
            case TETHER: case LEASH: return Sim.B_BOOK;
            default: return -1;
        }
    }

    /** Lance speed (u/s at speed 1), life (s) and the Storm arc: half the damage to the nearest other enemy within 140 u. */
    static final float LANCE_SPEED = 520f, LANCE_LIFE = 1.5f, ARC_RANGE = 140f, ARC_SHARE = 0.5f;
    /** Mortar: seconds in the air, base puddle radius, the Howitzer's landing blast (radius, share of the tick damage x). */
    static final float LOB_TIME = 0.7f, MORTAR_R = 70f, BLAST_R = 80f, BLAST_MUL = 4f;
    /** Tether Drone: orbit radius (u x area) and the orbit speed. */
    static final float ORBIT_R = 90f, ORBIT_SPEED = 2.4f;

    private final Sim s;
    private final int[] cand = new int[16];

    Arsenal(Sim s) {
        this.s = s;
    }

    // ------------------------------------------------------------------ Arc Lance / Storm Lance

    void fireLance(Sim.WeaponState w, int slot) {
        Projectile p = s.newProj(Entities.P_MISSILE, slot, w);
        if (p == null) return;
        s.sound.emit(SoundEvents.MAGIC_MISSILE);
        Enemy t = s.nearest(1000f);
        float a = t != null ? (float) Math.atan2(t.y - s.py, t.x - s.px) : (float) Math.atan2(s.faceY, s.faceX);
        // a volley fans out a little: the second lance leans left, the third right
        int k = w.shotIndex % 3;
        if (w.amount > 1 && k > 0) a += (k == 1 ? -1f : 1f) * 0.09f;
        float sp = LANCE_SPEED * w.speed;
        p.vx = (float) Math.cos(a) * sp;
        p.vy = (float) Math.sin(a) * sp;
        p.angle = a;
        p.radius = 10f * Math.max(0.8f, w.area) * (w.arsenal == STORM ? 1.35f : 1f);
        p.life = LANCE_LIFE;
        p.hitsLeft = w.pierce;
        p.tag = w.arsenal;
    }

    // ------------------------------------------------------------------ Slag Mortar / Magma Howitzer

    void fireMortar(Sim.WeaponState w, int slot) {
        Projectile p = s.newProj(Entities.P_PUDDLE, slot, w);
        if (p == null) return;
        // the shell lands where the nearest enemy stands NOW: one that keeps walking is long gone when it arrives
        Enemy t = s.nearest(480f);
        if (t != null) {
            p.x = t.x + (s.rng.nextFloat() - 0.5f) * 16f;
            p.y = t.y + (s.rng.nextFloat() - 0.5f) * 16f;
        } else {
            float a = (float) Math.atan2(s.faceY, s.faceX), d = 170f;
            p.x = s.px + (float) Math.cos(a) * d;
            p.y = s.py + (float) Math.sin(a) * d;
        }
        p.radius = MORTAR_R * w.area;
        p.flight = LOB_TIME;
        float active = Math.max(0.6f, w.duration);
        p.life = p.maxLife = LOB_TIME + active;
        p.hitDelay = w.hitDelay;
        p.hitsLeft = 1 << 30;
        p.tag = w.arsenal;
        p.aux = 0f;
    }

    // ------------------------------------------------------------------ Tether Drone / Leash Array

    /** A just-spawned drone: inert (no contact damage), on the tether orbit, its beam timer staggered by index. */
    void setupDrone(Sim.WeaponState w, Projectile p, int index) {
        p.tag = w.arsenal;
        p.inert = true;
        p.radius = 12f;
        p.orbitRadius = ORBIT_R * Math.max(0.8f, w.area);
        p.orbitSpeed = ORBIT_SPEED * w.speed;
        p.x = s.px + (float) Math.cos(p.orbitAngle) * p.orbitRadius;
        p.y = s.py + (float) Math.sin(p.orbitAngle) * p.orbitRadius;
        p.aux = w.beamEvery * (index + 1f) / Math.max(1, w.amount);
    }

    // ------------------------------------------------------------------ projectile hooks

    /** Per-frame work after the Sim moved the projectile. Returns true when the projectile must go. */
    boolean update(Projectile p, float dt) {
        switch (p.tag) {
            case MORTAR:
            case HOWITZER:
                if (p.kind == Entities.P_PUDDLE && p.aux == 0f && p.maxLife - p.life >= p.flight) {
                    p.aux = 1f; // it just landed
                    if (p.tag == HOWITZER) s.fusions.blast(p.x, p.y, BLAST_R * (p.radius / (MORTAR_R * 1.35f)), p.dmg * BLAST_MUL, Entities.ST_BURN);
                }
                return false;
            case TETHER:
            case LEASH:
                if (p.kind == Entities.P_BOOK) drone(p, dt);
                return false;
            default:
                return false;
        }
    }

    /** A hit by a tagged projectile: the Storm Lance electrifies and arcs, the mortar pools burn. */
    void onHit(Projectile p, Enemy e) {
        switch (p.tag) {
            case STORM:
                s.applyStatus(e, Entities.ST_ELECTRIFY, 1, p.dmg, 0f);
                arc(p, e);
                break;
            case MORTAR:
            case HOWITZER:
                s.applyStatus(e, Entities.ST_BURN, p.tag == HOWITZER ? 2 : 1, p.dmg, 0f);
                break;
            default:
        }
    }

    /** Storm Lance: half the lance's damage to the nearest other enemy within {@link #ARC_RANGE}. */
    private void arc(Projectile p, Enemy from) {
        int n = s.query(from.x, from.y, ARC_RANGE + 30f);
        Enemy best = null;
        float bd = ARC_RANGE * ARC_RANGE;
        for (int k = 0; k < n; k++) {
            Enemy t = s.enemies[s.qbuf[k]];
            if (t.dead || t == from || t.hitBy(p.stamp)) continue;
            float dx = t.x - from.x, dy = t.y - from.y, d = dx * dx + dy * dy;
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        if (best == null) return;
        best.markHit(p.stamp); // the lance does not hit it again on its own
        s.addFx(Entities.FX_BOLT, best.x, best.y, 14f, 0f, Sim.BOLT_LIFE, 0, false);
        s.softHit = true;
        s.hurt(best, p.dmg * ARC_SHARE, 0f, 1f, 0f, 0f, 0f);
        s.softHit = false;
        s.applyStatus(best, Entities.ST_ELECTRIFY, 1, p.dmg, 0f);
    }

    private void drone(Projectile p, float dt) {
        if (p.slot >= s.inv.weaponCount) return;
        Sim.WeaponState w = s.weapons[p.slot];
        if (w.arsenal != TETHER && w.arsenal != LEASH) return;
        if ((p.aux -= dt) > 0f) return;
        p.aux = 0.12f; // nothing in reach: look again soon
        int targets = w.arsenal == LEASH ? 2 : 1;
        float range = w.range;
        int n = s.query(p.x, p.y, range + 30f), m = 0;
        float r2 = range * range;
        for (int k = 0; k < n && m < cand.length; k++) {
            Enemy e = s.enemies[s.qbuf[k]];
            if (e.dead || e.dormant) continue;
            float dx = e.x - p.x, dy = e.y - p.y;
            if (dx * dx + dy * dy < r2) cand[m++] = s.qbuf[k];
        }
        if (m == 0) return;
        boolean any = false;
        int skip = -1;
        for (int t = 0; t < targets; t++) {
            int best = -1;
            float hp = Float.MAX_VALUE;
            for (int i = 0; i < m; i++) {
                if (cand[i] == skip) continue;
                Enemy e = s.enemies[cand[i]];
                if (!e.dead && e.hp < hp) {
                    hp = e.hp;
                    best = cand[i];
                }
            }
            if (best < 0) break;
            skip = best;
            tether(p, w, s.enemies[best]);
            any = true;
        }
        if (any) p.aux = w.beamEvery;
    }

    private void tether(Projectile p, Sim.WeaponState w, Enemy e) {
        s.softHit = true;
        s.hurt(e, w.dmg, w.critChance, w.critMul, 0f, 0f, w.knock);
        s.softHit = false;
        if (w.arsenal == LEASH) s.applyStatus(e, Entities.ST_FROST, e.frost < 1 ? 1 : 0, w.dmg, 0f);
        s.addFx(Entities.FX_BOLT, e.x, e.y, 10f, 0f, Sim.BOLT_LIFE, 0, w.arsenal == LEASH);
        float dx = e.x - p.x, dy = e.y - p.y;
        for (int k = 1; k <= 3; k++) s.spark(p.x + dx * k * 0.25f, p.y + dy * k * 0.25f, w.arsenal == LEASH ? 0xFFFFD85A : 0xFF5AE6C8, 1);
        s.sound.emit(SoundEvents.LIGHTNING);
    }
}
