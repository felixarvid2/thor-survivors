package se.felixarvid.thorsurvivors.screen;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import se.felixarvid.thorsurvivors.game.MetaProgress;
import se.felixarvid.thorsurvivors.util.IO;

/**
 * Persistence for gold, shop ranks and records. v1.5 B8 (78): the values live in memory and in one checksummed file
 * ({@code files/save.bin}, {@link IO#writeAtomic}: temp file, fsync, atomic rename, one {@code .bak}); a damaged file
 * falls back to the backup, then to the old SharedPreferences ({@code thor_survivors}, read once for migration and as
 * the last resort), then to an empty save. Nothing here throws on corrupt data. Writes run on one background thread
 * and coalesce, so {@code putInt} is cheap on the game thread.
 */
public final class PrefsStore implements MetaProgress.Store {
    private final File file;
    private final Map<String, Integer> values = new TreeMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "save-io");
            t.setDaemon(true);
            return t;
        }
    });
    private final AtomicBoolean queued = new AtomicBoolean();

    public PrefsStore(Context ctx) {
        this(new File(ctx.getApplicationContext().getFilesDir(), "save.bin"),
                legacy(ctx.getApplicationContext().getSharedPreferences("thor_survivors", Context.MODE_PRIVATE)));
    }

    /** Test constructor: {@code legacy} may be null. */
    PrefsStore(File file, Map<String, ?> legacy) {
        this.file = file;
        byte[] p = IO.readWithBackup(file);
        if (p != null && parse(p)) return;
        values.clear();
        if (legacy != null) { // first run of v1.5, or every copy of the new file damaged
            for (Map.Entry<String, ?> e : legacy.entrySet()) {
                if (e.getValue() instanceof Integer) values.put(e.getKey(), (Integer) e.getValue());
            }
            if (!values.isEmpty()) schedule();
        }
    }

    private static Map<String, ?> legacy(SharedPreferences sp) {
        try {
            return sp.getAll();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean parse(byte[] p) {
        try {
            for (String line : new String(p, StandardCharsets.UTF_8).split("\n")) {
                if (line.isEmpty()) continue;
                int eq = line.lastIndexOf('=');
                if (eq <= 0) return false;
                values.put(line.substring(0, eq), Integer.parseInt(line.substring(eq + 1)));
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public synchronized int getInt(String key, int def) {
        Integer v = values.get(key);
        return v == null ? def : v;
    }

    @Override
    public synchronized void putInt(String key, int value) {
        Integer old = values.put(key, value);
        if (old == null || old != value) schedule();
    }

    private void schedule() {
        if (!queued.compareAndSet(false, true)) return; // a write is already queued and will see the newest values
        io.execute(new Runnable() {
            @Override
            public void run() {
                queued.set(false);
                try {
                    IO.writeAtomic(file, snapshot());
                } catch (java.io.IOException | RuntimeException e) {
                    // disk full or read-only: the next change tries again
                }
            }
        });
    }

    private synchronized byte[] snapshot() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : values.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Blocks until queued writes are on disk (tests, and onPause). */
    public void flush() {
        try {
            io.submit(new Runnable() {
                @Override
                public void run() {
                }
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException ignored) {
            // nothing to wait for
        }
    }
}
