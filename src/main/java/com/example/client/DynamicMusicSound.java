package com.example.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;

public class DynamicMusicSound extends AbstractTickableSoundInstance {
	static final double MAX_DISTANCE = 64.0;

	private final WeighedSoundEvents event;
	private final long durationTicks;
	private long ticksPlayed;

	public DynamicMusicSound(Identifier id, BlockPos pos, double durationSeconds) {
		super(SoundEvents.EMPTY, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
		this.volume = 1.0F;
		this.x = pos.getX() + 0.5;
		this.y = pos.getY() + 0.5;
		this.z = pos.getZ() + 0.5;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.sound = new Sound(id, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, false, false, 64);
		this.event = new WeighedSoundEvents(id, null);
		this.event.addSound(this.sound);
		this.durationTicks = Math.max(1, (long) Math.ceil(durationSeconds * 20.0));
	}

	@Override
	public void tick() {
		Vec3 listener = Minecraft.getInstance().gameRenderer.mainCamera().position();
		double distance = listener.distanceToSqr(this.x, this.y, this.z);
		this.volume = attenuationForDistance(Math.sqrt(distance));
		if (++this.ticksPlayed >= this.durationTicks) {
			this.stop();
		}
	}

	public static float attenuationForDistance(double distance) {
		return (float) Math.max(0.0, Math.min(1.0, 1.0 - distance / MAX_DISTANCE));
	}

	@Override
	public WeighedSoundEvents getOrResolve(SoundManager manager) {
		return this.event;
	}

	@Override
	public WeighedSoundEvents getSoundEvent() {
		return this.event;
	}
}
