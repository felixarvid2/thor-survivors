package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.GameData;

/**
 * v1.5 B5 (not a JUnit test): damage per second of the new weapons against the ten VS base weapons on the same seeded field of
 * 200 stationary sponges as {@link FusionBalance} (docs/TEKNIK.md "v1.5 batch 5"). {@code main} prints level 1, level 4 and
 * max level, then the evolutions; args: seconds per run (default 30).
 */
public final class ContentBalance {
    private ContentBalance() {
    }

    private static double avg(GameData d, String key, int level, float secs) {
        double t = 0;
        for (long seed = 1; seed <= 3; seed++) t += FusionBalance.dps(d, key, level, seed, secs);
        return t / 3;
    }

    public static void main(String[] a) throws Exception {
        GameData d = StageSoak.data();
        float secs = a.length > 0 ? Float.parseFloat(a[0]) : 30f;
        String[] keys = {"WHIP", "MAGIC_MISSILE", "KNIFE", "AXE", "CROSS", "HOLYBOOK", "FIREBALL", "GARLIC", "HOLYWATER", "LIGHTNING",
                "ARC_LANCE", "SLAG_MORTAR", "TETHER_DRONE"};
        System.out.println("weapon              L1       L4      max    evolved");
        for (String k : keys) {
            String evo = d.weapons.get(k).evoInto;
            System.out.printf("%-16s %7.0f %8.0f %8.0f %8.0f%n", k, avg(d, k, 1, secs), avg(d, k, 4, secs), avg(d, k, 0, secs),
                    evo == null ? 0 : avg(d, evo, 0, secs));
        }
    }
}
