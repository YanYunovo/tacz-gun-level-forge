package com.eternal.gunlevel.client;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Chip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 模组自建的设置页，入口是原版「选项」界面左上角那个以模组名命名的大类按钮。
 * 每次点按钮都当场改数值并写回 config/tacz-gun-level.json，不用重启，
 * 下半屏那两行样例就是游戏里提示实际落点，方便照着躲开别的模组读数。
 */
public class GunLevelOptionsScreen extends Screen {
	private static final int MIN_MARGIN = 60;
	private static final int MAX_MARGIN = 160;
	private static final int MARGIN_STEP = 4;
	private static final int LINE_HEIGHT = 12;
	private static final int PREVIEW_COLOR = 0xFFE066;
	private static final int DIM_COLOR = 0x7F7F7F;

	private final Screen parent;

	public GunLevelOptionsScreen(Screen parent) {
		super(Component.translatable("gui.tacz_gun_level.settings_category"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 100;
		int top = this.height / 2 - 34;
		this.addRenderableWidget(Button.builder(toggleLabel(), button -> {
			GunLevelMod.CONFIG.chipHintEnabled = !GunLevelMod.CONFIG.chipHintEnabled;
			GunLevelMod.saveConfig();
			button.setMessage(toggleLabel());
		}).bounds(left, top, 200, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("-"),
				button -> shiftMargin(-MARGIN_STEP))
				.bounds(left, top + 28, 20, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("+"),
				button -> shiftMargin(MARGIN_STEP))
				.bounds(left + 180, top + 28, 20, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"),
				button -> this.onClose())
				.bounds(left, this.height / 2 + 66, 200, 20).build());
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.parent);
		}
	}

	private void shiftMargin(int delta) {
		int margin = GunLevelMod.CONFIG.chipHintBottomMargin + delta;
		GunLevelMod.CONFIG.chipHintBottomMargin = Math.max(MIN_MARGIN, Math.min(MAX_MARGIN, margin));
		GunLevelMod.saveConfig();
	}

	private static Component toggleLabel() {
		return Component.translatable("gui.tacz_gun_level.chip_hint", Component.translatable(
				GunLevelMod.CONFIG.chipHintEnabled ? "gui.tacz_gun_level.on" : "gui.tacz_gun_level.off"));
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
		context.drawCenteredString(this.font,
				Component.translatable("gui.tacz_gun_level.chip_hint_height",
						GunLevelMod.CONFIG.chipHintBottomMargin),
				this.width / 2, this.height / 2, 0xE0E0E0);
		int color = GunLevelMod.CONFIG.chipHintEnabled ? PREVIEW_COLOR : DIM_COLOR;
		int baseY = this.height - GunLevelMod.CONFIG.chipHintBottomMargin;
		// 一把枪最多装两颗芯片（LV5），游戏里同时最多就两行，样例照这个数画两行就够，
		// 全画完会随芯片数量越堆越高、顶穿上面那排按钮
		Chip[] chips = Chip.values();
		for (int i = 0; i < Math.min(2, chips.length); i++) {
			context.drawCenteredString(this.font,
					Component.translatable("msg.tacz_gun_level.chip.trigger",
							Component.translatable(chips[i].nameKey())),
					this.width / 2, baseY - i * LINE_HEIGHT, color);
		}
	}
}
