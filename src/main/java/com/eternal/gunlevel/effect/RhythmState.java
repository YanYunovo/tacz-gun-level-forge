package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节奏射击的运行时层数：按射手 UUID 记账，只在服务端有值。
 *
 * <p>每发命中叠一层（{@code chipPurpleRhythmPerHit}），封顶 {@code chipPurpleRhythmCap}；
 * 清零有两条路：连续 {@code chipPurpleRhythmResetSeconds} 秒没有命中（{@link #tick} 兜底），
 * 或者有一发打在方块上落空（{@link #reset}）—— 名字里的「节奏」就是这个意思，
 * 空一枪就得从头攒。
 *
 * <p>和 {@link FervorState} 一样，{@link #bonus} 只读不改状态：伤害缩放每发都要读一次，
 * 读的时候顺手删条目会把别人的窗口一起掐掉。
 */
public final class RhythmState {
	private static final ConcurrentHashMap<UUID, Rhythm> RHYTHMS = new ConcurrentHashMap<>();

	private static final class Rhythm {
		int stacks;
		/** 最后一次命中的世界刻，脱战判定按它算 */
		long lastHitAt;
	}

	private RhythmState() {
	}

	private static long resetTicks() {
		return Math.max(1, Math.round(GunLevelMod.CONFIG.chipPurpleRhythmResetSeconds * 20));
	}

	/** 当前伤害加成比例（过期就当没有，但不删条目） */
	public static double bonus(LivingEntity shooter, long time) {
		Rhythm rhythm = shooter == null ? null : RHYTHMS.get(shooter.getUUID());
		if (rhythm == null || rhythm.stacks <= 0 || time - rhythm.lastHitAt >= resetTicks()) {
			return 0.0;
		}
		return Math.min(GunLevelMod.CONFIG.chipPurpleRhythmCap,
				GunLevelMod.CONFIG.chipPurpleRhythmPerHit * rhythm.stacks);
	}

	/** 打中一下：过窗先清零再叠一层（所以落空之后重新起手，不会拿旧层数续上） */
	public static void onHit(LivingEntity shooter, long time) {
		if (shooter == null) {
			return;
		}
		Rhythm rhythm = RHYTHMS.computeIfAbsent(shooter.getUUID(), id -> new Rhythm());
		if (time - rhythm.lastHitAt >= resetTicks()) {
			rhythm.stacks = 0;
		}
		rhythm.lastHitAt = time;
		rhythm.stacks++;
	}

	/** 这一发落空（打在方块上）：整条清零 */
	public static void reset(LivingEntity shooter) {
		if (shooter != null) {
			RHYTHMS.remove(shooter.getUUID());
		}
	}

	public static void tick(MinecraftServer server) {
		if (RHYTHMS.isEmpty()) {
			return;
		}
		long time = server.overworld().getGameTime();
		long reset = resetTicks();
		RHYTHMS.entrySet().removeIf(entry -> time - entry.getValue().lastHitAt >= reset);
	}

	public static void clear() {
		RHYTHMS.clear();
	}
}
