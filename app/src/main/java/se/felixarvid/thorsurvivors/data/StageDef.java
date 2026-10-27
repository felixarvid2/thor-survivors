package se.felixarvid.thorsurvivors.data;

import java.util.List;

public final class StageDef {
    public final String key;
    public final String name;
    public final double timeLimit;
    public final double playerSpeed;
    public final double enemySpeed;
    public final double enemyHealth;
    public final double goldMultiplier;
    public final double xpBonus;
    public final int startingSpawns;
    public final boolean horizontalSpawns;
    /** What breaks and drops pickups on this stage (game_data.json destructibleType): "BRAZIER", "CANDELABRA" or "". */
    public final String destructible;
    public final List<WaveRow> rows;
    /**
     * Lumen tentacle hazard (design 4.1, Deep Mines): the minutes it breaks out, how many seconds it lasts, the seconds
     * between two waves of {@link #tentacleCount}, and the radius around the hero (empty = none).
     */
    public int[] tentacleMinutes = new int[0];
    public float tentacleLen = 20f, tentacleEvery = 4f, tentacleRadius = 400f;
    public int tentacleCount = 3;
    /** v1.3 timed bosses (ashfall_data.json {@code timedBosses}): the run second each comes at and its enemy key, in time order. */
    public int[] timedAt = new int[0];
    public String[] timedBoss = new String[0];
    /** v1.4 events of minutes 20-30 (ashfall_data.json {@code events}, see game/Events): the id of each and the run second it starts at, in time order. */
    public int[] eventAt = new int[0];
    public String[] eventId = new String[0];
    /**
     * Ashfall balance (phase 12): the gem value of a kill fades linearly from 1.0 at {@code xpFadeFrom} seconds to
     * {@code xpFadeEnd} at {@code xpFadeTo} (no fade when {@code xpFadeTo <= xpFadeFrom}). Keeps the early levels, stops the late XP flood.
     */
    public double xpFadeFrom, xpFadeTo, xpFadeEnd = 1.0;
    /**
     * Ashfall difficulty (phase 13): multiplier on the HP of every wave enemy and elite (not bosses, not tentacles) by run time,
     * linear between the points {seconds, multiplier}; before the first point 1.0, after the last point the last value. Empty = 1.
     */
    public double[][] hpCurve = new double[0][];
    /** Same shape: multiplier on the contact damage and the bolt / ring damage of wave enemies and elites (not bosses). */
    public double[][] dmgCurve = new double[0][];
    /**
     * v1.4: multiplier on the walking speed of wave enemies and elites spawned at a run time (not bosses, not tentacles), same shape as
     * {@link #hpCurve}. Empty = 1. Per stage, so a new stage can be as quick as it likes.
     */
    public double[][] speedCurve = new double[0][];
    /** v1.4: per-stage overrides of the global elite numbers (ashfall_data.json {@code elites}); a key missing here falls back to the global one. */
    public java.util.Map<String, Double> eliteOv = new java.util.HashMap<>();
    /** v1.4: ranged enemies alive at once (Sim.RANGED_CAP when 0). */
    public int rangedCap;
    /**
     * Horde bursts (phase 13): {start s, length s, minimum alive, frequency ms}; while one runs the wave minimum is raised to
     * {@code minimum}, the spawn tick shortened to {@code frequency} and the top-up picks from {@link #hordeTypes}.
     */
    public double[][] hordes = new double[0][];
    public String[] hordeTypes = new String[0];

    public StageDef(String key, String name, double timeLimit, double playerSpeed, double enemySpeed,
                    double enemyHealth, double goldMultiplier, double xpBonus, int startingSpawns,
                    boolean horizontalSpawns, String destructible, List<WaveRow> rows) {
        this.key = key;
        this.name = name;
        this.timeLimit = timeLimit;
        this.playerSpeed = playerSpeed;
        this.enemySpeed = enemySpeed;
        this.enemyHealth = enemyHealth;
        this.goldMultiplier = goldMultiplier;
        this.xpBonus = xpBonus;
        this.startingSpawns = startingSpawns;
        this.horizontalSpawns = horizontalSpawns;
        this.destructible = destructible;
        this.rows = rows;
    }

    /** A copy with another display name (the Ashfall overlay). */
    public StageDef withName(String newName) {
        StageDef d = new StageDef(key, newName, timeLimit, playerSpeed, enemySpeed, enemyHealth, goldMultiplier, xpBonus,
                startingSpawns, horizontalSpawns, destructible, rows);
        d.tentacleMinutes = tentacleMinutes;
        d.tentacleLen = tentacleLen;
        d.tentacleEvery = tentacleEvery;
        d.tentacleRadius = tentacleRadius;
        d.tentacleCount = tentacleCount;
        d.timedAt = timedAt;
        d.timedBoss = timedBoss;
        d.eventAt = eventAt;
        d.eventId = eventId;
        d.xpFadeFrom = xpFadeFrom;
        d.xpFadeTo = xpFadeTo;
        d.xpFadeEnd = xpFadeEnd;
        d.hpCurve = hpCurve;
        d.dmgCurve = dmgCurve;
        d.speedCurve = speedCurve;
        d.eliteOv = eliteOv;
        d.rangedCap = rangedCap;
        d.hordes = hordes;
        d.hordeTypes = hordeTypes;
        return d;
    }

    /** Multiplier on the XP a kill drops at run time {@code seconds} (1 without a fade). */
    public double xpFade(double seconds) {
        if (xpFadeTo <= xpFadeFrom || seconds <= xpFadeFrom) return 1.0;
        if (seconds >= xpFadeTo) return xpFadeEnd;
        return 1.0 + (xpFadeEnd - 1.0) * (seconds - xpFadeFrom) / (xpFadeTo - xpFadeFrom);
    }

    /** Multiplier on the HP of a wave enemy spawned at run time {@code seconds} ({@link #hpCurve}). */
    public double hpMul(double seconds) {
        return curve(hpCurve, seconds);
    }

    /** Multiplier on the damage a wave enemy spawned at run time {@code seconds} deals ({@link #dmgCurve}). */
    public double dmgMul(double seconds) {
        return curve(dmgCurve, seconds);
    }

    /** Multiplier on the walking speed of a wave enemy spawned at run time {@code seconds} ({@link #speedCurve}). */
    public double speedMul(double seconds) {
        return curve(speedCurve, seconds);
    }

    static double curve(double[][] c, double seconds) {
        if (c.length == 0) return 1.0;
        if (seconds <= c[0][0]) return c[0][1];
        for (int i = 1; i < c.length; i++) {
            if (seconds <= c[i][0]) return c[i - 1][1] + (c[i][1] - c[i - 1][1]) * (seconds - c[i - 1][0]) / (c[i][0] - c[i - 1][0]);
        }
        return c[c.length - 1][1];
    }

    /** Index into {@link #hordes} of the burst running at {@code seconds}, or -1. */
    public int hordeAt(double seconds) {
        for (int i = 0; i < hordes.length; i++) if (seconds >= hordes[i][0] && seconds < hordes[i][0] + hordes[i][1]) return i;
        return -1;
    }

    /** The row that governs the given run time: the last row whose minute is <= elapsed minutes. */
    public WaveRow rowAt(double seconds) {
        return WaveSelector.rowAt(rows, seconds);
    }

    /** The treasure table of the latest row (at or before the given time) that has one, or null. */
    public Treasure treasureAt(double seconds) {
        for (int i = WaveSelector.indexAt(rows, seconds); i >= 0; i--) if (rows.get(i).treasure != null) return rows.get(i).treasure;
        return null;
    }
}
