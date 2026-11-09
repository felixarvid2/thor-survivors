package se.felixarvid.thorsurvivors.game;

/**
 * v1.5 B3 (item 26): one-line tips for the menu and the results screen. 30 general tips and a few per stage; {@link #pick} takes
 * a stage tip half of the time when the selected stage has some. The seed is rolled once per run (Engine), so the same tip stays
 * on screen until the next run. Text only; every line is at most {@link #MAX_LEN} characters (checked by TipsTest).
 */
public final class Tips {
    public static final int MAX_LEN = 80;

    public static final String[] GENERAL = {
            "Dodge has a short cooldown and makes you untouchable while it lasts.",
            "Standing still is how you die. Keep moving and let the weapons work.",
            "Level-up cards show what you already own: build around two or three weapons.",
            "A fully upgraded weapon next to its passive can evolve from a boss chest.",
            "Two mastered weapons can fuse into a new one and free a weapon slot.",
            "Supply caches drop from bosses: kill them fast, the loot is worth it.",
            "Armor takes a flat amount off every hit. It matters most against swarms.",
            "Gems fade as the run goes on. Early levels come cheap, so use them.",
            "The Salvager visits every five minutes and pays for gold you carried.",
            "Gold is banked at the end of a run, even if you abandon it from pause.",
            "Power-ups in the shop are permanent and apply to every hero and stage.",
            "Elites show their modifiers on the panel. Check them before you engage.",
            "Relay terminals offer a choice of buffs: stand on one until it opens.",
            "Magnet range grows with passives. Pick up the red gem to pull them all.",
            "Orbiting weapons are safest when enemies cannot get behind you.",
            "Bastion tanks, Spectre kites, Xenomant controls: play to the hero.",
            "Branches on the main weapon are chosen on level-up. Commit to one early.",
            "Break barrels and crates: they hide food, coins and the odd surprise.",
            "Stage events from minute 20 change the rules. Read the banner.",
            "Revivals clear the screen around you. Do not waste the second chance.",
            "If a boss telegraphs a red zone, leave it. Damage you avoid is free.",
            "Frost slows, Burn spreads damage, Electrify chains: mix statuses.",
            "Use the right stick or the aim area to point the main weapon.",
            "Pausing is free. Look at your build and plan the next card.",
            "Equipment drops in six slots. A higher rarity beats a lucky roll.",
            "Healing is rare on hard stages. Pick fights you can leave.",
            "A crowd that cannot reach you cannot hurt you: use obstacles.",
            "Lumen glows where it is safe. Darkness is where things wait.",
            "Better a weak build alive at minute ten than a perfect one dead at five.",
            "The Codex fills as you meet enemies and find weapons. Look for gaps.",
    };

    /** Per stage key (Engine.stageKeys): tips that only make sense there. */
    private static final String[][] STAGE_KEYS_TIPS = {
            {"FLATS",
                    "Cinder Flats: slag rain lights the ground. Keep to clear ash.",
                    "Cinder Flats: the long night starts at minute 20. Save your dodge.",
                    "Cinder Flats: braziers hide pickups. Smash them on your way."},
            {"MINES",
                    "Deep Mines: carts run the rails. Never cross one head on.",
                    "Deep Mines: tentacles break out of the floor. Stay mobile.",
                    "Deep Mines: the collapse buries the area for a short time."},
            {"HYDRO",
                    "Hydroponic Vaults: burn Bloom Hearts before the growth spreads.",
                    "Hydroponic Vaults: plant patches slow you and stop regeneration.",
                    "Hydroponic Vaults: fire clears the overgrowth fastest."},
            {"REFINERY",
                    "Refinery District: conveyors push you. Use them to run, or avoid them.",
                    "Refinery District: molten channels are lethal. Use the bridges.",
                    "Refinery District: belts reverse every twenty seconds."},
            {"WRECK",
                    "Meridian Wreck: gravity wells pull everything, you and the gems too.",
                    "Meridian Wreck: step on a teleporter pad to cross the voids.",
                    "Meridian Wreck: a vacuum breach hurts outside the safe bubble."},
    };

    private Tips() {
    }

    /** The tip for a stage and a seed (any int): half of the picks are stage tips when the stage has some. */
    public static String pick(String stageKey, int seed) {
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h &= 0x7fffffff;
        String[] st = stageTips(stageKey);
        if (st != null && (h & 1) == 0) return st[1 + (h >>> 1) % (st.length - 1)];
        return GENERAL[(h >>> 1) % GENERAL.length];
    }

    private static String[] stageTips(String stageKey) {
        if (stageKey == null) return null;
        for (String[] t : STAGE_KEYS_TIPS) if (t[0].equals(stageKey)) return t;
        return null;
    }

    /** All stage tip lines (tests). */
    public static int stageTipCount() {
        int n = 0;
        for (String[] t : STAGE_KEYS_TIPS) n += t.length - 1;
        return n;
    }

    public static String stageTip(int stage, int i) {
        return STAGE_KEYS_TIPS[stage][1 + i];
    }

    public static int stages() {
        return STAGE_KEYS_TIPS.length;
    }
}
