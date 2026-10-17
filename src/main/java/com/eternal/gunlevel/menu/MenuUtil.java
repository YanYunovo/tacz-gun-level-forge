package com.eternal.gunlevel.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 关界面时把容器内容退回给玩家。
 *
 * <p>三台机器的容器不是方块实体，关掉就没人替我们存着，所以必须自己还：先塞背包，塞不下再丢脚下。
 * 创造模式也照还 —— 原版箱子的「创造不掉落」是因为箱子还在地上，我们这儿跳过就是单纯吞东西。
 */
public final class MenuUtil {
	private MenuUtil() {
	}

	public static void returnContents(Player player, Container container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}
		container.clearContent();
	}
}
