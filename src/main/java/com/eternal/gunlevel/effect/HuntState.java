package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.net.GunLevelNetwork;
import com.eternal.gunlevel.util.ChipHints;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 狩猎时刻的运行时状态：一个射手同一时间只有一个标记目标，标记与它的账都记在射手身上。
 *
 * <p>一轮的节奏是：命中任意敌人 → 那一个成为标记目标（之后打别的敌人**不会**把标记转移过去）
 * → 每次打中被标记的目标，把该次实际伤害的 10% 记进账、命中次数 +1
 * → 次数到 15 给三档加成，到 30 开 3 秒爆发窗口
 * → 结束时（爆发窗口走完 / 目标血量跌破记录值 / 切枪）把记录值当固定伤害结算掉，整轮清零。
 *
 * <p>三种收尾的口径不一样，是用户定的：正常收尾与收割都按 100% 记录值结算；
 * **切枪**提前收尾只按 50% 结算；**脱战**（连续 5 秒没命中）直接清账、一分不结算。
 *
 * <p>收割判定既在命中那一刻查，也在 {@link #tick} 里每 tick 查 —— 后半句是用户特别要求的
 * （「3 秒内的特殊状态也要记得检测记录值是不是大于被标记的目标的血量」），
 * 因为爆发窗口里玩家可能只打两三发就把窗口熬完了，中间目标还可能被队友打掉血。
 */
public final class HuntState {
	/** 移速那一档要两端各挂一次（原版不同步玩家属性），走 GunLevelNetwork 的 boolean 包 */
	public static final UUID SPEED_MODIFIER_ID = UUID.nameUUIDFromBytes(
			"tacz_gun_level:hunt_speed".getBytes(StandardCharsets.UTF_8));
	private static final String SPEED_MODIFIER_NAME = "tacz_gun_level_hunt_speed";
	/** 标记特效：每多少个 tick 在目标头顶撒一次粒子（每 tick 撒会白刷网络包） */
	private static final int MARK_PARTICLE_INTERVAL = 10;

	private static final class Mark {
		/** 被标记的目标 */
		UUID target;
		/** 累计的记录值（命中伤害的 10% 之和） */
		double record;
		/** 命中被标记目标多少次（决定 15/30 两档） */
		int hits;
		/** 最后一次命中的世界刻，脱战判定按它算 */
		long lastHitAt;
		/** 爆发窗口到什么时候（0 = 没开过） */
		long burstUntil;
		/** 移速修正挂着没有（只在真的翻转时才动属性） */
		boolean speedOn;
	}

	private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();

	private HuntState() {
	}

	/** 被动条件：枪上装着这颗芯片（不看元素） */
	public static boolean carries(ItemStack gun) {
		return !gun.isEmpty() && GunChipData.get(gun).contains(Chip.HUNTING_MOMENT);
	}

	/**
	 * 命中结算（服务端、子弹真的打中了生物）。
	 *
	 * @param damage 这一发实际造成的伤害（已经过等级/暴击/芯片那整条倍率链）
	 */
	public static void onHit(ServerPlayer shooter, LivingEntity target, double damage) {
		if (target == null || target.isRemoved()) {
			return;
		}
		long now = shooter.level().getGameTime();
		Mark mark = MARKS.computeIfAbsent(shooter.getUUID(), id -> new Mark());
		if (mark.target == null) {
			// 第一枪只负责挂上标记：它自己不算「命中被标记的敌人」，不记账
			mark.target = target.getUUID();
			mark.record = 0;
			mark.hits = 0;
			mark.burstUntil = 0;
			mark.lastHitAt = now;
			return;
		}
		if (!mark.target.equals(target.getUUID())) {
			return;   // 标记不转移：打别人就是普通命中
		}
		mark.lastHitAt = now;
		mark.record += damage * GunLevelMod.CONFIG.chipHuntRecordRatio;
		mark.hits++;
		if (mark.burstUntil == 0 && mark.hits >= GunLevelMod.CONFIG.chipHuntBurstStacks) {
			mark.burstUntil = now + Math.max(1,
					Math.round(GunLevelMod.CONFIG.chipHuntBurstSeconds * 20));
			ChipHints.show(shooter, Chip.HUNTING_MOMENT);
		}
		// 收割当场就查一次，不等下一个 tick
		if (target.getHealth() < mark.record) {
			settle(shooter, target, mark, 1.0);
		}
	}

	/** 三档加成的伤害那一档（跟着标记走，次数没到 15 就是 0） */
	public static double damageBonus(LivingEntity shooter, long time) {
		Mark mark = aliveMark(shooter, time);
		return mark != null && mark.hits >= GunLevelMod.CONFIG.chipHuntBonusStacks
				? GunLevelMod.CONFIG.chipHuntBonusDamage : 0.0;
	}

	/** 三档加成的暴击率那一档：加在武器面板那一次掷骰上 */
	public static double critBonus(LivingEntity shooter, long time) {
		Mark mark = aliveMark(shooter, time);
		return mark != null && mark.hits >= GunLevelMod.CONFIG.chipHuntBonusStacks
				? GunLevelMod.CONFIG.chipHuntBonusCrit : 0.0;
	}

	/** 爆发窗口的射速档：乘到射击间隔上（提升 16% ⇒ 间隔除以 1.16） */
	public static double intervalScale(LivingEntity shooter, long time) {
		Mark mark = aliveMark(shooter, time);
		return mark != null && time < mark.burstUntil
				? 1.0 / (1.0 + GunLevelMod.CONFIG.chipHuntBurstFireRate) : 1.0;
	}

	/** 这一枪是不是在爆发窗口里打的（窗口内不消耗弹药） */
	public static boolean bursting(LivingEntity shooter, long time) {
		Mark mark = aliveMark(shooter, time);
		return mark != null && time < mark.burstUntil;
	}

	public static void tick(MinecraftServer server) {
		if (MARKS.isEmpty()) {
			return;
		}
		long timeout = Math.max(1, Math.round(GunLevelMod.CONFIG.chipHuntTimeoutSeconds * 20));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Mark mark = MARKS.get(player.getUUID());
			if (mark == null) {
				continue;
			}
			ServerLevel world = player.serverLevel();
			long now = world.getGameTime();
			LivingEntity target = mark.target == null ? null
					: world.getEntity(mark.target) instanceof LivingEntity living ? living : null;
			// 切枪：提前收尾，只按 50% 结算（用户口径）
			if (!carries(player.getMainHandItem())) {
				if (target != null && !target.isRemoved()) {
					settle(player, target, mark, GunLevelMod.CONFIG.chipHuntSwapRatio);
				}
				drop(player, mark);
				continue;
			}
			// 脱战：连续 5 秒没命中，或者目标已经没了/死了 —— 直接清账，一分不结算
			if (now - mark.lastHitAt > timeout || target == null || target.isRemoved()
					|| !target.isAlive()) {
				drop(player, mark);
				continue;
			}
			// 爆发窗口走完：正常收尾，按 100% 结算记录伤害
			if (mark.burstUntil != 0 && now >= mark.burstUntil) {
				settle(player, target, mark, 1.0);
				drop(player, mark);
				continue;
			}
			// 窗口内/标记期间都要盯血量：跌到记录值以下立刻收割
			if (mark.record > 0 && target.getHealth() < mark.record) {
				settle(player, target, mark, 1.0);
				drop(player, mark);
				continue;
			}
			markSpeed(player, mark, now);
			if (now % MARK_PARTICLE_INTERVAL == 0) {
				Vec3 pos = target.position().add(0, target.getBbHeight() * 0.85, 0);
				world.sendParticles(ParticleTypes.END_ROD, pos.x(), pos.y(), pos.z(),
						3, target.getBbWidth() * 0.4, 0.15, target.getBbWidth() * 0.4, 0.0);
			}
		}
		MARKS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	public static void clear() {
		MARKS.clear();
	}

	/** 结算记录伤害：走固定伤害那条路（不吃护甲、不吃暴击、不占受害者受伤间隔、归因射手） */
	private static void settle(ServerPlayer shooter, LivingEntity target, Mark mark, double ratio) {
		double amount = mark.record * ratio;
		if (amount > 0) {
			CombatState.applyFixedDamage(shooter.level(), target, shooter, (float) amount);
		}
		mark.record = 0;
	}

	/** 清掉整轮账（不结算），顺手把移速修正摘了 */
	private static void drop(ServerPlayer player, Mark mark) {
		mark.target = null;
		mark.record = 0;
		mark.hits = 0;
		mark.burstUntil = 0;
		mark.lastHitAt = 0;
		MARKS.remove(player.getUUID());
		if (mark.speedOn) {
			mark.speedOn = false;
			applySpeed(player, false);
		}
	}

	/** 只返回「还有效」的账：脱战超时的当场丢掉，避免伤害/射速读到已经作废的加成 */
	private static Mark aliveMark(LivingEntity shooter, long time) {
		if (shooter == null) {
			return null;
		}
		Mark mark = MARKS.get(shooter.getUUID());
		if (mark == null || mark.target == null) {
			return null;
		}
		long timeout = Math.max(1, Math.round(GunLevelMod.CONFIG.chipHuntTimeoutSeconds * 20));
		if (time - mark.lastHitAt > timeout) {
			MARKS.remove(shooter.getUUID());
			return null;
		}
		return mark;
	}

	/** 移速那一档只在翻转时动属性（每 tick 加一次会让客户端和服务端各自漂移） */
	private static void markSpeed(ServerPlayer player, Mark mark, long now) {
		boolean should = mark.hits >= GunLevelMod.CONFIG.chipHuntBonusStacks;
		if (should == mark.speedOn) {
			return;
		}
		mark.speedOn = should;
		applySpeed(player, should);
		GunLevelNetwork.sendHuntSpeed(player, should);
	}

	private static void applySpeed(LivingEntity entity, boolean on) {
		AttributeInstance instance = entity.getAttributes().getInstance(Attributes.MOVEMENT_SPEED);
		if (instance == null) {
			return;
		}
		instance.removeModifier(SPEED_MODIFIER_ID);
		if (on) {
			instance.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID,
					SPEED_MODIFIER_NAME, GunLevelMod.CONFIG.chipHuntBonusSpeed,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	/** 客户端那一份：S2C 收到后给本地玩家挂/摘同一个修正 */
	public static void applySpeedClient(LivingEntity clientPlayer, boolean on) {
		if (clientPlayer != null) {
			applySpeed(clientPlayer, on);
		}
	}
}
