package com.example.item;

import com.example.TemplateMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public class UltimateMusicDiscItem extends Item {
	public static final int MAX_FILENAME_LENGTH = 128;
	private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".mp3", ".wav", ".aiff", ".mp4", ".m4a", ".aac", ".ogg");

	public UltimateMusicDiscItem(Properties properties) {
		super(properties);
	}

	public static Path getMusicDirectory() {
		return net.fabricmc.loader.api.FabricLoader.getInstance()
			.getConfigDir()
			.resolve(TemplateMod.MOD_ID)
			.resolve("music");
	}

	public static boolean isValidFilename(String filename) {
		if (filename.isBlank()
			|| filename.equals(".")
			|| filename.equals("..")
			|| filename.contains("/")
			|| filename.contains("\\")
			|| filename.indexOf('\0') >= 0) {
			return false;
		}
		String lowerCase = filename.toLowerCase(Locale.ROOT);
		return SUPPORTED_EXTENSIONS.stream().anyMatch(lowerCase::endsWith);
	}

	@Override
	public InteractionResult use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			openRenameScreen(hand, player.getItemInHand(hand));
		}
		return InteractionResult.SUCCESS;
	}

	@Environment(EnvType.CLIENT)
	private static void openRenameScreen(InteractionHand hand, ItemStack stack) {
		net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
			new com.example.client.RenameMusicDiscScreen(hand, stack.getCustomName() == null ? "" : stack.getCustomName().getString())
		);
	}

}
