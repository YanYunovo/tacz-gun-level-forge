package com.eternal.gunlevel.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 极光追击的按枪状态，全部记在枪物品 NBT 上，状态跟着枪走而不是跟着人走：
 * <ul>
 *   <li>{@code TAG} —— 手上这一匣是特殊换弹换出来的极光匣（换枪、放下再拿起都不会带错地方）。</li>
 *   <li>{@code HITS_TAG} —— 这把枪攒了几次命中（只对装了极光追击的枪记账，见 AuroraState）。</li>
 * </ul>
 */
public final class GunAuroraData {
	public static final String TAG = "tacz_gun_level_aurora_mag";
	public static final String HITS_TAG = "tacz_gun_level_aurora_hits";

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

	/** 这把枪攒了几次命中（用户口径「武器累计命中」，所以按枪记，不按人记） */
	public static int hits(ItemStack gun) {
		CompoundTag nbt = gun.isEmpty() ? null : gun.getTag();
		return nbt == null ? 0 : nbt.getInt(HITS_TAG);
	}

	/** 写命中数：归零时只把键删掉，别给一张本来干净的枪留个空 compound（tooltip 会多一行模组名） */
	public static void setHits(ItemStack gun, int hits) {
		if (gun.isEmpty()) {
			return;
		}
		if (hits <= 0) {
			CompoundTag nbt = gun.getTag();
			if (nbt != null) {
				nbt.remove(HITS_TAG);
			}
			return;
		}
		gun.getOrCreateTag().putInt(HITS_TAG, hits);
	}
}
