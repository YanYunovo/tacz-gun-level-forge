package com.eternal.gunlevel.client;

import com.eternal.gunlevel.effect.GogglesState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** 数据倾泻期间的主视角量子以太边框：青色细框、四角亮括号与一条缓慢扫描线。 */
public final class GogglesHud {
	private GogglesHud() {
	}

	public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
			int screenWidth, int screenHeight) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui
				|| !GogglesState.isClientActive(client.player.getUUID())) {
			return;
		}
		long time = System.currentTimeMillis();
		int pulse = 70 + (int) (25.0 * Math.sin(time / 280.0));
		int frame = (pulse << 24) | 0x00E5FF;
		int corner = 0xCC7DF9FF;
		int margin = 6;
		int len = 26;
		int thick = 2;

		graphics.fill(margin, margin, screenWidth - margin, margin + 1, frame);
		graphics.fill(margin, screenHeight - margin - 1, screenWidth - margin, screenHeight - margin, frame);
		graphics.fill(margin, margin, margin + 1, screenHeight - margin, frame);
		graphics.fill(screenWidth - margin - 1, margin, screenWidth - margin, screenHeight - margin, frame);

		graphics.fill(margin, margin, margin + len, margin + thick, corner);
		graphics.fill(margin, margin, margin + thick, margin + len, corner);
		graphics.fill(screenWidth - margin - len, margin, screenWidth - margin, margin + thick, corner);
		graphics.fill(screenWidth - margin - thick, margin, screenWidth - margin, margin + len, corner);
		graphics.fill(margin, screenHeight - margin - thick, margin + len, screenHeight - margin, corner);
		graphics.fill(margin, screenHeight - margin - len, margin + thick, screenHeight - margin, corner);
		graphics.fill(screenWidth - margin - len, screenHeight - margin - thick, screenWidth - margin, screenHeight - margin, corner);
		graphics.fill(screenWidth - margin - thick, screenHeight - margin - len, screenWidth - margin, screenHeight - margin, corner);

		int scanY = margin + (int) ((time / 14L) % Math.max(1, screenHeight - margin * 2));
		graphics.fill(margin + 1, scanY, screenWidth - margin - 1, scanY + 1, 0x2200E5FF);
	}
}