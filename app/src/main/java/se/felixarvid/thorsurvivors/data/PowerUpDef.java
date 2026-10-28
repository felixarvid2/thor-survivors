package se.felixarvid.thorsurvivors.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A permanent shop upgrade. Row 0 has price + stat, later rows are per-rank stat additions. */
public final class PowerUpDef {
    public final String key;
    public final String name;
    public final String description;
    public final int basePrice;
    /** v1.4: the price curve exponent (ashfall_data.json {@code powerups.KEY.exp}); 1 = the Vampire Survivors line price * (1 + ranks). */
    public final double priceExp;
    public final List<Map<String, Double>> rows;

    public PowerUpDef(String key, String name, String description, int basePrice, List<Map<String, Double>> rows) {
        this(key, name, description, basePrice, 1.0, rows);
    }

    public PowerUpDef(String key, String name, String description, int basePrice, double priceExp, List<Map<String, Double>> rows) {
        this.priceExp = priceExp;
        this.key = key;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.rows = rows;
    }

    /** A copy with another display name (the Ashfall overlay). */
    public PowerUpDef withName(String newName) {
        return new PowerUpDef(key, newName, description, basePrice, priceExp, rows);
    }

    /** A copy with another price line (the Ashfall overlay): rank n+1 costs basePrice * (1 + n) ^ exp, rounded to 10. */
    public PowerUpDef withPrice(int newBase, double newExp) {
        return new PowerUpDef(key, name, description, newBase, newExp, rows);
    }

    public int maxRank() {
        return rows.size();
    }

    /** Price of the next rank when `ranks` are already owned: price * (1 + ranks) ^ exp (exp 1 = the plain VS line; otherwise rounded to 10). */
    public int priceForNext(int ranks) {
        if (priceExp == 1.0) return basePrice * (1 + ranks);
        return (int) (Math.round(basePrice * Math.pow(1 + ranks, priceExp) / 10.0) * 10);
    }

    /** Summed stats of the first `ranks` rows. */
    public Map<String, Double> statsAt(int ranks) {
        Map<String, Double> out = new java.util.HashMap<>();
        for (int i = 0; i < Math.min(ranks, rows.size()); i++) {
            for (Map.Entry<String, Double> e : rows.get(i).entrySet()) {
                if (e.getKey().equals("price") || e.getKey().equals("level") || e.getKey().equals("unlockedRank")) continue;
                Double p = out.get(e.getKey());
                out.put(e.getKey(), p == null ? e.getValue() : p + e.getValue());
            }
        }
        return out;
    }

    public List<String> statKeys() {
        List<String> k = new ArrayList<>(rows.get(0).keySet());
        k.remove("price");
        k.remove("level");
        k.remove("unlockedRank");
        return k;
    }
}
