package com.example.client;

import com.example.item.UltimateMusicDiscItem;
import com.example.network.RenameDiscPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

public class RenameMusicDiscScreen extends Screen {
	private final InteractionHand hand;
	private final String initialName;
	private EditBox filenameField;

	public RenameMusicDiscScreen(InteractionHand hand, String initialName) {
		super(Component.translatable("screen.template-mod.rename_music_disc"));
		this.hand = hand;
		this.initialName = initialName;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		this.filenameField = this.addRenderableWidget(new EditBox(
			this.font,
			centerX - 100,
			this.height / 2 - 10,
			200,
			20,
			Component.translatable("screen.template-mod.music_filename")
		));
		this.filenameField.setMaxLength(UltimateMusicDiscItem.MAX_FILENAME_LENGTH);
		this.filenameField.setValue(this.initialName);
		this.filenameField.setHint(Component.translatable("screen.template-mod.music_filename_hint"));
		this.filenameField.setFocused(true);
		this.addRenderableWidget(Button.builder(
			Component.translatable("screen.template-mod.save"),
			button -> save()
		).bounds(centerX - 100, this.height / 2 + 20, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 42, 0xFFFFFF);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (this.filenameField.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		return this.filenameField.charTyped(event) || super.charTyped(event);
	}

	private void save() {
		String filename = this.filenameField.getValue().trim();
		if (UltimateMusicDiscItem.isValidFilename(filename)) {
			ClientPlayNetworking.send(new RenameDiscPayload(this.hand, filename));
			this.onClose();
		}
	}
}
