package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 先发制人的战斗进入记账：**命中才算进入战斗**（与愈战愈勇同一条口径，用户 2026-10-17 定），
 * 连续 {@code chipPurpleOpeningSeconds} 秒没有命中视为脱战，下一次命中重新「进入战斗」；
 * 进入战斗后的这个秒数内的命中吃加成，之后还在打也不加（开局爆发的收益模型）。
 *
 * <p>账按射手 UUID 记、**不挑枪**：换别的枪打一样维持「在战斗中」的状态，
 * 免得连打十分钟之后切回这把枪莫名其妙又领一次「进入战斗」。
 * 只在服务端有值。
 */
public final class OpeningState {
	private static final class Combat {
		/** 这一场战斗从哪一刻开始（第一次命中的世界刻） */
		long startedAt;
		/** 最后一次命中的世界刻，脱战判定按它算 */
		long lastHitAt;
	}

	private static final ConcurrentHashMap<UUID, Combat> COMBATS = new ConcurrentHashMap<>();

	private OpeningState() {
	}

	private static long windowTicks() {
		return Math.max(1, Math.round(GunLevelMod.CONFIG.chipPurpleOpeningSeconds * 20));
	}

	/** 进入战斗后窗口内的命中吃加成（0 = 没在战斗或已过窗口） */
	public static double bonus(LivingEntity shooter, long time) {
		Combat combat = shooter == null ? null : COMBATS.get(shooter.getUUID());
		return combat != null && time - combat.startedAt <= windowTicks()
				? GunLevelMod.CONFIG.chipPurpleOpeningDamage : 0.0;
	}

	/** 打中一下：脱战过就新开一场（这一下就是「进入战斗」那一刻） */
	public static void onHit(LivingEntity shooter, long time) {
		if (shooter == null) {
			return;
		}
		Combat combat = COMBATS.computeIfAbsent(shooter.getUUID(), id -> new Combat());
		if (time - combat.lastHitAt >= windowTicks()) {
			combat.startedAt = time;
		}
		combat.lastHitAt = time;
	}

	public static void tick(MinecraftServer server) {
		if (COMBATS.isEmpty()) {
			return;
		}
		long time = server.overworld().getGameTime();
		long window = windowTicks();
		COMBATS.entrySet().removeIf(entry -> time - entry.getValue().lastHitAt >= window);
	}

	public static void clear() {
		COMBATS.clear();
	}
}
