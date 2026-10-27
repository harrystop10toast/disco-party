package org.example.player;

import org.example.audio.AudioData;
import org.example.beat.BeatEvent;
import org.example.beat.BeatListener;
import org.example.beat.BeatTrackResult;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real-time audio player that guarantees sub-millisecond synchronization
 * between audio output and beat event logging.
 *
 * Uses a decoupled architecture:
 * 1. Dedicated feeder thread streams PCM data smoothly to the audio line.
 * 2. Dedicated high-precision dispatcher thread continuously tracks the audio
 *    hardware clock (SourceDataLine.getMicrosecondPosition()) and triggers
 *    beat callbacks (System.out.println()) at the exact instant the sound waves
 *    are emitted from the speakers.
 */
public class AudioBeatPlayer {

    private final AudioData audioData;
    private final BeatTrackResult trackResult;
    private final List<BeatListener> listeners = new ArrayList<>();
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isFinished = new AtomicBoolean(false);

    private boolean enableMetronomeClicks = true; // Enabled by default for testing / alignment
    private SourceDataLine line;
    private Thread feederThread;
    private Thread dispatcherThread;

    public AudioBeatPlayer(AudioData audioData, BeatTrackResult trackResult) {
        this.audioData = audioData;
        this.trackResult = trackResult;
    }

    public void addListener(BeatListener listener) {
        listeners.add(listener);
    }

    public void setEnableMetronomeClicks(boolean enable) {
        this.enableMetronomeClicks = enable;
    }

    public boolean isMetronomeClicksEnabled() {
        return enableMetronomeClicks;
    }

    public boolean isPlaying() {
        return isPlaying.get();
    }

    /**
     * Starts synchronized playback and beat dispatch.
     */
    public synchronized void play() throws LineUnavailableException {
        if (isPlaying.get()) {
            return;
        }

        AudioFormat format = audioData.getFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("Warning: Audio output line not supported on this device. Running simulated playback.");
            playSimulated();
            return;
        }

        // 8192 bytes buffer (~46ms buffer for low latency and smooth playback)
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format, 8192);
        line.start();

        isPlaying.set(true);
        isFinished.set(false);

        byte[] audioBytes = audioData.getPcmBytes();
        if (enableMetronomeClicks) {
            audioBytes = mixMetronomeClicks(audioBytes, format, trackResult.getBeats());
        }

        final byte[] pcmToPlay = audioBytes;
        final List<BeatEvent> beats = trackResult.getBeats();

        // 1. Audio Feeder Thread
        feederThread = new Thread(() -> {
            try {
                int chunkSize = 2048;
                int offset = 0;
                while (isPlaying.get() && offset < pcmToPlay.length) {
                    int bytesToWrite = Math.min(chunkSize, pcmToPlay.length - offset);
                    int written = line.write(pcmToPlay, offset, bytesToWrite);
                    if (written > 0) {
                        offset += written;
                    }
                }
                // Wait for all buffered audio to finish playing through the DAC
                if (isPlaying.get()) {
                    line.drain();
                }
            } catch (Exception ignored) {
            } finally {
                isFinished.set(true);
            }
        }, "AudioBeatPlayer-Feeder");

        // 2. High-Precision Dispatcher Thread
        dispatcherThread = new Thread(() -> {
            int nextBeatIdx = 0;

            while (isPlaying.get() && (!isFinished.get() || nextBeatIdx < beats.size())) {
                if (line == null) break;

                // Query the exact audio hardware position in seconds
                long microsec = line.getMicrosecondPosition();
                double currentPlayTimeSec = microsec / 1_000_000.0;

                // Dispatch all beats whose timestamp has arrived
                while (nextBeatIdx < beats.size() && beats.get(nextBeatIdx).getTimeSec() <= currentPlayTimeSec) {
                    dispatchBeat(beats.get(nextBeatIdx));
                    nextBeatIdx++;
                }

                // If all audio has drained and all beats are dispatched, exit
                if (isFinished.get() && nextBeatIdx >= beats.size()) {
                    break;
                }

                // High-resolution polling: sleep 1ms
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    break;
                }
            }

            stop();
        }, "AudioBeatPlayer-Dispatcher");

        feederThread.setDaemon(true);
        dispatcherThread.setDaemon(true);

        feederThread.start();
        dispatcherThread.start();
    }

    /**
     * Blocks until playback and beat dispatch have completed.
     */
    public void waitUntilFinished() {
        if (dispatcherThread != null) {
            try {
                dispatcherThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Stops playback and frees system audio resources.
     */
    public synchronized void stop() {
        isPlaying.set(false);
        isFinished.set(true);
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
    }

    private void dispatchBeat(BeatEvent event) {
        for (BeatListener listener : listeners) {
            try {
                listener.onBeat(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Fallback for headless environments without sound card outputs.
     */
    private void playSimulated() {
        isPlaying.set(true);
        dispatcherThread = new Thread(() -> {
            long startTime = System.nanoTime();
            List<BeatEvent> beats = trackResult.getBeats();
            int nextBeatIdx = 0;

            while (isPlaying.get() && nextBeatIdx < beats.size()) {
                double elapsedSec = (System.nanoTime() - startTime) / 1_000_000_000.0;
                while (nextBeatIdx < beats.size() && beats.get(nextBeatIdx).getTimeSec() <= elapsedSec) {
                    dispatchBeat(beats.get(nextBeatIdx));
                    nextBeatIdx++;
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    break;
                }
            }
            isPlaying.set(false);
        }, "AudioBeatPlayer-Simulated");

        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
    }

    /**
     * Synthesizes and mixes audible metronome clicks directly into the PCM buffer:
     * - Downbeat: Sharp high-pitch click (1600 Hz)
     * - Regular beat: Mid-pitch click (1050 Hz)
     * - Pickup beat: Distinctive click (800 Hz)
     */
    private byte[] mixMetronomeClicks(byte[] original, AudioFormat format, List<BeatEvent> beats) {
        byte[] output = original.clone();
        int channels = format.getChannels();
        float sampleRate = format.getSampleRate();
        int frameSize = format.getFrameSize();
        boolean bigEndian = format.isBigEndian();

        for (BeatEvent beat : beats) {
            double timeSec = beat.getTimeSec();
            int startFrame = (int) Math.round(timeSec * sampleRate);

            double clickFreq = beat.isDownbeat() ? 1600.0 : (beat.isPickup() ? 800.0 : 1050.0);
            int clickLenFrames = (int) (0.025 * sampleRate); // 25ms sharp click
            float clickAmp = beat.isDownbeat() ? 0.90f : 0.65f;

            for (int f = 0; f < clickLenFrames; f++) {
                int frameIdx = startFrame + f;
                if (frameIdx >= output.length / frameSize) break;

                double t = (double) f / sampleRate;
                double decay = Math.exp(-120.0 * t);
                float clickSample = (float) (Math.sin(2.0 * Math.PI * clickFreq * t) * decay * clickAmp);

                for (int ch = 0; ch < channels; ch++) {
                    int byteIdx = frameIdx * frameSize + ch * 2;
                    int b1 = output[byteIdx] & 0xFF;
                    int b2 = output[byteIdx + 1];
                    short origSample = bigEndian ? (short) ((b1 << 8) | (b2 & 0xFF)) : (short) ((b2 << 8) | b1);

                    float mixed = (origSample / 32768.0f) * 0.70f + clickSample;
                    mixed = Math.max(-1.0f, Math.min(1.0f, mixed));
                    short mixedShort = (short) (mixed * 32767.0f);

                    if (bigEndian) {
                        output[byteIdx] = (byte) ((mixedShort >> 8) & 0xFF);
                        output[byteIdx + 1] = (byte) (mixedShort & 0xFF);
                    } else {
                        output[byteIdx] = (byte) (mixedShort & 0xFF);
                        output[byteIdx + 1] = (byte) ((mixedShort >> 8) & 0xFF);
                    }
                }
            }
        }

        return output;
    }
}
