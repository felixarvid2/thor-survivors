package se.felixarvid.thorsurvivors.game;

/**
 * v1.5 B4: the best time, the most kills and the fastest win per (stage, hero). {@link #submit} stores a run and sets
 * {@link #wasRecord()} for the results screen ("NEW RECORD"); the flag belongs to the last run submitted. Kept in the save as
 * {@code rec_<STAGE>_<HERO>_t / _k / _w} (the win time is stored +1 so that 0 means none).
 */
public final class Records {
    private final MetaProgress.Store store;
    private boolean record, recTime, recKills, recWin;

    Records(MetaProgress.Store store) {
        this.store = store;
    }

    private static String key(String stage, String hero, String what) {
        return "rec_" + stage + "_" + (hero == null ? "NONE" : hero) + "_" + what;
    }

    public int bestTime(String stage, String hero) {
        return store.getInt(key(stage, hero, "t"), 0);
    }

    public int bestKills(String stage, String hero) {
        return store.getInt(key(stage, hero, "k"), 0);
    }

    /** Fastest win in seconds, or 0 when the pair was never won. */
    public int fastestWin(String stage, String hero) {
        int v = store.getInt(key(stage, hero, "w"), 0);
        return v <= 0 ? 0 : v - 1;
    }

    /** Stores a finished run; true when it beat the best time, the kills or the fastest win of its (stage, hero). The first run is always a record. */
    public boolean submit(String stage, String hero, int timeSec, int kills, boolean win) {
        recTime = timeSec > bestTime(stage, hero);
        recKills = kills > bestKills(stage, hero);
        int fw = fastestWin(stage, hero);
        recWin = win && (fw == 0 || timeSec < fw);
        if (recTime) store.putInt(key(stage, hero, "t"), timeSec);
        if (recKills) store.putInt(key(stage, hero, "k"), kills);
        if (recWin) store.putInt(key(stage, hero, "w"), timeSec + 1);
        record = recTime || recKills || recWin;
        return record;
    }

    /** True when the last submitted run set any record (read by the results screen). */
    public boolean wasRecord() {
        return record;
    }

    public boolean wasTimeRecord() {
        return recTime;
    }

    public boolean wasKillsRecord() {
        return recKills;
    }

    public boolean wasWinRecord() {
        return recWin;
    }
}
