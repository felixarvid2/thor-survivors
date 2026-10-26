package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Entities.Projectile;

/**
 * The behaviours of the seven weapon fusions (design v1.3 section 1, {@code docs/TEKNIK.md} "Vapenfusioner"). Each fusion
 * reuses the Sim behaviour of one parent ({@code Sim.B_*}) and adds a small piece here: Stormlash chains its slash, Foundry
 * Spill lobs fire bursts from its pool, Needle Choir fires a homing fan, Miasma Censer gives each orbiting drone an aura,
 * Ricochet Ordnance blasts at the turn and the return, Faraday Halo calls lightning from its drones and Kiln Breath sends out
 * a fire ring. The Sim calls in at a few marked places; everything here works on preallocated arrays (no per-frame allocation).
 */
final class Fusions {
    static final int NONE = 0, STORM = 1, FOUNDRY = 2, NEEDLE = 3, CENSER = 4, ORDNANCE = 5, FARADAY = 6, KILN = 7;

    static int idOf(String key) {
        switch (key) {
            case "STORMLASH": return STORM;
            case "FOUNDRY_SPILL": return FOUNDRY;
            case "NEEDLE_CHOIR": return NEEDLE;
            case "MIASMA_CENSER": return CENSER;
            case "RICOCHET_ORDNANCE": return ORDNANCE;
            case "FARADAY_HALO": return FARADAY;
            case "KILN_BREATH": return KILN;
            default: return NONE;
        }
    }

    /** Stormlash: a chain hit deals half the slash damage to up to 2 other enemies within 140 u, 6 chains at most per slash. */
    static final float CHAIN_RANGE = 140f, CHAIN_SHARE = 0.5f;
    static final int CHAIN_PER_HIT = 2, CHAIN_MAX = 6;
    /** Foundry Spill: the pool lobs a fire burst of radius 40 every 0.5 s (a share of the pool's tick damage per level). */
    static final float BURST_EVERY = 0.5f, BURST_R = 40f, BURST_SHARE = 1.0f;
    /** Needle Choir: a fan of up to 5 homing needles in a 40 degree cone. */
    static final int NEEDLE_CAP = 5;
    static final float NEEDLE_CONE = 40f * 0.017453292f, NEEDLE_TURN = 5f;
    /** Miasma Censer: aura radius 45 u x area, the orbit radius grows by 20% over each pass. */
    static final float CENSER_AURA = 45f, CENSER_GROW = 0.2f;
    /** Ricochet Ordnance: blast radius 70 u x area at the turn point and again on the return. */
    static final float BLAST_R = 70f;
    /** Faraday Halo: each drone calls a bolt on the nearest enemy within 150 u every 0.6 s. */
    static final float HALO_RANGE = 150f, HALO_EVERY = 0.6f;
    /** Kiln Breath: an expanding ring of radius 200 every 3 s (it takes {@link #RING_TIME} s to reach it). */
    static final float RING_R = 200f, RING_EVERY = 3f, RING_TIME = 0.55f, RING_SHARE = 2f;
    private static final int MAX_RINGS = 4;

    private final Sim s;
    private final Enemy[] slashHits = new Enemy[64];
    private int slashN, slashStamp;
    private final int[] cand;
    // Kiln Breath rings
    private final float[] ringX = new float[MAX_RINGS], ringY = new float[MAX_RINGS], ringT = new float[MAX_RINGS],
            ringDmg = new float[MAX_RINGS];
    private final int[] ringStamp = new int[MAX_RINGS];
    private int ringN;

    Fusions(Sim s) {
        this.s = s;
        cand = new int[Sim.MAX_ENEMIES];
    }

    // ------------------------------------------------------------------ Stormlash

    /** Called by the whip for every enemy a slash hits: applies Electrify and remembers it for the chains. */
    void slashHit(Sim.WeaponState w, Enemy e) {
        if (slashN == 0) slashStamp = s.nextStamp++;
        s.applyStatus(e, Entities.ST_ELECTRIFY, 1, w.dmg, 0f);
        e.markHit(slashStamp);
        if (slashN < slashHits.length) slashHits[slashN++] = e;
    }

    /** After the slash: each remembered hit chains to the 2 nearest enemies the slash did not touch, 6 chains in all. */
    void slashEnd(Sim.WeaponState w) {
        int chains = 0;
        for (int i = 0; i < slashN && chains < CHAIN_MAX; i++) {
            Enemy from = slashHits[i];
            int n = s.query(from.x, from.y, CHAIN_RANGE + 30f);
            for (int pick = 0; pick < CHAIN_PER_HIT && chains < CHAIN_MAX; pick++) {
                Enemy best = null;
                float bd = CHAIN_RANGE * CHAIN_RANGE;
                for (int k = 0; k < n; k++) {
                    Enemy t = s.enemies[s.qbuf[k]];
                    if (t.dead || t.hitBy(slashStamp)) continue;
                    float dx = t.x - from.x, dy = t.y - from.y, d = dx * dx + dy * dy;
                    if (d < bd) {
                        bd = d;
                        best = t;
                    }
                }
                if (best == null) break;
                best.markHit(slashStamp);
                chains++;
                s.addFx(Entities.FX_BOLT, best.x, best.y, 14f, 0f, Sim.BOLT_LIFE, 0, false);
                s.softHit = true;
                s.hurt(best, w.dmg * CHAIN_SHARE, 0f, 1f, 0f, 0f, 0f);
                s.softHit = false;
                s.applyStatus(best, Entities.ST_ELECTRIFY, 1, w.dmg, 0f);
            }
        }
        if (chains > 0) s.sound.emit(SoundEvents.LIGHTNING);
        slashN = 0;
    }

    // ------------------------------------------------------------------ Needle Choir

    /** One volley: a fan of {@code amount} (at most 5) needles toward the nearest enemy, all homing. */
    void needles(Sim.WeaponState w, int slot) {
        Enemy t = s.nearest(1000f);
        float base = t != null ? (float) Math.atan2(t.y - s.py, t.x - s.px) : (float) Math.atan2(s.faceY, s.faceX);
        int n = Math.min(NEEDLE_CAP, Math.max(1, w.amount));
        s.sound.emit(SoundEvents.KNIFE);
        for (int i = 0; i < n; i++) {
            Projectile p = s.newProj(Entities.P_KNIFE, slot, w);
            if (p == null) return;
            float a = base + (n == 1 ? 0f : (i / (n - 1f) - 0.5f) * NEEDLE_CONE);
            float sp = 520f * w.speed;
            p.vx = (float) Math.cos(a) * sp;
            p.vy = (float) Math.sin(a) * sp;
            p.angle = a;
            p.radius = 6f * Math.max(0.8f, w.area);
            p.life = 1.6f;
            p.targetId = t == null ? 0 : t.id;
        }
    }

    private void home(Projectile p, float dt) {
        Enemy t = null;
        if (p.targetId != 0) {
            for (int i = 0; i < s.enemyN; i++) {
                if (s.enemies[i].id == p.targetId) {
                    t = s.enemies[i];
                    break;
                }
            }
            if (t != null && t.dead) t = null;
        }
        if (t == null) {
            float bd = 320f * 320f;
            for (int i = 0; i < s.enemyN; i++) {
                Enemy e = s.enemies[i];
                if (e.dead) continue;
                float dx = e.x - p.x, dy = e.y - p.y, d = dx * dx + dy * dy;
                if (d < bd) {
                    bd = d;
                    t = e;
                }
            }
            p.targetId = t == null ? 0 : t.id;
        }
        if (t == null) return;
        float want = (float) Math.atan2(t.y - p.y, t.x - p.x), cur = (float) Math.atan2(p.vy, p.vx);
        float diff = want - cur;
        while (diff > Math.PI) diff -= 6.2831855f;
        while (diff < -Math.PI) diff += 6.2831855f;
        float turn = Math.max(-NEEDLE_TURN * dt, Math.min(NEEDLE_TURN * dt, diff));
        float sp = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
        p.vx = (float) Math.cos(cur + turn) * sp;
        p.vy = (float) Math.sin(cur + turn) * sp;
    }

    // ------------------------------------------------------------------ orbiting drones (Miasma Censer, Faraday Halo)

    /** A just-spawned orbiting drone of a fusion: the censer gets its aura as the hit radius, the halo drone a staggered bolt timer. */
    void setupBook(Sim.WeaponState w, Projectile p, int index) {
        p.aux2 = p.orbitRadius;
        if (w.fusion == CENSER) {
            p.radius = CENSER_AURA * w.area;
        } else {
            p.aux = HALO_EVERY * (index + 1f) / Math.max(1, w.amount);
        }
    }

    // ------------------------------------------------------------------ projectile hooks

    /**
     * Per-frame work of a fusion projectile, called by the Sim after it moved. Returns true when the projectile must go.
     */
    boolean update(Projectile p, float dt) {
        switch (p.fusion) {
            case NEEDLE:
                if (p.kind == Entities.P_KNIFE) home(p, dt);
                return false;
            case FOUNDRY:
                if (p.kind != Entities.P_PUDDLE) return false;
                if ((p.aux -= dt) <= 0f) {
                    p.aux += BURST_EVERY;
                    foundryBurst(p);
                }
                return false;
            case CENSER:
                p.orbitRadius = p.aux2 * (1f + CENSER_GROW * 0.5f * (1f - (float) Math.cos(p.orbitAngle)));
                return false;
            case FARADAY:
                if ((p.aux -= dt) <= 0f) halo(p);
                return false;
            case ORDNANCE:
                if (p.kind != Entities.P_CROSS || !p.returning) return false;
                if (p.aux2 == 0f) { // the turn point
                    p.aux2 = 1f;
                    blast(p.x, p.y, p.blastR, p.dmg, Entities.ST_NONE);
                    return false;
                }
                float dx = p.x - s.px, dy = p.y - s.py;
                if (dx * dx + dy * dy < 40f * 40f || p.life < 0.8f) { // back at the hero (or where it started)
                    blast(p.x, p.y, p.blastR, p.dmg, Entities.ST_NONE);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    /** A hit by a fusion projectile: the status its parent weapon applies. */
    void onHit(Projectile p, Enemy e) {
        if (p.fusion == CENSER) s.applyStatus(e, Entities.ST_DECAY, 1, p.dmg, 0f);
        else if (p.fusion == FOUNDRY && p.kind == Entities.P_PUDDLE) s.applyStatus(e, Entities.ST_BURN, 1, p.dmg, 0f);
    }

    /** A fusion projectile runs out of life. */
    void expire(Projectile p) {
        if (p.fusion == ORDNANCE && p.kind == Entities.P_CROSS && p.aux2 == 1f) blast(p.x, p.y, p.blastR, p.dmg, Entities.ST_NONE);
    }

    private void foundryBurst(Projectile p) {
        int n = s.query(p.x, p.y, p.radius + 30f), m = 0;
        float r2 = p.radius * p.radius;
        for (int k = 0; k < n; k++) {
            Enemy e = s.enemies[s.qbuf[k]];
            float dx = e.x - p.x, dy = e.y - p.y;
            if (!e.dead && dx * dx + dy * dy < r2) cand[m++] = s.qbuf[k];
        }
        if (m == 0) return;
        Enemy t = s.enemies[cand[s.rng.nextInt(m)]];
        s.sound.emit(SoundEvents.FIREBALL);
        blast(t.x, t.y, BURST_R, p.dmg * BURST_SHARE, Entities.ST_BURN);
    }

    private void halo(Projectile p) {
        p.aux = 0.15f; // nothing in reach: look again soon
        if (p.slot >= s.inv.weaponCount || s.weapons[p.slot].fusion != FARADAY) return;
        Enemy best = null;
        float bd = HALO_RANGE * HALO_RANGE;
        int n = s.query(p.x, p.y, HALO_RANGE + 30f);
        for (int k = 0; k < n; k++) {
            Enemy e = s.enemies[s.qbuf[k]];
            if (e.dead) continue;
            float dx = e.x - p.x, dy = e.y - p.y, d = dx * dx + dy * dy;
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) return;
        p.aux = HALO_EVERY;
        s.strike(s.weapons[p.slot], best.x, best.y);
    }

    /** Damages every enemy within {@code r} of a point (radius counted to the enemy's edge) and applies a status when asked. */
    void blast(float x, float y, float r, float dmg, int status) {
        int n = s.query(x, y, r + 40f);
        for (int k = 0; k < n; k++) {
            Enemy e = s.enemies[s.qbuf[k]];
            if (e.dead) continue;
            float dx = e.x - x, dy = e.y - y, d = (float) Math.sqrt(dx * dx + dy * dy) + 0.01f;
            if (d > r + e.radius * 0.5f) continue;
            s.hurt(e, dmg, 0f, 1f, dx / d, dy / d, 0.4f);
            if (status != Entities.ST_NONE) s.applyStatus(e, status, 1, dmg, 0f);
        }
        s.hitDest(x, y, r);
        s.addFx(Entities.FX_BLAST, x, y, r, 1f, 0.3f, 0, true);
    }

    // ------------------------------------------------------------------ Kiln Breath

    /** Every frame of the Kiln Breath weapon: counts to the next ring and expands the rings in flight. */
    void kiln(Sim.WeaponState w, float dt) {
        if ((w.aux -= dt) <= 0f) {
            w.aux += RING_EVERY;
            if (ringN < MAX_RINGS) {
                ringX[ringN] = s.px;
                ringY[ringN] = s.py;
                ringT[ringN] = 0f;
                ringDmg[ringN] = w.dmg * RING_SHARE;
                ringStamp[ringN++] = s.nextStamp++;
                s.addFx(Entities.FX_SLAM, s.px, s.py, RING_R * w.area, 0f, RING_TIME + 0.15f, 0, true);
                s.sound.emit(SoundEvents.FIREBALL);
            }
        }
        for (int i = ringN - 1; i >= 0; i--) {
            ringT[i] += dt;
            float u = Math.min(1f, ringT[i] / RING_TIME), r = 20f + (RING_R * w.area - 20f) * u;
            int n = s.query(ringX[i], ringY[i], r + 30f);
            for (int k = 0; k < n; k++) {
                Enemy e = s.enemies[s.qbuf[k]];
                if (e.dead || e.hitBy(ringStamp[i])) continue;
                float dx = e.x - ringX[i], dy = e.y - ringY[i], rr = r + e.radius;
                if (dx * dx + dy * dy >= rr * rr) continue;
                e.markHit(ringStamp[i]);
                float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.01f;
                s.hurt(e, ringDmg[i], 0f, 1f, dx / d, dy / d, 0.5f);
                s.applyStatus(e, Entities.ST_BURN, 1, ringDmg[i], 0f);
            }
            if (ringT[i] >= RING_TIME) { // done: swap-remove
                ringN--;
                ringX[i] = ringX[ringN]; ringY[i] = ringY[ringN]; ringT[i] = ringT[ringN];
                ringDmg[i] = ringDmg[ringN]; ringStamp[i] = ringStamp[ringN];
            }
        }
    }
}
