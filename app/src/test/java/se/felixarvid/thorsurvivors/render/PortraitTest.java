package se.felixarvid.thorsurvivors.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.felixarvid.thorsurvivors.TestData;
import se.felixarvid.thorsurvivors.data.EquipDef;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.render.SpriteAtlas.Img;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * v1.5 B6 (items 51 and 52): every boss has a portrait of its own and every unique has an icon of its own: no two
 * decoded pictures match by hash, and no two are near-copies (mean difference of a 16 x 16 reduction above 10 of 255).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class PortraitTest {
    private static int[] grid(Img im) {
        int w = im.w(), h = im.h();
        int[] px = new int[w * h];
        im.sheet.getPixels(px, 0, w, im.src.left, im.src.top, w, h);
        return px;
    }

    /** 16 x 16 luminance reduction x 3 channels of the picture, to compare pictures of the same size. */
    private static int[] reduce(Img im) {
        int[] px = grid(im);
        int w = im.w(), h = im.h(), n = 16;
        int[] out = new int[n * n * 3];
        for (int gy = 0; gy < n; gy++) {
            for (int gx = 0; gx < n; gx++) {
                long r = 0, g = 0, b = 0, c = 0;
                for (int y = gy * h / n; y < (gy + 1) * h / n; y++) {
                    for (int x = gx * w / n; x < (gx + 1) * w / n; x++) {
                        int p = px[y * w + x];
                        r += p >> 16 & 255;
                        g += p >> 8 & 255;
                        b += p & 255;
                        c++;
                    }
                }
                int o = (gy * n + gx) * 3;
                out[o] = (int) (r / c);
                out[o + 1] = (int) (g / c);
                out[o + 2] = (int) (b / c);
            }
        }
        return out;
    }

    private static void allDistinct(String what, List<String> keys, SpriteAtlas a) {
        List<int[]> hashes = new ArrayList<>(), small = new ArrayList<>();
        for (String k : keys) {
            Img im = a.img(k);
            assertNotNull(k, im);
            hashes.add(new int[]{java.util.Arrays.hashCode(grid(im))});
            small.add(reduce(im));
        }
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                assertTrue(what + ": " + keys.get(i) + " and " + keys.get(j) + " have the same pixels", hashes.get(i)[0] != hashes.get(j)[0]);
                long sum = 0;
                int[] p = small.get(i), q = small.get(j);
                for (int k = 0; k < p.length; k++) sum += Math.abs(p[k] - q[k]);
                double mean = sum / (double) p.length;
                assertTrue(what + ": " + keys.get(i) + " and " + keys.get(j) + " look alike, mean difference " + mean, mean > 10.0);
            }
        }
    }

    @Test
    public void everyBossHasItsOwnPortrait() throws Exception {
        SpriteAtlas a = SpriteAtlas.get();
        a.load(path -> new FileInputStream("src/main/assets/" + path));
        GameData d = TestData.load();
        List<String> keys = new ArrayList<>();
        for (String k : d.enemies.keySet()) if (a.has("icon.boss_" + k)) keys.add("icon.boss_" + k);
        assertTrue("the ten Ashfall bosses and the v1.4 finales: " + keys, keys.size() >= 18);
        for (String must : new String[]{"ROOTMIND", "IRONHEART", "THE_PILLAR", "FOREMAN_BRASS", "STOKER_CAPTAIN", "HULL_BREACHER_ALPHA",
                "SALVAGE_WARDEN", "VERDANT_WELDER"}) {
            assertTrue(must, keys.contains("icon.boss_" + must));
        }
        allDistinct("portrait", keys, a);
    }

    @Test
    public void everyUniqueHasItsOwnIcon() throws Exception {
        SpriteAtlas a = SpriteAtlas.get();
        a.load(path -> new FileInputStream("src/main/assets/" + path));
        GameData d = TestData.load();
        List<String> keys = new ArrayList<>();
        for (EquipDef e : d.equipment.values()) {
            if (!e.unique) continue;
            assertTrue("icon of " + e.name, a.has("icon." + e.icon));
            keys.add("icon." + e.icon);
        }
        assertTrue("nine uniques: " + keys, keys.size() >= 9);
        assertEquals("no two uniques share an icon key", keys.size(), new java.util.HashSet<>(keys).size());
        allDistinct("unique icon", keys, a);
    }
}
