package se.felixarvid.thorsurvivors.screen;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.InputDevice;

/**
 * v1.5 B8 (item 73): a short rumble on the gamepad when the hero is hit (scaled by the damage) and on level-up. Uses
 * {@code InputDevice.getVibratorManager()}, which exists from Android 12 (API 31); on older systems, or without a
 * gamepad that can vibrate, every call is a no-op. The mapping from damage to strength is pure ({@link #intensity},
 * {@link #amplitude}, {@link #millis}) so it is unit-tested; the vibrator call is device-only.
 */
public final class Haptics {
    /** Fraction of max HP at which a hit gives the strongest rumble. */
    static final float FULL_AT = 0.25f;
    static final float MIN_INTENSITY = 0.18f;
    static final long MIN_GAP_MS = 90;

    private static volatile Vibrator vib;
    private static volatile boolean probed;
    private static long lastMs;

    private Haptics() {
    }

    /** 0 for no damage, else 0.18 (a scratch) to 1 (a quarter of max HP or more), rising linearly in between. */
    static float intensity(float damage, float maxHp) {
        if (!(damage > 0f) || !(maxHp > 0f)) return 0f;
        float f = Math.min(1f, damage / (maxHp * FULL_AT));
        return MIN_INTENSITY + (1f - MIN_INTENSITY) * f;
    }

    /** VibrationEffect amplitude 1..255 for an intensity (0 stays 0 = no rumble). */
    static int amplitude(float intensity) {
        if (!(intensity > 0f)) return 0;
        return Math.max(1, Math.min(255, Math.round(intensity * 255f)));
    }

    /** Duration in ms: 20 for the lightest, 90 for the heaviest. */
    static long millis(float intensity) {
        if (!(intensity > 0f)) return 0;
        return 20 + Math.round(70f * Math.min(1f, intensity));
    }

    /** v1.5 B8 STUB: batch 3 replaces this with {@code Settings.haptics()}; until then rumble is on. */
    static boolean enabled() {
        return true;
    }

    /** Looks for a gamepad with a vibrator (API 31+). Safe to call again; does nothing below Android 12. */
    public static void attach(Context ctx) {
        probed = true;
        vib = null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            vib = V31.find(ctx);
        } catch (Throwable t) {
            vib = null; // a hint only: no rumble is fine
        }
    }

    /** The hero lost {@code damage} HP (of {@code maxHp}). */
    public static void hit(float damage, float maxHp) {
        pulse(intensity(damage, maxHp));
    }

    /** Level-up: a medium double-feel pulse. */
    public static void levelUp() {
        pulse(0.6f);
    }

    private static void pulse(float intensity) {
        Vibrator v = vib;
        if (v == null || !probed || !enabled() || !(intensity > 0f)) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastMs < MIN_GAP_MS) return;
        lastMs = now;
        try {
            v.vibrate(VibrationEffect.createOneShot(millis(intensity), amplitude(intensity)));
        } catch (Throwable ignored) {
            // device refused: no-op
        }
    }

    /** API 31 calls live here so the outer class verifies on Android 10 and 11. */
    private static final class V31 {
        static Vibrator find(Context ctx) {
            InputManager im = (InputManager) ctx.getSystemService(Context.INPUT_SERVICE);
            if (im == null) return null;
            for (int id : im.getInputDeviceIds()) {
                InputDevice d = im.getInputDevice(id);
                if (d == null) continue;
                int src = d.getSources();
                boolean pad = (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                        || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
                if (!pad) continue;
                VibratorManager vm = d.getVibratorManager();
                Vibrator v = vm == null ? null : vm.getDefaultVibrator();
                if (v != null && v.hasVibrator()) return v;
            }
            return null;
        }
    }
}
