package org.example.beat;

@FunctionalInterface
public interface BeatListener {
    void onBeat(BeatEvent event);
}
