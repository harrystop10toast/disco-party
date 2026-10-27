package com.example.block;

import com.example.TemplateMod;
import com.example.music.JukeboxMusicManager;
import com.example.network.MusicPacket;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class DiscoJukeboxBlockEntity extends BlockEntity {
	private static final String RECORD_ITEM_TAG = "RecordItem";
	private static final String TICKS_SINCE_SONG_STARTED_TAG = "ticks_since_song_started";
	private ItemStack item = ItemStack.EMPTY;
	private final JukeboxSongPlayer songPlayer = new JukeboxSongPlayer(this::onSongChanged, getBlockPos());

	public DiscoJukeboxBlockEntity(BlockPos pos, BlockState state) {
		super(TemplateMod.DISCO_JUKEBOX_BLOCK_ENTITY, pos, state);
	}

	public ItemStack getTheItem() {
		return item;
	}

	public JukeboxSongPlayer getSongPlayer() {
		return songPlayer;
	}

	public void setTheItem(ItemStack stack) {
		item = stack;
		if (level != null) {
			level.setBlock(
				worldPosition,
				getBlockState().setValue(DiscoJukeboxBlock.HAS_RECORD, !stack.isEmpty()),
				2
			);
			if (!stack.isEmpty()) {
				JukeboxSong.fromStack(stack).ifPresentOrElse(
					song -> songPlayer.play(level, song),
					() -> songPlayer.stop(level, getBlockState())
				);
			} else {
				songPlayer.stop(level, getBlockState());
			}
		}
		setChanged();
		if (level instanceof ServerLevel serverLevel) {
			JukeboxMusicManager.onDiscChanged(serverLevel, this, stack);
		}
		syncDisc();
	}

	public void popOutTheItem() {
		if (level == null || level.isClientSide() || item.isEmpty()) {
			return;
		}
		ItemStack disc = item;
		item = ItemStack.EMPTY;
		if (level instanceof ServerLevel serverLevel) {
			JukeboxMusicManager.stop(serverLevel, worldPosition);
		}
		songPlayer.stop(level, getBlockState());
		level.setBlock(worldPosition, getBlockState().setValue(DiscoJukeboxBlock.HAS_RECORD, false), 2);
		Vec3 pos = Vec3.atLowerCornerWithOffset(worldPosition, 0.5, 1.01, 0.5)
			.offsetRandomXZ(level.getRandom(), 0.7F);
		ItemEntity itemEntity = new ItemEntity(level, pos.x, pos.y, pos.z, disc.copy());
		itemEntity.setDefaultPickUpDelay();
		level.addFreshEntity(itemEntity);
		onSongChanged();
		syncDisc();
	}

	public void preRemoveSideEffects() {
		if (level == null || level.isClientSide()) {
			return;
		}
		JukeboxMusicManager.stop((ServerLevel) level, worldPosition);
		songPlayer.stop(level, getBlockState());
		if (!item.isEmpty()) {
			ItemEntity itemEntity = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
				worldPosition.getZ() + 0.5, item.copy());
			level.addFreshEntity(itemEntity);
			item = ItemStack.EMPTY;
		}
		syncDisc();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, DiscoJukeboxBlockEntity jukebox) {
		jukebox.songPlayer.tick(level, state);
		if (level instanceof ServerLevel serverLevel && !jukebox.item.isEmpty()
			&& jukebox.songPlayer.isPlaying()) {
			JukeboxMusicManager.ensurePlaying(serverLevel, jukebox, jukebox.item);
			if (serverLevel.getGameTime() % 20 == 0) {
				jukebox.sendMusicPacket();
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		item = input.read(RECORD_ITEM_TAG, ItemStack.CODEC).orElse(ItemStack.EMPTY);
		input.getLong(TICKS_SINCE_SONG_STARTED_TAG).ifPresent(ticks ->
			JukeboxSong.fromStack(item).ifPresent(song -> songPlayer.setSongWithoutPlaying(song, ticks))
		);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!item.isEmpty()) {
			output.store(RECORD_ITEM_TAG, ItemStack.CODEC, item);
		}
		if (songPlayer.isPlaying()) {
			output.putLong(TICKS_SINCE_SONG_STARTED_TAG, songPlayer.getTicksSinceSongStarted());
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		preRemoveSideEffects();
	}

	@Override
	public void setRemoved() {
		if (level instanceof ServerLevel serverLevel) {
			JukeboxMusicManager.stop(serverLevel, worldPosition);
			sendMusicPacket("");
		}
		super.setRemoved();
	}

	@Override
	@Nullable
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	private void onSongChanged() {
		if (level != null) {
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
			setChanged();
		}
	}

	private void syncDisc() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		sendMusicPacket();
	}

	private void sendMusicPacket() {
		String filename = item.is(TemplateMod.ULTIMATE_MUSIC_DISC_ITEM) && item.getCustomName() != null
			? item.getCustomName().getString()
			: "";
		sendMusicPacket(filename);
	}

	private void sendMusicPacket(String filename) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		MusicPacket packet = new MusicPacket(serverLevel.dimension().identifier(), worldPosition, filename);
		for (var player : PlayerLookup.tracking(serverLevel, worldPosition)) {
			if (ServerPlayNetworking.canSend(player, MusicPacket.TYPE)) {
				ServerPlayNetworking.send(player, packet);
			}
		}
	}
}
