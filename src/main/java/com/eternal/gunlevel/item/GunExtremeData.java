package com.eternal.gunlevel.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** 极限火力：把「定点射击进行中」记在枪物品 NBT 上，让容量改写点与服务端/客户端都能读到同一状态。 */
public final class GunExtremeData {
	private static final String TAG = "tacz_gun_level_extreme_fire";

	private GunExtremeData() {
	}

	public static boolean isOn(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CompoundTag nbt = stack.getTag();
		return nbt != null && nbt.getBoolean(TAG);
	}

	public static void set(ItemStack stack, boolean on) {
		if (stack.isEmpty()) {
			return;
		}
		if (on) {
			stack.getOrCreateTag().putBoolean(TAG, true);
		} else {
			CompoundTag nbt = stack.getTag();
			if (nbt != null) {
				nbt.remove(TAG);
			}
		}
	}
}
