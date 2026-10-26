package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.PowerUpDef;
import se.felixarvid.thorsurvivors.data.WeaponDef;

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
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e : new TreeMap<>(stats).entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(puStat(e.getKey(), e.getValue(), label));
        }
        return sb.length() == 0 ? d.description : sb.toString();
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
}
