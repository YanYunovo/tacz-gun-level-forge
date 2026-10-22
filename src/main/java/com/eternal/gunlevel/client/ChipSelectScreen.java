package com.eternal.gunlevel.client;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.menu.ChipSelectMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * 芯片自选包的界面：上半区把本品级的芯片摆成网格，点一颗就把序号发给服务端；
 * 下半区是玩家自己的背包（数量同步走的是这个容器的 broadcastChanges，所以必须带上）。
 * 金蓝两档共用这一个 Screen 类，摆哪一档看菜单实例的品级。
 */
public class ChipSelectScreen extends AbstractContainerScreen<ChipSelectMenu> {
	private final List<ChipButton> choices = new ArrayList<>();

	public ChipSelectScreen(ChipSelectMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = VanillaPanel.WIDTH;
		this.imageHeight = VanillaPanel.HEIGHT;
		this.inventoryLabelY = VanillaPanel.HEIGHT - 94;
	}

	@Override
	protected void init() {
		super.init();
		choices.clear();
		List<Chip> chips = this.menu.choices();
		for (int i = 0; i < chips.size(); i++) {
			ChipButton button = new ChipButton(this.leftPos + this.menu.cellX(i),
					this.topPos + this.menu.cellY(i), chips.get(i), this.minecraft);
			choices.add(button);
			this.addRenderableWidget(button);
		}
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		VanillaPanel.draw(context, this.leftPos, this.topPos);
		// 每颗芯片一个原版槽框：光秃秃摆物品看着像没互动的装饰
		for (int i = 0; i < this.menu.choices().size(); i++) {
			VanillaPanel.slotFrame(context, this.leftPos, this.topPos,
					this.menu.cellX(i) + 2, this.menu.cellY(i) + 2);
		}
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		for (ChipButton button : choices) {
			if (button.isHovered()) {
				context.renderComponentTooltip(this.font, button.tooltip(), mouseX, mouseY);
			}
		}
	}

	/** 一格芯片：悬停补一圈框，点击把序号发给服务端 */
	private static class ChipButton extends AbstractWidget {
		private final Chip chip;
		private final ItemStack stack;
		private final Minecraft client;

		ChipButton(int x, int y, Chip chip, Minecraft client) {
			super(x, y, 20, 20, Component.translatable(chip.nameKey()));
			this.chip = chip;
			this.client = client;
			this.stack = new ItemStack(GunLevelMod.CHIPS[chip.ordinal()]);
		}

		@Override
		protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
			context.renderItem(this.stack, this.getX() + 2, this.getY() + 2);
			if (this.isHovered()) {
				context.renderOutline(this.getX() + 1, this.getY() + 1, 18, 18, 0xE0FFFFFF);
			}
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			ChipSelectMenu.sendChoice(this.chip.ordinal());
		}

		@Override
		protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
			this.defaultButtonNarrationText(out);
		}

		List<Component> tooltip() {
			return this.stack.getTooltipLines(this.client.player, TooltipFlag.Default.NORMAL);
		}
	}
}
