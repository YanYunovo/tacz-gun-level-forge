package com.eternal.gunlevel.client;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.util.ChipHints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 芯片触发提示：画在经验条上方那一块，每有一颗在显示就多占一行、往上叠，
 * 两颗同时触发也分得清是哪两颗。
 * 不能用 action bar —— 假人模组的 DPS 读数就是发 overlay 消息，会和它叠在同一行。
 * 原版把 overlay 画在 scaledHeight-68 平移后再 -4（即底部往上 72px），
 * 所以默认从 84 起，正好压在那条读数上方一行，间距想调就改 chipHintBottomMargin。
 */
public final class ChipHud implements IGuiOverlay {
	private static final Chip[] CHIPS = Chip.values();
	private static final long SHOW_MILLIS = 1500L;
	private static final int LINE_HEIGHT = 12;
	private static final int COLOR = 0xFFE066;

	private final long[] triggeredAt = new long[CHIPS.length];
	/** 每颗芯片当前这一行用哪种文案（ChipHints 的 kind） */
	private final int[] kinds = new int[CHIPS.length];

	@Override
	public void render(ForgeGui gui, GuiGraphics context, float partialTick, int width, int height) {
		Minecraft client = Minecraft.getInstance();
		if (!GunLevelMod.CONFIG.chipHintEnabled
				|| client.player == null || client.options.hideGui) {
			return;
		}
		int centerX = width / 2;
		int baseY = height - GunLevelMod.CONFIG.chipHintBottomMargin;
		long now = System.currentTimeMillis();
		// 行号跟着「当前在显示的第几条」走，不绑芯片序号，
		// 否则以后芯片种类多于槽位数时，装着的会是靠后的几颗、中间空出一行
		int row = 0;
		for (int i = 0; i < CHIPS.length; i++) {
			if (now - triggeredAt[i] >= SHOW_MILLIS) {
				continue;
			}
			Component line = switch (kinds[i]) {
				case ChipHints.AURORA_READY -> Component.translatable("msg.tacz_gun_level.aurora.ready");
				case ChipHints.SKILL_READY -> Component.translatable(CHIPS[i].skillReadyKey());
				case ChipHints.SKILL_LOCKED -> Component.translatable("msg.tacz_gun_level.skill.locked");
				default -> Component.translatable("msg.tacz_gun_level.chip.trigger",
						Component.translatable(CHIPS[i].nameKey()));
			};
			context.drawCenteredString(client.font, line, centerX, baseY - row * LINE_HEIGHT, COLOR);
			row++;
		}
	}

	public void show(int chipOrdinal, int kind) {
		if (chipOrdinal >= 0 && chipOrdinal < CHIPS.length) {
			triggeredAt[chipOrdinal] = System.currentTimeMillis();
			kinds[chipOrdinal] = kind;
		}
	}
}
