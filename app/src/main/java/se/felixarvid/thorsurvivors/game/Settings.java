package se.felixarvid.thorsurvivors.game;

/**
 * v1.5 B3 (item 21): the player's options, persisted as one key group ({@code set_*}) through the same {@link MetaProgress.Store}
 * as the records. Other systems read the getters only; every default is the behaviour before the options existed (full shake, all
 * damage numbers, standard telegraphs, normal text, no aim assist). Music and SFX volume are the menu's existing steps in
 * {@link MetaProgress}; {@link #musicVol} and {@link #sfxVol} read them (0..1), they are not stored twice.
 * Android-free; {@link #get} is the process-wide instance, {@link #load} attaches the store once at start-up.
 */
public final class Settings {
    /** Shake strength steps (0, 50 %, 100 %), damage numbers (off, crits, all), telegraphs, aim assist. */
    public static final int SHAKE_OFF = 0, SHAKE_HALF = 1, SHAKE_FULL = 2;
    public static final int NUM_OFF = 0, NUM_CRITS = 1, NUM_ALL = 2;
    public static final int AIM_OFF = 0, AIM_SOFT = 1, AIM_FULL = 2;
    public static final String[] SHAKE_NAMES = {"OFF", "50%", "100%"};
    public static final String[] NUM_NAMES = {"OFF", "CRITS", "ALL"};
    public static final String[] AIM_NAMES = {"OFF", "SOFT", "FULL"};
    public static final String[] TELE_NAMES = {"STANDARD", "SHAPES"};
    public static final String[] ON_OFF = {"OFF", "ON"};
    /** Text scale of "large text" (read by the HUD through {@link #textScale}). */
    public static final float LARGE_TEXT = 1.25f;
    /** The telegraph pair of the Shapes palette (blue fill, orange outline): safe for red-green colour blindness. */
    public static final int SHAPES_FILL = 0x663C8CFF, SHAPES_EDGE = 0xFFFF9A1F;

    private static final Settings INSTANCE = new Settings();

    public static Settings get() {
        return INSTANCE;
    }

    private MetaProgress.Store store;
    private volatile int shake = SHAKE_FULL, numbers = NUM_ALL, aim = AIM_OFF;
    private volatile boolean shapes, large, haptics = true;
    public final ButtonMap buttons = new ButtonMap();

    /** Attaches the store and reads every option; null keeps the values in memory only (tests). */
    public void load(MetaProgress.Store st) {
        store = st;
        shake = clamp(get("shake", SHAKE_FULL), 2);
        numbers = clamp(get("numbers", NUM_ALL), 2);
        aim = clamp(get("aim", AIM_OFF), 2);
        shapes = get("shapes", 0) != 0;
        large = get("large", 0) != 0;
        haptics = get("haptics", 1) != 0;
        buttons.load(st);
    }

    /** Back to the defaults, in memory and in the store. */
    public void resetAll() {
        setShake(SHAKE_FULL);
        setNumbers(NUM_ALL);
        setAim(AIM_OFF);
        setShapes(false);
        setLarge(false);
        setHaptics(true);
        buttons.reset();
    }

    private int get(String k, int def) {
        return store == null ? def : store.getInt("set_" + k, def);
    }

    private void put(String k, int v) {
        if (store != null) store.putInt("set_" + k, v);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    private static int wrap(int v, int d, int n) {
        return ((v + d) % n + n) % n;
    }

    // ---- getters read by the other systems

    /** Screen shake multiplier: 0, 0.5 or 1. */
    public float shake() {
        return shake == SHAKE_OFF ? 0f : shake == SHAKE_HALF ? 0.5f : 1f;
    }

    /** {@link #NUM_OFF}, {@link #NUM_CRITS} or {@link #NUM_ALL}. */
    public int damageNumbers() {
        return numbers;
    }

    /** True when a damage number of this kind is drawn. */
    public boolean showDamageNumber(boolean crit) {
        return numbers == NUM_ALL || numbers == NUM_CRITS && crit;
    }

    public boolean largeText() {
        return large;
    }

    /** 1 or {@link #LARGE_TEXT}. */
    public float textScale() {
        return large ? LARGE_TEXT : 1f;
    }

    /** "Telegraphs: Shapes": danger zones get a hatched fill, a bold outline and the blue-orange pair. */
    public boolean colorblindShapes() {
        return shapes;
    }

    /** {@link #AIM_OFF} (the aim input or the facing, as before), {@link #AIM_SOFT} or {@link #AIM_FULL}. */
    public int autoAim() {
        return aim;
    }

    public boolean haptics() {
        return haptics;
    }

    /** Music volume 0..1: the menu's volume step. Without an engine the shipped default (2 of 3). */
    public float musicVol() {
        Engine e = Engine.get();
        return e.meta == null ? 2f / MetaProgress.VOL_MAX : SoundEvents.stepGain(e.meta.musicVol);
    }

    public float sfxVol() {
        Engine e = Engine.get();
        return e.meta == null ? 1f : SoundEvents.stepGain(e.meta.sfxVol);
    }

    // ---- telegraph palette (item 22)

    /** Telegraph fill of danger zones: the stage's own colour in Standard, a blue in Shapes (hatched by the caller, see {@link #hatched}). */
    public int telegraphFill(int standardArgb) {
        return shapes ? SHAPES_FILL : standardArgb;
    }

    /** Telegraph outline: the standard colour, or a bold orange against the blue fill. */
    public int telegraphEdge(int standardArgb) {
        return shapes ? SHAPES_EDGE : standardArgb;
    }

    /** Outline width multiplier: 1, or 2 in Shapes. */
    public float telegraphEdgeMul() {
        return shapes ? 2f : 1f;
    }

    /** True when the danger zone must be drawn hatched (diagonal lines) on top of the fill. */
    public boolean hatched() {
        return shapes;
    }

    // ---- setters (the options screen)

    public void setShake(int v) {
        shake = clamp(v, 2);
        put("shake", shake);
    }

    public void setNumbers(int v) {
        numbers = clamp(v, 2);
        put("numbers", numbers);
    }

    public void setAim(int v) {
        aim = clamp(v, 2);
        put("aim", aim);
    }

    public void setShapes(boolean v) {
        shapes = v;
        put("shapes", v ? 1 : 0);
    }

    public void setLarge(boolean v) {
        large = v;
        put("large", v ? 1 : 0);
    }

    public void setHaptics(boolean v) {
        haptics = v;
        put("haptics", v ? 1 : 0);
    }

    public void stepShake(int d) {
        setShake(wrap(shake, d, 3));
    }

    public void stepNumbers(int d) {
        setNumbers(wrap(numbers, d, 3));
    }

    public void stepAim(int d) {
        setAim(wrap(aim, d, 3));
    }
}
