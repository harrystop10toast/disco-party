package com.example.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class JellyEffectClient {
	private static final long PULSE_DURATION_NANOS = 800_000_000L;
	private static final double RANGE = 64.0;
	private static final List<Pulse> PULSES = new ArrayList<>();

	private JellyEffectClient() {}

	public static void onBeat(Identifier dimension, BlockPos pos) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !minecraft.level.dimension().identifier().equals(dimension)) {
			return;
		}
		PULSES.add(new Pulse(Vec3.atCenterOf(pos), System.nanoTime()));
	}

	public static void applyScale(double x, double y, double z, com.mojang.blaze3d.vertex.PoseStack poseStack) {
		long now = System.nanoTime();
		Pulse active = null;
		for (Iterator<Pulse> iterator = PULSES.iterator(); iterator.hasNext();) {
			Pulse pulse = iterator.next();
			if (now - pulse.startedAtNanos() >= PULSE_DURATION_NANOS) {
				iterator.remove();
			} else if (pulse.position().distanceToSqr(x, y, z) <= RANGE * RANGE
				&& (active == null || pulse.startedAtNanos() > active.startedAtNanos())) {
				active = pulse;
			}
		}

		if (active == null) {
			return;
		}

		double age = (now - active.startedAtNanos()) / 1_000_000_000.0;
		float yScale;
		float horizontalScale;
		if (age < 0.12) {
			float progress = smoothstep((float) (age / 0.12));
			yScale = lerp(1.0F, 0.65F, progress);
			horizontalScale = lerp(1.0F, 1.15F, progress);
		} else if (age < 0.26) {
			float progress = smoothstep((float) ((age - 0.12) / 0.14));
			yScale = lerp(0.65F, 1.22F, progress);
			horizontalScale = lerp(1.15F, 0.90F, progress);
		} else {
			double oscillation = Math.exp(-7.0 * (age - 0.26)) * Math.cos(2.0 * Math.PI * 4.0 * (age - 0.26));
			yScale = (float) (1.0 + 0.22 * oscillation);
			horizontalScale = (float) (1.0 - 0.12 * oscillation);
		}

		poseStack.scale(horizontalScale, yScale, horizontalScale);
	}

	private static float smoothstep(float value) {
		float t = Math.clamp(value, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static float lerp(float from, float to, float amount) {
		return from + (to - from) * amount;
	}

	private record Pulse(Vec3 position, long startedAtNanos) {}
}
