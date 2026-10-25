package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.util.TaczCompat;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 弹药征用：击杀按永恒改装等级返还后备弹药（{@code chipSalvageAmmoPerLevel} 发/级）。
 * 造弹与入包与烈焰咆哮同一条管线：弹型写在枪数据 AmmoId 上，只能走 AmmoItemBuilder，
 * insertStack 塞不下的掉在脚下，绝不凭空消失。
 */
public final class SalvageState {
	private SalvageState() {
	}

	public static void onKill(ServerPlayer player, ItemStack gun, int level) {
		if (player == null || gun == null || gun.isEmpty() || level <= 0) {
			return;
		}
		var ammoId = TaczCompat.ammoIdOf(gun);
		if (ammoId == null) {
			return;
		}
		int rounds = GunLevelMod.CONFIG.chipSalvageAmmoPerLevel * level;
		int max = Math.max(1, AmmoItemBuilder.create().setId(ammoId).setCount(1)
				.build().getMaxStackSize());
		int left = rounds;
		List<ItemStack> main = player.getInventory().items;
		// 先把已有的同型弹药堆补满，再走造弹入包
		while (left > 0) {
			int before = left;
			for (ItemStack stack : main) {
				if (left <= 0) {
					break;
				}
				IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
				if (ammo == null || !ammo.isAmmoOfGun(gun, stack)
						|| stack.getCount() >= max) {
					continue;
				}
				int give = Math.min(left, max - stack.getCount());
				stack.grow(give);
				left -= give;
			}
			if (left == before) {
				break;
			}
		}
		while (left > 0) {
			int size = Math.min(left, max);
			left -= size;
			ItemStack stack = AmmoItemBuilder.create().setId(ammoId).setCount(size).build();
			if (!player.getInventory().add(stack) && !stack.isEmpty()) {
				player.drop(stack, false);
			}
		}
	}
}
