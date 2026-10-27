package org.example.beat;

import org.example.audio.AudioData;
import org.example.dsp.OnsetDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Robust Dynamic Programming Beat Tracker with:
 * - Dynamic local tempo tracking (handles mid-way tempo changes)
 * - Pickup measure (anacrusis / upbeat) detection and resolution
 * - Downbeat metric grid alignment
 */
public class BeatTracker {

    private final int minBpm;
    private final int maxBpm;
    private final int timeSignatureNumerator; // e.g. 4 for 4/4
    private final double tempoPriorBpm; // Default 120.0
    private final double tempoPriorStd; // Default 0.8

    public BeatTracker() {
        this(55, 215, 4, 120.0, 0.85);
    }

    public BeatTracker(int minBpm, int maxBpm, int timeSignatureNumerator, double tempoPriorBpm, double tempoPriorStd) {
        this.minBpm = minBpm;
        this.maxBpm = maxBpm;
        this.timeSignatureNumerator = timeSignatureNumerator;
        this.tempoPriorBpm = tempoPriorBpm;
        this.tempoPriorStd = tempoPriorStd;
    }

    /**
     * Tracks beats from decoded audio.
     */
    public BeatTrackResult track(AudioData audioData) {
        OnsetDetector detector = new OnsetDetector();
        OnsetDetector.OnsetResult onsetResult = detector.process(audioData.getSamples(), audioData.getSampleRate());
        return track(onsetResult);
    }

    /**
     * Tracks beats from pre-computed onset result.
     */
    public BeatTrackResult track(OnsetDetector.OnsetResult onsetResult) {
        float[] novelty = onsetResult.noveltyCurve;
        float[] bassFlux = onsetResult.bassFlux;
        double frameRate = onsetResult.frameRate;
        int nFrames = novelty.length;

        if (nFrames < 50) {
            return new BeatTrackResult(Collections.emptyList(), 120.0, 120.0, 0, timeSignatureNumerator);
        }

        // 1. Calculate local tempo trajectory tau(t) in frames
        double[] localTau = estimateLocalTempoTrajectory(novelty, frameRate);

        // 2. Dynamic Programming Beat Tracking
        List<Integer> beatFrames = dynamicProgrammingBeatTrack(novelty, localTau);

        if (beatFrames.isEmpty()) {
            return new BeatTrackResult(Collections.emptyList(), 120.0, 120.0, 0, timeSignatureNumerator);
        }

        // 3. Resolve Pickup Measure and Metric Grid (Downbeat vs Upbeat)
        int pickupBeats = detectPickupBeats(beatFrames, novelty, bassFlux, localTau);

        // 4. Build BeatEvent list with tempo change tracking
        List<BeatEvent> beatEvents = new ArrayList<>();
        double totalBpm = 0.0;
        int count = 0;
        double lastReportedBpm = -1.0;

        for (int i = 0; i < beatFrames.size(); i++) {
            int frame = beatFrames.get(i);
            double timeSec = onsetResult.frameToTime(frame);

            // Instantaneous / local BPM
            double bpm;
            if (i == 0) {
                if (beatFrames.size() > 1) {
                    double nextTime = onsetResult.frameToTime(beatFrames.get(1));
                    bpm = 60.0 / Math.max(0.1, nextTime - timeSec);
                } else {
                    bpm = (60.0 * frameRate) / localTau[frame];
                }
            } else {
                double prevTime = onsetResult.frameToTime(beatFrames.get(i - 1));
                bpm = 60.0 / Math.max(0.1, timeSec - prevTime);
            }

            // Bound reasonable BPM values
            bpm = Math.max(minBpm, Math.min(maxBpm, bpm));
            totalBpm += bpm;
            count++;

            boolean isPickup = (i < pickupBeats);
            int measure;
            int beatInMeasure;
            boolean isDownbeat;

            if (isPickup) {
                measure = 0;
                // e.g. if pickupBeats = 1, beatInMeasure is 4
                beatInMeasure = timeSignatureNumerator - pickupBeats + 1 + i;
                isDownbeat = false;
            } else {
                int adjustedIndex = i - pickupBeats;
                measure = (adjustedIndex / timeSignatureNumerator) + 1;
                beatInMeasure = (adjustedIndex % timeSignatureNumerator) + 1;
                isDownbeat = (beatInMeasure == 1);
            }

            boolean isTempoChange = false;
            if (lastReportedBpm > 0 && Math.abs(bpm - lastReportedBpm) >= 8.0) {
                isTempoChange = true;
            }

            double prevBpmToStore = lastReportedBpm > 0 ? lastReportedBpm : bpm;
            lastReportedBpm = bpm;

            beatEvents.add(new BeatEvent(
                    i + 1,
                    timeSec,
                    bpm,
                    measure,
                    beatInMeasure,
                    isDownbeat,
                    isPickup,
                    isTempoChange,
                    prevBpmToStore
            ));
        }

        double initialBpm = beatEvents.isEmpty() ? 120.0 : beatEvents.get(0).getBpm();
        double avgBpm = count > 0 ? (totalBpm / count) : initialBpm;

        return new BeatTrackResult(beatEvents, initialBpm, avgBpm, pickupBeats, timeSignatureNumerator);
    }

    /**
     * Estimates local beat period tau(t) across sliding windows of the novelty curve.
     */
    private double[] estimateLocalTempoTrajectory(float[] novelty, double frameRate) {
        int nFrames = novelty.length;
        double[] localTau = new double[nFrames];

        int minLag = (int) Math.round((60.0 * frameRate) / maxBpm); // ~28 frames
        int maxLag = (int) Math.round((60.0 * frameRate) / minBpm); // ~109 frames

        int windowSize = (int) (6.0 * frameRate); // 6 second window
        int hopSize = (int) (1.0 * frameRate);    // 1 second hop

        List<Integer> centerFrames = new ArrayList<>();
        List<Double> estimatedTaus = new ArrayList<>();

        for (int start = 0; start < nFrames; start += hopSize) {
            int end = Math.min(nFrames, start + windowSize);
            int center = (start + end) / 2;

            double bestLag = estimateWindowTempoLag(novelty, start, end, minLag, maxLag, frameRate);
            centerFrames.add(center);
            estimatedTaus.add(bestLag);
        }

        // Interpolate across all frames
        for (int t = 0; t < nFrames; t++) {
            localTau[t] = interpolate(centerFrames, estimatedTaus, t);
        }

        return localTau;
    }

    private double estimateWindowTempoLag(float[] novelty, int start, int end, int minLag, int maxLag, double frameRate) {
        double maxScore = -1.0;
        int bestLag = (int) Math.round((60.0 * frameRate) / tempoPriorBpm);

        for (int lag = minLag; lag <= maxLag; lag++) {
            double acf = 0.0;
            int count = 0;
            for (int i = start; i + lag < end; i++) {
                acf += novelty[i] * novelty[i + lag];
                count++;
            }
            if (count > 0) {
                acf /= count;
            }

            // Also check 2 * lag harmonic consistency (half tempo / double tempo check)
            double acf2 = 0.0;
            if (lag * 2 <= maxLag) {
                int count2 = 0;
                for (int i = start; i + lag * 2 < end; i++) {
                    acf2 += novelty[i] * novelty[i + lag * 2];
                    count2++;
                }
                if (count2 > 0) acf2 /= count2;
            }

            double combinedAcf = acf + 0.35 * acf2;

            // Prior weighting around tempoPriorBpm (log-normal distribution)
            double bpm = (60.0 * frameRate) / lag;
            double logRatio = (Math.log(bpm) - Math.log(tempoPriorBpm)) / tempoPriorStd;
            double prior = Math.exp(-0.5 * logRatio * logRatio);

            double score = combinedAcf * prior;
            if (score > maxScore) {
                maxScore = score;
                bestLag = lag;
            }
        }

        return bestLag;
    }

    private double interpolate(List<Integer> x, List<Double> y, int queryX) {
        if (x.isEmpty()) return 50.0;
        if (queryX <= x.get(0)) return y.get(0);
        if (queryX >= x.get(x.size() - 1)) return y.get(y.size() - 1);

        for (int i = 0; i < x.size() - 1; i++) {
            if (queryX >= x.get(i) && queryX <= x.get(i + 1)) {
                double t = (double) (queryX - x.get(i)) / (x.get(i + 1) - x.get(i));
                return y.get(i) + t * (y.get(i + 1) - y.get(i));
            }
        }
        return y.get(y.size() - 1);
    }

    /**
     * Dynamic Programming Beat Induction (Ellis/Böck algorithm).
     */
    private List<Integer> dynamicProgrammingBeatTrack(float[] novelty, double[] localTau) {
        int n = novelty.length;
        double[] cumScore = new double[n];
        int[] prev = new int[n];

        double alpha = 6.0; // Penalty weight for tempo deviation

        for (int t = 0; t < n; t++) {
            prev[t] = -1;
            double tau = localTau[t];
            int minP = Math.max(0, (int) Math.round(t - 1.8 * tau));
            int maxP = Math.max(0, (int) Math.round(t - 0.5 * tau));

            double maxPrevScore = Double.NEGATIVE_INFINITY;
            int bestP = -1;

            for (int p = minP; p <= maxP; p++) {
                double delta = t - p;
                double logRatio = Math.log(delta / tau);
                double score = cumScore[p] - alpha * logRatio * logRatio;
                if (score > maxPrevScore) {
                    maxPrevScore = score;
                    bestP = p;
                }
            }

            cumScore[t] = novelty[t];
            if (bestP >= 0 && maxPrevScore > 0) {
                cumScore[t] += maxPrevScore;
                prev[t] = bestP;
            }
        }

        // Find best terminating beat in the last window
        double lastTau = localTau[n - 1];
        int searchStart = Math.max(0, (int) (n - 1.5 * lastTau));
        int bestEnd = n - 1;
        double maxEndScore = Double.NEGATIVE_INFINITY;

        for (int t = searchStart; t < n; t++) {
            if (cumScore[t] > maxEndScore) {
                maxEndScore = cumScore[t];
                bestEnd = t;
            }
        }

        // Traceback
        List<Integer> beats = new ArrayList<>();
        int curr = bestEnd;
        while (curr >= 0) {
            beats.add(curr);
            curr = prev[curr];
        }

        Collections.reverse(beats);
        return beats;
    }

    /**
     * Identifies pickup measure (anacrusis) by determining which beat is the true downbeat.
     * Evaluates bass flux and onset strength across candidate downbeat phases.
     */
    private int detectPickupBeats(List<Integer> beatFrames, float[] novelty, float[] bassFlux, double[] localTau) {
        if (beatFrames.size() < 4) {
            return 0; // Not enough beats to determine pickup
        }

        // Candidate downbeat indices: 0, 1, 2, 3
        int candidates = Math.min(timeSignatureNumerator, beatFrames.size());
        double[] phaseScores = new double[candidates];

        // Evaluate over the first 4 full measures (or as many as available)
        int maxMeasures = Math.min(6, (beatFrames.size() / timeSignatureNumerator));

        for (int k = 0; k < candidates; k++) {
            double weightedScore = 0.0;
            double totalWeight = 0.0;

            for (int m = 0; m < maxMeasures; m++) {
                int beatIdx = k + m * timeSignatureNumerator;
                if (beatIdx < beatFrames.size()) {
                    int frame = beatFrames.get(beatIdx);
                    float bFlux = getWindowPeak(bassFlux, frame, 3);
                    float oFlux = getWindowPeak(novelty, frame, 3);

                    // Earlier measures are especially informative for establishing the meter
                    double weight = 1.0 / (1.0 + 0.25 * m);
                    weightedScore += weight * (3.0 * bFlux + 1.0 * oFlux);
                    totalWeight += weight;
                }
            }

            if (totalWeight > 0) {
                phaseScores[k] = weightedScore / totalWeight;
            }
            // System.out.printf("Phase %d score: %.4f%n", k, phaseScores[k]);
        }

        // Find candidate with maximum downbeat score
        int bestPhase = 0;
        double maxPhaseScore = phaseScores[0];
        for (int k = 1; k < candidates; k++) {
            // Require non-zero phase to exceed phase 0 by at least 4% to declare a pickup measure
            if (phaseScores[k] > maxPhaseScore * 1.04) {
                maxPhaseScore = phaseScores[k];
                bestPhase = k;
            }
        }

        return bestPhase;
    }

    private float getWindowPeak(float[] array, int center, int radius) {
        float max = 0.0f;
        int start = Math.max(0, center - radius);
        int end = Math.min(array.length - 1, center + radius);
        for (int i = start; i <= end; i++) {
            if (array[i] > max) max = array[i];
        }
        return max;
    }
}
