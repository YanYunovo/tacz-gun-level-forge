package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunAuroraData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极光追击的命中记账：只在服务端按射手 UUID 累计，攒满 chipAuroraHits 就就绪，
 * 下一次换弹消耗掉就绪（换成极光匣）并清零。从极光匣里打出去的子弹一颗都不记，
 * 所以「打完一匣 → 重新攒满 → 再开一匣」是串不成连发的。
 *
 * 「还在不在极光匣里」这件事记在枪物品 NBT 上（见 {@link GunAuroraData}），不在计时器上：
 * 跟着弹匣走才不会一匣没打完就掉加成，也不会因为效果到点而提前重新开始累计命中。
 */
public final class AuroraState {
	private static final ConcurrentHashMap<UUID, Integer> HITS = new ConcurrentHashMap<>();

	private AuroraState() {
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
	 * 打中一发：返回是否刚跨过就绪线（调用方据此提示「极光弹匣已就绪」）。
	 * 只在跨过去的那一次返回 true，所以攒满之后继续命中不会每发刷一行提示；
	 * 用「跨过」而不是「等于」是因为就绪阈值可以在运行时改小，配相等会永远对不上。
	 *
	 * {@code fromAuroraMag} 是「这发子弹出自极光匣」，由调用方在子弹生成时按那把枪的标记定格 ——
	 * 命中落地的时刻玩家可能已经换枪了，不能拿当时的主手来判断。
	 */
	public static boolean onHit(LivingEntity shooter, boolean fromAuroraMag) {
		if (shooter == null || fromAuroraMag) {
			return false;
		}
		int threshold = threshold();
		int hits = HITS.merge(shooter.getUUID(), 1, Integer::sum);
		return hits >= threshold && hits - 1 < threshold;
	}

	public static boolean isReady(LivingEntity shooter) {
		if (shooter == null) {
			return false;
		}
		return HITS.getOrDefault(shooter.getUUID(), 0) >= threshold();
	}

	private static int threshold() {
		return Math.max(1, GunLevelMod.CONFIG.chipAuroraHits);
	}

	/** 特殊换弹用掉就绪：计数清零，重新攒下一轮 */
	public static void consume(LivingEntity shooter) {
		if (shooter != null) {
			HITS.remove(shooter.getUUID());
		}
	}

	public static void clear() {
		HITS.clear();
	}

	/** 掉线就把手上的账抹掉，免得留一堆没人认领的计数（就绪状态本来就只在这一局有意义） */
	public static void tick(MinecraftServer server) {
		if (HITS.isEmpty()) {
			return;
		}
		Set<UUID> online = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			online.add(player.getUUID());
		}
		HITS.keySet().retainAll(online);
	}
}
