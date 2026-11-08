package se.felixarvid.thorsurvivors.screen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import se.felixarvid.thorsurvivors.util.IO;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/** v1.5 B8 (78): saves survive truncation, garbage and an empty file; the backup and the old prefs take over. */
public class PrefsStoreTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File save() {
        return new File(tmp.getRoot(), "save.bin");
    }

    private PrefsStore open(Map<String, ?> legacy) {
        return new PrefsStore(save(), legacy);
    }

    @Test
    public void valuesSurviveARestart() {
        PrefsStore a = open(null);
        a.putInt("gold", 120);
        a.putInt("rank.hp", 3);
        a.flush();
        PrefsStore b = open(null);
        assertEquals(120, b.getInt("gold", 0));
        assertEquals(3, b.getInt("rank.hp", 0));
        assertEquals(-1, b.getInt("missing", -1));
    }

    @Test
    public void aSecondSaveKeepsTheFirstAsBackup() throws Exception {
        PrefsStore a = open(null);
        a.putInt("gold", 10);
        a.flush();
        a.putInt("gold", 20);
        a.flush();
        assertTrue(IO.backup(save()).isFile());
        Files.write(save().toPath(), new byte[]{1, 2, 3}); // the main file is destroyed
        assertEquals("backup holds the previous save", 10, open(null).getInt("gold", 0));
    }

    @Test
    public void truncatedEmptyAndGarbageFilesNeverCrash() throws Exception {
        PrefsStore a = open(null);
        a.putInt("gold", 77);
        a.flush();
        byte[] good = Files.readAllBytes(save().toPath());
        for (int cut : new int[]{0, 3, 11, 12, good.length - 1}) {
            Files.write(save().toPath(), java.util.Arrays.copyOf(good, cut));
            Files.deleteIfExists(IO.backup(save()).toPath());
            assertEquals("cut at " + cut, 5, open(null).getInt("gold", 5));
        }
        byte[] junk = new byte[500];
        new java.util.Random(1).nextBytes(junk);
        Files.write(save().toPath(), junk);
        assertEquals(5, open(null).getInt("gold", 5));
        byte[] flipped = good.clone();
        flipped[flipped.length - 1] ^= 0x55; // checksum must catch a flipped bit
        Files.write(save().toPath(), flipped);
        assertEquals(5, open(null).getInt("gold", 5));
    }

    @Test
    public void oldSharedPreferencesAreImportedOnce() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("gold", 900);
        legacy.put("flag", "not an int");
        PrefsStore a = open(legacy);
        assertEquals(900, a.getInt("gold", 0));
        a.flush();
        assertTrue(save().isFile());
        a.putInt("gold", 950);
        a.flush();
        assertEquals("the new file wins over the old prefs", 950, open(legacy).getInt("gold", 0));
    }

    @Test
    public void everyCopyDamagedFallsBackToTheOldPrefsThenToEmpty() throws Exception {
        PrefsStore a = open(null);
        a.putInt("gold", 5);
        a.flush();
        a.putInt("gold", 6);
        a.flush();
        Files.write(save().toPath(), new byte[]{9});
        Files.write(IO.backup(save()).toPath(), new byte[]{9});
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("gold", 4);
        assertEquals(4, open(legacy).getInt("gold", 0));
        Files.write(save().toPath(), new byte[]{9});
        Files.write(IO.backup(save()).toPath(), new byte[]{9});
        assertEquals(0, open(null).getInt("gold", 0));
    }

    @Test
    public void aBadBackupIsNeverCopiedOverAGoodOne() throws Exception {
        PrefsStore a = open(null);
        a.putInt("gold", 1);
        a.flush();
        a.putInt("gold", 2);
        a.flush(); // main = 2, bak = 1
        Files.write(save().toPath(), new byte[]{0, 0}); // main destroyed
        PrefsStore b = open(null); // loads 1 from the backup
        b.putInt("gold", 3);
        b.flush(); // main is bad, so it must not replace the good backup
        assertEquals(3, open(null).getInt("gold", 0));
        assertEquals(1, new PrefsStore(IO.backup(save()), null).getInt("gold", 0));
    }

    @Test
    public void ioHelpersReturnNullForMissingFiles() {
        assertNull(IO.readChecked(new File(tmp.getRoot(), "nope")));
        assertNull(IO.readWithBackup(new File(tmp.getRoot(), "nope")));
    }
}
