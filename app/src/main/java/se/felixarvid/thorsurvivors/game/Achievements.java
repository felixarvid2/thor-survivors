package se.felixarvid.thorsurvivors.game;

/**
 * v1.5 B4: 24 achievements. {@link #check} is fed the facts of a finished run (and the shop calls {@link #checkShop} after a purchase), unlocks
 * what the facts earn, pays each reward once (a gold bonus of 250 to 2 000, {@link MetaProgress#achClaim}) and grants the hero tint of the
 * hero achievements. The unlocked set is one bitmask in the save ({@code ach}); lifetime totals ({@code stat_runs / kills / gold}) are
 * counted here.
 */
public final class Achievements {
    private Achievements() {
    }

    /** What a finished run tells (a plain bag so a test can feed any counters). */
    public static final class Facts {
        public String stage, hero;
        public boolean win, daily;
        public int timeSec, kills, level, evolutions, passives, tier, gold;
    }

    public static final int COUNT = 24;
    public static final String[] NAME = new String[COUNT], DESC = new String[COUNT];
    public static final int[] REWARD = new int[COUNT];
    /** Hero whose 4th tint the achievement grants, or null. */
    private static final String[] TINT_HERO = new String[COUNT];

    private static final int WIN_FLATS = 0, WIN_HERO0 = 5, KILLS_1000 = 8, KILLS_3000 = 9, EVO_1 = 10, EVO_3 = 11, NO_PASSIVE = 12,
            SURVIVE_10 = 13, SURVIVE_20 = 14, LEVEL_40 = 15, ASC_1 = 16, ASC_3 = 17, ASC_5 = 18, DAILY = 19, SPENT = 20, ALL_UPS = 21,
            RUNS_10 = 22, KILLS_20K = 23;

    private static void def(int i, String name, String desc, int reward) {
        NAME[i] = name;
        DESC[i] = desc;
        REWARD[i] = reward;
    }

    static {
        String[] st = {"Cinder Flats", "Deep Mines", "Hydro Works", "Refinery", "Wreck Yard"};
        for (int i = 0; i < 5; i++) def(WIN_FLATS + i, "Cleared: " + st[i], "Win " + st[i], 500 + 250 * i);
        String[] heroes = {"Bastion", "Spectre", "Xenomant"};
        for (int i = 0; i < 3; i++) {
            def(WIN_HERO0 + i, heroes[i] + " Ascendant", "Win a stage with " + heroes[i] + " (unlocks a tint)", 750);
            TINT_HERO[WIN_HERO0 + i] = GameData_HERO(i);
        }
        def(KILLS_1000, "Thousand Down", "1,000 kills in one run", 400);
        def(KILLS_3000, "Meat Grinder", "3,000 kills in one run", 1000);
        def(EVO_1, "Upgrade Path", "Evolve or fuse a weapon in one run", 250);
        def(EVO_3, "Arsenal", "Evolve or fuse three weapons in one run", 1000);
        def(NO_PASSIVE, "Bare Hands", "Reach 25:00 without picking a passive", 2000);
        def(SURVIVE_10, "Ten Minutes", "Survive 10:00", 250);
        def(SURVIVE_20, "Twenty Minutes", "Survive 20:00", 500);
        def(LEVEL_40, "Veteran", "Reach level 40 in one run", 500);
        def(ASC_1, "Ascended", "Win on ascension tier 1 or higher", 500);
        def(ASC_3, "High Ascension", "Win on ascension tier 3 or higher", 1000);
        def(ASC_5, "Apex", "Win on ascension tier 5", 2000);
        def(DAILY, "Daily Grind", "Win the daily challenge", 750);
        def(SPENT, "Investor", "Have 5,000 gold invested in the terminal", 500);
        def(ALL_UPS, "Fully Wired", "Own at least one rank of every power-up", 750);
        def(RUNS_10, "Regular", "Finish 10 runs", 250);
        def(KILLS_20K, "Exterminator", "20,000 kills over all runs", 1000);
    }

    private static String GameData_HERO(int i) {
        return se.felixarvid.thorsurvivors.data.GameData.HERO_ORDER[i];
    }

    /**
     * Counts the run into the lifetime totals, unlocks what the facts earn and returns the ids unlocked now (empty when none). Rewards are
     * paid here, once each.
     */
    public static int[] check(MetaProgress m, Facts f) {
        m.addStat("runs", 1);
        m.addStat("kills", f.kills);
        m.addStat("gold", f.gold);
        int[] out = new int[COUNT];
        int n = 0;
        for (int i = 0; i < COUNT; i++) if (!m.achDone(i) && earned(m, f, i) && claim(m, i)) out[n++] = i;
        return java.util.Arrays.copyOf(out, n);
    }

    /** Achievements decided by the terminal (gold invested, every power-up owned); call after a purchase. */
    public static int[] checkShop(MetaProgress m) {
        int[] out = new int[COUNT];
        int n = 0;
        for (int i = SPENT; i <= ALL_UPS; i++) if (!m.achDone(i) && earned(m, null, i) && claim(m, i)) out[n++] = i;
        return java.util.Arrays.copyOf(out, n);
    }

    private static boolean claim(MetaProgress m, int i) {
        if (!m.achClaim(i, REWARD[i])) return false;
        if (TINT_HERO[i] != null) m.grantTint(TINT_HERO[i], MetaProgress.TINTS - 1);
        return true;
    }

    private static boolean earned(MetaProgress m, Facts f, int i) {
        if (i == SPENT) return m.totalSpent() >= 5000;
        if (i == ALL_UPS) return m.allPowerUpsOwned();
        if (i == RUNS_10) return m.stat("runs") >= 10;
        if (i == KILLS_20K) return m.stat("kills") >= 20000;
        if (f == null) return false;
        if (i < WIN_HERO0) return f.win && f.stage != null && f.stage.equals(se.felixarvid.thorsurvivors.data.GameData.STAGE_ORDER[i - WIN_FLATS]);
        if (i < KILLS_1000) return f.win && f.hero != null && f.hero.equals(TINT_HERO[i]);
        switch (i) {
            case KILLS_1000: return f.kills >= 1000;
            case KILLS_3000: return f.kills >= 3000;
            case EVO_1: return f.evolutions >= 1;
            case EVO_3: return f.evolutions >= 3;
            case NO_PASSIVE: return f.timeSec >= 1500 && f.passives == 0;
            case SURVIVE_10: return f.timeSec >= 600;
            case SURVIVE_20: return f.timeSec >= 1200;
            case LEVEL_40: return f.level >= 40;
            case ASC_1: return f.win && f.tier >= 1;
            case ASC_3: return f.win && f.tier >= 3;
            case ASC_5: return f.win && f.tier >= 5;
            case DAILY: return f.win && f.daily;
            default: return false;
        }
    }
}
