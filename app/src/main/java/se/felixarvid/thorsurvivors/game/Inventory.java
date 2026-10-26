package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.WeaponDef;

import java.util.Map;

/** The run's weapon and passive slots (max 6 + 6) and the evolution rules. */
public final class Inventory {
    public static final int MAX_WEAPONS = 6;
    public static final int MAX_PASSIVES = 6;

    public static final class Slot {
        public WeaponDef def;
        public int level;
    }

    public final Slot[] weapons = new Slot[MAX_WEAPONS];
    public final Slot[] passives = new Slot[MAX_PASSIVES];
    public int weaponCount;
    public int passiveCount;

    public Inventory() {
        for (int i = 0; i < MAX_WEAPONS; i++) weapons[i] = new Slot();
        for (int i = 0; i < MAX_PASSIVES; i++) passives[i] = new Slot();
    }

    public int weaponLevel(String key) {
        for (int i = 0; i < weaponCount; i++) if (weapons[i].def.key.equals(key)) return weapons[i].level;
        return 0;
    }

    public int passiveLevel(String key) {
        for (int i = 0; i < passiveCount; i++) if (passives[i].def.key.equals(key)) return passives[i].level;
        return 0;
    }

    public int levelOf(String key) {
        int l = weaponLevel(key);
        return l > 0 ? l : passiveLevel(key);
    }

    public boolean hasPassive(String key) {
        return passiveLevel(key) > 0;
    }

    /** Adds a new weapon/passive at level 1; returns false when full or already owned. */
    public boolean add(WeaponDef def) {
        if (levelOf(def.key) > 0) return false;
        if (def.powerUp) {
            if (passiveCount >= MAX_PASSIVES) return false;
            passives[passiveCount].def = def;
            passives[passiveCount].level = 1;
            passiveCount++;
        } else {
            if (weaponCount >= MAX_WEAPONS) return false;
            weapons[weaponCount].def = def;
            weapons[weaponCount].level = 1;
            weaponCount++;
        }
        return true;
    }

    /** Raises an owned weapon/passive by one level (up to its max); returns the new level or 0. */
    public int levelUp(String key) {
        for (int i = 0; i < weaponCount; i++) {
            Slot s = weapons[i];
            if (s.def.key.equals(key) && s.level < s.def.maxLevel()) return ++s.level;
        }
        for (int i = 0; i < passiveCount; i++) {
            Slot s = passives[i];
            if (s.def.key.equals(key) && s.level < s.def.maxLevel()) return ++s.level;
        }
        return 0;
    }

    /** True if the weapon in this slot is at max level and owns one of its synergy passives. */
    public boolean canEvolve(int slot, Map<String, WeaponDef> defs) {
        if (slot < 0 || slot >= weaponCount) return false;
        Slot s = weapons[slot];
        WeaponDef d = s.def;
        if (d.evolution || d.evoInto == null || s.level < d.maxLevel()) return false;
        WeaponDef target = defs.get(d.evoInto);
        if (target == null || !target.evolution) return false;
        for (String p : d.evoSynergy) if (hasPassive(p)) return true;
        return false;
    }

    /** First slot that can evolve now, or -1. */
    public int firstEvolvable(Map<String, WeaponDef> defs) {
        for (int i = 0; i < weaponCount; i++) if (canEvolve(i, defs)) return i;
        return -1;
    }

    /** Replaces the weapon in the slot with its evolved form. Returns the new def or null. */
    public WeaponDef evolve(int slot, Map<String, WeaponDef> defs) {
        if (!canEvolve(slot, defs)) return null;
        WeaponDef target = defs.get(weapons[slot].def.evoInto);
        weapons[slot].def = target;
        weapons[slot].level = 1;
        return target;
    }

    // ------------------------------------------------------------------ fusions (design v1.3 section 1)

    /** Set by {@link #canFuse}: the two slots of the pair it found (a < b). */
    public int fuseA = -1, fuseB = -1;

    /** True when the weapon in the slot is the base weapon {@code baseKey} at max level, or its evolved form. */
    public boolean mastered(int slot, String baseKey, Map<String, WeaponDef> defs) {
        if (slot < 0 || slot >= weaponCount) return false;
        Slot s = weapons[slot];
        WeaponDef d = s.def;
        if (d.fusion) return false; // a fusion is never a parent
        if (d.key.equals(baseKey)) return s.level >= d.maxLevel();
        if (!d.evolution) return false;
        WeaponDef base = defs.get(baseKey);
        return base != null && d.key.equals(base.evoInto);
    }

    /** True when the slot holds an evolved weapon (counts +1 start level when it is fused). */
    private boolean evolvedSlot(int slot) {
        return weapons[slot].def.evolution;
    }

    /**
     * Checks whether two different slots hold the two parents of {@code f}, each mastered (max level or evolved). On
     * success {@link #fuseA} and {@link #fuseB} are the slots (a < b). A fusion already owned cannot be made again.
     */
    public boolean canFuse(WeaponDef f, Map<String, WeaponDef> defs) {
        if (f == null || !f.fusion || f.parents.size() < 2 || weaponLevel(f.key) > 0) return false;
        String p0 = f.parents.get(0), p1 = f.parents.get(1);
        for (int i = 0; i < weaponCount; i++) {
            if (!mastered(i, p0, defs)) continue;
            for (int j = 0; j < weaponCount; j++) {
                if (j == i || !mastered(j, p1, defs)) continue;
                fuseA = Math.min(i, j);
                fuseB = Math.max(i, j);
                return true;
            }
        }
        return false;
    }

    /** The first fusion (in data order) that can be made now, with {@link #fuseA}/{@link #fuseB} set, or null. */
    public WeaponDef readyFusion(WeaponDef[] fusions, Map<String, WeaponDef> defs) {
        for (WeaponDef f : fusions) if (canFuse(f, defs)) return f;
        return null;
    }

    /**
     * Fuses the pair of {@code f}: the fusion takes the lower slot, the higher one is freed (later slots move up), and the
     * fusion starts at level 1 plus one for every parent that was already evolved. Returns the level, or 0 when not possible.
     */
    public int fuse(WeaponDef f, Map<String, WeaponDef> defs) {
        if (!canFuse(f, defs)) return 0;
        int a = fuseA, b = fuseB;
        int level = Math.min(f.maxLevel(), 1 + (evolvedSlot(a) ? 1 : 0) + (evolvedSlot(b) ? 1 : 0));
        weapons[a].def = f;
        weapons[a].level = level;
        Slot freed = weapons[b];
        for (int i = b; i < weaponCount - 1; i++) weapons[i] = weapons[i + 1];
        weapons[weaponCount - 1] = freed;
        freed.def = null;
        freed.level = 0;
        weaponCount--;
        return level;
    }
}
