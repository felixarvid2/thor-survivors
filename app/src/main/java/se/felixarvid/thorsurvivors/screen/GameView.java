package se.felixarvid.thorsurvivors.screen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import se.felixarvid.thorsurvivors.audio.AudioEngine;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.render.WorldRenderer;

/**
 * Top-screen world surface. A dedicated thread is paced by the display's vsync (Choreographer), so it runs at
 * whatever rate the panel does (60 or 120 Hz). Each frame advances the simulation by the real time between vsyncs
 * ({@link Engine#advance} splits it into steps of at most 1/60 s) and draws once; there is no sleeping and no
 * fixed-step accumulator, so a frame never gets 0 or 2 updates in alternation.
 */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private final WorldRenderer renderer = new WorldRenderer();
    private volatile boolean dual;
    private volatile AudioEngine audio;
    /** v1.5 B8: false while the atlas and UI art decode on the boot thread (79); a plain splash is drawn instead. */
    private volatile boolean ready = true;
    private volatile boolean debug;
    private volatile float targetHz = 120f;
    private final FrameStats stats = new FrameStats();
    private final DebugOverlay overlay = new DebugOverlay();
    private final Paint splash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private HandlerThread thread;
    private Loop loop;

    public GameView(Context ctx) {
        super(ctx);
        getHolder().addCallback(this);
        setFocusable(false);
    }

    public void setAudio(AudioEngine audio) {
        this.audio = audio;
    }

    /** False until the art is decoded (a splash is drawn meanwhile); the game does not advance while not ready. */
    public void setReady(boolean ready) {
        this.ready = ready;
    }

    /** The refresh rate to request from the Surface (the panel's best at the current size). */
    public void setTargetHz(float hz) {
        if (hz >= 30f) targetHz = hz;
    }

    /** Hidden debug overlay (frame time p95, entity counts). */
    public void toggleDebug() {
        debug = !debug;
    }

    boolean debugOn() {
        return debug;
    }

    public void setDual(boolean dual) {
        this.dual = dual;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        stopLoop();
        thread = new HandlerThread("game-loop", android.os.Process.THREAD_PRIORITY_DISPLAY);
        thread.start();
        loop = new Loop(holder);
        new Handler(thread.getLooper()).post(loop::start);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // ask for the panel's best rate (120 Hz on the Thor); Surface.setFrameRate is API 30+, minSdk is 29, so it is
        // guarded. DEFAULT compatibility lets the system fall back to what it supports.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                holder.getSurface().setFrameRate(targetHz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            } catch (Throwable ignored) {
                // a hint only
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopLoop();
    }

    volatile boolean held;

    private void drawSplash(Canvas c) {
        c.drawColor(0xFF0A0C0B);
        splash.setColor(0xFFC8D0C0);
        splash.setTextAlign(Paint.Align.CENTER);
        splash.setTextSize(getHeight() / 18f);
        c.drawText("ASHFALL: LUMEN TIDE", getWidth() / 2f, getHeight() / 2f, splash);
    }

    private void stopLoop() {
        Loop l = loop;
        HandlerThread t = thread;
        loop = null;
        thread = null;
        if (l != null) l.stop();
        if (t != null) {
            t.quitSafely();
            try {
                t.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private final class Loop implements Choreographer.FrameCallback {
        private final SurfaceHolder holder;
        private volatile boolean running = true;
        private Choreographer choreographer;
        private long lastNs;

        Loop(SurfaceHolder holder) {
            this.holder = holder;
        }

        void start() { // runs on the game thread, which owns the Choreographer instance
            choreographer = Choreographer.getInstance();
            choreographer.postFrameCallback(this);
        }

        void stop() {
            running = false;
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!running) return;
            Engine eng = Engine.get();
            float dt = lastNs == 0 ? 0f : (frameTimeNanos - lastNs) / 1e9f;
            lastNs = frameTimeNanos;
            if (dt > 0f) stats.add(dt * 1000f);
            Canvas c = null;
            try {
                c = holder.lockHardwareCanvas();
            } catch (Throwable t) {
                try {
                    c = holder.lockCanvas();
                } catch (Throwable ignored) {
                    c = null;
                }
            }
            try {
                if (!ready) {
                    if (c != null) drawSplash(c);
                } else synchronized (eng.lock) {
                    Sim before = eng.sim;
                    float hp0 = before == null ? 0f : before.hp;
                    int lv0 = before == null ? 0 : before.level;
                    eng.advance(renderer.filterDt(dt)); // v1.5 B1: hit-stop on elite and boss kills
                    Sim after = eng.sim;
                    if (after != null && after == before && eng.phase != Engine.PH_RESULT) {
                        if (after.hp < hp0) Haptics.hit(hp0 - after.hp, after.maxHp);
                        if (after.level > lv0) Haptics.levelUp();
                    }
                    if (c != null) {
                        renderer.draw(c, getWidth(), getHeight(), eng, dual);
                        if (debug) overlay.draw(c, getWidth(), getHeight(), stats, after, held);
                    }
                }
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
            AudioEngine a = audio;
            if (a != null) a.update(); // game thread: drains the sound events
            if (running) choreographer.postFrameCallback(this);
        }
    }
}
