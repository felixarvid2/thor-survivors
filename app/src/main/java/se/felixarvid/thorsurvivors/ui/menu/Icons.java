package se.felixarvid.thorsurvivors.ui.menu;

/** v1.5 B3: the panel icon of a weapon key (evolutions share the icon of the weapon they grew from). */
final class Icons {
    private Icons() {
    }

    static String base(String key) {
        switch (key) {
            case "VAMPIRICA": return "WHIP";
            case "HOLY_MISSILE": return "MAGIC_MISSILE";
            case "THOUSAND": return "KNIFE";
            case "SCYTHE": return "AXE";
            case "HEAVENSWORD": return "CROSS";
            case "VESPERS": return "HOLYBOOK";
            case "HELLFIRE": return "FIREBALL";
            case "VORTEX": return "GARLIC";
            case "BORA": return "HOLYWATER";
            case "LOOP": return "LIGHTNING";
            default: return key;
        }
    }
}
