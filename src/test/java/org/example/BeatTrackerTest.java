package org.example;

import org.example.audio.AudioData;
import org.example.audio.AudioGenerator;
import org.example.beat.BeatEvent;
import org.example.beat.BeatTrackResult;
import org.example.beat.BeatTracker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BeatTrackerTest {

    @Test
    void testTrackSyntheticAudioWithPickupAndTempoChange() {
        AudioData demoAudio = AudioGenerator.createDemoTrack();
        assertNotNull(demoAudio);
        assertTrue(demoAudio.getDurationSec() > 10.0);

        BeatTracker tracker = new BeatTracker();
        BeatTrackResult result = tracker.track(demoAudio);

        assertNotNull(result);
        List<BeatEvent> beats = result.getBeats();
        assertFalse(beats.isEmpty(), "Beats should be detected");

        System.out.println("Total beats detected: " + beats.size());
        System.out.println("Pickup beats count: " + result.getPickupBeatsCount());
        System.out.println("Initial BPM: " + result.getInitialBpm());
        System.out.println("Average BPM: " + result.getAverageBpm());

        // Check pickup measure detection
        assertTrue(result.hasPickupMeasure(), "Demo track has a pickup beat (Beat 4 before bar 1), should be detected");
        assertEquals(1, result.getPickupBeatsCount(), "Should detect exactly 1 pickup beat");

        // First beat should be pickup beat (Measure 0, Beat 4)
        BeatEvent firstBeat = beats.get(0);
        assertTrue(firstBeat.isPickup(), "First beat should be marked as pickup");
        assertEquals(0, firstBeat.getMeasure(), "Measure should be 0 for pickup");
        assertEquals(4, firstBeat.getBeatInMeasure(), "Should be Beat 4 of pickup measure");

        // Second beat should be Bar 1, Beat 1 [Downbeat]
        BeatEvent secondBeat = beats.get(1);
        assertFalse(secondBeat.isPickup());
        assertEquals(1, secondBeat.getMeasure());
        assertEquals(1, secondBeat.getBeatInMeasure());
        assertTrue(secondBeat.isDownbeat(), "Second beat should be downbeat");

        // Check tempo change detection mid-way
        boolean foundTempoChange = false;
        double tempoBeforeChange = 0.0;
        double tempoAfterChange = 0.0;

        for (BeatEvent b : beats) {
            if (b.getMeasure() == 2) {
                tempoBeforeChange = b.getBpm();
            }
            if (b.getMeasure() == 6) {
                tempoAfterChange = b.getBpm();
            }
            if (b.isTempoChange()) {
                foundTempoChange = true;
            }
        }

        System.out.printf("Tempo before change: %.1f BPM, after change: %.1f BPM%n", tempoBeforeChange, tempoAfterChange);
        assertTrue(foundTempoChange, "Should detect tempo change mid-way");
        assertTrue(tempoAfterChange > tempoBeforeChange, "Tempo in section 2 (150 BPM) should be faster than section 1 (120 BPM)");
    }
}
