package com.example.mixin.client;

import com.example.client.MusicDiscAudio;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.example.audio.AudioData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

@Mixin(SoundBufferLibrary.class)
public class DynamicSoundBufferMixin {
	@Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
	private void useDynamicAudio(Identifier soundPath, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
		AudioData audioData = MusicDiscAudio.getAudioBuffer(soundPath);
		if (audioData == null) {
			return;
		}

		ByteBuffer pcm = ByteBuffer.allocateDirect(audioData.getPcmBytes().length);
		pcm.put(audioData.getPcmBytes()).flip();
		cir.setReturnValue(CompletableFuture.completedFuture(new SoundBuffer(pcm, audioData.getFormat())));
	}
}
