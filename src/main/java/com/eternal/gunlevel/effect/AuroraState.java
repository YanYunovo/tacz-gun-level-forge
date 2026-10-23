package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunAuroraData;
import com.eternal.gunlevel.item.GunChipData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 极光追击的命中记账：**按枪**累计（用户口径「武器累计命中 chipAuroraHits 次」），
 * 账记在那把枪物品的 NBT 上（见 {@link GunAuroraData}）。所以：
 * 只有装着这颗芯片的枪打出去的命中才攒；换另一把极光枪不会接到上一把的进度；
 * 用没装这颗芯片的枪打人一个数都不加。攒满后下一次换弹消耗掉就绪（换成极光匣）并把这把枪清零。
 * 从极光匣里打出去的子弹一颗都不记，所以「打完一匣 → 重新攒满 → 再开一匣」串不成连发。
 *
 * <p>「还在不在极光匣里」同样记在枪 NBT 上，不在计时器上：
 * 跟着弹匣走才不会一匣没打完就掉加成，也不会因为效果到点而提前重新开始累计命中。
 */
public final class AuroraState {
	private AuroraState() {
	}

	/** 这把枪是不是装了极光追击 */
	public static boolean carries(ItemStack gun) {
		return !gun.isEmpty() && GunChipData.get(gun).contains(Chip.AURORA);
	}

	/**
	 * 手上这一匣是不是特殊换弹换出来的极光匣（决定射速加成、弹匣翻倍和停止累计）。
	 * 服务端读枪 NBT；客户端读不到可靠的枪 NBT（弹药是 TACZ 自己同步的），
	 * 所以只看 {@link #syncCarrier} 投影出来的那个效果。
	 */
	public static boolean isAuroraMag(LivingEntity shooter) {
		if (shooter == null) {
			return false;
		}
		return shooter.level().isClientSide
				? shooter.hasEffect(GunLevelMod.AURORA)
				: GunAuroraData.isOn(shooter.getMainHandItem());
	}

	/**
	 * 把「手上这匣是不是极光匣」投影成射手身上的效果，供客户端预测射速。
	 * 效果恒定是无限时长（不会在一匣没打完时到点），每次开火都会重新对齐，
	 * 所以换了枪最多晚一发就会把图标和加成从错误的枪上撤掉。
	 */
	public static void syncCarrier(LivingEntity shooter) {
		if (shooter == null || shooter.level().isClientSide) {
			return;
		}
		boolean marked = GunAuroraData.isOn(shooter.getMainHandItem());
		MobEffectInstance current = shooter.getEffect(GunLevelMod.AURORA);
		// 已经对上了就别每发都去动效果表；旧存档里残留的限时效果要换成无限的
		if (marked == (current != null) && (current == null || current.isInfiniteDuration())) {
			return;
		}
		if (marked) {
			shooter.addEffect(new MobEffectInstance(
					GunLevelMod.AURORA, MobEffectInstance.INFINITE_DURATION, 0, false, true));
		} else {
			shooter.removeEffect(GunLevelMod.AURORA);
		}
	}

	/**
	 * 打中一发：只认装着这颗芯片的那把枪，账记在枪 NBT 上。
	 * 返回是否刚跨过就绪线（调用方据此提示「极光弹匣已就绪」）；只在跨过去那一次返回 true，
	 * 用「跨过」而不是「相等」是因为阈值可以运行时改小，配相等会永远对不上。
	 *
	 * @param gun           打中这一发的那把枪（服务端从射手背包里找出来的那一件）
	 * @param fromAuroraMag 这一发是不是从极光匣里打出去的（子弹生成时定格，命中时玩家可能已经换枪）
	 */
	public static boolean onHit(LivingEntity shooter, ItemStack gun, boolean fromAuroraMag) {
		if (shooter == null || fromAuroraMag || !carries(gun)) {
			return false;
		}
		int threshold = threshold();
		int before = GunAuroraData.hits(gun);
		int after = before + 1;
		GunAuroraData.setHits(gun, after);
		return after >= threshold && before < threshold;
	}

	public static boolean isReady(ItemStack gun) {
		return carries(gun) && GunAuroraData.hits(gun) >= threshold();
	}

	private static int threshold() {
		return Math.max(1, GunLevelMod.CONFIG.chipAuroraHits);
	}

	/** 特殊换弹用掉就绪：这把枪的账清零，重新攒下一轮 */
	public static void consume(ItemStack gun) {
		GunAuroraData.setHits(gun, 0);
	}
}
