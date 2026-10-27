package com.example.block;

import com.example.TemplateMod;
import com.example.item.UltimateMusicDiscItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class DiscoJukeboxBlock extends JukeboxBlock {
	public DiscoJukeboxBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(HAS_RECORD, false));
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack stack,
		BlockState state,
		Level level,
		BlockPos pos,
		Player player,
		net.minecraft.world.InteractionHand hand,
		BlockHitResult hit
	) {
		if (!stack.is(TemplateMod.ULTIMATE_MUSIC_DISC_ITEM)) {
			return InteractionResult.FAIL;
		}
		if (state.getValue(HAS_RECORD)) {
			return InteractionResult.CONSUME;
		}

		String filename = stack.getCustomName() == null ? "" : stack.getCustomName().getString();
		if (!UltimateMusicDiscItem.isValidFilename(filename)) {
			if (level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.template-mod.music_disc.rename_first"));
			}
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide()) {
			if (!(level.getBlockEntity(pos) instanceof DiscoJukeboxBlockEntity jukebox)) {
				return InteractionResult.FAIL;
			}
			Holder<JukeboxSong> song = level.registryAccess()
				.lookupOrThrow(Registries.JUKEBOX_SONG)
				.getOrThrow(TemplateMod.ULTIMATE_MUSIC_DISC_SONG_KEY);
			ItemStack disc = stack.consumeAndReturn(1, player);
			disc.set(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE, new JukeboxPlayable(song));
			jukebox.setTheItem(disc);
			level.gameEvent(
				net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE,
				pos,
				net.minecraft.world.level.gameevent.GameEvent.Context.of(player, state)
			);
			player.awardStat(net.minecraft.stats.Stats.PLAY_RECORD);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(
		BlockState state,
		Level level,
		BlockPos pos,
		Player player,
		BlockHitResult hit
	) {
		if (!state.getValue(HAS_RECORD)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DiscoJukeboxBlockEntity jukebox) {
			jukebox.popOutTheItem();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DiscoJukeboxBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
		Level level,
		BlockState state,
		BlockEntityType<T> type
	) {
		return type == TemplateMod.DISCO_JUKEBOX_BLOCK_ENTITY
			? (tickLevel, pos, blockState, blockEntity) ->
				DiscoJukeboxBlockEntity.tick(tickLevel, pos, blockState, (DiscoJukeboxBlockEntity) blockEntity)
			: null;
	}
}
