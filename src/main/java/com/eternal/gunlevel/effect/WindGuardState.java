package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.item.GunCoreData;
import com.eternal.gunlevel.net.GunLevelNetwork;
import com.eternal.gunlevel.util.ChipHints;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灵风之祝：风元素枪械的被动（二段跳 + 免疫摔落，见 {@link #passiveActive}）
 * 加上第一颗有主动技能的芯片「风的护佑」。
 *
 * <p>护佑的移速修正必须**两端各挂一次**：1.20.1 玩家移动由客户端预测、服务端接受客户端上报的位置，
 * 只挂服务端等于没挂；原版又没有玩家属性的 S2C 同步包，所以自己发一个 {@link GunLevelNetwork.WindGuardSync}。
 * 两端用同一个 UUID，退出世界时客户端那份跟着实体消失，不留残渣。
 */
public final class WindGuardState {
	/** 两端必须认同一个修正 id，摘的时候才知道摘的是哪个 */
	public static final UUID GUARD_MODIFIER_ID = UUID.nameUUIDFromBytes(
			"tacz_gun_level:wind_guard".getBytes(StandardCharsets.UTF_8));
	private static final String GUARD_MODIFIER_NAME = "tacz_gun_level_wind_guard";

	private static final class Guard {
		/** 护佑到什么时候（世界刻） */
		long guardUntil;
		/** 冷却到什么时候 */
		long readyAt;
		/** 护佑期间还没用掉的扩散强化 */
		boolean gather;
		/** 移速修正还挂着（到期那天要两端一起摘掉） */
		boolean speedOn;
		/** 「已就绪」那行小字只报一次 */
		boolean announced;
	}

	private static final Map<UUID, Guard> GUARDS = new ConcurrentHashMap<>();

	private WindGuardState() {
	}

	/**
	 * 被动条件：手持的主手枪既是风元素、又装了灵风之祝。
	 * 只读物品 NBT，所以客户端和服务端都能算（客户端用来决定能不能二段跳，服务端用来免摔落伤害）。
	 */
	public static boolean passiveActive(LivingEntity entity) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		ItemStack gun = player.getMainHandItem();
		return GunCoreData.get(gun) == Element.WIND
				&& GunChipData.get(gun).contains(Chip.WIND_BLESSING);
	}

	/**
	 * 紫芯片两颗的前置（2026-10-24 用户补的口径）：**装了风元素核芯**才算数。
	 * 风灵契约（免摔）与风灵祝福（二段跳）共用这一个判据，与灵风之祝同一套「风元素 + 芯片」。
	 */
	public static boolean purpleWindActive(LivingEntity entity, Chip chip) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		ItemStack gun = player.getMainHandItem();
		return GunCoreData.get(gun) == Element.WIND && GunChipData.holdsChip(gun, chip);
	}

	/**
	 * 玩家按键：一次按键只交给「槽位顺序里第一颗有主动技能的芯片」。
	 * 它这时候还在冷却，这一次按键就什么都不做，不会替补去放第二格那颗的技能。
	 */
	public static void requestSkill(ServerPlayer player) {
		ItemStack gun = player.getMainHandItem();
		long now = player.level().getGameTime();
		for (Chip chip : GunChipData.ordered(gun)) {
			if (!chip.hasActiveSkill()) {
				continue;
			}
			// 全局锁：别的主动技能还在冷却时这一键不放技能，只飘一行「其他主动技能冷却中」
			// （2026-10-25 用户口径：不许拿一排带主动技能的枪当摔炮轮流点）
			if (SkillLock.locked(player, now)) {
				ChipHints.show(player, chip, ChipHints.SKILL_LOCKED);
				return;
			}
			// 一次按键只交给「槽位顺序里第一颗有主动技能的芯片」，它在冷却就什么都不做，不替补放第二格那颗
			switch (chip) {
				case WIND_BLESSING -> guardWind(player);
				case THUNDER_VOICE -> ThunderVoiceState.dash(player);
				case FLAME_ROAR -> FlameRoarState.roar(player);
				case RIME, TIDE, VINE, ROCK_WARD -> ElementSkillState.requestSkill(player, chip);
				case TAC_RELOAD -> TacticalReloadState.quickReload(player);
				default -> {
				}
			}
			return;
		}
	}

	private static void guardWind(ServerPlayer player) {
		if (GunCoreData.get(player.getMainHandItem()) != Element.WIND) {
			return;
		}
		long now = player.level().getGameTime();
		Guard guard = GUARDS.computeIfAbsent(player.getUUID(), id -> new Guard());
		if (now < guard.readyAt) {
			return;
		}
		double seconds = GunLevelMod.CONFIG.chipWindGuardSeconds;
		guard.guardUntil = now + Math.max(1, Math.round(seconds * 20));
		guard.readyAt = now + Math.max(1,
				Math.round(GunLevelMod.CONFIG.chipWindGuardCooldownSeconds * 20));
		SkillLock.arm(player, Chip.WIND_BLESSING);
		guard.gather = true;
		guard.speedOn = true;
		guard.announced = false;
		applySpeed(player, true);
		// 放技能这一侧不飘小字：用户给这颗芯片的提示只有冷却完毕那一句「风的护佑已就绪」
		syncSpeed(player, true);
	}

	/** 扩散结算时问一句：这一发是不是护佑期间的第一次扩散（是就当场用掉） */
	public static boolean consumeGather(LivingEntity shooter) {
		if (shooter == null) {
			return false;
		}
		Guard guard = GUARDS.get(shooter.getUUID());
		if (guard == null || !guard.gather || shooter.level().getGameTime() >= guard.guardUntil) {
			return false;
		}
		guard.gather = false;
		return true;
	}

	/** 风的护佑还剩多少 tick（≤0 = 可用）：全局主动技能锁用它判断什么时候解锁 */
	public static long remaining(LivingEntity player, long now) {
		Guard guard = player == null ? null : GUARDS.get(player.getUUID());
		return guard == null ? 0L : Math.max(0L, guard.readyAt - now);
	}

	public static void tick(MinecraftServer server) {
		if (GUARDS.isEmpty()) {
			return;
		}
		server.getPlayerList().getPlayers().forEach(player -> {
			Guard guard = GUARDS.get(player.getUUID());
			if (guard == null) {
				return;
			}
			long now = player.level().getGameTime();
			if (guard.speedOn && now >= guard.guardUntil) {
				guard.speedOn = false;
				applySpeed(player, false);
				syncSpeed(player, false);
			}
			if (!guard.announced && now >= guard.readyAt) {
				guard.announced = true;
				ChipHints.show(player, Chip.WIND_BLESSING, ChipHints.SKILL_READY);
			}
		});
		// 掉线的人那一行记录没人回收（服务端那份修正跟着实体一起消失），直接删
		GUARDS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	public static void clear() {
		GUARDS.clear();
	}

	private static void applySpeed(LivingEntity entity, boolean on) {
		AttributeInstance instance = entity.getAttributes()
				.getInstance(Attributes.MOVEMENT_SPEED);
		if (instance == null) {
			return;
		}
		instance.removeModifier(GUARD_MODIFIER_ID);
		if (on) {
			instance.addTransientModifier(new AttributeModifier(GUARD_MODIFIER_ID,
					GUARD_MODIFIER_NAME, GunLevelMod.CONFIG.chipWindGuardSpeed,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	/** 客户端那一份：S2C 收到后给本地玩家挂/摘同一个修正 */
	public static void applySpeedClient(LivingEntity clientPlayer, boolean on) {
		if (clientPlayer != null) {
			applySpeed(clientPlayer, on);
		}
	}

	private static void syncSpeed(ServerPlayer player, boolean on) {
		GunLevelNetwork.sendGuard(player, on);
	}
}
