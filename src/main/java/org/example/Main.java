package org.example;

import org.example.audio.AudioData;
import org.example.audio.AudioDecoder;
import org.example.audio.AudioGenerator;
import org.example.beat.BeatEvent;
import org.example.beat.BeatTrackResult;
import org.example.beat.BeatTracker;
import org.example.player.AudioBeatPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Main application for audio & MP4 import, beat & tempo tracking,
 * pickup measure handling, and synchronized real-time audio playback.
 */
public class Main {

    private static final String TRACKS_DIR = "tracks";
    private static final List<String> SUPPORTED_EXTS = Arrays.asList(
            ".mp4", ".m4a", ".mp3", ".wav", ".aiff", ".aac", ".ogg"
    );

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("          AUDIO BEAT & TEMPO TRACKER (METRONOME ENGINE)           ");
        System.out.println("==================================================================");

        try {
            File tracksFolder = new File(TRACKS_DIR);
            if (!tracksFolder.exists()) {
                tracksFolder.mkdirs();
            }

            // Ensure there is at least one sample MP4 in tracks/ for instant testing
            File defaultMp4 = new File(tracksFolder, "demo_pickup_tempo_change.mp4");
            if (!defaultMp4.exists() && AudioDecoder.isFfmpegAvailable()) {
                System.out.println("Creating sample demo MP4 in 'tracks/' folder...");
                AudioGenerator.createDemoMp4(defaultMp4);
            }

            File chosenFile = null;
            AudioData audioData = null;
            String sourceName = "";

            // 1. Check if argument passed directly
            if (args.length > 0 && !args[0].startsWith("-")) {
                File argFile = new File(args[0]);
                if (!argFile.exists()) {
                    File inTracks = new File(tracksFolder, args[0]);
                    if (inTracks.exists()) {
                        argFile = inTracks;
                    }
                }
                if (argFile.exists()) {
                    chosenFile = argFile;
                } else {
                    System.err.println("File not found: " + args[0]);
                    return;
                }
            }

            // 2. Interactive Selection from dedicated 'tracks/' folder
            if (chosenFile == null) {
                List<File> trackFiles = listTrackFiles(tracksFolder);

                System.out.println("\n--- Dedicated Folder: ./" + TRACKS_DIR + "/ ---");
                if (trackFiles.isEmpty()) {
                    System.out.println("No media files currently in ./" + TRACKS_DIR + "/.");
                    System.out.println("Drop any .mp4, .mp3, or .wav files into '" + TRACKS_DIR + "/' for 1-click selection.");
                } else {
                    System.out.println("Available tracks (Enter number to select):");
                    for (int i = 0; i < trackFiles.size(); i++) {
                        File f = trackFiles.get(i);
                        double sizeMb = f.length() / (1024.0 * 1024.0);
                        System.out.printf("  [%d] %-36s (%.2f MB)%n", (i + 1), f.getName(), sizeMb);
                    }
                }

                System.out.println("\nOptions:");
                if (!trackFiles.isEmpty()) {
                    System.out.printf("  [1-%d] Select a track from ./%s/%n", trackFiles.size(), TRACKS_DIR);
                }
                System.out.println("  [D]   Play Built-in Synthetic Demo (Memory synthesized)");
                System.out.println("  [P]   Enter custom path to any audio/MP4 file");
                System.out.print("\nChoice (default 1): ");

                Scanner scanner = new Scanner(System.in);
                String choice = scanner.hasNextLine() ? scanner.nextLine().trim() : "";

                if (choice.isEmpty() || "1".equals(choice)) {
                    if (!trackFiles.isEmpty()) {
                        chosenFile = trackFiles.get(0);
                    }
                } else if (choice.equalsIgnoreCase("D")) {
                    // Synthetic demo in-memory
                } else if (choice.equalsIgnoreCase("P")) {
                    System.out.print("Enter full path: ");
                    String customPath = scanner.nextLine().trim();
                    File f = new File(customPath);
                    if (f.exists()) {
                        chosenFile = f;
                    } else {
                        System.err.println("File not found: " + customPath);
                        return;
                    }
                } else {
                    try {
                        int num = Integer.parseInt(choice);
                        if (num >= 1 && num <= trackFiles.size()) {
                            chosenFile = trackFiles.get(num - 1);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            // 3. Load / Decode Audio
            if (chosenFile != null) {
                System.out.println("\nImporting media file: " + chosenFile.getAbsolutePath());
                audioData = AudioDecoder.decode(chosenFile);
                sourceName = chosenFile.getName();
            } else {
                System.out.println("\nGenerating built-in demo track (with pickup measure & 120 -> 150 BPM shift)...");
                audioData = AudioGenerator.createDemoTrack();
                sourceName = "Synthetic Demo (Pickup + Tempo Change)";
            }

            System.out.printf("Audio loaded successfully: %s%n", sourceName);
            System.out.printf("Duration: %.2f sec | Sample Rate: %.0f Hz | Channels: %d%n%n",
                    audioData.getDurationSec(), audioData.getSampleRate(), audioData.getFormat().getChannels());

            // 4. Beat & Tempo Tracking Analysis
            System.out.println("Analyzing rhythm, meter, pickup measures, and mid-way tempo variations...");
            BeatTracker tracker = new BeatTracker();
            long t0 = System.currentTimeMillis();
            BeatTrackResult result = tracker.track(audioData);
            long analysisTimeMs = System.currentTimeMillis() - t0;

            System.out.printf("Analysis finished in %d ms!%n", analysisTimeMs);
            System.out.println("------------------------------------------------------------------");
            System.out.printf("Initial Tempo:    %.1f BPM%n", result.getInitialBpm());
            System.out.printf("Average Tempo:    %.1f BPM%n", result.getAverageBpm());
            System.out.printf("Pickup Measure:   %s%n",
                    result.hasPickupMeasure()
                            ? "YES (" + result.getPickupBeatsCount() + " pickup beat(s) detected and aligned)"
                            : "NO (Starts on Downbeat)");
            System.out.printf("Total Beats:      %d%n", result.size());
            System.out.println("------------------------------------------------------------------\n");

            // 5. Synchronized Audio Playback & Real-Time Beat Logging
            System.out.println("STARTING AUDIO PLAYBACK (FULLY SYNCHRONIZED WITH BEAT LOG):");
            System.out.println("You will hear the music + synchronized metronome clicks through your speakers.");
            System.out.println("Every beat is logged via System.out.println() at the exact millisecond it plays:\n");

            AudioBeatPlayer player = new AudioBeatPlayer(audioData, result);
            player.setEnableMetronomeClicks(true); // Audible clicks aligned with audio

            // Beat callback using System.out.println()
            player.addListener(event -> {
                System.out.println(event.toLogString());
                System.out.flush();
            });

            // Start synchronized audio output
            player.play();

            // Wait until audio playback completes
            player.waitUntilFinished();

            System.out.println("\nPlayback completed successfully!");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<File> listTrackFiles(File dir) {
        List<File> list = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                if (f.isFile()) {
                    String name = f.getName().toLowerCase();
                    if (SUPPORTED_EXTS.stream().anyMatch(name::endsWith)) {
                        list.add(f);
                    }
                }
            }
        }
        return list;
    }
}
