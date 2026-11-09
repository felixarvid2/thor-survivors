package se.felixarvid.thorsurvivors.ui.menu;

/** v1.5 B3: where a menu screen registers its touch regions; {@code PanelRenderer} implements it (the tap becomes a CMD_TAP). */
public interface MenuHits {
    void hit(float l, float t, float r, float b, int idx, int sub);
}
