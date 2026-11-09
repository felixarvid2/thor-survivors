package se.felixarvid.thorsurvivors.game;

/**
 * v1.5 B7 item 68: the pre-run curse selector (0 / 10 / 20 / 30 percent). Enemy HP, speed and spawn count each scale by
 * {@code 1 + curse * 0.5}, the gold reward by {@code 1 + curse}. The HP of curse x ascension tier x the shop's Curse
 * power-up is capped at {@link #HP_CAP}.
 *
 * <p>The selector UI (batch 3) and the run start (batch 4) only call {@link #set(double)} / {@link #get()}; B4's ascension tier
 * feeds {@link #setTierHpMul(double)} (1 = no tier). Until B4's {@code RunMods} exists this class is the single source; a
 * {@code RunMods.curse()} can simply delegate here. {@link Sim} snapshots the values in its constructor, so a change
 * during a run does nothing and a test that never calls {@link #set(double)} sees the old balance (curse 0).
 */
public final class CurseMod {
    /** The selectable steps (fractions, 0.1 = 10 percent). */
    public static final double[] STEPS = {0.0, 0.1, 0.2, 0.3};
    /** Highest combined HP multiplier of curse, ascension tier and the shop Curse power-up. */
    public static final double HP_CAP = 3.0;

    private static volatile double curse;
    private static volatile double tierHpMul = 1.0;

    private CurseMod() {
    }

    /** Selected curse as a fraction (0 .. 0.3). Values are clamped to [0, 0.3]. */
    public static void set(double fraction) {
        curse = Math.max(0.0, Math.min(STEPS[STEPS.length - 1], fraction));
    }

    public static double get() {
        return curse;
    }

    /** B4: the ascension tier's enemy HP multiplier for the next run (1 = none). */
    public static void setTierHpMul(double m) {
        tierHpMul = Math.max(1.0, m);
    }

    public static double tierHpMul() {
        return tierHpMul;
    }

    /** Next step of the selector (wraps around): 0 -> 0.1 -> 0.2 -> 0.3 -> 0. */
    public static double next(double current) {
        for (int i = 0; i < STEPS.length; i++) if (Math.abs(STEPS[i] - current) < 1e-6) return STEPS[(i + 1) % STEPS.length];
        return 0.0;
    }

    /** The common factor of HP, speed and spawn count: 1 + curse * 0.5 (1 .. 1.15). */
    public static double factor(double curse) {
        return 1.0 + Math.max(0.0, curse) * 0.5;
    }

    /** Gold multiplier 1 + curse (1 .. 1.3). */
    public static double goldMul(double curse) {
        return 1.0 + Math.max(0.0, curse);
    }

    /** Enemy HP multiplier of the run: curse factor x ascension tier x (1 + shop curse), never above {@link #HP_CAP}. */
    public static double hpMul(double curse, double tierHp, double shopCurse) {
        return Math.min(HP_CAP, factor(curse) * Math.max(1.0, tierHp) * (1.0 + Math.max(0.0, shopCurse)));
    }
}
