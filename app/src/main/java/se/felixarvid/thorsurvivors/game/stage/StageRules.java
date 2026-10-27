package se.felixarvid.thorsurvivors.game.stage;

import se.felixarvid.thorsurvivors.data.StageDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Sim;

/**
 * What makes one stage play differently (v1.4 stage framework): every stage has one rules object, made by {@link #forKey} when
 * the {@link Sim} is built ({@code Sim.rules}). All hooks default to "nothing", so a stage overrides only what it needs; the
 * Sim calls them from fixed places (listed per hook) and never branches on a stage key itself.
 *
 * <p>Rules for the rest of the game: no android imports (drawing lives in {@code render/stagefx/StageFx}), no allocation in
 * any hook (they run every step), and any random numbers come from the rules' own {@code Random(seed ^ salt)}, never from
 * {@code Sim.rng}, so a stage with rules draws the same main-generator sequence as one without.
 */
public abstract class StageRules {
    /** Damage categories of {@link #damageIn}: a direct hit (projectile, melee, aura) or one of the status ticks and blasts. */
    public static final int CAT_HIT = 0, CAT_BURN = 1, CAT_FROST = 2, CAT_ELECTRIFY = 3, CAT_DECAY = 4, CAT_BLAST = 5;
    /** Who a {@link #push} drifts: the hero, an enemy, a pickup (its drift is a fraction of {@link #PICKUP_SPEED}). */
    public static final int PUSH_HERO = 0, PUSH_ENEMY = 1, PUSH_PICKUP = 2;
    /** Reference speed in u/s that a pickup's drift fraction is applied to. */
    public static final float PICKUP_SPEED = 100f;

    /** The run these rules belong to (set by {@link #init}). */
    protected Sim s;
    /** The stage's {@code rules} parameters from ashfall_data.json (never null). */
    protected StageDef def;

    /** The rules of a stage key ({@code FLATS}, {@code MINES}, {@code HYDRO}, {@code REFINERY}, {@code WRECK}; anything else: {@link PlainRules}). */
    public static StageRules forKey(String key) {
        switch (key) {
            case "FLATS": return new FlatsRules();
            case "MINES": return new MinesRules();
            case "HYDRO": return new HydroRules();
            case "REFINERY": return new RefineryRules();
            case "WRECK": return new WreckRules();
            default: return new PlainRules();
        }
    }

    /**
     * Called once from the Sim constructor, right after the map and before the first spawns; the Sim is only partly built then, so
     * touch just {@code stage}, {@code data} and {@code runSeed} here and build the rest of the state on the first {@link #update}.
     */
    public void init(Sim sim) {
        this.s = sim;
        this.def = sim.stage;
    }

    /** Once per step, after the hero moved and before {@code updateWaves}. {@code dt} is the step length in seconds. */
    public void update(float dt) {
    }

    /**
     * Ground drift at (x, y): writes a velocity into {@code out[0..1]} as a fraction of the mover's own speed and returns true,
     * or returns false (and leaves {@code out}) where the ground does not move things. Used by the hero's move, the enemies'
     * move ({@code Sim.updateEnemies}) and the pickups; {@link #pushWeight} scales it per kind.
     */
    public boolean push(float x, float y, float[] out) {
        return false;
    }

    /** How much of the {@link #push} drift an entity takes: 1 for the hero, e.g. 0.6 for an enemy, 0.2 for a boss, 0 for none. {@code e} is null unless {@code who} is {@link #PUSH_ENEMY}. */
    public float pushWeight(int who, Enemy e) {
        return 1f;
    }

    /** Extra multiplier on an enemy's walking speed ({@code EnemyAi.speedOf}). */
    public float enemySpeedMul(Enemy e) {
        return 1f;
    }

    /** Extra multiplier on the hero's walking speed ({@code Sim.movePlayer}; not the dodge). */
    public float heroSpeedMul() {
        return 1f;
    }

    /** Multiplier on how fast the wave director tops up the field (the spawn tick), 1 = as the rows say. */
    public float spawnMul() {
        return 1f;
    }

    /** What an enemy takes of {@code amount} damage of {@code category} ({@code CAT_*}); called by {@code Sim.hurt} and the status ticks. */
    public float damageIn(Enemy e, float amount, int category) {
        return amount;
    }

    /** An enemy was reaped (killed, not recycled), before its drops. */
    public void onEnemyDeath(Enemy e) {
    }

    /** First timers of a freshly spawned enemy of a stage archetype ({@code EnemyDef.ARCH_HEALER} and up). */
    public void initArch(Enemy e) {
    }

    /**
     * One step of an enemy of a stage archetype. Returns true when it moved the enemy itself; false lets the caller chase the
     * hero like a plain chaser (so an unfinished archetype is a walker, not a statue).
     */
    public boolean updateArch(Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        return false;
    }

    /** 0..1 taken off the ambient darkness (a lit stage), added to the Surge lift. */
    public float ambientLift() {
        return 0f;
    }

    /** Multiplier on the hero's light radius (Flats: the ash storm). */
    public float lightMul() {
        return 1f;
    }

    /** True when the run places Weapon Caches, Gear Lockers and the Salvager (the Ashfall stages; not the legacy FOREST / LIBRARY). */
    public boolean hasFinds() {
        return true;
    }

    // ---- chunk E (Flats and Mines add-ons): shared by every stage, all no-ops by default

    /**
     * Switch for the stage's add-ons (the Flats storm lightning, convoy and blooms; the Mines carts, firedamp and Lurkers): tests and
     * soaks that must isolate the old behaviour set it to false before the first step. Stages without add-ons ignore it.
     */
    public boolean addonsEnabled = true;

    /**
     * The add-ons' enemies (convoy, bloom hounds, Lurkers) obey {@code Sim.noSpawns} like the waves ("a scenario that only contains the
     * enemies it adds"): set this to true to let them come anyway (the add-on tests silence the waves but want the add-ons).
     */
    public boolean spawnWhenQuiet = false;

    /** An explosion of radius {@code r} went off at (x, y) (barrel, grenade, bomb, fusion blast): firedamp catches it. Called by the code that blows it up. */
    public void onBlast(float x, float y, float r) {
    }

    /** True when an enemy must not be recycled to the edge of the screen when it falls far behind (the convoy tanker crossing the map). */
    public boolean keepFar(Enemy e) {
        return false;
    }

    /** True when the renderer must not draw a creature of this type at (x, y) (the Dark Lurker outside the light); it can still be hit. */
    public boolean hidden(se.felixarvid.thorsurvivors.game.EnemyType t, float x, float y) {
        return false;
    }
}
