package com.example;

import com.example.entity.DiscoBallEntity;
import com.example.client.DynamicMusicSound;
import com.example.item.DiscoBallItem;
import com.example.item.UltimateMusicDiscItem;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class DiscoBallTest {

	@BeforeAll
	static void setup() throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		// Unfreeze registries for standalone test environment (mimicking Fabric loader runtime)
		Field intrusiveHolders = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
		intrusiveHolders.setAccessible(true);
		intrusiveHolders.set(BuiltInRegistries.ENTITY_TYPE, new IdentityHashMap<>());
		intrusiveHolders.set(BuiltInRegistries.ITEM, new IdentityHashMap<>());
		intrusiveHolders.set(BuiltInRegistries.BLOCK, new IdentityHashMap<>());
		intrusiveHolders.set(BuiltInRegistries.BLOCK_ENTITY_TYPE, new IdentityHashMap<>());

		Field frozen = MappedRegistry.class.getDeclaredField("frozen");
		frozen.setAccessible(true);
		frozen.set(BuiltInRegistries.ENTITY_TYPE, false);
		frozen.set(BuiltInRegistries.ITEM, false);
		frozen.set(BuiltInRegistries.BLOCK, false);
		frozen.set(BuiltInRegistries.BLOCK_ENTITY_TYPE, false);
	}

	@Test
	void testDiscoBallItemRegistered() {
		assertNotNull(TemplateMod.DISCO_BALL_ITEM);
		assertInstanceOf(DiscoBallItem.class, TemplateMod.DISCO_BALL_ITEM);
		assertEquals("template-mod:disco_ball", TemplateMod.DISCO_BALL_ITEM_KEY.identifier().toString());
	}

	@Test
	void testDiscoBallEntityTypeRegistered() {
		assertNotNull(TemplateMod.DISCO_BALL_ENTITY_TYPE);
		assertEquals("template-mod:disco_ball", TemplateMod.DISCO_BALL_ENTITY_KEY.identifier().toString());
	}

	@Test
	void ultimateMusicDiscAcceptsSupportedFilenamesOnly() {
		assertTrue(UltimateMusicDiscItem.isValidFilename("test.mp4"));
		assertTrue(UltimateMusicDiscItem.isValidFilename("TRACK.MP3"));
		assertFalse(UltimateMusicDiscItem.isValidFilename("../test.mp3"));
		assertFalse(UltimateMusicDiscItem.isValidFilename("test.exe"));
		assertFalse(UltimateMusicDiscItem.isValidFilename(""));
	}

	@Test
	void ultimateMusicDiscIsRegisteredAsSingleStackItem() {
		assertNotNull(TemplateMod.ULTIMATE_MUSIC_DISC_ITEM);
		assertInstanceOf(UltimateMusicDiscItem.class, TemplateMod.ULTIMATE_MUSIC_DISC_ITEM);
		assertEquals("template-mod:ultimate_music_disc", TemplateMod.ULTIMATE_MUSIC_DISC_ITEM_KEY.identifier().toString());
	}

	@Test
	void summonedDiscoJukeboxHasItsOwnBlockAndBlockEntity() {
		assertNotNull(TemplateMod.DISCO_JUKEBOX_BLOCK);
		assertNotNull(TemplateMod.DISCO_JUKEBOX_BLOCK_ENTITY);
		assertTrue(TemplateMod.DISCO_JUKEBOX_BLOCK_ENTITY.isValid(
			TemplateMod.DISCO_JUKEBOX_BLOCK.defaultBlockState()
		));
	}

	@Test
	void dynamicMusicSoundFadesWithListenerDistance() {
		assertEquals(1.0F, DynamicMusicSound.attenuationForDistance(0));
		assertEquals(0.5F, DynamicMusicSound.attenuationForDistance(32));
		assertEquals(0.0F, DynamicMusicSound.attenuationForDistance(64));
		assertEquals(0.0F, DynamicMusicSound.attenuationForDistance(100));
	}
}
