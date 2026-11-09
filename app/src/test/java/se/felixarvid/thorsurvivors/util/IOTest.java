package se.felixarvid.thorsurvivors.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;

public class IOTest {
    @Test
    public void readsEverythingAcrossBufferBoundaries() throws Exception {
        byte[] data = new byte[20000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31);
        assertArrayEquals(data, IO.readAll(new ByteArrayInputStream(data)));
        assertEquals(0, IO.readAll(new ByteArrayInputStream(new byte[0])).length);
    }

    @Test
    public void decodesUtf8() throws Exception {
        String s = "åäö {\"k\": 1}";
        assertEquals(s, IO.readUtf8(new ByteArrayInputStream(s.getBytes("UTF-8"))));
    }

    @org.junit.Rule
    public org.junit.rules.TemporaryFolder tmp = new org.junit.rules.TemporaryFolder();

    @Test
    public void atomicWriteRoundTripsAndKeepsOneBackup() throws Exception {
        java.io.File f = new java.io.File(tmp.getRoot(), "s.bin");
        IO.writeAtomic(f, "one".getBytes("UTF-8"));
        assertEquals("one", new String(IO.readChecked(f), "UTF-8"));
        IO.writeAtomic(f, "two".getBytes("UTF-8"));
        IO.writeAtomic(f, "three".getBytes("UTF-8"));
        assertEquals("three", new String(IO.readChecked(f), "UTF-8"));
        assertEquals("two", new String(IO.readChecked(IO.backup(f)), "UTF-8"));
        assertEquals(false, new java.io.File(f.getPath() + ".tmp").exists());
        IO.writeAtomic(f, new byte[0]);
        assertEquals(0, IO.readChecked(f).length);
    }

    @Test
    public void damagedFilesReadAsNullAndFallBackToTheBackup() throws Exception {
        java.io.File f = new java.io.File(tmp.getRoot(), "d.bin");
        IO.writeAtomic(f, "old".getBytes("UTF-8"));
        IO.writeAtomic(f, "new".getBytes("UTF-8"));
        byte[] good = java.nio.file.Files.readAllBytes(f.toPath());
        for (int cut = 0; cut < good.length; cut++) { // every truncation is rejected
            java.nio.file.Files.write(f.toPath(), java.util.Arrays.copyOf(good, cut));
            org.junit.Assert.assertNull("cut " + cut, IO.readChecked(f));
            assertEquals("old", new String(IO.readWithBackup(f), "UTF-8"));
        }
        java.nio.file.Files.write(f.toPath(), new byte[]{'g', 'a', 'r', 'b', 'a', 'g', 'e', '!', 1, 2, 3, 4, 5, 6});
        org.junit.Assert.assertNull(IO.readChecked(f));
    }
}
