package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.LevelConfig;
import net.minecraft.server.MinecraftServer;
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
		return stack.hits == 1;
	}

	public static void tick(MinecraftServer server) {
		if (STACKS.isEmpty()) {
			return;
		}
		long time = server.overworld().getGameTime();
		long reset = resetTicks();
		STACKS.entrySet().removeIf(entry -> time - entry.getValue().lastHitAt >= reset);
	}

	public static void clear() {
		STACKS.clear();
	}
}
