package com.eternal.gunlevel.effect;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主动技能的全局锁：放过一个「按时间冷却」的主动技能之后，**直到那颗芯片的冷却真正走完**，
 * 这个玩家不能再放别的枪上的主动技能。
 *
 * <p>用户口径（2026-10-25）：防止「装一排带主动技能的枪轮流点」当摔炮用。
 * 锁记的是**来源芯片**而不是一个固定时间戳 —— 像迅雷之音那种「每命中 5 次减 1 秒冷却」的技能，
 * 冷却实际缩短了，锁也要跟着提前解开。
 *
 * <p>烈焰咆哮那种靠击杀/爆头刷新、没有时间冷却的技能**不设锁**（调用方直接不 arm）；
 * 但锁着的时候它也放不出来 —— 它同样是「别的主动技能」。
 *
 * <p>只管服务端：按键请求到服务端才裁决，客户端说了不算。
 */
public final class SkillLock {
	/** 锁的来源芯片：锁一直有效到这颗芯片的主动技能冷却完毕 */
	private static final Map<UUID, Chip> SOURCE = new ConcurrentHashMap<>();

	private SkillLock() {
	}

	/** 技能真的放出去之后记住是它锁的（刷新制技能不要调这个） */
	public static void arm(LivingEntity player, Chip chip) {
		if (player != null && chip != null) {
			SOURCE.put(player.getUUID(), chip);
		}
	}

	/** 现在是不是被别的主动技能的冷却锁着；冷却走完就自动解锁 */
	public static boolean locked(LivingEntity player, long now) {
		if (player == null) {
			return false;
		}
		Chip chip = SOURCE.get(player.getUUID());
		if (chip == null) {
			return false;
		}
		if (coolingDown(player, chip, now)) {
			return true;
		}
		SOURCE.remove(player.getUUID());
		return false;
	}

	private static boolean coolingDown(LivingEntity player, Chip chip, long now) {
		return switch (chip) {
			case WIND_BLESSING -> WindGuardState.remaining(player, now) > 0;
			case THUNDER_VOICE -> ThunderVoiceState.remaining(player, now) > 0;
			case RIME, TIDE, VINE, ROCK_WARD -> ElementSkillState.remaining(player, chip, now) > 0;
			case TAC_RELOAD -> TacticalReloadState.remaining(player, now) > 0;
			// 刷新制（烈焰咆哮）不设锁
			default -> false;
		};
	}

	public static void tick(MinecraftServer server) {
		if (SOURCE.isEmpty()) {
			return;
		}
		SOURCE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	public static void clear() {
		SOURCE.clear();
	}
}
