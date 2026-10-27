package com.example.mixin;

import com.example.TemplateMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JukeboxBlockEntity.class)
public abstract class VanillaJukeboxRejectMixin {
	@Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
	private void rejectUltimateDisc(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> callbackInfo) {
		if (stack.is(TemplateMod.ULTIMATE_MUSIC_DISC_ITEM)) {
			callbackInfo.setReturnValue(false);
		}
	}
}
