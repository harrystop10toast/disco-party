package org.example.audio;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Synthesizes test audio tracks with rhythm patterns, pickup measures,
 * and mid-way tempo changes for automated testing and demo playback.
 */
public class AudioGenerator {

    private static final float SAMPLE_RATE = 44100.0f;

    /**
     * Generates a test track demonstrating:
     * 1. A pickup beat (anacrusis on beat 4 before measure 1)
     * 2. 4 measures at 120 BPM
     * 3. 4 measures at 150 BPM (tempo change mid-way)
     */
    public static AudioData createDemoTrack() {
        int sampleRate = (int) SAMPLE_RATE;
        // Total duration ~ 15 seconds
        int totalSamples = sampleRate * 16;
        float[] buffer = new float[totalSamples];

        Random random = new Random(42);

        // We start with an intro silence of 0.5s.
        // Then at t = 0.5s, there is a pickup beat (Beat 4)
        // At t = 1.0s, Downbeat (Measure 1, Beat 1) at 120 BPM (period = 0.5s).
        double currentTime = 0.5; // Pickup beat time
        double tempoBpm1 = 120.0;
        double beatPeriod1 = 60.0 / tempoBpm1; // 0.5s

        // Pickup note (snare + bass note)
        synthesizeSnare(buffer, (int) (currentTime * sampleRate), sampleRate, random);
        currentTime += beatPeriod1; // now currentTime = 1.0s

        // Section 1: 4 measures at 120 BPM (16 beats)
        for (int m = 1; m <= 4; m++) {
            for (int b = 1; b <= 4; b++) {
                int sampleIdx = (int) (currentTime * sampleRate);
                if (b == 1) {
                    // Strong Downbeat: Kick + Bass + Hat
                    synthesizeKick(buffer, sampleIdx, sampleRate);
                    synthesizeBass(buffer, sampleIdx, sampleRate, 110.0f);
                    synthesizeHiHat(buffer, sampleIdx, sampleRate, random);
                } else if (b == 2 || b == 4) {
                    // Snare on 2 and 4
                    synthesizeSnare(buffer, sampleIdx, sampleRate, random);
                    synthesizeHiHat(buffer, sampleIdx, sampleRate, random);
                } else {
                    // Beat 3: Kick + HiHat
                    synthesizeKick(buffer, sampleIdx, sampleRate);
                    synthesizeHiHat(buffer, sampleIdx, sampleRate, random);
                }
                currentTime += beatPeriod1;
            }
        }

        // Section 2: 4 measures at 150 BPM (tempo change mid-way!)
        double tempoBpm2 = 150.0;
        double beatPeriod2 = 60.0 / tempoBpm2; // 0.4s

        for (int m = 5; m <= 8; m++) {
            for (int b = 1; b <= 4; b++) {
                int sampleIdx = (int) (currentTime * sampleRate);
                if (b == 1) {
                    // Downbeat at new tempo
                    synthesizeKick(buffer, sampleIdx, sampleRate);
                    synthesizeBass(buffer, sampleIdx, sampleRate, 130.81f);
                    synthesizeHiHat(buffer, sampleIdx, sampleRate, random);
                } else if (b == 2 || b == 4) {
                    synthesizeSnare(buffer, sampleIdx, sampleRate, random);
                    synthesizeHiHat(buffer, sampleIdx, sampleRate, random);
                } else {
                    synthesizeKick(buffer, sampleIdx, sampleRate);
                    synthesizeHiHat(buffer, sampleIdx, sampleRate, random);
                }
                currentTime += beatPeriod2;
            }
        }

        // Final closing crash / downbeat
        int finalIdx = (int) (currentTime * sampleRate);
        if (finalIdx < buffer.length) {
            synthesizeKick(buffer, finalIdx, sampleRate);
            synthesizeBass(buffer, finalIdx, sampleRate, 110.0f);
        }

        // Convert float buffer to 16-bit PCM bytes
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        byte[] pcmBytes = new byte[buffer.length * 2];
        for (int i = 0; i < buffer.length; i++) {
            float val = Math.max(-1.0f, Math.min(1.0f, buffer[i]));
            short sample = (short) (val * 32767.0f);
            pcmBytes[2 * i] = (byte) (sample & 0xFF);
            pcmBytes[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        return new AudioData(buffer, SAMPLE_RATE, pcmBytes, format);
    }

    /**
     * Saves an AudioData instance as a WAV file.
     */
    public static void saveAsWav(AudioData audioData, File outputFile) throws IOException {
        byte[] pcmBytes = audioData.getPcmBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(pcmBytes);
        AudioInputStream ais = new AudioInputStream(
                bais,
                audioData.getFormat(),
                pcmBytes.length / audioData.getFormat().getFrameSize()
        );
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
    }

    /**
     * Creates a demo MP4 file (video + audio with pickup measure and tempo change)
     * using ffmpeg.
     */
    public static boolean createDemoMp4(File outputMp4) {
        if (!AudioDecoder.isFfmpegAvailable()) {
            return false;
        }
        try {
            File tempWav = File.createTempFile("demo_gen_", ".wav");
            tempWav.deleteOnExit();
            saveAsWav(createDemoTrack(), tempWav);

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-f", "lavfi", "-i", "color=c=navy:s=640x360:r=25",
                    "-i", tempWav.getAbsolutePath(),
                    "-c:v", "libx264", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "192k",
                    "-shortest",
                    outputMp4.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            tempWav.delete();
            return exit == 0 && outputMp4.exists();
        } catch (Exception e) {
            return false;
        }
    }

    // --- Drum synthesis helpers ---

    private static void synthesizeKick(float[] buffer, int start, int sampleRate) {
        int length = (int) (0.25 * sampleRate); // 250ms
        for (int i = 0; i < length && (start + i) < buffer.length; i++) {
            double t = (double) i / sampleRate;
            // Frequency drop from 150Hz to 40Hz
            double freq = 40.0 + 110.0 * Math.exp(-25.0 * t);
            double envelope = Math.exp(-12.0 * t);
            double sample = Math.sin(2.0 * Math.PI * freq * t) * envelope * 0.9;
            buffer[start + i] += (float) sample;
        }
    }

    private static void synthesizeSnare(float[] buffer, int start, int sampleRate, Random random) {
        int length = (int) (0.20 * sampleRate); // 200ms
        for (int i = 0; i < length && (start + i) < buffer.length; i++) {
            double t = (double) i / sampleRate;
            double bodyFreq = 180.0 * Math.exp(-15.0 * t);
            double body = Math.sin(2.0 * Math.PI * bodyFreq * t) * Math.exp(-15.0 * t) * 0.5;
            double noise = (random.nextDouble() * 2.0 - 1.0) * Math.exp(-20.0 * t) * 0.6;
            buffer[start + i] += (float) (body + noise);
        }
    }

    private static void synthesizeHiHat(float[] buffer, int start, int sampleRate, Random random) {
        int length = (int) (0.05 * sampleRate); // 50ms
        for (int i = 0; i < length && (start + i) < buffer.length; i++) {
            double t = (double) i / sampleRate;
            double noise = (random.nextDouble() * 2.0 - 1.0) * Math.exp(-60.0 * t) * 0.35;
            buffer[start + i] += (float) noise;
        }
    }

    private static void synthesizeBass(float[] buffer, int start, int sampleRate, float frequency) {
        int length = (int) (0.35 * sampleRate);
        for (int i = 0; i < length && (start + i) < buffer.length; i++) {
            double t = (double) i / sampleRate;
            double envelope = Math.exp(-5.0 * t) * 0.4;
            double sample = Math.sin(2.0 * Math.PI * frequency * t) * envelope;
            buffer[start + i] += (float) sample;
        }
    }
}
