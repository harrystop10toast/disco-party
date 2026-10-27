package org.example.beat;

import java.util.List;

public class BeatTrackResult {
    private final List<BeatEvent> beats;
    private final double initialBpm;
    private final double averageBpm;
    private final int pickupBeatsCount;
    private final int timeSignatureNumerator; // e.g. 4 for 4/4

    public BeatTrackResult(List<BeatEvent> beats, double initialBpm, double averageBpm,
                           int pickupBeatsCount, int timeSignatureNumerator) {
        this.beats = beats;
        this.initialBpm = initialBpm;
        this.averageBpm = averageBpm;
        this.pickupBeatsCount = pickupBeatsCount;
        this.timeSignatureNumerator = timeSignatureNumerator;
    }

    public List<BeatEvent> getBeats() {
        return beats;
    }

    public double getInitialBpm() {
        return initialBpm;
    }

    public double getAverageBpm() {
        return averageBpm;
    }

    public int getPickupBeatsCount() {
        return pickupBeatsCount;
    }

    public boolean hasPickupMeasure() {
        return pickupBeatsCount > 0;
    }

    public int getTimeSignatureNumerator() {
        return timeSignatureNumerator;
    }

    public int size() {
        return beats.size();
    }
}
