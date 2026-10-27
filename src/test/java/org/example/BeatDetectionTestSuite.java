package org.example;

import org.example.audio.AudioData;
import org.example.audio.AudioDecoder;
import org.example.audio.AudioGenerator;
import org.example.beat.BeatEvent;
import org.example.beat.BeatTrackResult;
import org.example.beat.BeatTracker;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BeatDetectionTestSuite {

    @Test
    void testAudioSaveAndDecode() throws IOException, Exception {
        AudioData synthetic = AudioGenerator.createDemoTrack();
        File tempWav = Files.createTempFile("test_audio_", ".wav").toFile();
        tempWav.deleteOnExit();

        AudioGenerator.saveAsWav(synthetic, tempWav);
        assertTrue(tempWav.exists() && tempWav.length() > 0, "WAV file should be created");

        AudioData loaded = AudioDecoder.decode(tempWav);
        assertNotNull(loaded);
        assertEquals(synthetic.getSampleRate(), loaded.getSampleRate(), 0.1f);
        assertTrue(Math.abs(synthetic.getDurationSec() - loaded.getDurationSec()) < 0.2);
    }

    @Test
    void testTrackStartingDirectlyOnDownbeat() {
        // Track with 0 pickup beats (starts right on Beat 1 of Measure 1)
        int sampleRate = 44100;
        int totalSamples = sampleRate * 12;
        float[] buffer = new float[totalSamples];
        double currentTime = 0.5; // Starts on downbeat
        double beatPeriod = 60.0 / 120.0; // 120 BPM
        java.util.Random random = new java.util.Random(123);

        for (int m = 1; m <= 4; m++) {
            for (int b = 1; b <= 4; b++) {
                int sampleIdx = (int) (currentTime * sampleRate);
                if (b == 1) {
                    // Strong downbeat: Kick + Bass
                    synthesizeKickDrum(buffer, sampleIdx, sampleRate);
                } else if (b == 2 || b == 4) {
                    // Snare on 2 and 4
                    synthesizeSnareDrum(buffer, sampleIdx, sampleRate, random);
                } else {
                    synthesizeHiHat(buffer, sampleIdx, sampleRate, random);
                }
                currentTime += beatPeriod;
            }
        }

        AudioData audio = new AudioData(buffer, sampleRate, new byte[0], null);
        BeatTracker tracker = new BeatTracker();
        BeatTrackResult result = tracker.track(audio);

        assertNotNull(result);
        assertEquals(0, result.getPickupBeatsCount(), "Should detect 0 pickup beats for track starting on downbeat");
        assertFalse(result.hasPickupMeasure());

        List<BeatEvent> beats = result.getBeats();
        assertFalse(beats.isEmpty());
        BeatEvent firstBeat = beats.get(0);
        assertEquals(1, firstBeat.getMeasure(), "First beat should be measure 1");
        assertEquals(1, firstBeat.getBeatInMeasure(), "First beat should be beat 1");
        assertTrue(firstBeat.isDownbeat());
    }

    @Test
    void testAudioBeatPlayerDispatch() throws Exception {
        AudioData synthetic = AudioGenerator.createDemoTrack();
        BeatTracker tracker = new BeatTracker();
        BeatTrackResult result = tracker.track(synthetic);

        org.example.player.AudioBeatPlayer player = new org.example.player.AudioBeatPlayer(synthetic, result);
        java.util.concurrent.atomic.AtomicInteger beatCount = new java.util.concurrent.atomic.AtomicInteger(0);

        player.addListener(event -> {
            beatCount.incrementAndGet();
            assertNotNull(event.toLogString());
        });

        // Test play
        player.play();
        Thread.sleep(600); // Wait for first beat(s)
        player.stop();

        assertTrue(beatCount.get() >= 1, "At least one beat event should have been dispatched to listener");
    }

    @Test
    void testMp4DecodingAndBeatTracking() throws Exception {
        if (!AudioDecoder.isFfmpegAvailable()) {
            System.out.println("Skipping MP4 test: ffmpeg not available.");
            return;
        }

        File tempMp4 = Files.createTempFile("test_demo_", ".mp4").toFile();
        tempMp4.deleteOnExit();

        boolean created = AudioGenerator.createDemoMp4(tempMp4);
        assertTrue(created, "Demo MP4 file should be created via ffmpeg");
        assertTrue(tempMp4.length() > 0, "MP4 file should have non-zero size");

        AudioData mp4Audio = AudioDecoder.decode(tempMp4);
        assertNotNull(mp4Audio);
        assertTrue(mp4Audio.getDurationSec() > 10.0);

        BeatTracker tracker = new BeatTracker();
        BeatTrackResult result = tracker.track(mp4Audio);

        assertNotNull(result);
        assertFalse(result.getBeats().isEmpty(), "Beats should be detected from MP4 audio");
        assertTrue(result.hasPickupMeasure(), "Pickup measure should be detected from MP4 audio");
    }

    @Test
    void testUserTracksDecodingIfExists() throws Exception {
        File tracksDir = new File("tracks");
        File test2 = new File(tracksDir, "test2.mp3");
        if (test2.exists()) {
            AudioData data = AudioDecoder.decode(test2);
            assertNotNull(data);
            assertTrue(data.getDurationSec() > 0);
            System.out.printf("Successfully decoded test2.mp3: duration=%.2fs, rate=%.0fHz%n",
                    data.getDurationSec(), data.getSampleRate());
        }

        File test1 = new File(tracksDir, "test.mp3");
        if (test1.exists()) {
            AudioData data = AudioDecoder.decode(test1);
            assertNotNull(data);
            assertTrue(data.getDurationSec() > 0);
            System.out.printf("Successfully decoded test.mp3: duration=%.2fs, rate=%.0fHz%n",
                    data.getDurationSec(), data.getSampleRate());
        }
    }

    private void synthesizeKickDrum(float[] buffer, int start, int sampleRate) {
        int len = (int) (0.25 * sampleRate);
        for (int i = 0; i < len && (start + i) < buffer.length; i++) {
            double t = (double) i / sampleRate;
            double freq = 45.0 + 120.0 * Math.exp(-30.0 * t);
            double env = Math.exp(-10.0 * t);
            buffer[start + i] += (float) (Math.sin(2.0 * Math.PI * freq * t) * env);
        }
    }

    private void synthesizeSnareDrum(float[] buffer, int start, int sampleRate, java.util.Random random) {
        int len = (int) (0.15 * sampleRate);
        for (int i = 0; i < len && (start + i) < buffer.length; i++) {
            double t = (double) i / sampleRate;
            double body = Math.sin(2.0 * Math.PI * 220.0 * t) * Math.exp(-20.0 * t) * 0.4;
            double noise = (random.nextDouble() * 2.0 - 1.0) * Math.exp(-30.0 * t) * 0.5;
            buffer[start + i] += (float) (body + noise);
        }
    }

    private void synthesizeHiHat(float[] buffer, int start, int sampleRate, java.util.Random random) {
        int len = (int) (0.05 * sampleRate);
        for (int i = 0; i < len && (start + i) < buffer.length; i++) {
            double t = (double) i / sampleRate;
            buffer[start + i] += (float) ((random.nextDouble() * 2.0 - 1.0) * Math.exp(-70.0 * t) * 0.3);
        }
    }
}
