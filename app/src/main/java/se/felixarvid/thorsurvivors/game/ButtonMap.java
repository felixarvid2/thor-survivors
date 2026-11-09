package se.felixarvid.thorsurvivors.game;

/**
 * v1.5 B3 (item 29): which gamepad button does what. Four actions can be moved to another button: confirm and back (menus),
 * pause (the Start button; starts the run in the menu) and dash/ability (while playing). Android-free: the buttons are
 * {@code KeyEvent.KEYCODE_BUTTON_*} values. The defaults are the behaviour before the remap existed: A confirms, B goes
 * back, Start pauses, and while playing A, B, X, Y, L1, R1, L2 and R2 all dash (the "legacy dash set", kept until the
 * dash is bound to one button of its own).
 */
public final class ButtonMap {
    public static final int CONFIRM = 0, BACK = 1, PAUSE = 2, DASH = 3, COUNT = 4;
    public static final String[] NAMES = {"CONFIRM", "BACK", "PAUSE", "DASH / ABILITY"};
    /** KeyEvent.KEYCODE_BUTTON_*: A, B, X, Y, L1, R1, L2, R2, THUMBL, THUMBR, START, SELECT. */
    public static final int A = 96, B = 97, X = 99, Y = 100, L1 = 102, R1 = 103, L2 = 104, R2 = 105, THUMBL = 106, THUMBR = 107,
            START = 108, SELECT = 109;
    public static final int[] BINDABLE = {A, B, X, Y, L1, R1, L2, R2, THUMBL, THUMBR, START, SELECT};
    public static final String[] BUTTON_NAMES = {"A", "B", "X", "Y", "L1", "R1", "L2", "R2", "L3", "R3", "START", "SELECT"};
    /** The default button per action. */
    public static final int[] DEFAULT = {A, B, START, A};
    /** Result of {@link #bind}: OK, or the action that already uses the button, or not bindable. */
    public static final int BIND_OK = -1, BIND_BAD = -2;

    private static final String KEY = "btn_";
    private final int[] code = DEFAULT.clone();
    private MetaProgress.Store store;

    public void load(MetaProgress.Store st) {
        store = st;
        for (int a = 0; a < COUNT; a++) {
            int v = st == null ? 0 : st.getInt(KEY + a, 0); // 0 = default
            code[a] = bindable(v) ? v : DEFAULT[a];
        }
        // a stored set that clashes (hand-edited prefs) falls back to the defaults
        for (int a = 0; a < COUNT; a++) {
            for (int b = a + 1; b < COUNT; b++) if (clash(a, b) && code[a] == code[b]) reset();
        }
    }

    public int code(int action) {
        return code[action];
    }

    public static boolean bindable(int c) {
        for (int b : BINDABLE) if (b == c) return true;
        return false;
    }

    public static String buttonName(int c) {
        for (int i = 0; i < BINDABLE.length; i++) if (BINDABLE[i] == c) return BUTTON_NAMES[i];
        return "?";
    }

    /** Actions that can be pressed in the same screen state may not share a button (confirm/back/pause in menus; dash/pause playing). */
    private static boolean clash(int a, int b) {
        if (a == b) return false;
        boolean menuA = a != DASH, menuB = b != DASH;
        boolean playA = a == DASH || a == PAUSE, playB = b == DASH || b == PAUSE;
        return menuA && menuB || playA && playB;
    }

    /** The action that already uses {@code c} and would clash with {@code action}, or {@link #BIND_OK}. */
    public int conflict(int action, int c) {
        for (int o = 0; o < COUNT; o++) if (clash(action, o) && code[o] == c) return o;
        return BIND_OK;
    }

    /** Binds; returns {@link #BIND_OK}, {@link #BIND_BAD}, or the clashing action (nothing changes then). */
    public int bind(int action, int c) {
        if (action < 0 || action >= COUNT || !bindable(c)) return BIND_BAD;
        int o = conflict(action, c);
        if (o != BIND_OK) return o;
        code[action] = c;
        if (store != null) store.putInt(KEY + action, c == DEFAULT[action] ? 0 : c);
        return BIND_OK;
    }

    public void reset() {
        for (int a = 0; a < COUNT; a++) {
            code[a] = DEFAULT[a];
            if (store != null) store.putInt(KEY + a, 0);
        }
    }

    public boolean isDefault() {
        for (int a = 0; a < COUNT; a++) if (code[a] != DEFAULT[a]) return false;
        return true;
    }

    /** True when the dash still sits on its default button: then every face and shoulder button dashes, as before the remap. */
    public boolean legacyDash() {
        return code[DASH] == DEFAULT[DASH];
    }

    public boolean isDash(int c) {
        if (c == code[DASH]) return true;
        return legacyDash() && (c == A || c == B || c == X || c == Y || c == L1 || c == R1 || c == L2 || c == R2) && c != code[PAUSE];
    }

    /** What a gamepad button does: one of the actions, or -1 for nothing. {@code playing} picks the screen state. */
    public int action(int c, boolean playing) {
        if (c == code[PAUSE]) return PAUSE;
        if (playing) return isDash(c) ? DASH : -1;
        if (c == code[CONFIRM]) return CONFIRM;
        if (c == code[BACK]) return BACK;
        return -1;
    }
}
