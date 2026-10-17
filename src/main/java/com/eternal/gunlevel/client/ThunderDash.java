package com.eternal.gunlevel.client;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 迅捷闪冲的执行端：服务端只批「能不能冲、冲几格」，位置由客户端自己挪。
 *
 * <p>必须在客户端做：1.20.1 玩家移动是客户端预测的，服务端直接改位置会被下一帧位置包抹平。
 * 逐格往里退着试碰撞，撞墙时就停在墙前那一格，不会把人塞进方块里。
 */
public final class ThunderDash {
	private static final double STEP = 0.25;

	private ThunderDash() {
	}

	public static void dash(Player player, double blocks) {
		if (player == null || blocks <= 0) {
			return;
		}
		Vec3 look = player.getLookAngle();
		double dx = look.x();
		double dz = look.z();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0E-4) {
			return;
		}
		dx /= length;
		dz /= length;
		Vec3 from = player.position();
		Level level = player.level();
		AABB box = player.getBoundingBox();
		for (double step = blocks; step >= STEP; step -= STEP) {
			Vec3 to = from.add(dx * step, 0.0, dz * step);
			if (level.getBlockCollisions(player,
					box.move(to.x - from.x, 0.0, to.z - from.z)).iterator().hasNext()) {
				continue;
			}
			player.moveTo(to.x, to.y, to.z, player.getYRot(), player.getXRot());
			// 位置改完要主动告诉服务端，否则它按老位置算，下一帧就把人拽回去
			player.hurtMarked = true;
			return;
		}
	}
}
