package org.example.audio;

import javax.sound.sampled.AudioFormat;

public class AudioData {
    private final float[] samples; // Normalized mono samples [-1.0, 1.0]
    private final float sampleRate;
    private final byte[] pcmBytes;  // Raw PCM for playback
    private final AudioFormat format;
    private final double durationSec;

    public AudioData(float[] samples, float sampleRate, byte[] pcmBytes, AudioFormat format) {
        this.samples = samples;
        this.sampleRate = sampleRate;
        this.pcmBytes = pcmBytes;
        this.format = format;
        this.durationSec = samples.length / (double) sampleRate;
    }

    public float[] getSamples() {
        return samples;
    }

    public float getSampleRate() {
        return sampleRate;
    }

    public byte[] getPcmBytes() {
        return pcmBytes;
    }

    public AudioFormat getFormat() {
        return format;
    }

    public double getDurationSec() {
        return durationSec;
    }

    public int getLength() {
        return samples.length;
    }
}
