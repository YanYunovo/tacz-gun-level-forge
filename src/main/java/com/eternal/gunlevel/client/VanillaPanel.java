package com.eternal.gunlevel.client;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 深色枪钢面板（2026-10-23 用户选型定稿，贴图由 Fabric 仓库的
 * tools/make_gui_textures.py 生成并同步到本仓库 resources）：
 * 每个界面一张 176x168 的整图，槽位底框、背包槽、黄铜标记都烘焙在贴图里，运行时一铺即可。
 * 只有芯片自选的网格要按实际芯片数动态补框 —— 从贴图右上角的备用槽格 (200,8) 取。
 * 旧版拿原版 generic_54 三段拼法的做法已弃用（用户嫌像大箱子）。
 */
final class VanillaPanel {
	/** 深底上的文字色：原版 0x404040 那套深灰在钢底上不可读，全部翻成浅色 */
	static final int TEXT = 0xE4E7EC;
	static final int TEXT_DIM = 0xA8AEB9;
	static final int TEXT_OK = 0x8CD973;
	static final int TEXT_BAD = 0xF08A75;

	/** 与原版三行箱子（114 + 18*3）完全一致，所以背包槽位坐标不用动 */
	static final int WIDTH = 176;
	static final int HEIGHT = 168;

	/** 三张界面贴图（assets/tacz_gun_level/textures/gui/ 下） */
	static final ResourceLocation UPGRADE = texture("machine_upgrade");
	static final ResourceLocation SIX = texture("machine_six");
	static final ResourceLocation SELECT = texture("chip_select");

	private VanillaPanel() {
	}

	private static ResourceLocation texture(String name) {
		return new ResourceLocation(GunLevelMod.MOD_ID, "textures/gui/" + name + ".png");
	}

	static void draw(GuiGraphics context, int x, int y, ResourceLocation texture) {
		context.blit(texture, x, y, 0, 0, WIDTH, HEIGHT);
	}

	/** 芯片自选的动态槽格：从贴图 (200,8) 那格备用槽框取 18x18 */
	static void slotFrame(GuiGraphics context, ResourceLocation texture, int x, int y, int slotX, int slotY) {
		context.blit(texture, x + slotX - 1, y + slotY - 1, 200, 8, 18, 18);
	}

	static void center(GuiGraphics context, Component text, net.minecraft.client.gui.Font renderer,
			int centerX, int y, int color) {
		// 深色底上阴影会把中文字形糊成一片，一律不画阴影
		context.drawString(renderer, text, centerX - renderer.width(text) / 2, y, color, false);
	}
}
