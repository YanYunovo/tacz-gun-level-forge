package com.eternal.gunlevel.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 唯一直接引用 TACZ 的类：只允许在探测到前置存在时被 {@link TaczCompat} 加载。
 * 方法签名一律不出现 TACZ 类型，免得调用方因为方法引用而提前解析这个类。
 */
final class TaczBridge {
	private TaczBridge() {
	}

	static boolean isGun(ItemStack stack) {
		return IGun.getIGunOrNull(stack) != null;
	}

	/** 往主弹匣里补子弹：允许顶破容量上限（迅雷之音的设计就是这一匣可以多出来） */
	static void refillMagazine(ItemStack gun, int amount) {
		IGun iGun = IGun.getIGunOrNull(gun);
		if (iGun == null || amount <= 0) {
			return;
		}
		iGun.setCurrentAmmoCount(gun, iGun.getCurrentAmmoCount(gun) + amount);
	}

	/** 这一匣装满是多少发（带配件修正，也含我们挂同一条链上的迅雷减容/弹匣扩容） */
	static int magazineCapacity(ItemStack gun) {
		IGun iGun = IGun.getIGunOrNull(gun);
		if (iGun == null) {
			return 0;
		}
		return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
				.map(index -> com.tacz.guns.util.AttachmentDataUtils
						.getAmmoCountWithAttachment(gun, index.getGunData()))
				.orElse(0);
	}

	static ResourceLocation gunIdOf(ItemStack stack) {
		IGun gun = IGun.getIGunOrNull(stack);
		return gun == null ? null : gun.getGunId(stack);
	}

	/** 这把枪的逻辑弹型 id（ammoId 在 NBT 上，不是物品 id；拿不到返回 null） */
	static ResourceLocation ammoIdOf(ItemStack gun) {
		IGun iGun = IGun.getIGunOrNull(gun);
		if (iGun == null) {
			return null;
		}
		return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
				.map(index -> index.getGunData().getAmmoId())
				.orElse(null);
	}
}
