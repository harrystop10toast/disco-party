package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import com.example.client.JellyEffectClient;
import com.example.client.MusicDiscAudio;
import com.example.network.BeatPulsePacket;
import com.example.network.MusicPacket;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class TemplateModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(TemplateMod.DISCO_BALL_ENTITY_TYPE, ThrownItemRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(MusicPacket.TYPE, (packet, context) ->
			context.client().execute(() -> MusicDiscAudio.handle(packet))
		);
		ClientPlayNetworking.registerGlobalReceiver(BeatPulsePacket.TYPE, (packet, context) ->
			context.client().execute(() -> JellyEffectClient.onBeat(packet.dimension(), packet.pos()))
		);
	}
}
