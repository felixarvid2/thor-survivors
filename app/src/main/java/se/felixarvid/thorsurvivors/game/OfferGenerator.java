package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.BranchDef;
import se.felixarvid.thorsurvivors.data.MainWeaponDef;
import se.felixarvid.thorsurvivors.data.WeaponDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/** Builds the 3 (or 4 with luck) level-up choices, weighted by each def's rarity. */
public final class OfferGenerator {
    private OfferGenerator() {
    }

    /** Every offer currently possible: respects slot limits and never includes evolved weapons. */
    public static List<Offer> candidates(Inventory inv, Collection<WeaponDef> defs) {
        List<Offer> out = new ArrayList<>();
        for (WeaponDef d : defs) {
            if (d.evolution) continue;
            int owned = inv.levelOf(d.key);
            if (d.fusion && owned == 0) continue; // a fusion is made, never offered as a new weapon (it levels up like any weapon)
            if (owned > 0) {
                if (owned < d.maxLevel()) {
                    out.add(new Offer(d.powerUp ? Offer.PASSIVE_LEVEL : Offer.WEAPON_LEVEL, d, owned + 1));
                }
            } else if (d.powerUp ? inv.passiveCount < Inventory.MAX_PASSIVES : inv.weaponCount < Inventory.MAX_WEAPONS) {
                out.add(new Offer(d.powerUp ? Offer.NEW_PASSIVE : Offer.NEW_WEAPON, d, 1));
            }
        }
        return out;
    }

    /**
     * @param luck total luck (0.1 = 10%): chance of a 4th card.
     * When nothing is upgradeable, falls back to a heal / gold card.
     */
    public static List<Offer> generate(Inventory inv, Collection<WeaponDef> defs, double luck, Random rng) {
        return generate(inv, defs, luck, rng, null, null, 0);
    }

    /** Hero levels that offer a branch card: 5, 10, 15, ... */
    public static boolean isBranchLevel(int level) {
        return level > 0 && level % 5 == 0;
    }

    /**
     * The branch cards for a level-up at hero level {@code level} (design 2.2 and 8), or an empty list. Only at levels
     * 5, 10, 15, ...; tiers go in order and tier I / II / III need hero level 5 / 15 / 25 ({@link BranchDef#TIER_LEVEL}).
     * When any started branch can take its next tier now, those next tiers are offered (up to 2, one card each: the
     * choice at level 15 is between your branches); otherwise tier I of up to 2 random branches not started yet.
     */
    public static List<Offer> branchOffers(MainWeaponDef mw, int[] tiers, int level, Random rng) {
        List<Offer> out = new ArrayList<>();
        if (mw == null || tiers == null || !isBranchLevel(level)) return out;
        for (int b = 0; b < mw.branches.size(); b++) {
            int next = tiers[b] + 1;
            if (tiers[b] > 0 && next <= BranchDef.TIERS && level >= BranchDef.TIER_LEVEL[next - 1]) {
                out.add(Offer.branch(mw.branches.get(b), b, next));
            }
        }
        if (out.isEmpty()) {
            for (int b = 0; b < mw.branches.size(); b++) {
                if (tiers[b] == 0 && level >= BranchDef.TIER_LEVEL[0]) out.add(Offer.branch(mw.branches.get(b), b, 1));
            }
        }
        while (out.size() > 2) out.remove(rng.nextInt(out.size()));
        return out;
    }

    /**
     * Like {@link #generate(Inventory, Collection, double, Random)} for a hero run: at hero levels 5, 10, 15, ... the
     * branch cards replace the last card slots (one or two cards), the rest are the usual weighted draws.
     */
    public static List<Offer> generate(Inventory inv, Collection<WeaponDef> defs, double luck, Random rng,
                                       MainWeaponDef mw, int[] tiers, int level) {
        List<Offer> pool = candidates(inv, defs);
        double[] weight = new double[pool.size()];
        for (int i = 0; i < weight.length; i++) weight[i] = weightOf(inv, defs, pool.get(i));
        int count = 3;
        if (rng.nextDouble() < Math.min(0.75, Math.max(0, luck))) count = 4;
        List<Offer> branches = branchOffers(mw, tiers, level, rng);
        count -= branches.size();
        List<Offer> out = new ArrayList<>();
        int left = pool.size();
        while (out.size() < count && left > 0) {
            double total = 0;
            for (int i = 0; i < left; i++) total += weight[i];
            double r = rng.nextDouble() * total;
            int pick = left - 1;
            for (int i = 0; i < left; i++) {
                r -= weight[i];
                if (r < 0) {
                    pick = i;
                    break;
                }
            }
            out.add(pool.remove(pick));
            System.arraycopy(weight, pick + 1, weight, pick, left - pick - 1);
            left--;
        }
        guard(out, pool, weight, left, !branches.isEmpty(), rng); // v1.5 B7 item 64
        if (out.isEmpty() && branches.isEmpty()) {
            out.add(new Offer(Offer.HEAL, null, 0));
            out.add(new Offer(Offer.GOLD, null, 0));
        }
        out.addAll(branches);
        return out;
    }

    // ------------------------------------------------------------------ v1.5 B7 item 64: offer-quality guard

    /** True for a card that is something new or an upgrade of an owned weapon (a passive level-up alone is a dead level). */
    static boolean isFresh(Offer o) {
        return o.kind == Offer.NEW_WEAPON || o.kind == Offer.NEW_PASSIVE || o.kind == Offer.WEAPON_LEVEL
                || o.kind == Offer.BRANCH || o.kind == Offer.FUSE;
    }

    /** The stat a passive card raises (its stat keys at the last level, sorted and joined); "" for a non-passive. */
    static String statOf(Offer o) {
        if (o.def == null || !o.def.powerUp) return "";
        java.util.TreeSet<String> keys = new java.util.TreeSet<>(o.def.statsAt(o.def.maxLevel()).keySet());
        return keys.toString();
    }

    /** Index in [0, left) of a weighted draw among the cards that satisfy {@code want}, or -1. */
    private static int drawWhere(List<Offer> pool, double[] weight, int left, Random rng, java.util.function.Predicate<Offer> want) {
        double total = 0;
        for (int i = 0; i < left; i++) if (want.test(pool.get(i))) total += weight[i];
        if (total <= 0) return -1;
        double r = rng.nextDouble() * total;
        int last = -1;
        for (int i = 0; i < left; i++) {
            if (!want.test(pool.get(i))) continue;
            last = i;
            r -= weight[i];
            if (r < 0) return i;
        }
        return last;
    }

    /**
     * Item 64: of the cards drawn, at least one must be fresh (new, a weapon level, a branch or a fusion; skipped when nothing
     * fresh is left in the pool), and never three passive cards with the same stat. A failing card (the last drawn) is replaced
     * by a weighted draw from what is left, so a normal draw keeps its random sequence.
     */
    static void guard(List<Offer> out, List<Offer> pool, double[] weight, int left, boolean hasBranch, Random rng) {
        if (out.isEmpty()) return;
        if (!hasBranch) {
            boolean fresh = false;
            for (Offer o : out) if (isFresh(o)) fresh = true;
            if (!fresh) {
                int k = drawWhere(pool, weight, left, rng, OfferGenerator::isFresh);
                if (k >= 0) out.set(out.size() - 1, pool.remove(k));
                else return;
                System.arraycopy(weight, k + 1, weight, k, left - k - 1);
                left--;
            }
        }
        for (int guardLoop = 0; guardLoop < 4; guardLoop++) {
            int bad = -1;
            for (int i = 0; i < out.size() && bad < 0; i++) {
                String st = statOf(out.get(i));
                if (st.isEmpty()) continue;
                int n = 0;
                for (Offer o : out) if (st.equals(statOf(o))) n++;
                if (n >= 3) bad = i;
            }
            if (bad < 0) return;
            final String st = statOf(out.get(bad));
            int k = drawWhere(pool, weight, left, rng, o -> !st.equals(statOf(o)));
            if (k < 0) return;
            out.set(bad, pool.remove(k));
            System.arraycopy(weight, k + 1, weight, k, left - k - 1);
            left--;
        }
    }

    /**
     * Item 62: weight multiplier of passive cards while no weapon you own has reached level {@link #EARLY_WEAPON_LEVEL}.
     * Default 1 = off: measured (skilled bot, 20 seeds x 3 heroes) 0.6 moved the first evolution EARLIER (Flats 627 s -> 531 s, Refinery 564 -> 530),
     * because the weapons reach level 8 before the first boss chest; the lever is therefore the chest time ({@code first_boss_shift}), not this.
     */
    public static double earlyPassiveMul = 1.0;
    public static final int EARLY_WEAPON_LEVEL = 5;

    private static boolean weaponReachedEarlyLevel(Inventory inv) {
        for (int i = 0; i < inv.weaponCount; i++) {
            Inventory.Slot s = inv.weapons[i];
            if (!s.def.evolution && !s.def.fusion && s.level >= EARLY_WEAPON_LEVEL) return true;
        }
        return false;
    }

    /**
     * Item 65: passives taken in more than 70 % of the surviving skilled builds (measured, 90 runs: Cooldown 92 %, Power 79 %, Amount 76 %)
     * weigh less in the draw. The per-level values live in game_data.json (never changed), so the price is paid in offer weight: 0.8 / 0.85 / 0.85.
     * The x3 weight of the synergy passive of a maxed weapon still applies to the reduced weight.
     */
    static double dominantMul(String key) {
        switch (key) {
            case "COOLDOWN": return 0.8;
            case "POWER":
            case "AMOUNT": return 0.85;
            default: return 1.0;
        }
    }

    /** Draw weight of a card: the def's rarity, x3 for the synergy passive of a maxed weapon you own, x1.5 for a new weapon whose fusion partner you own (design v1.3 1.6). */
    static double weightOf(Inventory inv, Collection<WeaponDef> defs, Offer o) {
        double w = o.def.rarity;
        if (o.def.powerUp) {
            if (earlyPassiveMul != 1.0 && !weaponReachedEarlyLevel(inv)) w *= earlyPassiveMul; // v1.5 B7 item 62
            w *= dominantMul(o.def.key); // v1.5 B7 item 65
            for (int i = 0; i < inv.weaponCount; i++) {
                Inventory.Slot s = inv.weapons[i];
                if (s.def.evolution || s.def.fusion || s.level < s.def.maxLevel()) continue;
                if (s.def.evoSynergy.contains(o.def.key)) return w * 3.0;
            }
            return w;
        }
        o.partner = hasMasteredPartner(inv, defs, o.def);
        if (o.partner && o.kind == Offer.NEW_WEAPON) w *= 1.5;
        return w;
    }

    /** True when {@code d} is a parent of a fusion whose other parent is mastered in the inventory (and the fusion is not owned yet). */
    static boolean hasMasteredPartner(Inventory inv, Collection<WeaponDef> defs, WeaponDef d) {
        if (d.powerUp || d.evolution || d.fusion) return false;
        for (WeaponDef f : defs) {
            if (!f.fusion || f.parents.size() < 2 || inv.weaponLevel(f.key) > 0) continue;
            String other = f.parents.get(0).equals(d.key) ? f.parents.get(1) : f.parents.get(1).equals(d.key) ? f.parents.get(0) : null;
            if (other != null && ownsMastered(inv, defs, other)) return true;
        }
        return false;
    }

    private static boolean ownsMastered(Inventory inv, Collection<WeaponDef> defs, String baseKey) {
        for (int i = 0; i < inv.weaponCount; i++) {
            Inventory.Slot s = inv.weapons[i];
            if (s.def.fusion) continue;
            if (s.def.key.equals(baseKey)) {
                if (s.level >= s.def.maxLevel()) return true;
            } else if (s.def.evolution) {
                for (WeaponDef b : defs) if (b.key.equals(baseKey) && s.def.key.equals(b.evoInto)) return true;
            }
        }
        return false;
    }
}
