package se.felixarvid.thorsurvivors.game;

/**
 * v1.5 B3 (item 27): the first-run hints. Three one-time hints during the very first run only: MOVE (until the hero has been
 * steered for a second), CARDS (while a level-up is open, until a card is picked) and CHEST (while a supply cache is open, until
 * it is closed). The first run ending, whichever way, ends the tutorial for good ({@code tut_done} in the store). A plain state
 * machine fed by {@link #update} with the phase; Android-free.
 */
public final class Tutorial {
    public static final int H_MOVE = 0, H_CARDS = 1, H_CHEST = 2, HINTS = 3;
    public static final String[] TEXT = {
            "USE THE LEFT STICK TO MOVE. YOUR WEAPONS FIRE ON THEIR OWN.",
            "LEVEL UP: PICK A CARD. A OR TAP CONFIRMS.",
            "SUPPLY CACHE: TAKE WHAT IT GIVES, THEN CONTINUE."};
    public static final float MOVE_NEEDED = 1.0f;

    private static final int NEW = 0, SHOWN = 1, DONE = 2;
    private final int[] state = new int[HINTS];
    private boolean finished, inRun;
    private float moved;
    private MetaProgress.Store store;

    public void load(MetaProgress.Store st) {
        store = st;
        finished = st != null && st.getInt("tut_done", 0) != 0;
        inRun = false;
        moved = 0f;
        for (int i = 0; i < HINTS; i++) state[i] = NEW;
    }

    /** True while hints can still show (the first run, not over). */
    public boolean active() {
        return !finished;
    }

    public boolean finished() {
        return finished;
    }

    /**
     * Called every step with the phase (Engine.PH_*), the seconds elapsed and the length of the move input (0..1). Advances the
     * hints: a hint is shown while its situation lasts and done when the player did what it asked.
     */
    public void update(int phase, float dt, float moveMag) {
        if (finished) return;
        if (phase == Engine.PH_PLAYING || phase == Engine.PH_LEVELUP || phase == Engine.PH_CHEST || phase == Engine.PH_PAUSED
                || phase == Engine.PH_EQUIP || phase == Engine.PH_MERCHANT || phase == Engine.PH_TERMINAL) {
            inRun = true;
        } else if (inRun) { // back at the menu or on the result screen: the first run is over
            finish();
            return;
        }
        if (phase == Engine.PH_PLAYING && state[H_MOVE] != DONE) {
            state[H_MOVE] = SHOWN;
            if (moveMag > 0.3f) moved += dt;
            if (moved >= MOVE_NEEDED) state[H_MOVE] = DONE;
        }
        track(H_CARDS, phase == Engine.PH_LEVELUP);
        track(H_CHEST, phase == Engine.PH_CHEST);
        if (state[H_MOVE] == DONE && state[H_CARDS] == DONE && state[H_CHEST] == DONE) finish();
    }

    private void track(int h, boolean open) {
        if (state[h] == DONE) return;
        if (open) state[h] = SHOWN;
        else if (state[h] == SHOWN) state[h] = DONE; // the card was picked, the cache closed
    }

    private void finish() {
        finished = true;
        for (int i = 0; i < HINTS; i++) state[i] = DONE;
        if (store != null) store.putInt("tut_done", 1);
    }

    /** The hint to draw for this phase, or -1. */
    public int hint(int phase) {
        if (finished) return -1;
        if (phase == Engine.PH_PLAYING) return state[H_MOVE] == SHOWN ? H_MOVE : -1;
        if (phase == Engine.PH_LEVELUP) return state[H_CARDS] == SHOWN ? H_CARDS : -1;
        if (phase == Engine.PH_CHEST) return state[H_CHEST] == SHOWN ? H_CHEST : -1;
        return -1;
    }

    public boolean done(int h) {
        return state[h] == DONE;
    }

    /** Debug and tests: start over. */
    public void reset() {
        finished = false;
        inRun = false;
        moved = 0f;
        for (int i = 0; i < HINTS; i++) state[i] = NEW;
        if (store != null) store.putInt("tut_done", 0);
    }
}
