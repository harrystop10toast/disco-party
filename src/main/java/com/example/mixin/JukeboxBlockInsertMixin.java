package com.example.mixin;

import com.example.TemplateMod;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JukeboxBlock.class)
public class JukeboxBlockInsertMixin {
	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void preventUltimateDiscOnVanillaJukebox(
		ItemStack stack,
		BlockState state,
		net.minecraft.world.level.Level level,
		net.minecraft.core.BlockPos pos,
		net.minecraft.world.entity.player.Player player,
		net.minecraft.world.InteractionHand hand,
		net.minecraft.world.phys.BlockHitResult hit,
		CallbackInfoReturnable<InteractionResult> callbackInfo
	) {
		if (stack.is(TemplateMod.ULTIMATE_MUSIC_DISC_ITEM)) {
			callbackInfo.setReturnValue(InteractionResult.FAIL);
		}
	}
}
