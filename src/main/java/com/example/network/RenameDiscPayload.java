package com.example.network;

import com.example.TemplateMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

public record RenameDiscPayload(InteractionHand hand, String filename) implements CustomPacketPayload {
	public static final Type<RenameDiscPayload> TYPE = new Type<>(TemplateMod.id("rename_music_disc"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RenameDiscPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT.map(
			value -> value == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
			hand -> hand == InteractionHand.MAIN_HAND ? 0 : 1
		),
		RenameDiscPayload::hand,
		ByteBufCodecs.stringUtf8(512),
		RenameDiscPayload::filename,
		RenameDiscPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
