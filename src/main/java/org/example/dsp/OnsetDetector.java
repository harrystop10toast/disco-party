package org.example.dsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Multi-band Log-Spectral Flux onset detection.
 * Generates novelty curves and isolates low-frequency (bass/downbeat) transients.
 */
public class OnsetDetector {

    public static class OnsetResult {
        public final float[] noveltyCurve;
        public final float[] bassFlux;
        public final double frameRate; // frames per second (e.g. 100.0)
        public final int hopSize;
        public final int sampleRate;

        public OnsetResult(float[] noveltyCurve, float[] bassFlux, double frameRate, int hopSize, int sampleRate) {
            this.noveltyCurve = noveltyCurve;
            this.bassFlux = bassFlux;
            this.frameRate = frameRate;
            this.hopSize = hopSize;
            this.sampleRate = sampleRate;
        }

        public double frameToTime(int frame) {
            return frame / frameRate;
        }

        public int timeToFrame(double timeSec) {
            return (int) Math.round(timeSec * frameRate);
        }
    }

    private final int fftSize;
    private final int hopSize;

    public OnsetDetector() {
        this(2048, 441); // ~46ms frame, 10ms hop (100 fps at 44.1kHz)
    }

    public OnsetDetector(int fftSize, int hopSize) {
        this.fftSize = fftSize;
        this.hopSize = hopSize;
    }

    public OnsetResult process(float[] samples, float sampleRate) {
        FFT fft = new FFT(fftSize);
        int totalFrames = (samples.length - fftSize) / hopSize;
        if (totalFrames <= 0) {
            return new OnsetResult(new float[0], new float[0], sampleRate / hopSize, hopSize, (int) sampleRate);
        }

        double frameRate = (double) sampleRate / hopSize;
        float binWidth = sampleRate / fftSize;

        // Band bin ranges: Sub-bass & Bass (20Hz to 140Hz) isolates kick drums and bass fundamentals
        int bassEndBin = Math.max(1, (int) (140.0f / binWidth));
        int midEndBin = Math.max(bassEndBin + 1, (int) (2200.0f / binWidth));
        int highEndBin = Math.min(fftSize / 2, (int) (10000.0f / binWidth));

        float[] rawFlux = new float[totalFrames];
        float[] bassFlux = new float[totalFrames];

        float[] prevMag = new float[fftSize / 2 + 1];
        float[] currMag;

        float compressionC = 1000.0f;

        for (int f = 0; f < totalFrames; f++) {
            int offset = f * hopSize;
            currMag = fft.computeMagnitudeSpectrum(samples, offset);

            float bFlux = 0.0f;
            float mFlux = 0.0f;
            float hFlux = 0.0f;

            for (int k = 1; k <= highEndBin; k++) {
                float logCurr = (float) Math.log1p(compressionC * currMag[k]);
                float logPrev = (float) Math.log1p(compressionC * prevMag[k]);
                float diff = Math.max(0.0f, logCurr - logPrev);

                if (k <= bassEndBin) {
                    bFlux += diff;
                } else if (k <= midEndBin) {
                    mFlux += diff;
                } else {
                    hFlux += diff;
                }
            }

            bassFlux[f] = bFlux;
            // Weighted combination: high weight on bass and mid for rhythmic clarity
            rawFlux[f] = 1.4f * bFlux + 1.0f * mFlux + 0.6f * hFlux;
            prevMag = currMag;
        }

        // Adaptive thresholding: moving median over 21 frames (~210ms)
        float[] novelty = applyAdaptiveThreshold(rawFlux, 21, 1.25f);

        // Normalize novelty curve to [0, 1]
        normalize(novelty);
        normalize(bassFlux);

        return new OnsetResult(novelty, bassFlux, frameRate, hopSize, (int) sampleRate);
    }

    private float[] applyAdaptiveThreshold(float[] input, int windowSize, float multiplier) {
        float[] output = new float[input.length];
        int half = windowSize / 2;
        float[] window = new float[windowSize];

        for (int i = 0; i < input.length; i++) {
            int count = 0;
            for (int j = -half; j <= half; j++) {
                int idx = i + j;
                if (idx >= 0 && idx < input.length) {
                    window[count++] = input[idx];
                }
            }
            Arrays.sort(window, 0, count);
            float median = window[count / 2];
            float threshold = median * multiplier;
            output[i] = Math.max(0.0f, input[i] - threshold);
        }
        return output;
    }

    private void normalize(float[] array) {
        float max = 0.0f;
        for (float v : array) {
            if (v > max) max = v;
        }
        if (max > 1e-6f) {
            for (int i = 0; i < array.length; i++) {
                array[i] /= max;
            }
        }
    }
}
