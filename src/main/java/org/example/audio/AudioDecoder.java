package org.example.audio;

import javax.sound.sampled.*;
import java.io.*;
import java.util.Arrays;
import java.util.List;

/**
 * Decodes audio from files (WAV, MP3, AIFF) as well as MP4/M4A video/audio containers
 * into PCM data and mono float samples.
 */
public class AudioDecoder {

    private static final List<String> VIDEO_CONTAINER_EXTS = Arrays.asList(
            ".mp4", ".m4a", ".mov", ".mkv", ".webm", ".flv", ".avi"
    );

    /**
     * Decodes an audio or video/audio container file from disk.
     * @param file the file to decode
     * @return decoded AudioData
     */
    public static AudioData decode(File file) throws UnsupportedAudioFileException, IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
        }

        // 1. If FFmpeg is available, prefer FFmpeg for all compressed/container formats
        // because FFmpeg inspects actual container magic bytes rather than trusting file extensions,
        // and is immune to Java Sound's "Resetting to invalid mark" ID3/buffer overflow bugs.
        if (isFfmpegAvailable()) {
            try {
                return decodeUsingFfmpeg(file);
            } catch (Exception ffmpegEx) {
                // If FFmpeg somehow failed, fallback to Java Sound below
            }
        }

        // 2. Fallback to Java Sound API (for pure WAV/AIFF or environments without FFmpeg)
        try (AudioInputStream sourceStream = AudioSystem.getAudioInputStream(file)) {
            return decodeStream(sourceStream);
        } catch (Exception e) {
            if (isFfmpegAvailable()) {
                return decodeUsingFfmpeg(file);
            }
            if (e instanceof UnsupportedAudioFileException) {
                throw (UnsupportedAudioFileException) e;
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to decode audio file: " + file.getName(), e);
        }
    }

    /**
     * Decodes an audio stream from an InputStream.
     */
    public static AudioData decode(InputStream inputStream) throws UnsupportedAudioFileException, IOException {
        BufferedInputStream bufferedIn = new BufferedInputStream(inputStream);
        try (AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bufferedIn)) {
            return decodeStream(sourceStream);
        }
    }

    /**
     * Uses ffmpeg to demux MP4 / M4A / video files into a standard WAV PCM stream.
     */
    private static AudioData decodeUsingFfmpeg(File file) throws IOException, UnsupportedAudioFileException {
        String ffmpegPath = findFfmpeg();
        if (ffmpegPath == null) {
            throw new UnsupportedAudioFileException(
                    "FFmpeg is required to extract audio from MP4/video files (" + file.getName() +
                    "), but was not found in PATH or standard system paths (/opt/homebrew/bin/ffmpeg, /usr/local/bin/ffmpeg)."
            );
        }

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-v", "error",
                "-i", file.getAbsolutePath(),
                "-vn",                 // Disable video
                "-acodec", "pcm_s16le",// 16-bit PCM
                "-ar", "44100",        // 44.1 kHz
                "-ac", "2",            // Stereo
                "-f", "wav",           // Standard WAV container
                "pipe:1"               // Output to stdout
        );

        Process process = pb.start();

        // Drain stderr asynchronously to avoid process deadlock
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // ignore or debug log
                }
            } catch (IOException ignored) {}
        }).start();

        try (InputStream processOut = new BufferedInputStream(process.getInputStream());
             AudioInputStream ais = AudioSystem.getAudioInputStream(processOut)) {
            AudioData data = decodeStream(ais);
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return data;
        }
    }

    private static String findFfmpeg() {
        // Check PATH first
        String[] candidates = {
                "ffmpeg",
                "/opt/homebrew/bin/ffmpeg",
                "/usr/local/bin/ffmpeg",
                "/usr/bin/ffmpeg"
        };
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "-version").start();
                if (p.waitFor() == 0) {
                    return c;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static boolean isFfmpegAvailable() {
        return findFfmpeg() != null;
    }

    private static AudioData decodeStream(AudioInputStream sourceStream) throws IOException {
        AudioFormat baseFormat = sourceStream.getFormat();
        float sampleRate = baseFormat.getSampleRate();
        if (sampleRate <= 0) {
            sampleRate = 44100.0f;
        }

        int channels = baseFormat.getChannels();
        if (channels <= 0) {
            channels = 2;
        }

        // Standard 16-bit signed PCM, little-endian
        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                channels,
                channels * 2,
                sampleRate,
                false
        );

        AudioInputStream pcmStream;
        if (AudioSystem.isConversionSupported(targetFormat, baseFormat)) {
            pcmStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
        } else {
            pcmStream = sourceStream;
        }

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = pcmStream.read(buffer)) != -1) {
            byteOut.write(buffer, 0, bytesRead);
        }

        byte[] pcmBytes = byteOut.toByteArray();
        float[] monoSamples = convertPcmToMonoFloat(pcmBytes, targetFormat);

        return new AudioData(monoSamples, sampleRate, pcmBytes, targetFormat);
    }

    private static float[] convertPcmToMonoFloat(byte[] pcmBytes, AudioFormat format) {
        int channels = format.getChannels();
        int bytesPerSample = format.getSampleSizeInBits() / 8;
        if (bytesPerSample != 2) {
            bytesPerSample = 2;
        }

        int frameSize = channels * bytesPerSample;
        int totalFrames = pcmBytes.length / frameSize;
        float[] mono = new float[totalFrames];

        boolean bigEndian = format.isBigEndian();

        for (int i = 0; i < totalFrames; i++) {
            float sum = 0.0f;
            for (int ch = 0; ch < channels; ch++) {
                int byteIndex = i * frameSize + ch * bytesPerSample;
                int b1 = pcmBytes[byteIndex] & 0xFF;
                int b2 = pcmBytes[byteIndex + 1];
                short sample;
                if (bigEndian) {
                    sample = (short) ((b1 << 8) | (b2 & 0xFF));
                } else {
                    sample = (short) ((b2 << 8) | b1);
                }
                sum += sample / 32768.0f;
            }
            mono[i] = sum / channels;
        }
        return mono;
    }
}
