package com.example.music;

import com.example.TemplateMod;
import com.example.block.DiscoJukeboxBlockEntity;
import com.example.item.UltimateMusicDiscItem;
import com.example.network.BeatPulsePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.example.audio.AudioData;
import org.example.audio.AudioDecoder;
import org.example.beat.BeatEvent;
import org.example.beat.BeatTrackResult;
import org.example.beat.BeatTracker;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

public final class JukeboxMusicManager {
	private static final double EFFECT_RANGE = 64.0;
	private static final Map<SessionKey, Session> SESSIONS = new HashMap<>();
	private static final Map<SessionKey, String> ATTEMPTED_FILES = new HashMap<>();
	private static final Map<ServerLevel, Map<Mob, Boolean>> DISABLED_MOBS = new WeakHashMap<>();

	private JukeboxMusicManager() {}

	public static void onDiscChanged(ServerLevel level, DiscoJukeboxBlockEntity jukebox, ItemStack stack) {
		BlockPos pos = jukebox.getBlockPos().immutable();
		SessionKey key = new SessionKey(level, pos);
		stop(key);

		if (!stack.is(TemplateMod.ULTIMATE_MUSIC_DISC_ITEM)
			|| stack.getCustomName() == null
			|| !jukebox.getSongPlayer().isPlaying()) {
			return;
		}

		String filename = stack.getCustomName().getString();
		if (!UltimateMusicDiscItem.isValidFilename(filename)) {
			return;
		}
		ATTEMPTED_FILES.put(key, filename);

		Path folder = UltimateMusicDiscItem.getMusicDirectory().toAbsolutePath().normalize();
		Path file = folder.resolve(filename).normalize();
		if (!folder.equals(file.getParent()) || !Files.isRegularFile(file)) {
			TemplateMod.LOGGER.error("Cannot analyze jukebox music; file not found: {}", file);
			return;
		}

		Session session = new Session(jukebox, filename, level.getGameTime());
		SESSIONS.put(key, session);
		CompletableFuture.supplyAsync(() -> analyze(file.toFile())).whenComplete((analysis, error) ->
			level.getServer().execute(() -> {
				if (SESSIONS.get(key) != session) {
					return;
				}
				if (error != null) {
					SESSIONS.remove(key);
					TemplateMod.LOGGER.error("Could not analyze jukebox music file {}", file, error);
					return;
				}

				session.analysis = analysis;
				double elapsedSeconds = (level.getGameTime() - session.startedAt) / 20.0;
				List<BeatEvent> beats = analysis.beatTrack().getBeats();
				while (session.nextBeat < beats.size()
					&& beats.get(session.nextBeat).getTimeSec() < elapsedSeconds) {
					TemplateMod.LOGGER.info("[JUKEBOX BEAT] {}", beats.get(session.nextBeat++).toLogString());
				}
				TemplateMod.LOGGER.info(
					"Analyzed jukebox song {}: {} beats at {:.1f} BPM",
					filename,
					beats.size(),
					analysis.beatTrack().getAverageBpm()
				);
			})
		);
	}

	public static void ensurePlaying(ServerLevel level, DiscoJukeboxBlockEntity jukebox, ItemStack stack) {
		SessionKey key = new SessionKey(level, jukebox.getBlockPos().immutable());
		String filename = stack.getCustomName() == null ? "" : stack.getCustomName().getString();
		Session session = SESSIONS.get(key);
		if ((session == null || session.jukebox != jukebox || !session.filename.equals(filename))
			&& !filename.equals(ATTEMPTED_FILES.get(key))) {
			onDiscChanged(level, jukebox, stack);
		}
	}

	public static void stop(ServerLevel level, BlockPos pos) {
		stop(new SessionKey(level, pos.immutable()));
	}

	public static void tickLevel(ServerLevel level) {
		long gameTime = level.getGameTime();
		Set<Mob> dancingMobs = new HashSet<>();
		Set<Entity> rotatingEntities = new HashSet<>();
		Iterator<Map.Entry<SessionKey, Session>> iterator = SESSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<SessionKey, Session> entry = iterator.next();
			SessionKey key = entry.getKey();
			Session session = entry.getValue();
			if (key.level() != level) {
				continue;
			}

			if (level.getBlockEntity(key.pos()) != session.jukebox
				|| !session.jukebox.getSongPlayer().isPlaying()
				|| !isPlayingDisc(session.jukebox, session.filename)) {
				iterator.remove();
				continue;
			}

			long elapsedTicks = gameTime - session.startedAt;
			Analysis analysis = session.analysis;
			if (analysis != null && elapsedTicks >= Math.ceil(analysis.audioData().getDurationSec() * 20.0)) {
				iterator.remove();
				session.jukebox.popOutTheItem();
				level.removeBlock(key.pos(), false);
				continue;
			}

			affectEntities(level, key.pos(), dancingMobs, rotatingEntities);
			if (analysis != null) {
				double elapsedSeconds = elapsedTicks / 20.0;
				List<BeatEvent> beats = analysis.beatTrack().getBeats();
				while (session.nextBeat < beats.size()
					&& beats.get(session.nextBeat).getTimeSec() <= elapsedSeconds) {
					BeatEvent beat = beats.get(session.nextBeat++);
					TemplateMod.LOGGER.info("[JUKEBOX BEAT] {}", beat.toLogString());
					sendBeatPulse(level, key.pos());
				}
			}
		}

		for (Entity entity : rotatingEntities) {
			float yaw = (entity.getYRot() + 6.0F) % 360.0F;
			entity.setYRot(yaw);
			if (entity instanceof LivingEntity livingEntity) {
				livingEntity.setYBodyRot(yaw);
				livingEntity.setYHeadRot(yaw);
			}
		}

		restoreUncoveredMobs(level, dancingMobs);
		for (Mob mob : dancingMobs) {
			Map<Mob, Boolean> disabled = DISABLED_MOBS.computeIfAbsent(level, ignored -> new IdentityHashMap<>());
			if (mob instanceof Enemy) {
				disabled.computeIfAbsent(mob, Mob::isNoAi);
				mob.setNoAi(true);
				mob.setTarget(null);
			}
		}
	}

	public static void stopServer(net.minecraft.server.MinecraftServer server) {
		SESSIONS.keySet().removeIf(key -> key.level().getServer() == server);
		ATTEMPTED_FILES.keySet().removeIf(key -> key.level().getServer() == server);
		DiscoJukeboxBlockEntity.removeAllDiscoJukeboxes(server);
		restoreMobs(server);
	}

	public static void beforeSave(net.minecraft.server.MinecraftServer server, boolean flush, boolean force) {
		restoreMobs(server);
	}

	public static void afterSave(net.minecraft.server.MinecraftServer server, boolean flush, boolean force) {
		for (ServerLevel level : server.getAllLevels()) {
			tickLevel(level);
		}
	}

	private static void restoreMobs(net.minecraft.server.MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			Map<Mob, Boolean> disabled = DISABLED_MOBS.remove(level);
			if (disabled != null) {
				disabled.forEach((mob, wasNoAi) -> {
					if (!mob.isRemoved()) {
						mob.setNoAi(wasNoAi);
					}
				});
			}
		}
	}

	private static boolean isPlayingDisc(DiscoJukeboxBlockEntity jukebox, String filename) {
		ItemStack itemStack = jukebox.getTheItem();
		return itemStack.is(TemplateMod.ULTIMATE_MUSIC_DISC_ITEM)
			&& itemStack.getCustomName() != null
			&& filename.equals(itemStack.getCustomName().getString());
	}

	private static void affectEntities(
		ServerLevel level,
		BlockPos pos,
		Set<Mob> dancingMobs,
		Set<Entity> rotatingEntities
	) {
		Vec3 center = Vec3.atCenterOf(pos);
		AABB area = new AABB(
			center.x - EFFECT_RANGE, center.y - EFFECT_RANGE, center.z - EFFECT_RANGE,
			center.x + EFFECT_RANGE, center.y + EFFECT_RANGE, center.z + EFFECT_RANGE
		);
		for (Entity entity : level.getEntities((Entity) null, area, Entity::isAlive)) {
			if (entity.distanceToSqr(center) > EFFECT_RANGE * EFFECT_RANGE) {
				continue;
			}

			if (!(entity instanceof Player)) {
				rotatingEntities.add(entity);
			}
			if (entity instanceof Mob mob) {
				dancingMobs.add(mob);
			}
		}
	}

	private static void sendBeatPulse(ServerLevel level, BlockPos pos) {
		Vec3 center = Vec3.atCenterOf(pos);
		BeatPulsePacket packet = new BeatPulsePacket(level.dimension().identifier(), pos);
		for (var player : level.players()) {
			if (player.distanceToSqr(center) <= EFFECT_RANGE * EFFECT_RANGE
				&& ServerPlayNetworking.canSend(player, BeatPulsePacket.TYPE)) {
				ServerPlayNetworking.send(player, packet);
			}
		}
	}

	private static void restoreUncoveredMobs(ServerLevel level, Set<Mob> dancingMobs) {
		Map<Mob, Boolean> disabled = DISABLED_MOBS.get(level);
		if (disabled == null) {
			return;
		}

		Iterator<Map.Entry<Mob, Boolean>> iterator = disabled.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Mob, Boolean> entry = iterator.next();
			Mob mob = entry.getKey();
			if (mob.level() != level || mob.isRemoved()) {
				iterator.remove();
			} else if (!dancingMobs.contains(mob)) {
				mob.setNoAi(entry.getValue());
				iterator.remove();
			}
		}
		if (disabled.isEmpty()) {
			DISABLED_MOBS.remove(level);
		}
	}

	private static Analysis analyze(File file) {
		try {
			AudioData audioData = AudioDecoder.decode(file);
			BeatTrackResult beatTrack = new BeatTracker().track(audioData);
			return new Analysis(audioData, beatTrack);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static void stop(SessionKey key) {
		SESSIONS.remove(key);
		ATTEMPTED_FILES.remove(key);
	}

	private record SessionKey(ServerLevel level, BlockPos pos) {}

	private record Analysis(AudioData audioData, BeatTrackResult beatTrack) {}

	private static final class Session {
		private final DiscoJukeboxBlockEntity jukebox;
		private final String filename;
		private final long startedAt;
		private Analysis analysis;
		private int nextBeat;

		private Session(DiscoJukeboxBlockEntity jukebox, String filename, long startedAt) {
			this.jukebox = jukebox;
			this.filename = filename;
			this.startedAt = startedAt;
		}
	}
}
