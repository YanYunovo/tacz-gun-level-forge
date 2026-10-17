package com.eternal.gunlevel.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 极光匣标记：记在枪物品 NBT 上，表示「手上这一匣是特殊换弹换出来的极光匣」。
 * 状态跟着弹匣走而不是跟着人走，所以换枪、放下枪再拿起来都不会把加成带错地方，
 * 也不会因为计时器到点而在一匣没打完时提前结束。
 */
public final class GunAuroraData {
	public static final String TAG = "tacz_gun_level_aurora_mag";

	private GunAuroraData() {
	}

	public static boolean isOn(ItemStack gun) {
		CompoundTag nbt = gun.isEmpty() ? null : gun.getTag();
		return nbt != null && nbt.getBoolean(TAG);
	}

	public static void set(ItemStack gun, boolean on) {
		if (gun.isEmpty()) {
			return;
		}
		if (on) {
			gun.getOrCreateTag().putBoolean(TAG, true);
		} else {
			gun.getOrCreateTag().remove(TAG);
		}
	}
}
