package com.eternal.gunlevel.effect;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 屏息凝神的静止记账：每 tick 对比在线玩家的坐标，动过就把「最后一次移动」刷成现在，
 * 连续静止满配置秒数才算「站稳了」。移动的判据是**坐标变化**——走路、跳跃、下落、
 * 被推、坐载具、传送都算动；只转头（旋转）不算。计时用玩家自己所在维度的游戏刻
 * （拿主世界时间去比会在下界/末地算错，见迅雷之音那条踩坑记录）。只在服务端记账。
 */
public final class StillnessState {
	/** 坐标变化小于这个平方和算没动（0.01 格的抖动不算移动） */
	private static final double MOVE_EPSILON = 1.0e-4;

	private static final class Mark {
		double x;
		double y;
		double z;
		long lastMovedAt;
	}

	private static final ConcurrentHashMap<UUID, Mark> MARKS = new ConcurrentHashMap<>();

	private StillnessState() {
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long time = player.level().getGameTime();
			Mark mark = MARKS.computeIfAbsent(player.getUUID(), id -> {
				Mark fresh = new Mark();
				fresh.x = player.getX();
				fresh.y = player.getY();
				fresh.z = player.getZ();
				fresh.lastMovedAt = time;
				return fresh;
			});
			double dx = player.getX() - mark.x;
			double dy = player.getY() - mark.y;
			double dz = player.getZ() - mark.z;
			if (dx * dx + dy * dy + dz * dz > MOVE_EPSILON) {
				mark.x = player.getX();
				mark.y = player.getY();
				mark.z = player.getZ();
				mark.lastMovedAt = time;
			}
		}
		MARKS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	/** 已经连续静止满 seconds 秒了吗（移动后立即失效） */
	public static boolean isStill(LivingEntity shooter, double seconds) {
		if (shooter == null) {
			return false;
		}
		Mark mark = MARKS.get(shooter.getUUID());
		return mark != null
				&& shooter.level().getGameTime() - mark.lastMovedAt >= Math.round(seconds * 20);
	}

	public static void clear() {
		MARKS.clear();
	}
}
