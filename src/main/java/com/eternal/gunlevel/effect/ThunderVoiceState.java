package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.item.GunCoreData;
import com.eternal.gunlevel.net.GunLevelNetwork;
import com.eternal.gunlevel.util.ChipHints;
import com.eternal.gunlevel.util.TaczCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 迅雷之音：被动是射速换弹匣容量（只看枪上有没有这颗芯片，不看元素），
 * 主动技能「迅捷闪冲」额外要求装雷元素核芯。
 *
 * <p>冲刺本身必须交给客户端执行：1.20.1 玩家移动由客户端预测、服务端接受客户端上报的位置，
 * 服务端直接挪位置会被下一帧位置包抹掉。所以服务端只负责「批不批、补多少弹、冷却到什么时候」，
 * 批下来之后发一个 {@link GunLevelNetwork.ThunderDash} 让客户端自己走那三格。
 */
public final class ThunderVoiceState {

	private static final class Skill {
		/** 冷却到什么时候（世界刻） */
		long readyAt;
		/** 攒了多少次命中，够 {@code chipThunderHitsPerSecond} 次就抵掉 1 秒冷却 */
		int hits;
		/** 「已就绪」那行小字只报一次 */
		boolean announced;
	}

	private static final Map<UUID, Skill> SKILLS = new ConcurrentHashMap<>();

	private ThunderVoiceState() {
	}

	/** 被动条件：枪上装着这颗芯片。射速与弹匣容量都按它算，不要求元素 */
	public static boolean equipped(LivingEntity shooter) {
		return shooter != null && carries(shooter.getMainHandItem());
	}

	public static boolean carries(ItemStack gun) {
		return !gun.isEmpty() && GunChipData.get(gun).contains(Chip.THUNDER_VOICE);
	}

	/** 射速档：乘到射击间隔上（射速提升 16% ⇒ 间隔除以 1.16） */
	public static double intervalScale(LivingEntity shooter) {
		return equipped(shooter) ? 1.0 / (1.0 + GunLevelMod.CONFIG.chipThunderRate) : 1.0;
	}

	/** 容量档：乘到「这一匣装满是多少发」上，和极光匣的翻倍同一条链上叠乘 */
	public static double capacityScale(ItemStack gun) {
		return carries(gun) ? 1.0 - GunLevelMod.CONFIG.chipThunderMagPenalty : 1.0;
	}

	/** 主动技能条件：雷元素核芯 + 这颗芯片 */
	private static boolean skillReady(ItemStack gun) {
		return GunCoreData.get(gun) == Element.THUNDER && carries(gun);
	}

	/** 玩家按键（由 {@link WindGuardState#requestSkill} 分发到这里）：在冷却就什么都不做 */
	public static void dash(ServerPlayer player) {
		ItemStack gun = player.getMainHandItem();
		if (!skillReady(gun)) {
			return;
		}
		long now = player.level().getGameTime();
		Skill skill = SKILLS.computeIfAbsent(player.getUUID(), id -> new Skill());
		if (now < skill.readyAt) {
			return;
		}
		skill.readyAt = now + Math.max(1,
				Math.round(GunLevelMod.CONFIG.chipThunderCooldownSeconds * 20));
		SkillLock.arm(player, Chip.THUNDER_VOICE);
		skill.hits = 0;
		skill.announced = false;
		TaczCompat.refillMagazine(gun, GunLevelMod.CONFIG.chipThunderDashAmmo);
		GunLevelNetwork.sendThunderDash(player, GunLevelMod.CONFIG.chipThunderDashBlocks);
	}

	/** 命中攒次数：每满配置里的次数就把冷却提前 1 秒，只在还在冷却时计数 */
	public static void onHit(LivingEntity shooter) {
		if (shooter == null || !equipped(shooter)) {
			return;
		}
		Skill skill = SKILLS.get(shooter.getUUID());
		if (skill == null || shooter.level().getGameTime() >= skill.readyAt) {
			return;
		}
		skill.hits++;
		int per = Math.max(1, GunLevelMod.CONFIG.chipThunderHitsPerSecond);
		if (skill.hits >= per) {
			skill.hits -= per;
			skill.readyAt = Math.max(shooter.level().getGameTime(), skill.readyAt - 20);
		}
	}

	/** 迅捷闪冲还剩多少 tick（≤0 = 可用）：全局主动技能锁用它判断什么时候解锁 */
	public static long remaining(LivingEntity player, long now) {
		Skill skill = player == null ? null : SKILLS.get(player.getUUID());
		return skill == null ? 0L : Math.max(0L, skill.readyAt - now);
	}

	public static void tick(MinecraftServer server) {
		if (SKILLS.isEmpty()) {
			return;
		}
		// 计时用玩家自己所在维度的游戏刻：readyAt 就是按它写的，拿主世界的刻去比会在别的维度算错
		server.getPlayerList().getPlayers().forEach(player -> {
			Skill skill = SKILLS.get(player.getUUID());
			if (skill == null || skill.announced || player.level().getGameTime() < skill.readyAt) {
				return;
			}
			skill.announced = true;
			ChipHints.show(player, Chip.THUNDER_VOICE, ChipHints.SKILL_READY);
		});
		SKILLS.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
	}

	public static void clear() {
		SKILLS.clear();
	}
}
