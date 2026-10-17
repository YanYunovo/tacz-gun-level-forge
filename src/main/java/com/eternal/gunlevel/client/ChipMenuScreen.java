package com.eternal.gunlevel.client;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.menu.ChipMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

/**
 * 芯片装配台界面：和元素合成台同款原版容器底图，左组装芯片、右组拆芯片。
 */
public class ChipMenuScreen extends AbstractContainerScreen<ChipMenu> {
	private static final int TEXT = 0x404040;
	private static final int TEXT_DIM = 0x6C6C6C;
	private static final String[] SLOT_KEYS = {
			"gui.tacz_gun_level.slot_gun", "gui.tacz_gun_level.slot_chip",
			"gui.tacz_gun_level.slot_result", "gui.tacz_gun_level.slot_gun",
			"gui.tacz_gun_level.slot_taken", "gui.tacz_gun_level.slot_result",
	};

	public ChipMenuScreen(ChipMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = VanillaPanel.WIDTH;
		this.imageHeight = VanillaPanel.HEIGHT;
		this.inventoryLabelY = VanillaPanel.HEIGHT - 94;
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		VanillaPanel.draw(context, this.leftPos, this.topPos);
		for (int slotX : ChipMenu.SLOT_X) {
			VanillaPanel.slotFrame(context, this.leftPos, this.topPos, slotX, ChipMenu.SLOT_Y);
		}
		center(context, "gui.tacz_gun_level.group_chip_install",
				this.leftPos + ChipMenu.GROUP_INSTALL_CENTER, this.topPos + 20, TEXT);
		center(context, "gui.tacz_gun_level.group_chip_unload",
				this.leftPos + ChipMenu.GROUP_UNLOAD_CENTER, this.topPos + 20, TEXT);

		int label = this.topPos + ChipMenu.SLOT_Y + 21;
		for (int i = 0; i < SLOT_KEYS.length; i++) {
			center(context, SLOT_KEYS[i], this.leftPos + ChipMenu.SLOT_X[i] + 9, label, TEXT_DIM);
		}

		// 两组各自的枪槽下面标出这颗枪的芯片占用：已装 n / 上限 m
		chipSlots(context, ChipMenu.SLOT_GUN, ChipMenu.GROUP_INSTALL_CENTER);
		chipSlots(context, ChipMenu.SLOT_UNLOAD_GUN, ChipMenu.GROUP_UNLOAD_CENTER);
	}

	private void chipSlots(GuiGraphics context, int gunSlot, int groupCenter) {
		ItemStack gun = this.menu.container.getItem(gunSlot);
		if (gun.isEmpty()) {
			return;
		}
		int level = GunLevelData.getLevel(gun);
		center(context, Component.translatable("gui.tacz_gun_level.chip_slots",
						GunChipData.get(gun).size(), GunChipData.capacity(level)),
				this.leftPos + groupCenter, this.topPos + 29, TEXT_DIM);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		renderTooltip(context, mouseX, mouseY);
	}

	private void center(GuiGraphics context, String key, int centerX, int y, int color) {
		center(context, Component.translatable(key), centerX, y, color);
	}

	private void center(GuiGraphics context, Component text, int centerX, int y, int color) {
		VanillaPanel.center(context, text, this.font, centerX, y, color);
	}
}
