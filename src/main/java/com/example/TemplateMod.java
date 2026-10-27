package com.example;

import com.example.entity.DiscoBallEntity;
import com.example.block.DiscoJukeboxBlock;
import com.example.block.DiscoJukeboxBlockEntity;
import com.example.item.DiscoBallItem;
import com.example.item.UltimateMusicDiscItem;
import com.example.music.JukeboxMusicManager;
import com.example.network.BeatPulsePacket;
import com.example.network.MusicPacket;
import com.example.network.RenameDiscPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

public class TemplateMod implements ModInitializer {
	public static final String MOD_ID = "template-mod";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ResourceKey<net.minecraft.world.item.JukeboxSong> ULTIMATE_MUSIC_DISC_SONG_KEY =
		ResourceKey.create(Registries.JUKEBOX_SONG, id("ultimate_music_disc"));
	public static final ResourceKey<Item> ULTIMATE_MUSIC_DISC_ITEM_KEY = ResourceKey.create(
		Registries.ITEM,
		id("ultimate_music_disc")
	);
	public static final Item ULTIMATE_MUSIC_DISC_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		ULTIMATE_MUSIC_DISC_ITEM_KEY,
		new UltimateMusicDiscItem(new Item.Properties().setId(ULTIMATE_MUSIC_DISC_ITEM_KEY).stacksTo(1))
	);

	public static final ResourceKey<Block> DISCO_JUKEBOX_BLOCK_KEY = ResourceKey.create(
		Registries.BLOCK,
		id("disco_jukebox")
	);
	public static final DiscoJukeboxBlock DISCO_JUKEBOX_BLOCK = Registry.register(
		BuiltInRegistries.BLOCK,
		DISCO_JUKEBOX_BLOCK_KEY,
		new DiscoJukeboxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.JUKEBOX).setId(DISCO_JUKEBOX_BLOCK_KEY))
	);
	public static final ResourceKey<Item> DISCO_JUKEBOX_ITEM_KEY = ResourceKey.create(
		Registries.ITEM,
		id("disco_jukebox")
	);
	public static final Item DISCO_JUKEBOX_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		DISCO_JUKEBOX_ITEM_KEY,
		new BlockItem(DISCO_JUKEBOX_BLOCK, new Item.Properties().setId(DISCO_JUKEBOX_ITEM_KEY))
	);
	public static final ResourceKey<BlockEntityType<?>> DISCO_JUKEBOX_BLOCK_ENTITY_KEY = ResourceKey.create(
		Registries.BLOCK_ENTITY_TYPE,
		id("disco_jukebox")
	);
	public static final BlockEntityType<DiscoJukeboxBlockEntity> DISCO_JUKEBOX_BLOCK_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		DISCO_JUKEBOX_BLOCK_ENTITY_KEY,
		new BlockEntityType<>(DiscoJukeboxBlockEntity::new, Set.of(DISCO_JUKEBOX_BLOCK))
	);

	public static final ResourceKey<EntityType<?>> DISCO_BALL_ENTITY_KEY = ResourceKey.create(
		Registries.ENTITY_TYPE,
		id("disco_ball")
	);

	public static final EntityType<DiscoBallEntity> DISCO_BALL_ENTITY_TYPE = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		DISCO_BALL_ENTITY_KEY,
		EntityType.Builder.<DiscoBallEntity>of(DiscoBallEntity::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.25F, 0.25F)
			.clientTrackingRange(4)
			.updateInterval(10)
			.build(DISCO_BALL_ENTITY_KEY)
	);

	public static final ResourceKey<Item> DISCO_BALL_ITEM_KEY = ResourceKey.create(
		Registries.ITEM,
		id("disco_ball")
	);

	public static final Item DISCO_BALL_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		DISCO_BALL_ITEM_KEY,
		new DiscoBallItem(new Item.Properties().setId(DISCO_BALL_ITEM_KEY).stacksTo(16))
	);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing TemplateMod with Disco Ball!");
		ServerTickEvents.END_LEVEL_TICK.register(JukeboxMusicManager::tickLevel);
		ServerLifecycleEvents.SERVER_STOPPING.register(JukeboxMusicManager::stopServer);
		ServerLifecycleEvents.BEFORE_SAVE.register(JukeboxMusicManager::beforeSave);
		ServerLifecycleEvents.AFTER_SAVE.register(JukeboxMusicManager::afterSave);
		try {
			Files.createDirectories(UltimateMusicDiscItem.getMusicDirectory());
		} catch (IOException e) {
			throw new IllegalStateException("Could not create the ultimate music disc folder", e);
		}

		PayloadTypeRegistry.serverboundPlay().register(RenameDiscPayload.TYPE, RenameDiscPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MusicPacket.TYPE, MusicPacket.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BeatPulsePacket.TYPE, BeatPulsePacket.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RenameDiscPayload.TYPE, (payload, context) ->
			context.server().execute(() -> {
				if (payload.filename().length() > UltimateMusicDiscItem.MAX_FILENAME_LENGTH
					|| !UltimateMusicDiscItem.isValidFilename(payload.filename())) {
					return;
				}

				ItemStack itemStack = context.player().getItemInHand(payload.hand());
				if (itemStack.is(ULTIMATE_MUSIC_DISC_ITEM)) {
					itemStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(payload.filename()));
				}
			})
		);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(DISCO_BALL_ITEM);
			output.accept(ULTIMATE_MUSIC_DISC_ITEM);
		});

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(DISCO_BALL_ITEM);
			output.accept(ULTIMATE_MUSIC_DISC_ITEM);
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
