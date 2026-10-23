package com.eternal.gunlevel.client;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.menu.SynthesisMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

/**
 * 元素合成台界面：深色枪钢面板（槽位烘焙在贴图里），左组合成核芯、右组给枪安装核芯。
 */
public class SynthesisMenuScreen extends AbstractContainerScreen<SynthesisMenu> {
	private static final int TEXT = VanillaPanel.TEXT;
	private static final int TEXT_DIM = VanillaPanel.TEXT_DIM;
	private static final String[] SLOT_KEYS = {
			"gui.tacz_gun_level.slot_material_core", "gui.tacz_gun_level.slot_carrier",
			"gui.tacz_gun_level.slot_core", "gui.tacz_gun_level.slot_gun",
			"gui.tacz_gun_level.slot_core", "gui.tacz_gun_level.slot_result",
	};

	public SynthesisMenuScreen(SynthesisMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = VanillaPanel.WIDTH;
		this.imageHeight = VanillaPanel.HEIGHT;
		this.inventoryLabelY = VanillaPanel.HEIGHT - 94;
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		VanillaPanel.draw(context, this.leftPos, this.topPos, VanillaPanel.SIX);
		center(context, "gui.tacz_gun_level.group_core",
				this.leftPos + SynthesisMenu.GROUP_CORE_CENTER, this.topPos + 20, TEXT);
		center(context, "gui.tacz_gun_level.group_install",
				this.leftPos + SynthesisMenu.GROUP_INSTALL_CENTER, this.topPos + 20, TEXT);

		int label = this.topPos + SynthesisMenu.SLOT_Y + 21;
		for (int i = 0; i < SLOT_KEYS.length; i++) {
			center(context, SLOT_KEYS[i], this.leftPos + SynthesisMenu.SLOT_X[i] + 9, label, TEXT_DIM);
		}

		// 枪槽里那把枪当前装的是什么核芯，直接标在组标题下
		ItemStack gun = this.menu.container.getItem(SynthesisMenu.SLOT_GUN);
		String element = gun.isEmpty() ? null : GunLevelData.elementName(gun);
		if (element != null) {
			center(context, Component.translatable("gui.tacz_gun_level.current_core", element),
					this.leftPos + SynthesisMenu.GROUP_INSTALL_CENTER, this.topPos + 29, TEXT_DIM);
		}
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		renderTooltip(context, mouseX, mouseY);
	}

	/** 标题与「物品栏」默认画 0x404040，在钢底上不可读，翻成浅色。
	 *  背包标题拿 translatable 而不是读父类字段：那个字段在这套映射里不可见。 */
	@Override
	protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
		context.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
		context.drawString(this.font, Component.translatable("container.inventory"),
				this.inventoryLabelX, this.inventoryLabelY, TEXT_DIM, false);
	}

	private void center(GuiGraphics context, String key, int centerX, int y, int color) {
		center(context, Component.translatable(key), centerX, y, color);
	}

	private void center(GuiGraphics context, Component text, int centerX, int y, int color) {
		VanillaPanel.center(context, text, this.font, centerX, y, color);
	}
}
