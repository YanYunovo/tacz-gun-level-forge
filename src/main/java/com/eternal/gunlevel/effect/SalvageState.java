package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.util.TaczCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 弹药征用：击杀后直接填充主弹匣，数量按弹匣容量、永恒改装等级和溢出上限计算。
 */
public final class SalvageState {
	private SalvageState() {
	}

	public static void onKill(ServerPlayer player, ItemStack gun, int level) {
		if (player == null || gun == null || gun.isEmpty() || level <= 0) {
			return;
		}
		int capacity = TaczCompat.magazineCapacity(gun);
		if (capacity <= 0) {
			return;
		}
		double ratio = GunLevelMod.CONFIG.chipSalvageMagazineRatio;
		int wanted = Math.max(GunLevelMod.CONFIG.chipSalvageMinAmmo,
				(int) Math.ceil(capacity * ratio * level));
		int current = TaczCompat.currentAmmoCount(gun);
		int limit = Math.max(0, (int) Math.round(capacity
				* GunLevelMod.CONFIG.chipSalvageCapacityCapRatio));
		int target = Math.min(limit, current + wanted);
		if (target > current) {
			TaczCompat.setAmmoCount(gun, target);
		}
	}
}
