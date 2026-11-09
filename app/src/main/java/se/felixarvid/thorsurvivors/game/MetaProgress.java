package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.PowerUpDef;

import java.util.HashMap;
import java.util.Map;

/** Persistent gold, shop ranks and records. Storage is abstracted so this stays unit-testable. */
public final class MetaProgress {
    public interface Store {
        int getInt(String key, int def);

        void putInt(String key, int value);
    }

    private final GameData data;
    private final Store store;
    public int gold;
    public int bestTimeSec;
    public int bestKills;
    /** Volume steps 0..{@link #VOL_MAX} (0 = off). */
    public static final int VOL_MAX = 3;
    public volatile int musicVol, sfxVol;
    /** Lighting quality: 0 OFF, 1 LOW (static vignette), 2 FULL (lightmap). */
    public static final int LIGHT_OFF = 0, LIGHT_LOW = 1, LIGHT_FULL = 2;
    public volatile int lighting = LIGHT_FULL;
    /** Selected hero: index into {@link GameData#HERO_ORDER} among the heroes the data defines (0 = Bastion, also for old saves). */
    public volatile int heroIdx;
    private String[] heroKeys;
    private final Map<String, Integer> ranks = new HashMap<>();
    private final Map<String, Integer> stageBest = new HashMap<>();
    private final Map<String, Boolean> stageCleared = new HashMap<>();
    private final Map<String, String> unlockLines = new HashMap<>();
    /**
     * Debug and tests: every stage is open, whatever the records say. Stored as the pref {@code unlockAll} (1 = on) so a device
     * can be opened up with a saved value; the test harnesses set the field.
     */
    public volatile boolean unlockAll;
    /** Counts record changes (a best time, a cleared flag): the stage cards rebuild their unlock lines when it moves. */
    public volatile int stageRev;

    public MetaProgress(GameData data, Store store) {
        this.data = data;
        this.store = store;
        gold = store.getInt("gold", 0);
        bestTimeSec = store.getInt("bestTime", 0);
        bestKills = store.getInt("bestKills", 0);
        musicVol = Math.max(0, Math.min(VOL_MAX, store.getInt("volMusic", 2)));
        sfxVol = Math.max(0, Math.min(VOL_MAX, store.getInt("volSfx", 3)));
        lighting = Math.max(0, Math.min(LIGHT_FULL, store.getInt("lighting", LIGHT_FULL)));
        unlockAll = store.getInt("unlockAll", 0) != 0;
        heroIdx = Math.max(0, Math.min(Math.max(0, data.heroes.size() - 1), store.getInt("hero", 0)));
        for (String k : data.powerUps.keySet()) ranks.put(k, store.getInt("pu_" + k, 0));
        rerollCharges = Math.max(0, Math.min(REROLL_PRICE.length, store.getInt("chargeReroll", 0))); // v1.5 B4
        banishCharges = Math.max(0, Math.min(BANISH_PRICE.length, store.getInt("chargeBanish", 0)));
        records = new Records(store);
        loadTints();
        ach = store.getInt("ach", 0);
    }

    // ------------------------------------------------------------------ v1.5 B4: refunds, charges, ascension, tints, achievements

    /** Gold prices of the permanent level-up charges: rank n+1 costs entry n. Up to 5 rerolls and 3 banishes per run. */
    public static final int[] REROLL_PRICE = {500, 1000, 2000, 4000, 8000};
    public static final int[] BANISH_PRICE = {1500, 3000, 6000};
    /** Price of one cosmetic tint; {@link #TINTS} tints per hero. */
    public static final int TINT_PRICE = 800, TINTS = 4;
    private int rerollCharges, banishCharges;
    /** Counts purchases and refunds; the total-spent counter is recomputed only when it moves. */
    public volatile int shopRev = 1;
    private int spentRev, spentCache;
    /** Best time, kills and fastest win per (stage, hero). */
    public final Records records;

    public int rerollCharges() {
        return rerollCharges;
    }

    public int banishCharges() {
        return banishCharges;
    }

    /** Price of the next reroll charge, or 0 when all are bought. */
    public int nextRerollPrice() {
        return rerollCharges >= REROLL_PRICE.length ? 0 : REROLL_PRICE[rerollCharges];
    }

    public int nextBanishPrice() {
        return banishCharges >= BANISH_PRICE.length ? 0 : BANISH_PRICE[banishCharges];
    }

    public boolean buyReroll() {
        int p = nextRerollPrice();
        if (p == 0 || gold < p) return false;
        spend(p);
        store.putInt("chargeReroll", ++rerollCharges);
        shopRev++;
        return true;
    }

    public boolean buyBanish() {
        int p = nextBanishPrice();
        if (p == 0 || gold < p) return false;
        spend(p);
        store.putInt("chargeBanish", ++banishCharges);
        shopRev++;
        return true;
    }

    private void spend(int p) {
        gold -= p;
        store.putInt("gold", gold);
    }

    /** Takes back the last reroll charge for what it cost (100%); returns the gold given back, 0 when none is owned. */
    public int refundReroll() {
        if (rerollCharges == 0) return 0;
        int p = REROLL_PRICE[--rerollCharges];
        store.putInt("chargeReroll", rerollCharges);
        shopRev++;
        addGold(p);
        return p;
    }

    public int refundBanish() {
        if (banishCharges == 0) return 0;
        int p = BANISH_PRICE[--banishCharges];
        store.putInt("chargeBanish", banishCharges);
        shopRev++;
        addGold(p);
        return p;
    }

    /** Takes back the last rank of a power-up for what it cost (100%, the price of that rank); returns the gold given back, 0 when rank 0. */
    public int refund(String key) {
        PowerUpDef d = data.powerUps.get(key);
        int r = rank(key);
        if (d == null || r == 0) return 0;
        int p = d.priceForNext(r - 1);
        ranks.put(key, r - 1);
        shopRev++;
        store.putInt("pu_" + key, r - 1);
        addGold(p);
        return p;
    }

    /** What refunding the last rank of a power-up would pay (0 at rank 0). */
    public int refundValue(String key) {
        PowerUpDef d = data.powerUps.get(key);
        int r = rank(key);
        return d == null || r == 0 ? 0 : d.priceForNext(r - 1);
    }

    /** Gold currently invested in permanent purchases (ranks, charges, bought tints): the total-spent counter. Refunds lower it. */
    public int totalSpent() {
        if (spentRev == shopRev) return spentCache;
        spentRev = shopRev;
        int sum = 0;
        for (PowerUpDef d : data.powerUps.values()) for (int i = 0; i < rank(d.key); i++) sum += d.priceForNext(i);
        for (int i = 0; i < rerollCharges; i++) sum += REROLL_PRICE[i];
        for (int i = 0; i < banishCharges; i++) sum += BANISH_PRICE[i];
        for (int h = 0; h < tintBuy.length; h++) sum += Integer.bitCount(tintBuy[h]) * TINT_PRICE;
        return spentCache = sum;
    }

    // ---- cosmetic tints: four per hero, bought or won, one worn at a time

    private static final int[][] TINT_COLORS = {
            {0xFFFF7A3C, 0xFF6FD0FF, 0xFF6FE06F, 0xFFB070FF},
            {0xFFE0383C, 0xFFFFD84A, 0xFF9FE8FF, 0xFFB8B0A4},
            {0xFF8CE84A, 0xFFFF7AC8, 0xFF4A9CFF, 0xFFE8E0C8}};
    private static final String[][] TINT_NAMES = {
            {"Ember", "Frost", "Verdant", "Void"}, {"Crimson", "Sun", "Glacier", "Ash"}, {"Toxic", "Rose", "Azure", "Bone"}};

    private static int heroSlot(String hero) {
        for (int i = 0; i < GameData.HERO_ORDER.length; i++) if (GameData.HERO_ORDER[i].equals(hero)) return i;
        return -1;
    }

    /** Colour (ARGB) of tint {@code i} (0..3) of a hero. */
    public static int tintColor(String hero, int i) {
        return TINT_COLORS[Math.max(0, heroSlot(hero))][Math.max(0, Math.min(TINTS - 1, i))];
    }

    public static String tintName(String hero, int i) {
        return TINT_NAMES[Math.max(0, heroSlot(hero))][Math.max(0, Math.min(TINTS - 1, i))];
    }

    // Tint state per hero slot ({@link GameData#HERO_ORDER}), read once: the shop asks every frame.
    private final int[] tintBuy = new int[GameData.HERO_ORDER.length], tintAch = new int[GameData.HERO_ORDER.length],
            tintWear = new int[GameData.HERO_ORDER.length];

    private void loadTints() {
        for (int i = 0; i < tintBuy.length; i++) {
            String h = GameData.HERO_ORDER[i];
            tintBuy[i] = store.getInt("tintBuy_" + h, 0);
            tintAch[i] = store.getInt("tintAch_" + h, 0);
            tintWear[i] = store.getInt("tint_" + h, 0);
        }
    }

    private void saveTints(int h) {
        String k = GameData.HERO_ORDER[h];
        store.putInt("tintBuy_" + k, tintBuy[h]);
        store.putInt("tintAch_" + k, tintAch[h]);
        store.putInt("tint_" + k, tintWear[h]);
        shopRev++;
    }

    /** True when the tint is owned (bought or granted by an achievement). */
    public boolean tintOwned(String hero, int i) {
        int h = heroSlot(hero);
        return h >= 0 && ((tintBuy[h] | tintAch[h]) >> i & 1) != 0;
    }

    /** Owned by purchase (and so refundable). */
    public boolean tintBought(String hero, int i) {
        int h = heroSlot(hero);
        return h >= 0 && (tintBuy[h] >> i & 1) != 0;
    }

    public boolean buyTint(String hero, int i) {
        int h = heroSlot(hero);
        if (h < 0 || i < 0 || i >= TINTS || tintOwned(hero, i) || gold < TINT_PRICE) return false;
        spend(TINT_PRICE);
        tintBuy[h] |= 1 << i;
        saveTints(h);
        return true;
    }

    /** Gives the tint back for 800 gold; an achievement tint cannot be refunded. Unequips it when worn. */
    public int refundTint(String hero, int i) {
        int h = heroSlot(hero);
        if (h < 0 || !tintBought(hero, i)) return 0;
        tintBuy[h] &= ~(1 << i);
        if (!tintOwned(hero, i) && tintWear[h] == i + 1) tintWear[h] = 0;
        saveTints(h);
        addGold(TINT_PRICE);
        return TINT_PRICE;
    }

    void grantTint(String hero, int i) {
        int h = heroSlot(hero);
        if (h < 0) return;
        tintAch[h] |= 1 << i;
        saveTints(h);
    }

    /** Wears tint i of the hero (an owned one), or none again when it is the worn one already (toggle). */
    public boolean wearTint(String hero, int i) {
        if (!tintOwned(hero, i)) return false;
        int h = heroSlot(hero);
        tintWear[h] = tintWear[h] == i + 1 ? 0 : i + 1;
        saveTints(h);
        return true;
    }

    /** Worn tint of the hero as ARGB, or 0 for none. {@code WorldRenderer} reads this through one getter (B1). */
    public int tint(String hero) {
        int w = wornTint(hero);
        return w < 0 ? 0 : tintColor(hero, w);
    }

    /** Index (0..3) of the worn tint, or -1. */
    public int wornTint(String hero) {
        int h = heroSlot(hero);
        int w = h < 0 ? 0 : tintWear[h];
        return w > 0 && tintOwned(hero, w - 1) ? w - 1 : -1;
    }

    // ---- ascension: tiers 1..5 per stage, each opened by winning the stage on the tier below

    private final Map<String, Integer> ascWonMap = new HashMap<>();

    /** Highest tier won on the stage, or -1 (a save from before the tiers that has the stage cleared counts as a win on tier 0). */
    public int ascWon(String stage) {
        Integer v = ascWonMap.get(stage);
        if (v == null) {
            int st = store.getInt("asc_" + stage, 0) - 1;
            v = st >= 0 ? st : cleared(stage) ? 0 : -1;
            ascWonMap.put(stage, v);
        }
        return v;
    }

    /** Highest tier the stage can be played on: 0 until it has been won, then one above the highest tier won, at most 5. */
    public int ascMax(String stage) {
        int w = ascWon(stage);
        return w < 0 ? 0 : Math.min(RunMods.MAX_TIER, w + 1);
    }

    /** A win of the stage on {@code tier}: opens the next tier. */
    public void recordAscWin(String stage, int tier) {
        if (stage == null) return;
        if (tier > ascWon(stage)) {
            store.putInt("asc_" + stage, tier + 1);
            ascWonMap.put(stage, tier);
        }
        stageRev++;
    }

    // ---- daily challenge: the x1.5 gold is paid once per date

    public boolean dailyBonusOpen(int date) {
        return store.getInt("dailyClaim", 0) != date;
    }

    public void claimDaily(int date) {
        store.putInt("dailyClaim", date);
    }

    // ---- achievements and lifetime totals (the unlock bits are in the save as {@code ach})

    private int ach;

    public boolean achDone(int i) {
        return (ach >> i & 1) != 0;
    }

    public int achCount() {
        return Integer.bitCount(ach);
    }

    /** Marks the achievement and pays its reward; true the first time only, so a reward is claimed once. */
    boolean achClaim(int i, int reward) {
        if (achDone(i)) return false;
        ach |= 1 << i;
        store.putInt("ach", ach);
        addGold(reward);
        return true;
    }

    /** True when every power-up of the shop has at least one rank. */
    public boolean allPowerUpsOwned() {
        for (String k : data.powerUps.keySet()) if (rank(k) == 0) return false;
        return !data.powerUps.isEmpty();
    }

    public int stat(String name) {
        return store.getInt("stat_" + name, 0);
    }

    void addStat(String name, int v) {
        store.putInt("stat_" + name, (int) Math.min(Integer.MAX_VALUE, (long) stat(name) + v));
    }

    public int rank(String key) {
        Integer r = ranks.get(key);
        return r == null ? 0 : r;
    }

    public int nextPrice(String key) {
        PowerUpDef d = data.powerUps.get(key);
        return d == null ? 0 : d.priceForNext(rank(key));
    }

    public boolean isMaxed(String key) {
        PowerUpDef d = data.powerUps.get(key);
        return d == null || rank(key) >= d.maxRank();
    }

    public boolean buy(String key) {
        PowerUpDef d = data.powerUps.get(key);
        if (d == null || isMaxed(key)) return false;
        int price = nextPrice(key);
        if (gold < price) return false;
        gold -= price;
        ranks.put(key, rank(key) + 1);
        shopRev++;
        store.putInt("gold", gold);
        store.putInt("pu_" + key, rank(key));
        return true;
    }

    /** Sum of all owned power-up stats, keyed by stat name. */
    public Map<String, Double> bonuses() {
        Map<String, Double> sum = new HashMap<>();
        for (PowerUpDef d : data.powerUps.values()) {
            for (Map.Entry<String, Double> e : d.statsAt(rank(d.key)).entrySet()) {
                Double p = sum.get(e.getKey());
                sum.put(e.getKey(), p == null ? e.getValue() : p + e.getValue());
            }
        }
        return sum;
    }

    public void addGold(int g) {
        gold += g;
        store.putInt("gold", gold);
    }

    /** Best survival time on one stage (0 = none yet; saves from before the stage cards only have the overall best). */
    public int bestTimeOf(String stageKey) {
        Integer v = stageBest.get(stageKey);
        if (v == null) {
            v = store.getInt("bestTime_" + stageKey, 0);
            stageBest.put(stageKey, v);
        }
        return v;
    }

    /** {@link #recordRun(int, int)} that also keeps the record of the stage played. */
    public void recordRun(int timeSec, int kills, String stageKey) {
        recordRun(timeSec, kills);
        if (stageKey != null && timeSec > bestTimeOf(stageKey)) {
            stageBest.put(stageKey, timeSec);
            store.putInt("bestTime_" + stageKey, timeSec);
            stageRev++;
        }
    }

    /** True when the stage was cleared (the 30:00 survived or its finale killed): the star on its card. Flag {@code cleared_KEY}. */
    public boolean cleared(String stageKey) {
        Boolean v = stageCleared.get(stageKey);
        if (v == null) {
            v = store.getInt("cleared_" + stageKey, 0) != 0;
            stageCleared.put(stageKey, v);
        }
        return v;
    }

    public void markCleared(String stageKey) {
        if (stageKey == null || cleared(stageKey)) return;
        stageCleared.put(stageKey, true);
        ascWonMap.remove(stageKey);
        store.putInt("cleared_" + stageKey, 1);
        stageRev++;
    }

    /**
     * Whether a stage can be played: it has no unlock rule, or the stage that opens it was survived for the required time
     * ({@link #bestTimeOf}) or cleared. Derived from the records, so no save key of its own.
     */
    public boolean unlocked(String stageKey) {
        if (unlockAll) return true;
        se.felixarvid.thorsurvivors.data.StageDef d = data.stages.get(stageKey);
        if (d == null || d.unlockKey == null) return true;
        return bestTimeOf(d.unlockKey) >= d.unlockSec || cleared(d.unlockKey);
    }

    /**
     * The line on a locked card, e.g. "Reach 15:00 on Deep Mines (best 11:20)"; "" for an open stage. Built once per record
     * change and then handed out as the same String, so drawing it allocates nothing.
     */
    public String unlockLine(String stageKey) {
        se.felixarvid.thorsurvivors.data.StageDef d = data.stages.get(stageKey);
        if (d == null || d.unlockKey == null) return "";
        String line = unlockLines.get(stageKey);
        if (line == null || stageRev != unlockRev) {
            if (stageRev != unlockRev) {
                unlockLines.clear();
                unlockRev = stageRev;
            }
            se.felixarvid.thorsurvivors.data.StageDef from = data.stages.get(d.unlockKey);
            line = "Reach " + clock(d.unlockSec) + " on " + (from == null ? d.unlockKey : from.name) + " (best " + clock(bestTimeOf(d.unlockKey)) + ")";
            unlockLines.put(stageKey, line);
        }
        return line;
    }

    private int unlockRev;

    private static String clock(int sec) {
        int m = sec / 60, s = sec % 60;
        return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }

    public void recordRun(int timeSec, int kills) {
        if (timeSec > bestTimeSec) bestTimeSec = timeSec;
        if (kills > bestKills) bestKills = kills;
        store.putInt("bestTime", bestTimeSec);
        store.putInt("bestKills", bestKills);
    }

    /** Steps the music volume by delta (wrapping) and stores it. */
    public void stepMusic(int delta) {
        musicVol = (musicVol + delta + VOL_MAX + 1) % (VOL_MAX + 1);
        store.putInt("volMusic", musicVol);
    }

    public void stepSfx(int delta) {
        sfxVol = (sfxVol + delta + VOL_MAX + 1) % (VOL_MAX + 1);
        store.putInt("volSfx", sfxVol);
    }

    /** Key of the selected hero, or null when the data defines none. */
    public String heroKey() {
        if (heroKeys == null) heroKeys = data.heroKeys().toArray(new String[0]); // once: this is read every frame
        return heroKeys.length == 0 ? null : heroKeys[Math.min(heroIdx, heroKeys.length - 1)];
    }

    /** Steps the selected hero by delta (wrapping) and stores it. */
    public void stepHero(int delta) {
        int n = data.heroes.size();
        if (n == 0) return;
        heroIdx = (heroIdx + delta % n + n) % n;
        store.putInt("hero", heroIdx);
    }

    /** Steps the lighting quality by delta (wrapping) and stores it. */
    public void stepLighting(int delta) {
        lighting = (lighting + delta + LIGHT_FULL + 1) % (LIGHT_FULL + 1);
        store.putInt("lighting", lighting);
    }
}
