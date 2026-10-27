package com.example.network;

import com.example.TemplateMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BeatPulsePacket(Identifier dimension, BlockPos pos) implements CustomPacketPayload {
	public static final Type<BeatPulsePacket> TYPE = new Type<>(TemplateMod.id("jukebox_beat"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BeatPulsePacket> CODEC = StreamCodec.composite(
		Identifier.STREAM_CODEC,
		BeatPulsePacket::dimension,
		BlockPos.STREAM_CODEC,
		BeatPulsePacket::pos,
		BeatPulsePacket::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
