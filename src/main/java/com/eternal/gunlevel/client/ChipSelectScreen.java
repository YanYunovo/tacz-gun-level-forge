package com.eternal.gunlevel.client;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.menu.ChipSelectMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * 芯片自选包的界面：上半区把本品级的芯片摆成网格，一排 5 格、只放得下两行，
 * 装不下的用右边「上页 / 下页」两颗按钮整页翻，网格下方标「1/2页」这种页码；
 * 下半区是玩家自己的背包（数量同步走的是这个容器的 broadcastChanges，所以必须带上）。
 * 金蓝两档共用这一个 Screen 类，摆哪一档、几页都看菜单实例给的芯片数。
 */
public class ChipSelectScreen extends AbstractContainerScreen<ChipSelectMenu> {
	/** 一格 20px、间隔 2px。一排 5 格而不是铺满：芯片还会继续加，摆成两排七列迟早挤成一坨 */
	private static final int CELL = 22;
	private static final int COLUMNS = 5;
	/** 原版底图给自定义区只留得下两行（y=17~71，再往下就是背包标题），多的翻页 */
	private static final int VISIBLE_ROWS = 2;
	private static final int GRID_Y = 20;
	private static final int GRID_X = 32;
	/** 每页颗数：翻页按整页走，页码才对得上「第几页」 */
	private static final int PER_PAGE = COLUMNS * VISIBLE_ROWS;
	/** 右边距两颗翻页键：24 宽正好贴到面板内侧（x=144~168） */
	private static final int NAV_X = GRID_X + COLUMNS * CELL + 2;
	private static final int NAV_WIDTH = 24;
	/** 原版按钮的标准高度；CELL 22 里上下各留 1px */
	private static final int NAV_HEIGHT = 20;
	/** 页码那行落在网格与背包标题之间那点空档（y=64~73，标题在 74） */
	private static final int PAGE_TEXT_Y = GRID_Y + VISIBLE_ROWS * CELL;

	private final List<ChipButton> choices = new ArrayList<>();
	/** 原版样式的文字按钮（跟「设置」那种同款），只有一页以上才出现，翻到头自己变灰 */
	private final Button previous = Button.builder(
			Component.translatable("container.tacz_gun_level.chip_select_prev"),
			button -> turnPage(-1)).bounds(0, 0, NAV_WIDTH, NAV_HEIGHT).build();
	private final Button next = Button.builder(
			Component.translatable("container.tacz_gun_level.chip_select_next"),
			button -> turnPage(1)).bounds(0, 0, NAV_WIDTH, NAV_HEIGHT).build();
	private int page;

	public ChipSelectScreen(ChipSelectMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = VanillaPanel.WIDTH;
		this.imageHeight = VanillaPanel.HEIGHT;
		this.inventoryLabelY = VanillaPanel.HEIGHT - 94;
	}

	private int pages() {
		return Math.max(1, (this.menu.choices().size() + PER_PAGE - 1) / PER_PAGE);
	}

	@Override
	protected void init() {
		super.init();
		choices.clear();
		for (Chip chip : this.menu.choices()) {
			ChipButton button = new ChipButton(chip, this.minecraft);
			choices.add(button);
			this.addRenderableWidget(button);
		}
		this.addRenderableWidget(previous);
		this.addRenderableWidget(next);
		page = Math.min(page, pages() - 1);
		layout();
	}

	/** 按当前页重排：芯片格摆到各自的行上，不在这一页的挪出视野 */
	private void layout() {
		for (int i = 0; i < choices.size(); i++) {
			ChipButton button = choices.get(i);
			int view = i / COLUMNS - page * VISIBLE_ROWS;
			boolean shown = view >= 0 && view < VISIBLE_ROWS;
			button.setX(this.leftPos + GRID_X + (i % COLUMNS) * CELL);
			button.setY(this.topPos + GRID_Y + view * CELL);
			// 只藏不置灰的话，落在面板里那一行点得到却看不见，会误发序号
			button.visible = shown;
			button.active = shown;
		}
		boolean paged = pages() > 1;
		// 贴在网格右侧一列，两颗各占一格、上下各留 1px；翻到头变灰，只有一页就整个藏掉
		previous.visible = next.visible = paged;
		previous.active = paged && page > 0;
		next.active = paged && page < pages() - 1;
		previous.setX(this.leftPos + NAV_X);
		previous.setY(this.topPos + GRID_Y + (CELL - NAV_HEIGHT) / 2);
		next.setX(this.leftPos + NAV_X);
		next.setY(this.topPos + GRID_Y + CELL + (CELL - NAV_HEIGHT) / 2);
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		VanillaPanel.draw(context, this.leftPos, this.topPos);
		// 每颗芯片一个原版槽框：光秃秃摆物品看着像没互动的装饰
		int chips = this.menu.choices().size();
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			for (int col = 0; col < COLUMNS; col++) {
				if ((page * VISIBLE_ROWS + row) * COLUMNS + col < chips) {
					VanillaPanel.slotFrame(context, this.leftPos, this.topPos,
							GRID_X + col * CELL + 2, GRID_Y + row * CELL + 2);
				}
			}
		}
		if (pages() > 1) {
			VanillaPanel.center(context,
					Component.translatable("container.tacz_gun_level.chip_select_page",
							page + 1, pages()),
					this.font, this.leftPos + GRID_X + COLUMNS * CELL / 2,
					this.topPos + PAGE_TEXT_Y, 0x404040);
		}
	}

	/** 一格芯片：悬停补一圈框，点击把序号发给服务端。位置由 {@link #layout()} 按当前页摆 */
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
		protected void updateWidgetNarration(NarrationElementOutput out) {
			this.defaultButtonNarrationText(out);
		}

		List<Component> tooltip() {
			return this.stack.getTooltipLines(this.client.player, TooltipFlag.Default.NORMAL);
		}
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

	private void turnPage(int direction) {
		int target = Math.max(0, Math.min(pages() - 1, page + direction));
		if (target != page) {
			page = target;
			layout();
		}
	}
}
