package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

/**
 * v1.5 B2: what the bottom-screen HUD reads from the Sim, as pure allocation-free functions (so they can be unit tested without
 * a canvas): weapon cooldown fractions, the hero's status strip, the boss panel (phase ticks, enrage, the next attack), the
 * elite modifier counts, the XP bar's chest tick and the text-contrast helper of the large-text mode.
 */
public final class HudInfo {
    private HudInfo() {
    }

    // ------------------------------------------------------------------ item 11: weapon cooldown arcs

    /** Garlic-like auras and endless weapons do not fire in volleys: they show a steady ring, not a sweep. */
    public static boolean continuous(Sim.WeaponState w) {
        return w.behavior == Sim.B_GARLIC || w.endless || period(w) < FAST_VOLLEY;
    }

    /** A weapon that fires faster than this shows a steady ring: a sweep every 0.4 s would only flicker. */
    public static final float FAST_VOLLEY = 0.45f;

    /** Seconds from one volley to the next. */
    public static float period(Sim.WeaponState w) {
        return Math.max(0.05f, w.interval + (w.dependsOnDuration ? w.duration : 0f));
    }

    /** 0..1: how far the weapon is through the wait for its next volley (1 = ready now). */
    public static float ready(Sim.WeaponState w) {
        if (w == null || w.def == null) return 1f;
        if (w.cd <= 0f) return 1f;
        return Math.max(0f, Math.min(1f, 1f - w.cd / period(w)));
    }

    // ------------------------------------------------------------------ item 12: the status strip

    /** Kinds of the strip, in display order. */
    public static final int HS_BURN = 0, HS_SLOW = 1, HS_DECAY = 2, HS_STUN = 3, HS_GUARD = 4, HS_DASH = 5, HS_COUNT = 6;
    public static final String[] HS_NAMES = {"BURN", "SLOW", "DECAY", "STUN", "GUARD", "DASH"};
    /** Whether the status helps the hero (drawn with a green rim) or hurts it. */
    public static boolean beneficial(int kind) {
        return kind == HS_GUARD || kind == HS_DASH;
    }

    /** Seconds left of a hero status. */
    public static float left(Sim s, int kind) {
        switch (kind) {
            case HS_BURN: return s.heroBurnT;
            case HS_SLOW: return s.heroSlowT;
            case HS_DECAY: return s.heroDecayT;
            case HS_STUN: return s.heroStunT;
            case HS_GUARD: return s.guardT;
            default: return s.dodgeT;
        }
    }

    /** The duration the bar of a status is full at (the hazards' own times; stun and guard are capped). */
    public static float full(int kind) {
        switch (kind) {
            case HS_BURN: return Status.HERO_BURN_TIME;
            case HS_SLOW: return Status.HERO_SLOW_TIME;
            case HS_DECAY: return Status.HERO_DECAY_TIME;
            case HS_STUN: return 1f;
            case HS_GUARD: return 4f;
            default: return Sim.DODGE_TIME;
        }
    }

    /** Fills {@code kinds} with the active statuses (display order, at most {@link #HS_COUNT}); returns how many. */
    public static int active(Sim s, int[] kinds) {
        int n = 0;
        for (int k = 0; k < HS_COUNT && n < kinds.length; k++) if (left(s, k) > 0f) kinds[n++] = k;
        return n;
    }

    /** Fill of a status bar, 0..1. */
    public static float fraction(Sim s, int kind) {
        return Math.max(0f, Math.min(1f, left(s, kind) / full(kind)));
    }

    /** A status that ends within a second blinks (4 Hz); {@code clock} is any running seconds value. */
    public static boolean blinkOff(float left, double clock) {
        return left > 0f && left <= 1f && ((int) (clock * 8.0) & 1) == 0;
    }

    // ------------------------------------------------------------------ item 13: the boss panel

    /**
     * Fills {@code out} with the HP fractions at which the boss changes phase (high to low) and returns how many: the 66 % and
     * 33 % of the three-phase bosses, the enrage, pull, burst or carrier threshold of the others. Read from the boss data, so a
     * boss with no phase at all has none.
     */
    public static int bossTicks(Enemy b, float[] out) {
        EnemyDef d = b.type.def;
        int n = 0;
        switch (b.type.pattern) {
            case 3: case 5: case 11: case 12: case 13: // phase1Hp / phase2Hp, 0.66 and 0.33 unless the data says otherwise
                out[n++] = d.param("phase1Hp", 0.66f);
                out[n++] = d.param("phase2Hp", 0.33f);
                break;
            case 2: out[n++] = d.param("enrageHp", 0.5f); break;
            case 9: out[n++] = d.param("enrageHp", 0.4f); break;
            case 4: out[n++] = d.param("pullHp", 0.25f); break;
            case 6: out[n++] = d.param("burstHp", 0.5f); break;
            case 10: out[n++] = d.param("carrierHp", 0.5f); break;
            default:
        }
        return n;
    }

    /** True when the boss has enraged (Slagborn, Overseer Unit 7). */
    public static boolean enraged(Enemy b) {
        // v1.5 B2: reads Enemy.enraged, which exists today. Batch 6 adds a boss enrage flag for the other bosses:
        // OR it in here when it lands (MARKED STUB, nothing else to change in the panel).
        return b.enraged;
    }

    public static final int NA_NONE = 0, NA_LINE = 1, NA_AREA = 2;

    /**
     * The attack the boss is winding up right now: a line (a charge, its telegraph has a length) or an area (a ring, a bomb
     * mat, a pulse). NONE between attacks.
     */
    public static int nextAttack(Enemy b) {
        // v1.5 B2 STUB: batch 6 adds the real "next attack" info to the boss. Until then only the windup the boss is in can be
        // read from the state that exists (EnemyAi.A_TELE with a telegraph time): this is the one place to swap.
        if (b.ai != BossAi.BA_WINDUP || b.tele <= 0f) return NA_NONE;
        return b.teleLen > 0f ? NA_LINE : NA_AREA;
    }

    // ------------------------------------------------------------------ item 14: elite tags

    /**
     * Counts the live elites per modifier (bit position = Elites.M_* order) into {@code perMod} (length 6) and returns the
     * number of elites. A sleeping or dormant slot does not count.
     */
    public static int eliteCounts(Sim s, int[] perMod) {
        for (int i = 0; i < perMod.length; i++) perMod[i] = 0;
        int elites = 0;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (!e.elite || e.dead || e.dormant) continue;
            elites++;
            for (int b = 0; b < Elites.COUNT && b < perMod.length; b++) if ((e.mods & (1 << b)) != 0) perMod[b]++;
        }
        return elites;
    }

    // ------------------------------------------------------------------ item 20: the XP bar

    /** A chest is close: one waiting on the ground or to open, a boss alive (its chest is certain), or a timed boss within 60 s. */
    public static boolean chestSoon(Sim s) {
        if (s.pendingChests > 0) return true;
        if (s.bossNow != null && !s.bossNow.dead && s.bossNow.type.def.ashfallBoss) return true;
        int next = s.timedBossesDone();
        if (next < s.stage.timedAt.length && s.stage.timedAt[next] - s.time <= 60.0) return true;
        for (int i = 0; i < s.pickN; i++) if (s.picks[i].kind == Entities.K_CHEST) return true;
        return false;
    }

    // ------------------------------------------------------------------ item 19: contrast

    private static double lin(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** WCAG relative luminance of an RGB colour (alpha ignored). */
    public static double luminance(int argb) {
        return 0.2126 * lin(argb >> 16 & 255) + 0.7152 * lin(argb >> 8 & 255) + 0.0722 * lin(argb & 255);
    }

    /** WCAG contrast ratio, 1..21. */
    public static double contrast(int fg, int bg) {
        double a = luminance(fg), b = luminance(bg);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /** {@code fg} lightened towards white until it reaches {@code min} against the (dark) {@code bg}. */
    public static int ensureContrast(int fg, int bg, double min) {
        if (contrast(fg, bg) >= min) return fg;
        int a = fg & 0xFF000000;
        for (int i = 1; i <= 20; i++) {
            float t = i / 20f;
            int r = (int) ((fg >> 16 & 255) + (255 - (fg >> 16 & 255)) * t);
            int g = (int) ((fg >> 8 & 255) + (255 - (fg >> 8 & 255)) * t);
            int b = (int) ((fg & 255) + (255 - (fg & 255)) * t);
            int c = a | r << 16 | g << 8 | b;
            if (contrast(c, bg) >= min) return c;
        }
        return a | 0xFFFFFF;
    }
}
