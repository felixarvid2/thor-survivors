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
