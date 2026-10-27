package org.example.beat;

/**
 * Represents a musical beat event.
 */
public class BeatEvent {
    private final int beatIndex;
    private final double timeSec;
    private final long timeMs;
    private final double bpm;
    private final int measure;
    private final int beatInMeasure;
    private final boolean isDownbeat;
    private final boolean isPickup;
    private final boolean isTempoChange;
    private final double previousBpm;

    public BeatEvent(int beatIndex, double timeSec, double bpm, int measure, int beatInMeasure,
                     boolean isDownbeat, boolean isPickup, boolean isTempoChange, double previousBpm) {
        this.beatIndex = beatIndex;
        this.timeSec = timeSec;
        this.timeMs = Math.round(timeSec * 1000.0);
        this.bpm = bpm;
        this.measure = measure;
        this.beatInMeasure = beatInMeasure;
        this.isDownbeat = isDownbeat;
        this.isPickup = isPickup;
        this.isTempoChange = isTempoChange;
        this.previousBpm = previousBpm;
    }

    public int getBeatIndex() {
        return beatIndex;
    }

    public double getTimeSec() {
        return timeSec;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public double getBpm() {
        return bpm;
    }

    public int getMeasure() {
        return measure;
    }

    public int getBeatInMeasure() {
        return beatInMeasure;
    }

    public boolean isDownbeat() {
        return isDownbeat;
    }

    public boolean isPickup() {
        return isPickup;
    }

    public boolean isTempoChange() {
        return isTempoChange;
    }

    public double getPreviousBpm() {
        return previousBpm;
    }

    /**
     * Formats the beat information into a clean, informative log string for System.out.println().
     */
    public String toLogString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[BEAT #%-3d | %6.3fs (%5d ms)] ", beatIndex, timeSec, timeMs));

        // Visual metronome display (e.g. [● ○ ○ ○])
        StringBuilder metronomeVis = new StringBuilder("[");
        if (isPickup) {
            metronomeVis.append("  UPBEAT  ]");
        } else {
            for (int i = 1; i <= 4; i++) {
                if (i == beatInMeasure) {
                    metronomeVis.append("●");
                } else {
                    metronomeVis.append("○");
                }
                if (i < 4) metronomeVis.append(" ");
            }
            metronomeVis.append("]");
        }

        if (isPickup) {
            sb.append(String.format("PICKUP / UPBEAT (Beat %d of pickup bar) %-11s | Tempo: %5.1f BPM",
                    beatInMeasure, metronomeVis, bpm));
        } else {
            sb.append(String.format("Bar %-2d, Beat %d ", measure, beatInMeasure));
            if (isDownbeat) {
                sb.append("[DOWNBEAT] ");
            } else {
                sb.append("           ");
            }
            sb.append(String.format("%-11s | Tempo: %5.1f BPM", metronomeVis, bpm));
        }

        if (isTempoChange) {
            sb.append(String.format("  <-- TEMPO CHANGE (was %.1f BPM)", previousBpm));
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toLogString();
    }
}
