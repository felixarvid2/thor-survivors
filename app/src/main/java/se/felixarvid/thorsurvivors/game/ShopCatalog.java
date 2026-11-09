package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.GameData;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.5 B4: what the salvage terminal lists, in four categories (Stats = the permanent power-ups, Run = the level-up charges and the
 * ascension and daily status, Cosmetics = hero tints, Awards = the achievements and their rewards). Each category starts on a new page
 * (2 columns x 4 rows). Built once in {@link Engine#init}; the entries are plain data, the panel reads their state from {@link MetaProgress}.
 */
public final class ShopCatalog {
    public static final int STATS = 0, RUN = 1, STYLE = 2, AWARDS = 3, CATS = 4;
    public static final String[] CAT_NAME = {"STATS", "RUN", "COSMETICS", "AWARDS"};
    public static final int K_POWER = 0, K_REROLL = 1, K_BANISH = 2, K_ASC = 3, K_DAILY = 4, K_TINT = 5, K_ACH = 6;

    public static final class Entry {
        public final int cat, kind, idx;
        /** Power-up key, hero key, stage key or "" . */
        public final String key;

        Entry(int cat, int kind, String key, int idx) {
            this.cat = cat;
            this.kind = kind;
            this.key = key;
            this.idx = idx;
        }
    }

    public final List<Entry> entries = new ArrayList<>();
    /** First entry of each page and the category of the page. */
    public int[] pageStart = new int[0], pageLen = new int[0], pageCat = new int[0];
    public final int[] catFirstPage = new int[CATS];

    public ShopCatalog(GameData d, List<String> stageKeys) {
        for (String k : d.powerUps.keySet()) entries.add(new Entry(STATS, K_POWER, k, 0));
        mark(STATS);
        entries.add(new Entry(RUN, K_REROLL, "REROLL", 0));
        entries.add(new Entry(RUN, K_BANISH, "BANISH", 0));
        for (String s : stageKeys) entries.add(new Entry(RUN, K_ASC, s, 0));
        entries.add(new Entry(RUN, K_DAILY, "DAILY", 0));
        mark(RUN);
        for (String h : d.heroKeys()) for (int i = 0; i < MetaProgress.TINTS; i++) entries.add(new Entry(STYLE, K_TINT, h, i));
        mark(STYLE);
        for (int i = 0; i < Achievements.COUNT; i++) entries.add(new Entry(AWARDS, K_ACH, "", i));
        mark(AWARDS);
        buildPages();
    }

    private final int[] catEnd = new int[CATS];

    private void mark(int cat) {
        catEnd[cat] = entries.size();
    }

    private void buildPages() {
        List<int[]> pages = new ArrayList<>();
        int start = 0;
        for (int c = 0; c < CATS; c++) {
            catFirstPage[c] = pages.size();
            for (int s = start; s < catEnd[c]; s += Engine.SHOP_PAGE) pages.add(new int[]{s, Math.min(Engine.SHOP_PAGE, catEnd[c] - s), c});
            start = catEnd[c];
        }
        pageStart = new int[pages.size()];
        pageLen = new int[pages.size()];
        pageCat = new int[pages.size()];
        for (int i = 0; i < pages.size(); i++) {
            pageStart[i] = pages.get(i)[0];
            pageLen[i] = pages.get(i)[1];
            pageCat[i] = pages.get(i)[2];
        }
    }

    public int pages() {
        return pageStart.length;
    }

    /** The page an entry is on. */
    public int pageOf(int entry) {
        for (int p = pageStart.length - 1; p >= 0; p--) if (entry >= pageStart[p]) return p;
        return 0;
    }
}
