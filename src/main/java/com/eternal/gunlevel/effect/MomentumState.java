package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 势如破竹的运行时窗口：对近距目标造成伤害时开一段加成，按射手 UUID 记账，只在服务端有值。
 *
 * 窗口长度就是那条冷却（chipMomentumSeconds）：开着的时候再打伤害不会重开，
 * 加成每秒掉 1/6（六档掉完正好到点），期间击杀目标会把加成重新拉满、衰减照常继续。
 * 所以这里只记「满值时刻」，结束时刻 = 满值时刻 + 窗口长度，击杀就是把满值时刻改回现在。
 */
public final class MomentumState {
	private static final ConcurrentHashMap<UUID, Long> FULL_AT = new ConcurrentHashMap<>();

	private MomentumState() {
	}

	private static long span() {
		return Math.max(1, Math.round(GunLevelMod.CONFIG.chipMomentumSeconds * 20));
	}

	/** 当前加成比例：1 = 满值，每秒掉一档（还剩几秒就是几分之几），窗口外为 0 */
	public static double strength(LivingEntity shooter, long time) {
		Long fullAt = shooter == null ? null : FULL_AT.get(shooter.getUUID());
		if (fullAt == null) {
			return 0.0;
		}
		long left = fullAt + span() - time;
		if (left <= 0) {
			return 0.0;
		}
		// 向上取整到整秒：最后 1 tick 仍然停在 1/6，到点才归零
		long secondsLeft = (left + 19) / 20;
		return Math.min(1.0, secondsLeft / GunLevelMod.CONFIG.chipMomentumSeconds);
	}

	/**
	 * 打中一下：窗口没开就开一个，开着的时候只有击杀能重新拉满。
	 * 返回 true 表示这一刻确实拿到了加成（新开或击杀刷新），调用方据此提示。
	 */
	public static boolean onHit(LivingEntity shooter, long time, boolean killed) {
		if (shooter == null) {
			return false;
		}
		Long fullAt = FULL_AT.get(shooter.getUUID());
		boolean active = fullAt != null && time < fullAt + span();
		if (active && !killed) {
			return false;
		}
		FULL_AT.put(shooter.getUUID(), time);
		return true;
	}

	public static void tick(MinecraftServer server) {
		if (FULL_AT.isEmpty()) {
			return;
		}
		long time = server.overworld().getGameTime();
		long span = span();
		FULL_AT.entrySet().removeIf(entry -> time >= entry.getValue() + span);
	}

	public static void clear() {
		FULL_AT.clear();
	}
}
