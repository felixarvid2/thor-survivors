package se.felixarvid.thorsurvivors.game;

/**
 * v1.5 B3 (item 24): what one run did, kept for the results screen. Damage dealt to enemies per weapon slot in a fixed
 * {@code long[]} (units of 1/{@link #SCALE} damage, so the sum is exact), filled from {@link Sim#hurt}: the Sim sets {@link #src}
 * to the slot of the weapon whose code is running (a projectile uses its own slot, the main weapon {@link #MAIN}, anything else
 * {@link #OTHER}: hazards, barrels, stage rules). Also remembers what hurt the hero last, for the cause of death. No allocation.
 */
public final class RunStats {
    /** Weapon slots 0..5 ({@code Inventory.MAX_WEAPONS}), then the hero's main weapon and everything else. */
    public static final int SLOTS = Inventory.MAX_WEAPONS, MAIN = SLOTS, OTHER = SLOTS + 1, N = SLOTS + 2;
    public static final int SCALE = 16;
    public static final String[] DEATH_TEXT = {"Overwhelmed in melee", "Shot down", "Killed by the ground", "Blown up"};

    public final long[] dmg = new long[N];
    /** Total damage dealt; always equals the sum of {@link #dmg}. */
    public long total;
    /** Bucket the next hit is added to. */
    public int src = OTHER;
    /** The {@code Sim.DMG_*} source of the hero's latest damage, -1 if none. */
    public int lastHurt = -1;

    /** Adds {@code amount} damage dealt to the current bucket. */
    public void add(float amount) {
        if (!(amount > 0f)) return;
        long v = (long) (amount * SCALE + 0.5f);
        int i = src < 0 || src >= N ? OTHER : src;
        dmg[i] += v;
        total += v;
    }

    public long sum() {
        long t = 0;
        for (int i = 0; i < N; i++) t += dmg[i];
        return t;
    }

    /** Damage of a bucket in whole points. */
    public int points(int bucket) {
        return (int) (dmg[bucket] / SCALE);
    }

    /** "Overwhelmed in melee" and the like; "" before the hero was hurt. */
    public String deathText() {
        return lastHurt >= 0 && lastHurt < DEATH_TEXT.length ? DEATH_TEXT[lastHurt] : "";
    }
}
