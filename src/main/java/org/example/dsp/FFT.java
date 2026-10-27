package org.example.dsp;

/**
 * Fast Fourier Transform (Cooley-Tukey Radix-2).
 */
public class FFT {
    private final int n;
    private final double[] cos;
    private final double[] sin;
    private final double[] window;

    public FFT(int n) {
        if (Integer.bitCount(n) != 1) {
            throw new IllegalArgumentException("FFT size must be a power of 2: " + n);
        }
        this.n = n;
        this.cos = new double[n / 2];
        this.sin = new double[n / 2];
        for (int i = 0; i < n / 2; i++) {
            double angle = -2 * Math.PI * i / n;
            cos[i] = Math.cos(angle);
            sin[i] = Math.sin(angle);
        }

        // Hann window
        this.window = new double[n];
        for (int i = 0; i < n; i++) {
            window[i] = 0.5 * (1.0 - Math.cos(2 * Math.PI * i / (n - 1)));
        }
    }

    public int getSize() {
        return n;
    }

    /**
     * Computes magnitude spectrum for windowed input buffer.
     * @param input audio samples of length at least n
     * @param offset start index in input
     * @return magnitude spectrum of size n/2 + 1
     */
    public float[] computeMagnitudeSpectrum(float[] input, int offset) {
        double[] real = new double[n];
        double[] imag = new double[n];

        int limit = Math.min(n, input.length - offset);
        for (int i = 0; i < limit; i++) {
            real[i] = input[offset + i] * window[i];
        }

        // In-place Radix-2 FFT
        transform(real, imag);

        int half = n / 2;
        float[] magnitude = new float[half + 1];
        for (int i = 0; i <= half; i++) {
            magnitude[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }
        return magnitude;
    }

    private void transform(double[] real, double[] imag) {
        // Bit-reversal permutation
        int j = 0;
        for (int i = 0; i < n - 1; i++) {
            if (i < j) {
                double tempR = real[i];
                real[i] = real[j];
                real[j] = tempR;

                double tempI = imag[i];
                imag[i] = imag[j];
                imag[j] = tempI;
            }
            int k = n >> 1;
            while (k <= j) {
                j -= k;
                k >>= 1;
            }
            j += k;
        }

        // Cooley-Tukey butterfly
        for (int len = 2; len <= n; len <<= 1) {
            int halfLen = len >> 1;
            int step = n / len;
            for (int i = 0; i < n; i += len) {
                for (int k = 0; k < halfLen; k++) {
                    int tableIdx = k * step;
                    double c = cos[tableIdx];
                    double s = sin[tableIdx];

                    int u = i + k;
                    int v = i + k + halfLen;

                    double rV = real[v];
                    double iV = imag[v];

                    double tR = rV * c - iV * s;
                    double tI = rV * s + iV * c;

                    real[v] = real[u] - tR;
                    imag[v] = imag[u] - tI;
                    real[u] = real[u] + tR;
                    imag[u] = imag[u] + tI;
                }
            }
        }
    }
}
