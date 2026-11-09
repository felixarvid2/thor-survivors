package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.game.Entities.Enemy;

/**
 * v1.5 B3 (item 28): optional aim help for the main weapon. {@link Settings#AIM_OFF} changes nothing. Soft pulls a manual aim
 * onto the nearest enemy inside a 25 degree cone; Full widens the cone to 70 degrees and, with no aim input, aims at the nearest
 * enemy in range. Pure arithmetic on the Sim's enemy array, no allocation.
 */
public final class AimAssist {
    public static final float RANGE = 560f;
    public static final float SOFT_COS = (float) Math.cos(Math.toRadians(25)), FULL_COS = (float) Math.cos(Math.toRadians(70));

    private AimAssist() {
    }

    /**
     * (bx, by) is the unit aim direction before the assist (the aim input, or the facing when {@code manual} is false); the assisted
     * direction goes to out[0..1]. Returns true when the assist changed it.
     */
    public static boolean pick(int mode, Sim s, float bx, float by, boolean manual, float[] out) {
        out[0] = bx;
        out[1] = by;
        if (mode == Settings.AIM_OFF || s.main == null) return false;
        if (!manual && mode == Settings.AIM_SOFT) return false; // soft only helps an aim that is being made
        float minCos = !manual ? -2f : mode == Settings.AIM_FULL ? FULL_COS : SOFT_COS;
        Enemy best = null;
        float bd = RANGE * RANGE, bnx = 0, bny = 0;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (e.dead || e.dormant) continue;
            float dx = e.x - s.px, dy = e.y - s.py, d = dx * dx + dy * dy;
            if (d >= bd || d < 1f) continue;
            float inv = 1f / (float) Math.sqrt(d);
            if ((dx * bx + dy * by) * inv < minCos) continue;
            bd = d;
            best = e;
            bnx = dx * inv;
            bny = dy * inv;
        }
        if (best == null) return false;
        out[0] = bnx;
        out[1] = bny;
        return true;
    }
}
