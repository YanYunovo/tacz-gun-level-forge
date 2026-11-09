package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunBurstData;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.net.GunLevelNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 爆裂射击的运行时账本（按射手 UUID + 枪物品唯一 ID，只在服务端有值）。
 *
 * <p>口径（2026-10-25 用户定）：
 * <ul>
 *   <li>触发概率 = 基础 10% + 射速加成档（每 5% 加 1%，最多 +10%）+ 触发层（每触发一次 +1%，
 *       最多 4%×永恒改装等级）。射速加成统计等级自带、射速改造、极光、迅雷、狩猎爆发、激化。</li>
 *   <li>每一段命中 +1 充能（一发拆成两段就是 +2）；过载期间与失效期间都不攒。</li>
 *   <li>充能满 300 → 清 0 并进入 5 秒过载：必定爆裂，另有 40% 追加一段。</li>
 *   <li>过载结束那一刻清空概率层，并失效 4 秒（不触发、不攒）。</li>
 * </ul>
 */
public final class BurstShotState {
	private record Key(UUID player, UUID gun) {
	}

	/** 每个射手、每把枪各自一套充能/过载/失效账本。 */
	private static final Map<Key, Ledger> LEDGERS = new ConcurrentHashMap<>();

	private BurstShotState() {
	}

	private static final class Ledger {
		int charge;
		/** 触发层（每层 +1% 概率），过载结束时清零 */
		int stacks;
		long overloadUntil;
		long disabledUntil;
		int syncedCharge = -1;
		long syncedOverload = -1;
		long syncedDisabled = -1;
	}

	/** 出膛定格的结果：这一发拆几段、是不是过载那一发 */
	public record Plan(boolean burst, int extraSegments, boolean overload) {
	}

	private static final Plan NONE = new Plan(false, 0, false);

	private static long ticks(double seconds) {
		return Math.max(1, Math.round(seconds * 20));
	}

	private static Ledger ledger(LivingEntity entity, ItemStack gun) {
		return LEDGERS.computeIfAbsent(new Key(entity.getUUID(), GunBurstData.id(gun)),
				id -> new Ledger());
	}

	/** 过载到点：清概率层并进入失效；返回是否发生了状态迁移 */
	private static boolean settle(Ledger ledger, long now) {
		if (ledger.overloadUntil != 0 && now >= ledger.overloadUntil) {
			ledger.overloadUntil = 0;
			ledger.stacks = 0;
			ledger.disabledUntil = now + ticks(GunLevelMod.CONFIG.chipBurstDisableSeconds);
			return true;
		}
		return false;
	}

	private static int maxStacks(int level) {
		double per = GunLevelMod.CONFIG.chipBurstStackPerTrigger;
		if (per <= 0) {
			return 0;
		}
		return (int) Math.floor(GunLevelMod.CONFIG.chipBurstStackPerLevel * level / per + 1e-9);
	}

	/**
	 * 当前射速加成的总和（小数，0.16 = 16%）。与各芯片自己的 intervalScale 同源，
	 * 这里只做加法统计，不改实际射击间隔。
	 */
	public static double totalFireRateBonus(LivingEntity shooter, ItemStack gun, int level, long now) {
		double sum = GunLevelMod.CONFIG.fireRateBonusPerLevel * Math.max(0, level - 1);
		EnumSet<Chip> chips = GunChipData.get(gun);
		if (chips.contains(Chip.FIRE_RATE_MOD)) {
			sum += GunLevelMod.CONFIG.chipBlueFireRate;
		}
		if (AuroraState.isAuroraMag(shooter)) {
			sum += GunLevelMod.CONFIG.chipAuroraRatePerLevel * level;
		}
		if (chips.contains(Chip.THUNDER_VOICE)) {
			sum += GunLevelMod.CONFIG.chipThunderRate;
		}
		if (HuntState.bursting(shooter, now)) {
			sum += GunLevelMod.CONFIG.chipHuntBurstFireRate;
		}
		sum += ExtremeFireState.totalFireRateBonus(shooter);
		MobEffectInstance quickening = shooter.getEffect(GunLevelMod.QUICKENING);
		if (quickening != null) {
			sum += GunLevelMod.CONFIG.quickenFireRateBonus * (quickening.getAmplifier() + 1);
		}
		return sum;
	}

	/** 这一发此刻的触发概率（0~1） */
	public static double chance(LivingEntity shooter, ItemStack gun, int level, long now) {
		Ledger ledger = ledger(shooter, gun);
		double rate = totalFireRateBonus(shooter, gun, level, now);
		double fromRate = Math.min(GunLevelMod.CONFIG.chipBurstFireRateCap,
				Math.floor(rate / GunLevelMod.CONFIG.chipBurstFireRateStep + 1e-9)
						* GunLevelMod.CONFIG.chipBurstFireRatePerStep);
		double fromStacks = Math.min(maxStacks(level),
				ledger.stacks) * GunLevelMod.CONFIG.chipBurstStackPerTrigger;
		return GunLevelMod.CONFIG.chipBurstBaseChance + fromRate + fromStacks;
	}

	/**
	 * 出膛定格：roll / overloadRoll 由调用方掷好（两端的随机源类型不同）。
	 * 命中期间不再重掷，保证一发子弹的段数与过载判定贯穿始终。
	 */
	public static Plan plan(LivingEntity shooter, ItemStack gun, int level, long now,
			float roll, float overloadRoll) {
		if (shooter == null || level <= 0) {
			return NONE;
		}
		Ledger ledger = ledger(shooter, gun);
		settle(ledger, now);
		if (now < ledger.disabledUntil) {
			sync(shooter, ledger);
			return NONE;
		}
		if (now < ledger.overloadUntil) {
			boolean extra = overloadRoll < GunLevelMod.CONFIG.chipBurstOverloadExtraChance;
			return new Plan(true, extra ? 2 : 1, true);
		}
		if (roll < chance(shooter, gun, level, now)) {
			ledger.stacks = Math.min(maxStacks(level), ledger.stacks + 1);
			sync(shooter, ledger);
			return new Plan(true, 1, false);
		}
		return NONE;
	}

	/** 每一段命中调用一次；过载/失效期间不攒。账记在开火那把枪上。 */
	public static void onHitSegment(LivingEntity shooter, ItemStack gun, long now) {
		if (shooter == null || gun == null || gun.isEmpty()) {
			return;
		}
		Ledger ledger = ledger(shooter, gun);
		boolean changed = settle(ledger, now);
		if (now < ledger.disabledUntil || now < ledger.overloadUntil) {
			if (changed) {
				sync(shooter, ledger);
			}
			return;
		}
		ledger.charge = Math.min(GunLevelMod.CONFIG.chipBurstChargeMax,
				ledger.charge + Math.max(0, GunLevelMod.CONFIG.chipBurstChargePerHit));
		if (ledger.charge >= GunLevelMod.CONFIG.chipBurstChargeMax) {
			ledger.charge = 0;
			ledger.overloadUntil = now + ticks(GunLevelMod.CONFIG.chipBurstOverloadSeconds);
		}
		sync(shooter, ledger);
	}

	/** 每 tick 把主手那把枪的状态推给客户端，并清掉下线玩家的账 */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack gun = player.getMainHandItem();
			long now = player.serverLevel().getGameTime();
			if (!GunChipData.get(gun).contains(Chip.BURST_SHOT)) {
				GunLevelNetwork.sendBurstState(player, 0, 0, 0);
				continue;
			}
			Key key = new Key(player.getUUID(), GunBurstData.id(gun));
			Ledger ledger = LEDGERS.get(key);
			if (ledger == null) {
				GunLevelNetwork.sendBurstState(player, 0, 0, 0);
				continue;
			}
			settle(ledger, now);
			GunLevelNetwork.sendBurstState(player, ledger.charge, ledger.overloadUntil,
					ledger.disabledUntil);
		}
		LEDGERS.keySet().removeIf(key -> server.getPlayerList().getPlayer(key.player()) == null);
	}

	/** 状态有变化才发包；客户端拿绝对游戏刻自己算倒计时 */
	private static void sync(LivingEntity entity, Ledger ledger) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}
		if (ledger.syncedCharge == ledger.charge
				&& ledger.syncedOverload == ledger.overloadUntil
				&& ledger.syncedDisabled == ledger.disabledUntil) {
			return;
		}
		ledger.syncedCharge = ledger.charge;
		ledger.syncedOverload = ledger.overloadUntil;
		ledger.syncedDisabled = ledger.disabledUntil;
		GunLevelNetwork.sendBurstState(player, ledger.charge, ledger.overloadUntil,
				ledger.disabledUntil);
	}
}