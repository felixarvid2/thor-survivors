package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.StageDef;
import se.felixarvid.thorsurvivors.data.WaveRow;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * v1.5 B3 (item 25): the bestiary and codex. Three lists (enemies met on the menu stages, weapons, passives). An entry unlocks the
 * first time the hero meets the enemy or owns the weapon or passive, and remembers the stage it was first met on. One store key
 * per entry ({@code cdx_E_KEY}, {@code cdx_W_KEY}, {@code cdx_P_KEY}; value = first stage index + 1) rather than positions in a bit
 * array, so content added by later versions cannot shift the saved flags. {@link #scan} runs twice a second and allocates nothing.
 */
public final class Codex {
    public static final int ENEMY = 0, WEAPON = 1, PASSIVE = 2, KINDS = 3;
    public static final String[] TITLES = {"BESTIARY", "WEAPONS", "PASSIVES"};
    private static final String[] PREFIX = {"cdx_E_", "cdx_W_", "cdx_P_"};

    private final String[][] keys = new String[KINDS][];
    private final int[][] firstStage = new int[KINDS][]; // 0 = unseen, else stage index + 1
    private final List<Map<String, Integer>> index = new ArrayList<>();
    private MetaProgress.Store store;
    private final List<String> stageKeys;

    public Codex(GameData d, List<String> stageKeys) {
        this.stageKeys = stageKeys;
        LinkedHashSet<String> en = new LinkedHashSet<>();
        for (String sk : stageKeys) {
            StageDef sd = d.stages.get(sk);
            if (sd == null) continue;
            for (WaveRow r : sd.rows) {
                for (String e : r.enemies) if (d.enemies.containsKey(e)) en.add(e);
                for (String e : r.bosses) if (d.enemies.containsKey(e)) en.add(e);
            }
            for (String e : sd.timedBoss) if (d.enemies.containsKey(e)) en.add(e);
            for (String h : sd.hordeTypes) if (d.enemies.containsKey(h)) en.add(h);
        }
        List<String> we = new ArrayList<>(), pa = new ArrayList<>();
        for (se.felixarvid.thorsurvivors.data.WeaponDef w : d.weapons.values()) (w.powerUp ? pa : we).add(w.key);
        keys[ENEMY] = en.toArray(new String[0]);
        keys[WEAPON] = we.toArray(new String[0]);
        keys[PASSIVE] = pa.toArray(new String[0]);
        for (int k = 0; k < KINDS; k++) {
            firstStage[k] = new int[keys[k].length];
            Map<String, Integer> m = new HashMap<>();
            for (int i = 0; i < keys[k].length; i++) m.put(keys[k][i], i);
            index.add(m);
        }
    }

    /** Attaches the store and reads the saved flags. */
    public void load(MetaProgress.Store st) {
        store = st;
        if (st == null) return;
        for (int k = 0; k < KINDS; k++) {
            for (int i = 0; i < keys[k].length; i++) firstStage[k][i] = st.getInt(PREFIX[k] + keys[k][i], 0);
        }
    }

    public int size(int kind) {
        return keys[kind].length;
    }

    public String key(int kind, int i) {
        return keys[kind][i];
    }

    public boolean seen(int kind, int i) {
        return firstStage[kind][i] != 0;
    }

    /** Key of the stage the entry was first met on, or null. */
    public String firstStageKey(int kind, int i) {
        int v = firstStage[kind][i];
        return v <= 0 || v > stageKeys.size() ? null : stageKeys.get(v - 1);
    }

    public int seenCount(int kind) {
        int n = 0;
        for (int v : firstStage[kind]) if (v != 0) n++;
        return n;
    }

    public int seenCount() {
        int n = 0;
        for (int k = 0; k < KINDS; k++) n += seenCount(k);
        return n;
    }

    public int total() {
        int n = 0;
        for (int k = 0; k < KINDS; k++) n += keys[k].length;
        return n;
    }

    /** Unlocks an entry; true when it was new. {@code stageIdx} is the index in the menu's stage list (-1 for a stage outside it). */
    public boolean mark(int kind, String key, int stageIdx) {
        Integer i = index.get(kind).get(key);
        if (i == null || firstStage[kind][i] != 0) return false;
        int v = stageIdx >= 0 && stageIdx < stageKeys.size() ? stageIdx + 1 : stageKeys.size() + 1; // a legacy stage: seen, no stage name
        firstStage[kind][i] = v;
        if (store != null) store.putInt(PREFIX[kind] + key, v);
        return true;
    }

    /** Looks at the live run: every enemy on the field and every weapon and passive in the inventory. Returns the number of new entries. */
    public int scan(Sim s, int stageIdx) {
        int n = 0;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (!e.dead && !e.dormant && !e.silent && mark(ENEMY, e.type.def.key, stageIdx)) n++;
        }
        Inventory inv = s.inv;
        for (int i = 0; i < inv.weaponCount; i++) if (mark(WEAPON, inv.weapons[i].def.key, stageIdx)) n++;
        for (int i = 0; i < inv.passiveCount; i++) if (mark(PASSIVE, inv.passives[i].def.key, stageIdx)) n++;
        return n;
    }
}
