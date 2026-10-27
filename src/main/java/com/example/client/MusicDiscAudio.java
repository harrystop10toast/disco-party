package com.example.client;

import com.example.TemplateMod;
import com.example.network.MusicPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.example.audio.AudioData;
import org.example.audio.AudioDecoder;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class MusicDiscAudio {
	private static final ConcurrentHashMap<PlaybackKey, UUID> REQUESTS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<PlaybackKey, String> FILENAMES = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Identifier, AudioData> BUFFERS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<PlaybackKey, DynamicMusicSound> PLAYING = new ConcurrentHashMap<>();

	private MusicDiscAudio() {}

	public static void handle(MusicPacket packet) {
		BlockPos pos = packet.pos().immutable();
		PlaybackKey key = new PlaybackKey(packet.dimension(), pos);
		if (packet.filename().isEmpty()) {
			stop(key);
			return;
		}
		if (packet.filename().equals(FILENAMES.put(key, packet.filename()))) {
			return;
		}

		stopSound(key);
		UUID request = UUID.randomUUID();
		REQUESTS.put(key, request);
		Path folder = net.fabricmc.loader.api.FabricLoader.getInstance()
			.getConfigDir()
			.resolve(TemplateMod.MOD_ID)
			.resolve("music");
		Path file = folder.resolve(packet.filename()).normalize();
		if (!file.getParent().equals(folder.normalize()) || !Files.isRegularFile(file)) {
			TemplateMod.LOGGER.error("Ultimate music disc file not found: {}", file);
			REQUESTS.remove(key);
			notifyUser("message.template-mod.music_disc.file_not_found");
			return;
		}

		CompletableFuture.supplyAsync(() -> decode(file.toFile())).whenComplete((audioData, error) ->
			Minecraft.getInstance().execute(() -> {
				if (!request.equals(REQUESTS.get(key))) {
					return;
				}
				if (error != null) {
					TemplateMod.LOGGER.error("Could not decode ultimate music disc file {}", file, error);
					REQUESTS.remove(key);
					notifyUser("message.template-mod.music_disc.decode_failed");
					return;
				}

				Identifier soundId = TemplateMod.id("dynamic/" + UUID.randomUUID().toString().replace("-", ""));
				Identifier soundPath = net.minecraft.client.resources.sounds.Sound.SOUND_LISTER.idToFile(soundId);
				BUFFERS.put(soundPath, audioData);
				DynamicMusicSound sound = new DynamicMusicSound(soundId, pos, audioData.getDurationSec());
				PLAYING.put(key, sound);
				Minecraft.getInstance().getSoundManager().play(sound);

				long durationMillis = (long) (audioData.getDurationSec() * 1000.0) + 10_000;
				CompletableFuture.delayedExecutor(durationMillis, TimeUnit.MILLISECONDS).execute(() ->
					Minecraft.getInstance().execute(() -> BUFFERS.remove(soundPath, audioData))
				);
			})
		);
	}

	public static AudioData getAudioBuffer(Identifier soundPath) {
		return BUFFERS.get(soundPath);
	}

	private static AudioData decode(File file) {
		try {
			AudioData audioData = AudioDecoder.decode(file);
			AudioFormat format = audioData.getFormat();
			if (audioData.getPcmBytes().length == 0
				|| format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
				|| format.getChannels() < 1
				|| format.getChannels() > 2
				|| format.getSampleSizeInBits() != 16
				|| format.isBigEndian()) {
				throw new IOException("Decoded audio must be non-empty, mono or stereo, 16-bit PCM");
			}
			return audioData;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static void notifyUser(String translationKey) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(translationKey));
		}
	}

	private static void stop(PlaybackKey key) {
		REQUESTS.remove(key);
		FILENAMES.remove(key);
		stopSound(key);
	}

	private static void stopSound(PlaybackKey key) {
		DynamicMusicSound sound = PLAYING.remove(key);
		if (sound != null) {
			Minecraft.getInstance().getSoundManager().stop(sound);
			BUFFERS.remove(sound.getSound().getPath());
		}
	}

	private record PlaybackKey(Identifier dimension, BlockPos pos) {}
}
