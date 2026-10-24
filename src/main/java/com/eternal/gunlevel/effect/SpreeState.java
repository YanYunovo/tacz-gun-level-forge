package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 乘胜追击的击杀记账：按射手 UUID 记「最后一次击杀的世界刻」，窗口内的命中吃加成。
 * 击杀的判定与势如破竹/烈焰咆哮同一条链（直击把目标打死；只吃直击、不吃爆炸段溅死）。
 * 只在服务端有值。
 */
public final class SpreeState {
	private static final ConcurrentHashMap<UUID, Long> LAST_KILL_AT = new ConcurrentHashMap<>();

	private SpreeState() {
	}

	private static long windowTicks() {
		return Math.max(1, Math.round(GunLevelMod.CONFIG.chipPurpleSpreeSeconds * 20));
	}

	/** 击杀后窗口内命中吃加成（0 = 窗口外或没杀过） */
	public static double bonus(LivingEntity shooter, long time) {
		Long at = shooter == null ? null : LAST_KILL_AT.get(shooter.getUUID());
		return at != null && time - at < windowTicks()
				? GunLevelMod.CONFIG.chipPurpleSpreeDamage : 0.0;
	}

	public static void onKill(LivingEntity shooter, long time) {
		if (shooter != null) {
			LAST_KILL_AT.put(shooter.getUUID(), time);
		}
	}

	public static void tick(MinecraftServer server) {
		if (LAST_KILL_AT.isEmpty()) {
			return;
		}
		long time = server.overworld().getGameTime();
		long window = windowTicks();
		LAST_KILL_AT.entrySet().removeIf(entry -> time - entry.getValue() >= window);
	}

	public static void clear() {
		LAST_KILL_AT.clear();
	}
}
