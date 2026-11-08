package se.felixarvid.thorsurvivors.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import se.felixarvid.thorsurvivors.game.Engine;

/** v1.5 B8 (75): each stage has its own calm track, the files exist, and a missing one falls back to the old track. */
public class StageMusicTest {
    private static final String[] STAGES = {"FLATS", "MINES", "HYDRO", "REFINERY", "WRECK"};
    private static final Path AUDIO = Paths.get("src/main/assets/audio");

    @Test
    public void everyStageMapsToAnExistingFile() throws Exception {
        JSONObject music = new JSONObject(new String(Files.readAllBytes(AUDIO.resolve("manifest.json")), StandardCharsets.UTF_8))
                .getJSONObject("music");
        for (String st : STAGES) {
            String track = MusicDirector.stageTrack(st);
            assertTrue(st + " -> " + track + " is a stage track", MusicDirector.isStageTrack(track));
            assertTrue(st + " -> " + track + " is in the manifest", music.has(track));
            assertTrue(st + " file", Files.exists(AUDIO.resolve(music.getJSONObject(track).getString("file"))));
        }
        assertTrue(music.has(MusicDirector.COMBAT));
        assertTrue(Files.exists(AUDIO.resolve(music.getJSONObject(MusicDirector.COMBAT).getString("file"))));
    }

    @Test
    public void stagesDoNotShareATrack() {
        Set<String> seen = new HashSet<>();
        for (String st : STAGES) assertTrue(st + " shares a track", seen.add(MusicDirector.stageTrack(st)));
    }

    @Test
    public void theOldMappingIsTheFallback() {
        assertEquals("forest", MusicDirector.legacyStageTrack("FLATS"));
        assertEquals("forest", MusicDirector.legacyStageTrack("WRECK"));
        assertEquals("forest", MusicDirector.legacyStageTrack("REFINERY"));
        assertEquals("library", MusicDirector.legacyStageTrack("MINES"));
        assertEquals("library", MusicDirector.legacyStageTrack("HYDRO"));
        for (String st : STAGES) assertTrue(MusicDirector.isStageTrack(MusicDirector.legacyStageTrack(st)));
    }

    @Test
    public void onlyStageTracksAreLayerable() {
        for (String t : new String[]{"menu", "boss", "final", "gameover", "victory", "combat"}) {
            assertFalse(t, MusicDirector.isStageTrack(t));
        }
        assertNotEquals("combat", MusicDirector.trackFor(Engine.PH_PLAYING, "HYDRO", false, false));
        assertEquals("hydro", MusicDirector.trackFor(Engine.PH_PLAYING, "HYDRO", false, false));
        assertEquals("boss", MusicDirector.trackFor(Engine.PH_PLAYING, "WRECK", true, false));
    }
}
