package com.eternal.gunlevel.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 判断是否为 TACZ 枪械物品并读取 gun id。
 *
 * <p>TACZ 是前置而不是硬依赖：dev 实例里没装它的时候，只要碰一下 {@code IGun} 就会
 * {@code NoClassDefFoundError}，连三台机器的界面都打不开。所以真正引用 TACZ 的代码全在
 * {@link TaczBridge} 里，这一类只按 {@link #TACZ_LOADED} 决定要不要去加载它 ——
 * 类没被用到就不会被解析，探测不到前置就当「手上没有枪」处理。
 */
public final class TaczCompat {
	private static final boolean TACZ_LOADED = taczLoaded();

	private TaczCompat() {
	}

	private static boolean taczLoaded() {
		try {
			Class.forName("com.tacz.guns.api.item.IGun", false, TaczCompat.class.getClassLoader());
			return true;
		} catch (Throwable e) {
			return false;
		}
	}

	/** 前置在不在：诊断日志用，免得「等级没生效」查半天其实是没装 TACZ */
	public static boolean isTaczLoaded() {
		return TACZ_LOADED;
	}

	public static boolean isGun(ItemStack stack) {
		return TACZ_LOADED && !stack.isEmpty() && TaczBridge.isGun(stack);
	}

	/** 往主弹匣补子弹：没装前置时什么都不做 */
	public static void refillMagazine(ItemStack gun, int amount) {
		if (TACZ_LOADED && !gun.isEmpty()) {
			TaczBridge.refillMagazine(gun, amount);
		}
	}

	public static ResourceLocation gunIdOf(ItemStack stack) {
		if (!TACZ_LOADED || stack.isEmpty()) {
			return null;
		}
		return TaczBridge.gunIdOf(stack);
	}

}
