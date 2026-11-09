package se.felixarvid.thorsurvivors.game;

/**
 * v1.5 B4: the modifiers of one run, fixed at its start: the ascension tier (a gold sink that makes a stage harder), the one
 * modifier of the daily challenge, and the charges bought in the shop (rerolls and banishes of the level-up cards). Immutable;
 * {@link Sim#mods} holds it and every getter is a plain multiplication, so reading it in the game loop allocates nothing.
 *
 * Ascension: tiers 1..{@link #MAX_TIER} per stage, each adds the next modifier of {@link #MOD_NAMES} (they stack), costs nothing and
 * raises the gold payout by {@link #GOLD_PER_TIER} per tier. A tier opens by winning the stage on the tier below ({@link MetaProgress#ascMax}).
 */
public final class RunMods {
    public static final int MAX_TIER = 5;
    public static final int MOD_HP = 0, MOD_SPEED = 1, MOD_ELITE = 2, MOD_HEAL = 3, MOD_MERCHANT = 4, MOD_COUNT = 5;
    /** What each tier adds (tier n = the first n entries). */
    public static final String[] MOD_NAMES = {"Enemy HP +15%", "Enemy speed +8%", "Elites arrive 2 min earlier", "Healing drops -30%",
            "Salvager prices +25%"};
    public static final String[] MOD_SHORT = {"HP +15%", "SPEED +8%", "ELITES -2:00", "HEAL -30%", "PRICES +25%"};
    public static final float HP_MULT = 1.15f, SPEED_MULT = 1.08f, ELITE_EARLY_SEC = 120f, HEAL_MULT = 0.7f, MERCHANT_MULT = 1.25f;
    public static final float GOLD_PER_TIER = 0.15f, DAILY_GOLD = 1.5f;

    public static final RunMods NONE = new RunMods(0, 0, 0, 0, false, false);

    /** Ascension tier of the run (0 = none). */
    public final int tier;
    /** Bit i set = {@code MOD_*} i is active (the first {@code tier} bits for an ascension, one bit for the daily challenge). */
    public final int modMask;
    public final int rerolls, banishes;
    /** The run is the daily challenge; {@code dailyBonus}: its x1.5 gold is still unclaimed today. */
    public final boolean daily, dailyBonus;

    private RunMods(int tier, int modMask, int rerolls, int banishes, boolean daily, boolean dailyBonus) {
        this.tier = tier;
        this.modMask = modMask;
        this.rerolls = rerolls;
        this.banishes = banishes;
        this.daily = daily;
        this.dailyBonus = dailyBonus;
    }

    /** A run on ascension {@code tier} (clamped to 0..5) with the bought charges. */
    public static RunMods ascension(int tier, int rerolls, int banishes) {
        int t = Math.max(0, Math.min(MAX_TIER, tier));
        return new RunMods(t, (1 << t) - 1, rerolls, banishes, false, false);
    }

    /** The daily challenge with its one modifier {@code mod} (0..4); {@code bonus}: the x1.5 gold has not been taken today. */
    public static RunMods daily(int mod, boolean bonus, int rerolls, int banishes) {
        return new RunMods(0, 1 << Math.floorMod(mod, MOD_COUNT), rerolls, banishes, true, bonus);
    }

    public boolean has(int mod) {
        return (modMask & (1 << mod)) != 0;
    }

    public float hpMult() {
        return has(MOD_HP) ? HP_MULT : 1f;
    }

    public float speedMult() {
        return has(MOD_SPEED) ? SPEED_MULT : 1f;
    }

    /** Seconds the first elite comes earlier. */
    public float eliteEarlySec() {
        return has(MOD_ELITE) ? ELITE_EARLY_SEC : 0f;
    }

    public float healMult() {
        return has(MOD_HEAL) ? HEAL_MULT : 1f;
    }

    /** Factor on the Salvager's prices. */
    public float merchantMult() {
        return has(MOD_MERCHANT) ? MERCHANT_MULT : 1f;
    }

    /** Factor on the gold the run pays out: +15% per tier, and x1.5 on the first daily challenge of the day. */
    public float goldMult() {
        return (1f + GOLD_PER_TIER * tier) * (dailyBonus ? DAILY_GOLD : 1f);
    }

    public int extraRerolls() {
        return rerolls;
    }

    public int extraBanishes() {
        return banishes;
    }

    // ------------------------------------------------------------------ the daily challenge

    /** What a date fixes: the run seed, the index among the stages the player may play, the hero index and the modifier. */
    public static final class Daily {
        public final long seed;
        public final int stage, hero, mod;

        Daily(long seed, int stage, int hero, int mod) {
            this.seed = seed;
            this.stage = stage;
            this.hero = hero;
            this.mod = mod;
        }
    }

    /** The challenge of a date (yyyymmdd): the same date always gives the same seed, stage, hero and modifier. Local, no service. */
    public static Daily dailyOf(int date, int stages, int heroes) {
        long z = mix(date * 0x9E3779B97F4A7C15L + 0x5DA11E5L);
        long a = mix(z + 1), b = mix(z + 2), c = mix(z + 3);
        return new Daily(z, (int) Long.remainderUnsigned(a, Math.max(1, stages)), (int) Long.remainderUnsigned(b, Math.max(1, heroes)),
                (int) Long.remainderUnsigned(c, MOD_COUNT));
    }

    private static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    /** Today as yyyymmdd (local time). */
    public static int today() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return c.get(java.util.Calendar.YEAR) * 10000 + (c.get(java.util.Calendar.MONTH) + 1) * 100 + c.get(java.util.Calendar.DAY_OF_MONTH);
    }
}
