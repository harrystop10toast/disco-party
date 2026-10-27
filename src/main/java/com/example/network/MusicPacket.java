package com.example.network;

import com.example.TemplateMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicPacket(Identifier dimension, BlockPos pos, String filename) implements CustomPacketPayload {
	public static final Type<MusicPacket> TYPE = new Type<>(TemplateMod.id("jukebox_music"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MusicPacket> CODEC = StreamCodec.composite(
		Identifier.STREAM_CODEC,
		MusicPacket::dimension,
		BlockPos.STREAM_CODEC,
		MusicPacket::pos,
		ByteBufCodecs.stringUtf8(512),
		MusicPacket::filename,
		MusicPacket::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
