package se.felixarvid.thorsurvivors.screen;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import java.io.InputStream;

import se.felixarvid.thorsurvivors.audio.AudioEngine;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Input;
import se.felixarvid.thorsurvivors.game.MetaProgress;
import se.felixarvid.thorsurvivors.render.SpriteAtlas;

/** Top screen: the game world. Owns the second-screen manager. */
public final class MainActivity extends Activity {
    private GameView gameView;
    private DualScreenManager dual;
    private AudioEngine audio;
    private PrefsStore prefs;
    private final PauseGuard guard = new PauseGuard();
    /** v1.5 B8 (79): the art decodes on this thread while a splash shows; the second screen starts when it is done. */
    private volatile boolean booted;
    private boolean started;
    private boolean padL1, padR1, padSel, comboLatched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        Engine eng = Engine.get();
        if (!eng.isReady()) {
            try (InputStream in = getAssets().open("game_data.json");
                 InputStream over = getAssets().open("ashfall_data.json")) {
                GameData data = GameData.load(in, over); // Ashfall names merged over the Vampire Survivors data
                prefs = new PrefsStore(this);
                eng.init(data, new MetaProgress(data, prefs));
                eng.attachStore(prefs); // v1.5 B3: options, button map, codex and tutorial flag
            } catch (Exception e) {
                throw new RuntimeException("Cannot load game_data.json", e);
            }
        }
        setVolumeControlStream(AudioManager.STREAM_MUSIC); // the hardware volume keys control the game's audio
        audio = new AudioEngine(this, eng);
        FrameLayout root = new FrameLayout(this);
        gameView = new GameView(this);
        gameView.setAudio(audio);
        root.addView(gameView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        Immersive.apply(getWindow());
        dual = new DualScreenManager(this, root, gameView, guard);
        preferFastestDisplayMode();
        Haptics.attach(this);
        gameView.setReady(false);
        final android.content.res.AssetManager am = getAssets();
        final long t0 = android.os.SystemClock.uptimeMillis();
        Thread boot = new Thread(new Runnable() {
            @Override
            public void run() {
                if (!SpriteAtlas.get().isLoaded()) SpriteAtlas.get().load(am); // decodes ~50 MB of sheets once
                se.felixarvid.thorsurvivors.ui.UiArt.get().ensure(am); // ASHFALL UI typeface and textures
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        booted = true;
                        Runtime rt = Runtime.getRuntime();
                        android.util.Log.i("ThorBoot", "art ready after " + (android.os.SystemClock.uptimeMillis() - t0)
                                + " ms, heap " + ((rt.totalMemory() - rt.freeMemory()) >> 20) + " of " + (rt.maxMemory() >> 20) + " MB");
                        if (!isFinishing() && !isDestroyed()) {
                            gameView.setReady(true);
                            if (started) dual.start();
                        }
                    }
                });
            }
        }, "boot-art");
        boot.start();
    }

    /** v1.5 B8 (72): ask for the panel's best refresh rate at the current size (API 23+ modes), and tell the view. */
    private void preferFastestDisplayMode() {
        try {
            android.view.Display d = getWindowManager().getDefaultDisplay(); // getDisplay() is API 30, minSdk is 29
            if (d == null) return;
            android.view.Display.Mode cur = d.getMode();
            android.view.Display.Mode[] ms = d.getSupportedModes();
            int[] w = new int[ms.length], h = new int[ms.length];
            float[] hz = new float[ms.length];
            for (int i = 0; i < ms.length; i++) {
                w[i] = ms[i].getPhysicalWidth();
                h[i] = ms[i].getPhysicalHeight();
                hz[i] = ms[i].getRefreshRate();
            }
            int best = DisplayModes.fastest(w, h, hz, cur.getPhysicalWidth(), cur.getPhysicalHeight(), cur.getRefreshRate());
            if (best >= 0) {
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.preferredDisplayModeId = ms[best].getModeId();
                getWindow().setAttributes(lp);
            }
            gameView.setTargetHz(DisplayModes.maxHz(w, h, hz, cur.getPhysicalWidth(), cur.getPhysicalHeight(), cur.getRefreshRate()));
        } catch (RuntimeException ignored) {
            // a hint only: stay on whatever the system runs
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        if (booted) dual.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Immersive.apply(getWindow());
        if (booted) dual.refresh();
        audio.resume();
    }

    @Override
    protected void onPause() {
        // pause an in-progress run; the game thread stops with the surface
        Engine.get().input.post(Input.CMD_AUTOPAUSE, 0, 0, 0);
        Engine.get().input.clearMovement();
        audio.pause();
        if (prefs != null) prefs.flush(); // v1.5 B8: queued saves reach the disk before the process can be killed
        super.onPause();
    }

    @Override
    protected void onStop() {
        started = false;
        dual.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        dual.stop();
        gameView.setAudio(null);
        audio.release();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            Engine.get().input.clearMovement();
            // v1.5 B8 (80): a system shade or another window took the focus: pause, and stay paused until the player presses
            if (guard.focusChanged(false)) Engine.get().input.post(Input.CMD_AUTOPAUSE, 0, 0, 0);
            gameView.held = true;
        } else Immersive.apply(getWindow());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        guard.userInput();
        gameView.held = false;
        debugCombo(event);
        if (InputRouter.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    /** Hold L1 + R1 + Select to toggle the frame-time overlay (once per press of the combination). */
    private void debugCombo(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_L1: padL1 = down; break;
            case KeyEvent.KEYCODE_BUTTON_R1: padR1 = down; break;
            case KeyEvent.KEYCODE_BUTTON_SELECT: padSel = down; break;
            default: return;
        }
        if (padL1 && padR1 && padSel) {
            if (!comboLatched) gameView.toggleDebug();
            comboLatched = true;
        } else comboLatched = false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (InputRouter.onMotion(ev)) return true;
        return super.dispatchGenericMotionEvent(ev);
    }
}
