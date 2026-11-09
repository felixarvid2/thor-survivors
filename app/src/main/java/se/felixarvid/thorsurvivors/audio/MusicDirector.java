package se.felixarvid.thorsurvivors.audio;

import se.felixarvid.thorsurvivors.game.Engine;

/** Which music track belongs to the game state. Pure, so it can be tested. */
public final class MusicDirector {
    public static final String MENU = "menu", FOREST = "forest", LIBRARY = "library", BOSS = "boss", FINAL = "final",
            GAMEOVER = "gameover", VICTORY = "victory",
            // v1.5 B8 (75): one calm track per stage (from the CC0 library), and the shared combat layer (74)
            HYDRO = "hydro", REFINERY = "refinery", WRECK = "wreck", COMBAT = "combat";

    private MusicDirector() {
    }

    /** Track name for a phase; null for silence. {@code bossAlive} switches the stage track to {@link #BOSS}. */
    public static String trackFor(int phase, String stageKey, boolean bossAlive, boolean win) {
        return trackFor(phase, stageKey, bossAlive, false, win);
    }

    /** Same; while The First lives ({@code finalAlive}) the {@link #FINAL} track plays instead of the boss track. */
    public static String trackFor(int phase, String stageKey, boolean bossAlive, boolean finalAlive, boolean win) {
        switch (phase) {
            case Engine.PH_MENU:
            case Engine.PH_SHOP:
            case Engine.PH_OPTIONS: // v1.5 B3
            case Engine.PH_CODEX:
            case Engine.PH_REMAP:
                return MENU;
            case Engine.PH_RESULT:
                return win ? VICTORY : GAMEOVER;
            default:
                if (finalAlive) return FINAL;
                if (bossAlive) return BOSS;
                return stageTrack(stageKey);
        }
    }

    /** The calm track of a stage: every stage has its own (Flats, Mines, Hydro, Refinery, Wreck). */
    public static String stageTrack(String stageKey) {
        if (stageKey != null) {
            switch (stageKey) {
                case "MINES":
                case "LIBRARY":
                    return LIBRARY;
                case "HYDRO":
                    return HYDRO;
                case "REFINERY":
                    return REFINERY;
                case "WRECK":
                    return WRECK;
                default:
            }
        }
        return FOREST;
    }

    /** What played before v1.5 for the stage; used when the stage's own file is missing. */
    public static String legacyStageTrack(String stageKey) {
        return "MINES".equals(stageKey) || "LIBRARY".equals(stageKey) || "HYDRO".equals(stageKey) ? LIBRARY : FOREST;
    }

    /** A stage track (not menu, boss, final or a stinger): the ones the combat layer may play over. */
    public static boolean isStageTrack(String track) {
        return FOREST.equals(track) || LIBRARY.equals(track) || HYDRO.equals(track) || REFINERY.equals(track)
                || WRECK.equals(track);
    }

    /** Stingers play once; every other track loops. */
    public static boolean isStinger(String track) {
        return GAMEOVER.equals(track) || VICTORY.equals(track);
    }
}
