package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 荣誉勋章的击杀叠层：击杀 +1 层（每层 +{@code chipMedalDamagePerLevel}×永恒改装等级，
 * 上限 {@code chipMedalMaxStacks} 层），**自己受到伤害立刻整条清零**（用户口径），
 * 连续 {@code chipMedalResetSeconds} 秒没有击杀也清零。账按玩家 UUID 记、不挑枪。
 * 只在服务端有值。
 */
public final class MedalState {
	private static final class Ledger {
		int stacks;
		long lastKillAt;
	}

	private static final Map<UUID, Ledger> LEDGERS = new ConcurrentHashMap<>();

	private MedalState() {
	}

	private static long resetTicks() {
		return Math.max(1, Math.round(GunLevelMod.CONFIG.chipMedalResetSeconds * 20));
	}

	/** 当前伤害加成比例（0 = 没有层数） */
	public static double bonus(LivingEntity shooter, long time, int level) {
		Ledger ledger = shooter == null ? null : LEDGERS.get(shooter.getUUID());
		if (ledger == null || ledger.stacks <= 0 || time - ledger.lastKillAt >= resetTicks()) {
			return 0.0;
		}
		return GunLevelMod.CONFIG.chipMedalDamagePerLevel * level * ledger.stacks;
	}

	/** 击杀叠一层（已在上限就只刷新时间） */
	public static void onKill(LivingEntity shooter, long time) {
		if (shooter == null) {
			return;
		}
		Ledger ledger = LEDGERS.computeIfAbsent(shooter.getUUID(), id -> new Ledger());
		if (time - ledger.lastKillAt >= resetTicks()) {
			ledger.stacks = 0;
		}
		ledger.lastKillAt = time;
		ledger.stacks = Math.min(GunLevelMod.CONFIG.chipMedalMaxStacks, ledger.stacks + 1);
	}

	/** 自己受到伤害：整条清零（挂在勇者无惧同一个伤害入口上） */
	public static void onPlayerDamaged(Player player) {
		if (player != null && !player.level().isClientSide()) {
			LEDGERS.remove(player.getUUID());
		}
	}

	public static void tick(MinecraftServer server) {
		if (LEDGERS.isEmpty()) {
			return;
		}
		long time = server.overworld().getGameTime();
		long reset = resetTicks();
		LEDGERS.entrySet().removeIf(entry -> time - entry.getValue().lastKillAt >= reset);
	}

	public static void clear() {
		LEDGERS.clear();
	}
}
