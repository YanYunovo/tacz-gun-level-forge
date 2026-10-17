package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连环暴击的「激化」窗口：暴击后一段时间内暴击率与暴击伤害再高一档。
 * 只在服务端按射手 UUID 记账（子弹构造和伤害结算都在服务端），主世界 tick 负责清过期条目。
 */
public final class ChipState {
	private static final ConcurrentHashMap<UUID, Window> WINDOWS = new ConcurrentHashMap<>();

	private ChipState() {
	}

	private static final class Window {
		/** 激化到期时间 */
		long until;
		/** 下次能重新触发激化的时间（冷却） */
		long readyAt;
	}

	private static long ticks(double seconds) {
		return Math.max(1, Math.round(seconds * 20));
	}

	/** 发射那一刻射手是否在激化窗口里 */
	public static boolean isEmpowered(LivingEntity shooter, long time) {
		Window window = shooter == null ? null : WINDOWS.get(shooter.getUUID());
		return window != null && time < window.until;
	}

	/** 打出一发暴击：不在冷却里就开一段激化并返回 true（调用方据此提示），冷却里则什么都不做（不续期） */
	public static boolean onCrit(LivingEntity shooter, long time) {
		if (shooter == null) {
			return false;
		}
		Window window = WINDOWS.computeIfAbsent(shooter.getUUID(), id -> new Window());
		if (time < window.readyAt) {
			return false;
		}
		window.until = time + ticks(GunLevelMod.CONFIG.chipEmpoweredSeconds);
		window.readyAt = time + ticks(GunLevelMod.CONFIG.chipEmpoweredCooldownSeconds);
		return true;
	}

	public static void tick(MinecraftServer server) {
		if (WINDOWS.isEmpty()) {
			return;
		}
		long time = server.overworld().getGameTime();
		WINDOWS.entrySet().removeIf(entry -> time >= entry.getValue().until);
	}

	public static void clear() {
		WINDOWS.clear();
	}
}
