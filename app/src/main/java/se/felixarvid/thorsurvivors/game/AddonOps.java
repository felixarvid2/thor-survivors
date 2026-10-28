package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.game.Entities.Enemy;

/**
 * What the Flats and Mines add-on rules (v1.4 chunk E, {@code game/stage/FlatsRules} and {@code MinesRules}) may do to the run: a thin,
 * allocation-free door into the package-private parts of {@link Sim}, so the rules can live in their own package without
 * widening Sim itself. Nothing here draws from {@link Sim#rng} except where noted (a dropped Arms Crate rolls its contents the way a
 * found one does).
 */
public final class AddonOps {
    /** {@link Enemy#poi} tags of the add-on enemies (a negative value is never an outpost index; {@link Pois#POI_HOUND} is -2). */
    public static final int POI_CONVOY = -10, POI_ESCORT = -11, POI_BLOOM = -12, POI_LURKER = -13;

    private final Sim s;

    public AddonOps(Sim s) {
        this.s = s;
    }

    /**
     * Damage to the hero from the floor or the sky (armor applies, 0.4 s of invulnerability after): false and nothing when the hero
     * is invulnerable (dodging, a hit just taken) or the run is over.
     */
    public boolean hitHero(float dmg) {
        if (s.invuln > 0f || s.ended != 0) return false;
        s.hurtHero(Math.max(1f, dmg - (float) s.stats.armor), 0.4f, true, Sim.DMG_BLAST);
        return true;
    }

    /** Pushes the hero {@code dist} units along (dx, dy); not while dodging, less with Armored Greaves. */
    public void shoveHero(float dx, float dy, float dist) {
        s.shoveHero(dx, dy, dist);
    }

    /** Breaks every explosive barrel (not the chests and pylons) whose body touches the circle; a barrel next to a barrel chains. Returns how many were hit. */
    public int igniteBarrels(float x, float y, float r) {
        int n = 0;
        for (int i = s.destN - 1; i >= 0; i--) {
            if (i >= s.destN) continue; // a chain removed several
            if (s.destKind[i] != Sim.DK_BARREL) continue;
            float dx = s.destX[i] - x, dy = s.destY[i] - y, rr = r + Sim.DEST_R;
            if (dx * dx + dy * dy >= rr * rr) continue;
            s.breakDest(i);
            n++;
        }
        return n;
    }

    /** Standing barrels inside the circle (for the screenshots and tests). */
    public int barrelsNear(float x, float y, float r) {
        int n = 0;
        for (int i = 0; i < s.destN; i++) {
            if (s.destKind[i] != Sim.DK_BARREL) continue;
            float dx = s.destX[i] - x, dy = s.destY[i] - y;
            if (dx * dx + dy * dy < r * r) n++;
        }
        return n;
    }

    /**
     * Enemies whose body touches the circle (x, y, r): fills {@link #hit} with their pool indexes and returns the count. The grid is
     * the one of the last step; call {@link Sim#rebuildGridForTest}-style freshness is the Sim's own business (it rebuilds every step).
     */
    public int enemiesIn(float x, float y, float r) {
        int n = s.query(x, y, r + 40f);
        int m = 0;
        for (int k = 0; k < n; k++) {
            Enemy e = s.enemies[s.qbuf[k]];
            if (e.dead || e.dormant) continue;
            float dx = e.x - x, dy = e.y - y, rr = r + e.radius * 0.5f;
            if (dx * dx + dy * dy < rr * rr) hit[m++] = s.qbuf[k];
        }
        return m;
    }

    /** Pool indexes written by {@link #enemiesIn}. */
    public final int[] hit = new int[Sim.MAX_ENEMIES];

    public Enemy enemy(int poolIndex) {
        return s.enemies[poolIndex];
    }

    /** True when the add-ons' enemies must stay away: a test scenario switched the wave spawns off ({@code Sim.noSpawns}) and did not ask for them. */
    public boolean quiet(boolean spawnWhenQuiet) {
        return s.noSpawns && !spawnWhenQuiet || s.events.spawnsBlocked(); // the Tide's lull: nothing arrives
    }

    /** One place's spawn: an enemy of {@code key} at (x, y) tagged {@code poi}, without drawing from {@link Sim#rng}; null when the pool is nearly full or the key is unknown. */
    public Enemy spawn(String key, float x, float y, int poi, boolean elite) {
        EnemyType t = s.type(key);
        if (t == null || s.enemyN >= WaveDirector.ENEMY_CAP) return null;
        if (t.ranged && s.rangedAlive() >= Sim.RANGED_CAP) { // like the waves: past the cap a shooter comes as a colonist
            EnemyType c = s.type("COLONIST");
            if (c != null) t = c;
        }
        return s.poiSpawn(t, x, y, poi, elite, 0);
    }

    /** A pickup on the ground (Entities.K_*). */
    public void pickup(int kind, float x, float y, float value) {
        s.addPickup(kind, x, y, value);
    }

    /** Gems worth {@code levels} levels of XP at the hero's level, in five pieces in a ring around (x, y). */
    public void xpPile(float x, float y, float levels) {
        float each = s.xpNeed * levels / 5f;
        for (int i = 0; i < 5; i++) {
            float a = i * 1.2566371f;
            s.addPickup(Entities.K_GEM, x + (float) Math.cos(a) * 18f, y + (float) Math.sin(a) * 14f, each);
        }
    }

    /** The Weapon Cache's contents (fusion, evolution, a new weapon or a level), as when found on the map. */
    public void armsCrate() {
        s.collectFind(Sim.FIND_WEAPON);
    }

    /** A Rare piece of gear, as the Gear Locker gives. */
    public void gearLocker() {
        s.collectFind(Sim.FIND_GEAR);
    }

    public void burst(float x, float y, float r, int color, int sparks) {
        s.addFx(Entities.FX_BLAST, x, y, r, 1f, 0.3f, 0, false);
        s.addFx(Entities.FX_FLASH, x, y - 8f, r * 1.6f, 0f, 0.3f, 0, false);
        s.spark(x, y - 8f, color, sparks);
        s.screenShake(0.12f);
    }

    public void flash(float x, float y, float size) {
        s.addFx(Entities.FX_FLASH, x, y - 8f, size, 0f, 0.22f, 0, false);
    }

    public void spark(float x, float y, int color, int count) {
        s.spark(x, y, color, count);
    }

    public void sound(int id) {
        s.sound.emit(id);
    }

    public void shake(float t) {
        s.screenShake(t);
    }

    /** Hurts an enemy as a weapon hit would (no crit, no draw from the main generator), with knockback along (kx, ky). */
    public void hurt(Enemy e, float dmg, float kx, float ky, float knock) {
        s.hurt(e, dmg, 0f, 1f, kx, ky, knock);
    }

    /** True where something big stands (a prop or wall). */
    public boolean blocked(float x, float y) {
        return s.map.blocked(x, y);
    }
}
