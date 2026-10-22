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
 * 芯片自选包的界面：上半区把本品级的芯片摆成网格（一排 5 格，装不下的靠滚轮往下翻），
 * 点一颗就把序号发给服务端；下半区是玩家自己的背包
 * （数量同步走的是这个容器的 broadcastChanges，所以必须带上）。
 * 金蓝两档共用这一个 Screen 类，摆哪一档看菜单实例的品级。
 */
public class ChipSelectScreen extends AbstractContainerScreen<ChipSelectMenu> {
	/** 一格 20px、间隔 2px。一排 5 格而不是铺满：芯片还会继续加，摆成两排七列迟早挤成一坨 */
	private static final int CELL = 22;
	private static final int COLUMNS = 5;
	/** 原版底图给自定义区只留得下两行（y=17~71，再往下就是背包标题），多出来的行只能滚 */
	private static final int VISIBLE_ROWS = 2;
	private static final int GRID_Y = 20;
	/** 左边留 32 比居中偏左 2px，给右边那条滚动条让出位置 */
	private static final int GRID_X = 32;
	private static final int BAR_X = GRID_X + COLUMNS * CELL + 6;
	private static final int BAR_WIDTH = 4;
	private static final int TRACK_HEIGHT = VISIBLE_ROWS * CELL - 2;

	private final List<ChipButton> choices = new ArrayList<>();
	/** 已经翻过去的行数（按整行翻，滚半行看着像没对齐） */
	private int scroll;

	public ChipSelectScreen(ChipSelectMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = VanillaPanel.WIDTH;
		this.imageHeight = VanillaPanel.HEIGHT;
		this.inventoryLabelY = VanillaPanel.HEIGHT - 94;
	}

	private int rows() {
		return (this.menu.choices().size() + COLUMNS - 1) / COLUMNS;
	}

	private int maxScroll() {
		return Math.max(0, rows() - VISIBLE_ROWS);
	}

	@Override
	protected void init() {
		super.init();
		choices.clear();
		List<Chip> chips = this.menu.choices();
		for (Chip chip : chips) {
			ChipButton button = new ChipButton(chip, this.minecraft);
			choices.add(button);
			this.addRenderableWidget(button);
		}
		scroll = Math.min(scroll, maxScroll());
		layout();
	}

	/** 按当前滚动量重排每一格：看不见的行摆到面板外，同时置灰，免得点不到却还能被键盘选中 */
	private void layout() {
		for (int i = 0; i < choices.size(); i++) {
			ChipButton button = choices.get(i);
			int view = i / COLUMNS - scroll;
			boolean shown = view >= 0 && view < VISIBLE_ROWS;
			button.setX(this.leftPos + GRID_X + (i % COLUMNS) * CELL);
			button.setY(this.topPos + GRID_Y + view * CELL);
			// 1.20.1 的可见/可用还是 ClickableWidget 上的公开字段，没有 setter
			button.visible = shown;
			button.active = shown;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (maxScroll() == 0 || amount == 0.0
				|| mouseY < this.topPos + GRID_Y || mouseY > this.topPos + GRID_Y + TRACK_HEIGHT
				|| mouseX < this.leftPos || mouseX > this.leftPos + VanillaPanel.WIDTH) {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}
		int next = Math.max(0, Math.min(maxScroll(), scroll + (amount < 0 ? 1 : -1)));
		if (next != scroll) {
			scroll = next;
			layout();
		}
		return true;
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		VanillaPanel.draw(context, this.leftPos, this.topPos);
		// 每颗芯片一个原版槽框：光秃秃摆物品看着像没互动的装饰
		int chips = this.menu.choices().size();
		for (int row = scroll; row < Math.min(rows(), scroll + VISIBLE_ROWS); row++) {
			for (int col = 0; col < COLUMNS && row * COLUMNS + col < chips; col++) {
				VanillaPanel.slotFrame(context, this.leftPos, this.topPos,
						GRID_X + col * CELL + 2, GRID_Y + (row - scroll) * CELL + 2);
			}
		}
		drawScrollBar(context);
	}

	/** 只在真需要翻的时候画：不满两行还挂条灰杠，看着像界面坏了 */
	private void drawScrollBar(GuiGraphics context) {
		int max = maxScroll();
		if (max == 0) {
			return;
		}
		int top = this.topPos + GRID_Y;
		context.fill(this.leftPos + BAR_X, top, this.leftPos + BAR_X + BAR_WIDTH,
				top + TRACK_HEIGHT, 0xFFB2B2B2);
		int thumb = Math.max(8, TRACK_HEIGHT * VISIBLE_ROWS / rows());
		int thumbTop = top + (TRACK_HEIGHT - thumb) * scroll / max;
		context.fill(this.leftPos + BAR_X, thumbTop, this.leftPos + BAR_X + BAR_WIDTH,
				thumbTop + thumb, 0xFF585858);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		for (ChipButton button : choices) {
			if (button.visible && button.isHovered()) {
				context.renderComponentTooltip(this.font, button.tooltip(), mouseX, mouseY);
			}
		}
	}

	/** 一格芯片：悬停补一圈框，点击把序号发给服务端。位置由 {@link #layout()} 按滚动量摆 */
	private static class ChipButton extends AbstractWidget {
		private final Chip chip;
		private final ItemStack stack;
		private final Minecraft client;

		ChipButton(Chip chip, Minecraft client) {
			super(0, 0, 20, 20, Component.translatable(chip.nameKey()));
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
