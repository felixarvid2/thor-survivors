package se.felixarvid.thorsurvivors.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;

/** Stream helpers shared by the asset loaders (plain Java, no android.*). */
public final class IO {
    private IO() {
    }

    /** All remaining bytes of {@code in}. Does not close the stream. */
    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] b = new byte[8192];
        for (int n; (n = in.read(b)) != -1; ) out.write(b, 0, n);
        return out.toByteArray();
    }

    /** All remaining bytes of {@code in} decoded as UTF-8. Does not close the stream. */
    public static String readUtf8(InputStream in) throws IOException {
        return new String(readAll(in), "UTF-8");
    }

    // ------------------------------------------------------------------ v1.5 B8 (78): robust saves

    /** File framing: magic "TSV1", payload length, CRC32 of the payload, then the payload. */
    private static final int MAGIC = 0x54535631, HEAD = 12, MAX_SAVE = 1 << 20;

    private static File tmp(File f) {
        return new File(f.getPath() + ".tmp");
    }

    public static File backup(File f) {
        return new File(f.getPath() + ".bak");
    }

    /**
     * Writes {@code payload} so that a crash at any moment leaves a readable file: temp file, fsync, then an atomic
     * rename over {@code f}. The previous file, if it still verifies, becomes the single {@code .bak} first.
     */
    public static void writeAtomic(File f, byte[] payload) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(payload, 0, payload.length);
        ByteBuffer bb = ByteBuffer.allocate(HEAD + payload.length);
        bb.putInt(MAGIC).putInt(payload.length).putInt((int) crc.getValue()).put(payload);
        File t = tmp(f);
        try (FileOutputStream out = new FileOutputStream(t)) {
            out.write(bb.array());
            out.flush();
            out.getFD().sync();
        }
        if (readChecked(f) != null) { // only a good file may replace the backup
            Files.copy(f.toPath(), backup(f).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(t.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(t.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The payload of a file written by {@link #writeAtomic}, or null when it is missing, truncated or fails the checksum. Never throws. */
    public static byte[] readChecked(File f) {
        try {
            if (f == null || !f.isFile() || f.length() < HEAD || f.length() > MAX_SAVE + HEAD) return null;
            byte[] all;
            try (InputStream in = new FileInputStream(f)) {
                all = readAll(in);
            }
            ByteBuffer bb = ByteBuffer.wrap(all);
            if (bb.getInt() != MAGIC) return null;
            int len = bb.getInt(), want = bb.getInt();
            if (len < 0 || len != all.length - HEAD) return null;
            CRC32 crc = new CRC32();
            crc.update(all, HEAD, len);
            if ((int) crc.getValue() != want) return null;
            byte[] p = new byte[len];
            System.arraycopy(all, HEAD, p, 0, len);
            return p;
        } catch (IOException | RuntimeException e) {
            return null; // corrupt or unreadable: the caller falls back, the game never crashes on a save
        }
    }

    /** {@link #readChecked} of {@code f}, else of its {@code .bak}, else null. */
    public static byte[] readWithBackup(File f) {
        byte[] p = readChecked(f);
        return p != null ? p : readChecked(backup(f));
    }
}
