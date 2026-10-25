package com.eternal.gunlevel.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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

	/**
	 * 在射手手上或背包里找到对应 gunId 的枪物品；找不到返回 EMPTY。
	 * 只用 isGun/gunIdOf 两个口子，所以这里不出现任何 TACZ 类型，前置缺席也只是找不到。
	 */
	public static ItemStack findGunStack(LivingEntity shooter, ResourceLocation gunId) {
		if (shooter == null || gunId == null) {
			return ItemStack.EMPTY;
		}
		ItemStack main = shooter.getMainHandItem();
		if (matches(main, gunId)) {
			return main;
		}
		ItemStack off = shooter.getOffhandItem();
		if (matches(off, gunId)) {
			return off;
		}
		if (shooter instanceof Player player) {
			for (ItemStack stack : player.getInventory().items) {
				if (matches(stack, gunId)) {
					return stack;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	private static boolean matches(ItemStack stack, ResourceLocation gunId) {
		return isGun(stack) && gunId.equals(gunIdOf(stack));
	}

	/** 这一匣装满是多少发（TACZ 的唯一真值）；没装前置返回 0 */
	public static int magazineCapacity(ItemStack gun) {
		if (!TACZ_LOADED || gun == null || gun.isEmpty()) {
			return 0;
		}
		return TaczBridge.magazineCapacity(gun);
	}

	public static ResourceLocation gunIdOf(ItemStack stack) {
		if (!TACZ_LOADED || stack.isEmpty()) {
			return null;
		}
		return TaczBridge.gunIdOf(stack);
	}

	/** 这把枪的逻辑弹型 id（ammoId 在 NBT 上，不是物品 id；拿不到返回 null） */
	public static ResourceLocation ammoIdOf(ItemStack gun) {
		if (!TACZ_LOADED || gun == null || gun.isEmpty()) {
			return null;
		}
		return TaczBridge.ammoIdOf(gun);
	}
}
