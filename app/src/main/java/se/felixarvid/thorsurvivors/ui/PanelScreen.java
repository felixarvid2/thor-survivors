package se.felixarvid.thorsurvivors.ui;

import android.graphics.Canvas;

/**
 * v1.5 B2: one screen of the bottom panel that lives in its own class (new menus of batch 3, the shop of batch 4, ...).
 * It handles one Engine phase ({@code Engine.PH_*}); {@link PanelScreens#register} plugs it in with one line and
 * {@link PanelRenderer} then draws it instead of its own switch. Taps need no code of their own: draw the touch rectangles with
 * {@link Host#hit}, the tap arrives at the Engine as {@code CMD_TAP} with the same phase, index and sub-index.
 * A frame must allocate nothing: build strings when the screen opens, not in {@link #draw}.
 */
public interface PanelScreen {
    /** What a screen may use of the renderer. */
    interface Host {
        /** The shared drawing helpers (plates, bars, text); {@code skin().u} is the scale of the frame (1 at 1080 px). */
        Skin skin();

        /** True in the single-screen overlay (no steel background: the game shows through, so {@link #dim} first). */
        boolean compact();

        /** Darkens the overlay behind the screen; a no-op on the full panel. */
        void dim(Canvas c, int w, int h, int color);

        /** A touch rectangle: a tap inside posts {@code CMD_TAP} (phase, idx, sub). Call it every frame. */
        void hit(float l, float t, float r, float b, int idx, int sub);
    }

    /** The Engine phase this screen is shown in. */
    int phase();

    /** Draws the whole screen (the panel background is already drawn on the full panel). */
    void draw(Canvas c, int w, int h, Host host);
}
