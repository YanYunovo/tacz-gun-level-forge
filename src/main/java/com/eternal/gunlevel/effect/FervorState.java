package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.LevelConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 愈战愈勇的运行时层数：按射手 UUID 记账，只在服务端有值。
 *
 * <p>命中才算进入战斗：第一次命中给基础档（不吃等级），之后每次命中再叠一层（乘等级），
 * 封顶同样乘等级。连续 {@code chipFervorResetSeconds} 秒没有命中就整条清零，
 * 所以它是「一场长战斗里滚起来」的收益，换弹、赶路都会掉回起点。
 *
 * <p>{@link #bonus} 只读不改状态（过期就当没有），清零只发生在 {@link #onHit} 与 {@link #tick}：
 * 伤害缩放每发子弹都要读一次，读的时候顺手删条目会把别人的窗口一起掐掉。
 */
public final class FervorState {
	private static final ConcurrentHashMap<UUID, Stack> STACKS = new ConcurrentHashMap<>();

	private static final class Stack {
		/** 已经命中几次：0 = 不在战斗 */
		int hits;
		/** 最后一次命中的世界刻，脱战判定按它算 */
		long lastHitAt;
	}

	private FervorState() {
	}

	private static long resetTicks() {
		return Math.max(1, Math.round(GunLevelMod.CONFIG.chipFervorResetSeconds * 20));
	}

	/** 当前伤害加成比例（0 = 不在战斗）；第一层只给基础档，所以层数要减一再乘 */
	public static double bonus(LivingEntity shooter, long time, int level) {
		Stack stack = shooter == null ? null : STACKS.get(shooter.getUUID());
		if (stack == null || stack.hits <= 0 || time - stack.lastHitAt >= resetTicks()) {
			return 0.0;
		}
		LevelConfig c = GunLevelMod.CONFIG;
		return Math.min(c.chipFervorCapPerLevel * level,
				c.chipFervorBase + c.chipFervorPerHit * level * (stack.hits - 1));
	}

	/** 打中一下：过窗先清零再叠一层；返回 true 表示这一刻刚进入战斗，调用方据此提示 */
	public static boolean onHit(LivingEntity shooter, long time) {
		if (shooter == null) {
			return false;
		}
		Stack stack = STACKS.computeIfAbsent(shooter.getUUID(), id -> new Stack());
		if (time - stack.lastHitAt >= resetTicks()) {
			stack.hits = 0;
		}
		stack.lastHitAt = time;
		stack.hits++;
		boolean entered = stack.hits == 1;
		if (entered && shooter.level() instanceof ServerLevel world) {
			net.minecraft.world.phys.Vec3 pos = shooter.position().add(0, shooter.getBbHeight() * 0.5, 0);
			world.sendParticles(ParticleTypes.FLAME, pos.x(), pos.y(), pos.z(),
					12, 0.35, 0.45, 0.35, 0.0);
		}
		return entered;
	}

	public static void tick(MinecraftServer server) {
		if (STACKS.isEmpty()) {
			return;
		}
		long time = server.overworld().getGameTime();
		long reset = resetTicks();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Stack stack = STACKS.get(player.getUUID());
			if (stack == null || stack.hits <= 0) {
				continue;
			}
			ServerLevel world = player.serverLevel();
			long now = world.getGameTime();
			if (now - stack.lastHitAt < reset && now % 10 == 0) {
				spawnAura(world, player, stack.hits, now);
			}
		}
		STACKS.entrySet().removeIf(entry -> time - entry.getValue().lastHitAt >= reset);
	}

	private static void spawnAura(ServerLevel world, ServerPlayer player, int hits, long now) {
		double strength = Math.min(1.0, hits / 60.0);
		int count = 2 + (int) Math.round(strength * 4.0);
		double radius = 0.55 + strength * 0.15;
		double base = now * 0.22 + player.getId() * 0.73;
		for (int i = 0; i < count; i++) {
			double angle = base + Math.PI * 2.0 * i / count;
			double x = player.getX() + Math.cos(angle) * radius;
			double y = player.getY() + player.getBbHeight() * 0.35
					+ Math.sin(now * 0.18 + i) * 0.18;
			double z = player.getZ() + Math.sin(angle) * radius;
			world.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0, 0, 0);
		}
	}

	public static void clear() {
		STACKS.clear();
	}
}
