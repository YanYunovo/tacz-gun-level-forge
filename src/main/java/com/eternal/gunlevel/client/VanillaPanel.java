package com.eternal.gunlevel.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 用原版箱子贴图打底的面板。generic_54 不能整张直接铺：它自带 6 排 9 列的槽位底框，
 * 排布和我们自定义槽位对不上，铺出来就是一大片错位的空网格。
 * 所以照原版自己的拼法分三段画，中间自定义区用贴图里那段没有槽框的空白带平铺，
 * 边框和底色都是原版像素，不会像 fill 那样把侧边框啃掉。
 */
final class VanillaPanel {
	private static final ResourceLocation CHEST_TEXTURE =
			new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
	/** 与原版三行箱子（114 + 18*3）完全一致 */
	static final int WIDTH = 176;
	static final int HEIGHT = 168;
	/** 自定义槽位区：上段结束处 ~ 下段（玩家背包）开始处 */
	static final int BODY_TOP = 17;
	static final int BODY_BOTTOM = HEIGHT - 97;
	/** 贴图里 v=125 起是背包标题上方那段空白：只有边框和底色，没有槽框 */
	private static final int STRIP_V = 125;
	private static final int STRIP_HEIGHT = 14;
	/** 原版槽位底框在贴图里的位置（一格 18x18） */
	private static final int FRAME_U = 7;
	private static final int FRAME_V = 17;

	private VanillaPanel() {
	}

	static void draw(GuiGraphics context, int x, int y) {
		context.blit(CHEST_TEXTURE, x, y, 0, 0, WIDTH, BODY_TOP);
		for (int dy = BODY_TOP; dy < BODY_BOTTOM; dy += STRIP_HEIGHT) {
			context.blit(CHEST_TEXTURE, x, y + dy, 0, STRIP_V,
					WIDTH, Math.min(STRIP_HEIGHT, BODY_BOTTOM - dy));
		}
		context.blit(CHEST_TEXTURE, x, y + BODY_BOTTOM, 0, 126, WIDTH, 96);
	}

	/** 1.20.1 的 AbstractContainerScreen 不给空槽画框，自定义槽位要自己补一格 */
	static void slotFrame(GuiGraphics context, int x, int y, int slotX, int slotY) {
		context.blit(CHEST_TEXTURE, x + slotX - 1, y + slotY - 1, FRAME_U, FRAME_V, 18, 18);
	}

	static void center(GuiGraphics context, Component text, net.minecraft.client.gui.Font renderer,
			int centerX, int y, int color) {
		// 浅灰底上用阴影会把中文字形糊成一片，一律不画阴影
		context.drawString(renderer, text, centerX - renderer.width(text) / 2, y, color, false);
	}
}
