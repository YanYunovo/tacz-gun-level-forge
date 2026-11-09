package com.eternal.gunlevel.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** 爆裂射击的每把枪独立身份：让同一位玩家手上的两把枪各记各的充能。 */
public final class GunBurstData {
	private static final String TAG = "tacz_gun_level_burst_id";
	private static final UUID NONE = new UUID(0L, 0L);

	private GunBurstData() {
	}

	public static UUID id(ItemStack stack) {
		if (stack.isEmpty()) {
			return NONE;
		}
		CompoundTag nbt = stack.getTag();
		if (nbt != null && nbt.hasUUID(TAG)) {
			return nbt.getUUID(TAG);
		}
		UUID id = UUID.randomUUID();
		stack.getOrCreateTag().putUUID(TAG, id);
		return id;
	}
}