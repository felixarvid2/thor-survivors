package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.PowerUpDef;
import se.felixarvid.thorsurvivors.data.WeaponDef;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Human-readable text for level-up cards, in the Ashfall tone: short, flat, no jokes. */
public final class Describe {
    private Describe() {
    }

    public static String offer(Offer o) {
        switch (o.kind) {
            case Offer.HEAL:
                return "Field repair. Restores 30 HP.";
            case Offer.GOLD:
                return "Salvage haul. +25 gold.";
            case Offer.BRANCH:
                return o.branch.tiers[o.newLevel - 1].description;
            case Offer.FUSE:
                return o.def.description + " Frees a weapon slot.";
            case Offer.NEW_WEAPON:
            case Offer.NEW_PASSIVE:
            case Offer.PASSIVE_LEVEL:
                return o.def.description;
            default:
                return delta(o.def, o.newLevel);
        }
    }

    /** Text for the stats a weapon gains when it reaches `level`. */
    public static String delta(WeaponDef d, int level) {
        Map<String, Double> row = new TreeMap<>(d.deltaFor(level));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e : row.entrySet()) {
            String s = one(e.getKey(), e.getValue());
            if (s == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.length() == 0 ? "Upgrade installed." : sb.toString();
    }

    private static String one(String k, double v) {
        switch (k) {
            case "amount":
                return sign(v, 0) + " round" + (Math.abs(v) > 1 ? "s" : "") + " per volley";
            case "power":
                return "Damage " + sign(v * 10, 0);
            case "area":
                return "Area " + sign(v * 100, 0) + "%";
            case "speed":
                return "Speed " + sign(v * 100, 0) + "%";
            case "interval":
                return "Cooldown " + sign(v / 1000.0, 1) + "s";
            case "duration":
                return "Duration " + sign(v / 1000.0, 1) + "s";
            case "penetrating":
                return "Pierce " + sign(v, 0);
            case "knockback":
                return "Knockback " + sign(v * 100, 0) + "%";
            case "critChance":
                return "Crit " + sign(v * 100, 0) + "%";
            case "critMul":
                return "Crit damage " + sign(v * 100, 0) + "%";
            default:
                return null;
        }
    }

    private static String sign(double v, int decimals) {
        String body = decimals == 0 ? Long.toString(Math.round(Math.abs(v)))
                : String.format(java.util.Locale.US, "%." + decimals + "f", Math.abs(v));
        return (v < 0 ? "-" : "+") + body;
    }

    /** Shop texts of one power-up, built once at load: the per-rank line and the NOW/NEXT line for every rank. */
    public static final class PowerUpText {
        public final String perRank;
        /** Index = owned rank (0..maxRank). */
        public final String[] totals;

        PowerUpText(String perRank, String[] totals) {
            this.perRank = perRank;
            this.totals = totals;
        }
    }

    public static PowerUpText powerUp(PowerUpDef d) {
        int mr = d.maxRank();
        String per = statLine(d, d.statsAt(1), true);
        if (mr > 1) per = per + " / RANK";
        String[] tot = new String[mr + 1];
        for (int r = 0; r <= mr; r++) {
            String now = r == 0 ? "-" : statLine(d, d.statsAt(r), false);
            if (r == mr) tot[r] = "MAXED: " + now;
            else tot[r] = "NOW " + now + "  NEXT " + statLine(d, d.statsAt(r + 1), false);
        }
        return new PowerUpText(per, tot);
    }

    private static String statLine(PowerUpDef d, Map<String, Double> stats, boolean label) {
        return statLine(d.description, stats, label);
    }

    private static String statLine(String fallback, Map<String, Double> stats, boolean label) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e : new TreeMap<>(stats).entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(puStat(e.getKey(), e.getValue(), label));
        }
        return sb.length() == 0 ? fallback : sb.toString();
    }

    private static String puStat(String k, double v, boolean label) {
        switch (k) {
            case "power": return pct(v, label, "DAMAGE");
            case "armor": return sign(v, 0).replace('+', '-') + (label ? " DAMAGE TAKEN" : "");
            case "maxHp": return pct(v, label, "MAX HP");
            case "regen": return sign(v, 1) + " HP/S";
            case "cooldown": return pct(v, label, "COOLDOWN");
            case "area": return pct(v, label, "AREA");
            case "speed": return pct(v, label, "PROJECTILE SPEED");
            case "duration": return pct(v, label, "DURATION");
            case "amount": return sign(v, 0) + (label ? " PROJECTILE" + (Math.abs(v) > 1 ? "S" : "") : "");
            case "moveSpeed": return pct(v, label, "MOVE SPEED");
            case "magnet": return pct(v, label, "PICKUP RANGE");
            case "luck": return pct(v, label, "LUCK");
            case "growth": return pct(v, label, "XP");
            case "greed": return pct(v, label, "GOLD");
            case "curse": return pct(v, label, "ENEMY HP, SPEED & SPAWNS");
            case "revivals": return label ? "REVIVE ONCE AT 50% HP" : sign(v, 0) + " REVIVE";
            default: return sign(v, 0) + " " + k.toUpperCase(java.util.Locale.US);
        }
    }

    /** "+5% DAMAGE" (label) or "+5%"; one decimal only when the value has one (2.5%). */
    private static String pct(double v, boolean label, String name) {
        double p = v * 100;
        String s = Math.abs(p - Math.rint(p)) < 1e-6 ? sign(p, 0) : sign(p, 1);
        return s + "%" + (label ? " " + name : "");
    }

    // ------------------------------------------------------------------ v1.5 B2 item 16: the stats of the pause screen

    public static final String[] STAT_LABELS = {"MIGHT", "AREA", "COOLDOWN", "MOVE SPEED", "ARMOR", "LUCK", "GROWTH", "GREED", "REVIVAL"};

    /**
     * The run's character stats for the pause screen, in {@link #STAT_LABELS} order: the multipliers as a signed percent against
     * 100 %, armor as a flat number, revivals as the ones still unused. Built when the pause opens, never per frame.
     */
    public static String[] statValues(PlayerStats p, int revivalsLeft) {
        return new String[]{
                pctOf(p.might - 1), pctOf(p.areaMul - 1), pctOf(p.cooldownMul - 1), pctOf(p.moveSpeedMul - 1),
                String.format(Locale.US, "%.0f", p.armor), pctOf(p.luck), pctOf(p.growthMul - 1), pctOf(p.greedMul - 1),
                Integer.toString(Math.max(0, revivalsLeft))};
    }

    private static String pctOf(double v) {
        long r = Math.round(v * 100);
        return (r > 0 ? "+" : "") + r + "%";
    }

    // ------------------------------------------------------------------ v1.5 B2 item 17: the tooltip of an offer card

    /**
     * The two lines under a focused level-up card: what the weapon or passive gives now and at the next level (a new one: what it
     * gives and where it ends). Null for cards that have no such pair (heal, gold, branch, fuse).
     */
    public static String[] tooltip(Offer o, Map<String, WeaponDef> defs) {
        WeaponDef d = o.def;
        switch (o.kind) {
            case Offer.WEAPON_LEVEL: {
                int now = Math.max(1, o.newLevel - 1);
                return new String[]{"NOW  LV " + now + ": " + weaponSummary(d, now), "NEXT  LV " + o.newLevel + ": " + weaponSummary(d, o.newLevel)};
            }
            case Offer.NEW_WEAPON: {
                StringBuilder end = new StringBuilder("MAX  LV " + d.maxLevel() + ": " + weaponSummary(d, d.maxLevel()));
                String with = synergyNames(d, defs);
                if (d.evoInto != null && !with.isEmpty()) end.append("   EVOLVES WITH ").append(with);
                return new String[]{"NEW  LV 1: " + weaponSummary(d, 1), end.toString()};
            }
            case Offer.NEW_PASSIVE:
                return new String[]{"NEW  LV 1: " + statLine(d.description, d.statsAt(1), true),
                        "MAX  LV " + d.maxLevel() + ": " + statLine(d.description, d.statsAt(d.maxLevel()), true)};
            case Offer.PASSIVE_LEVEL: {
                int now = Math.max(1, o.newLevel - 1);
                return new String[]{"NOW  LV " + now + ": " + statLine(d.description, d.statsAt(now), true),
                        "NEXT  LV " + o.newLevel + ": " + statLine(d.description, d.statsAt(o.newLevel), true)};
            }
            default:
                return null;
        }
    }

    /** The absolute numbers of a weapon at a level: damage, cooldown, area, rounds, duration, pierce (at most 4). */
    public static String weaponSummary(WeaponDef d, int level) {
        Map<String, Double> m = d.statsAt(level);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String k : SUMMARY_KEYS) {
            Double v = m.get(k);
            if (v == null || n >= 4) continue;
            if (n++ > 0) sb.append(", ");
            switch (k) {
                case "power": sb.append("Damage ").append(Math.round(v * 10)); break;
                case "interval": sb.append("Cooldown ").append(String.format(Locale.US, "%.1f", v / 1000.0)).append('s'); break;
                case "area": sb.append("Area ").append(Math.round(v * 100)).append('%'); break;
                case "amount": sb.append("Rounds ").append(Math.round(v)); break;
                case "duration": sb.append("Duration ").append(String.format(Locale.US, "%.1f", v / 1000.0)).append('s'); break;
                default: sb.append("Pierce ").append(Math.round(v));
            }
        }
        return sb.length() == 0 ? d.description : sb.toString();
    }

    private static final String[] SUMMARY_KEYS = {"power", "interval", "area", "amount", "duration", "penetrating"};

    /**
     * True when taking this card completes the requirements of an evolution: the last level of a weapon whose passive is already
     * owned, or the passive a maxed weapon is waiting for. (The chest then does the evolving.)
     */
    public static boolean completesEvolution(Offer o, Inventory inv, Map<String, WeaponDef> defs) {
        WeaponDef d = o.def;
        if (d == null) return false;
        if (o.kind == Offer.WEAPON_LEVEL) {
            if (o.newLevel < d.maxLevel() || d.evolution || d.fusion || !evolves(d, defs)) return false;
            for (String p : d.evoSynergy) if (inv.hasPassive(p)) return true;
            return false;
        }
        if (o.kind == Offer.NEW_PASSIVE) {
            for (int i = 0; i < inv.weaponCount; i++) {
                Inventory.Slot w = inv.weapons[i];
                if (w.def.evolution || w.def.fusion || !evolves(w.def, defs) || w.level < w.def.maxLevel()) continue;
                if (w.def.evoSynergy.contains(d.key)) return true;
            }
        }
        return false;
    }

    private static boolean evolves(WeaponDef d, Map<String, WeaponDef> defs) {
        if (d.evoInto == null) return false;
        WeaponDef t = defs.get(d.evoInto);
        return t != null && t.evolution;
    }

    private static String synergyNames(WeaponDef d, Map<String, WeaponDef> defs) {
        StringBuilder sb = new StringBuilder();
        for (String p : d.evoSynergy) {
            WeaponDef pd = defs.get(p);
            if (sb.length() > 0) sb.append(" or ");
            sb.append(pd != null ? pd.name : p);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ v1.5 B2 item 18: the evolution tracker

    /** No evolution (an evolved weapon, a fusion, a weapon without one), level below max, max but the passive missing, ready. */
    public static final int EVO_NONE = 0, EVO_NOT_MAX = 1, EVO_MISSING_PASSIVE = 2, EVO_READY = 3;

    public static int evoState(Inventory inv, int slot, Map<String, WeaponDef> defs) {
        if (slot < 0 || slot >= inv.weaponCount) return EVO_NONE;
        Inventory.Slot w = inv.weapons[slot];
        if (w.def.evolution || w.def.fusion || !evolves(w.def, defs)) return EVO_NONE;
        if (w.level < w.def.maxLevel()) return EVO_NOT_MAX;
        List<String> syn = w.def.evoSynergy; // indexed: this runs every frame in the HUD, an iterator would allocate
        for (int i = 0; i < syn.size(); i++) if (inv.hasPassive(syn.get(i))) return EVO_READY;
        return EVO_MISSING_PASSIVE;
    }

    /** "Lv 6/8, passive missing: Spinach, needs boss chest" (null when the weapon has no evolution). */
    public static String evoLine(Inventory inv, int slot, Map<String, WeaponDef> defs) {
        int st = evoState(inv, slot, defs);
        if (st == EVO_NONE) return null;
        Inventory.Slot w = inv.weapons[slot];
        String lv = "Lv " + w.level + "/" + w.def.maxLevel();
        String names = synergyNames(w.def, defs);
        boolean has = false;
        for (String p : w.def.evoSynergy) if (inv.hasPassive(p)) has = true;
        String pas = has ? ownedName(w.def, inv, defs) + " owned" : "passive missing: " + names;
        return lv + ", " + pas + (st == EVO_READY ? ", ready: needs boss chest" : ", needs boss chest");
    }

    private static String ownedName(WeaponDef d, Inventory inv, Map<String, WeaponDef> defs) {
        for (String p : d.evoSynergy) if (inv.hasPassive(p)) { WeaponDef pd = defs.get(p); return pd != null ? pd.name : p; }
        return "";
    }

    /** The short form beside the build strip: the weapon closest to evolving, or null. "Whip 6/8, needs Spinach". */
    public static String evoCompact(Inventory inv, Map<String, WeaponDef> defs) {
        int best = -1;
        double bestScore = -1;
        for (int i = 0; i < inv.weaponCount; i++) {
            int st = evoState(inv, i, defs);
            if (st == EVO_NONE) continue;
            double score = st * 100 + inv.weapons[i].level; // ready first, then maxed without the passive, then the highest level
            if (score > bestScore) { bestScore = score; best = i; }
        }
        if (best < 0) return null;
        Inventory.Slot w = inv.weapons[best];
        int st = evoState(inv, best, defs);
        if (st == EVO_READY) return w.def.name + " ready: boss chest";
        boolean has = false;
        for (String p : w.def.evoSynergy) if (inv.hasPassive(p)) has = true;
        return w.def.name + " " + w.level + "/" + w.def.maxLevel() + (has ? "" : ", needs " + synergyNames(w.def, defs));
    }
}
