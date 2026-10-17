package com.eternal.gunlevel.client;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.menu.UpgradeCosts;
import com.eternal.gunlevel.menu.UpgradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

/**
 * 枪械升级台界面：原版箱子底图（自定义区平铺空白带，槽框另外补画），
 * 上半区放等级/材料提示，一排三个槽位下方带小标签。
 */
public class UpgradeMenuScreen extends AbstractContainerScreen<UpgradeMenu> {
	private static final int TEXT = 0x404040;
	private static final int TEXT_DIM = 0x6C6C6C;
	private static final int TEXT_OK = 0x3F6E2E;
	private static final int TEXT_LACK = 0x9C3A2E;

	public UpgradeMenuScreen(UpgradeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = VanillaPanel.WIDTH;
		this.imageHeight = VanillaPanel.HEIGHT;
		this.inventoryLabelY = VanillaPanel.HEIGHT - 94;
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		VanillaPanel.draw(context, this.leftPos, this.topPos);
		VanillaPanel.slotFrame(context, this.leftPos, this.topPos, UpgradeMenu.SLOT_GUN_X, UpgradeMenu.SLOT_Y);
		VanillaPanel.slotFrame(context, this.leftPos, this.topPos, UpgradeMenu.SLOT_MAT_X, UpgradeMenu.SLOT_Y);
		VanillaPanel.slotFrame(context, this.leftPos, this.topPos, UpgradeMenu.SLOT_RESULT_X, UpgradeMenu.SLOT_Y);

		ItemStack gun = this.menu.container.getItem(UpgradeMenu.SLOT_GUN);
		int level = gun.isEmpty() ? 0 : GunLevelData.getLevel(gun);
		int max = GunLevelMod.CONFIG.maxLevel;
		int centerX = this.leftPos + VanillaPanel.WIDTH / 2;

		if (level <= 0) {
			center(context, "gui.tacz_gun_level.hint", centerX, this.topPos + 22, TEXT_DIM);
		} else if (level >= max) {
			center(context, "gui.tacz_gun_level.max", centerX, this.topPos + 22, TEXT);
		} else {
			center(context, Component.translatable("gui.tacz_gun_level.level()", level, level + 1),
					centerX, this.topPos + 19, TEXT);
			UpgradeCosts.Cost cost = UpgradeCosts.costFor(level);
			if (cost != null) {
				boolean enough = UpgradeCosts.matches(level,
						this.menu.container.getItem(UpgradeMenu.SLOT_MATERIAL));
				center(context, Component.translatable("gui.tacz_gun_level.cost",
								new ItemStack(cost.item()).getHoverName().getString(), cost.count()),
						centerX, this.topPos + 28, enough ? TEXT_OK : TEXT_LACK);
			}
		}

		int label = this.topPos + UpgradeMenu.SLOT_Y + 21;
		center(context, "gui.tacz_gun_level.slot_gun", this.leftPos + UpgradeMenu.SLOT_GUN_X + 9, label, TEXT_DIM);
		center(context, "gui.tacz_gun_level.slot_material", this.leftPos + UpgradeMenu.SLOT_MAT_X + 9, label, TEXT_DIM);
		center(context, "gui.tacz_gun_level.slot_result", this.leftPos + UpgradeMenu.SLOT_RESULT_X + 9, label, TEXT_DIM);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		// 1.20.1 的 AbstractContainerScreen#render 不会自己画悬停提示
		renderTooltip(context, mouseX, mouseY);
	}

	private void center(GuiGraphics context, String key, int centerX, int y, int color) {
		center(context, Component.translatable(key), centerX, y, color);
	}

	private void center(GuiGraphics context, Component text, int centerX, int y, int color) {
		VanillaPanel.center(context, text, this.font, centerX, y, color);
	}
}
