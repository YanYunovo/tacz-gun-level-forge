package com.eternal.gunlevel.item;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.LevelConfig;
import com.eternal.gunlevel.effect.Chip;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * 战术芯片：所有芯片共用一张贴图，靠物品名与说明区分。
 * 说明里的数值全部从配置算出来，改配置不用改文案。
 */
public class TacticalChipItem extends Item {
	private final Chip chip;

	public TacticalChipItem(Chip chip) {
		super(new Item.Properties().stacksTo(16)
				// 档次是用户定的：蓝=常见、紫=少见、金=稀有（tooltip 名字那行的颜色跟着走）
				.rarity(switch (chip.tier()) {
					case GOLD -> Rarity.RARE;
					case PURPLE -> Rarity.UNCOMMON;
					case BLUE -> Rarity.COMMON;
				}));
		this.chip = chip;
	}

	public Chip chip() {
		return chip;
	}

	@Override
	public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag flag) {
		LevelConfig c = GunLevelMod.CONFIG;
		// 一行只讲一件事，且只用用户给的原话：1.20.1 的 tooltip 不换行，长行会被屏幕右边裁掉
		if (chip == Chip.CHAIN_CRIT) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.chain_crit.1",
					percent(c.chipCritChancePerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.chain_crit.2",
					number(c.chipEmpoweredSeconds), percent(c.chipEmpoweredChancePerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.chain_crit.3",
					percent(c.chipEmpoweredDamagePerLevel), number(c.chipEmpoweredCooldownSeconds)));
		} else if (chip == Chip.ARMOR_BREAK) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.armor_break.1",
					percent(c.chipArmorPierceChancePerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.armor_break.2",
					percent(c.chipArmorPierceBonus)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.armor_break.3",
					number(c.chipArmorPierceMultiplier)));
		} else if (chip == Chip.HIGH_MAG) {
			// 余量阈值按玩家口径显示（配置里的 20 是扣掉这一发后的判定值，开火瞬间实际是 21 发）
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.high_mag.1",
					number(c.chipHighMagAmmo)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.high_mag.2"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.high_mag.3",
					decimal(c.chipHighMagDamagePerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.high_mag.4",
					percent(c.chipHighMagBonusPerLevel)));
		} else if (chip == Chip.AURORA) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.aurora.1",
					number(c.chipAuroraHits)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.aurora.2"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.aurora.3",
					number(c.chipAuroraMagMultiplier)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.aurora.4",
					percent(c.chipAuroraRatePerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.aurora.5"));
		} else if (chip == Chip.BARRAGE) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.barrage.1"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.barrage.2",
					percent(c.chipBarrageChancePerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.barrage.3",
					percent(c.chipBarrageDamageRatio)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.barrage.4"));
		} else if (chip == Chip.MOMENTUM) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.momentum.1",
					number(c.chipMomentumRange)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.momentum.2",
					percent(c.chipMomentumCritChancePerLevel)));
			// 衰减档数就是窗口秒数，写死 1/6 会在改配置后说谎
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.momentum.3",
					percent(c.chipMomentumDamagePerLevel), number(c.chipMomentumSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.momentum.4",
					number(c.chipMomentumSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.momentum.5"));
		} else if (chip == Chip.CRYSTAL_SUMMON) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.crystal_summon.1"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.crystal_summon.2",
					number(c.crystalSummonTauntRadius)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.crystal_summon.3",
					number(c.crystalSummonBuffRadius)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.crystal_summon.4"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.crystal_summon.5",
					String.valueOf(c.crystalSummonHits)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.crystal_summon.6",
					number(c.crystalSummonLeashRadius)));
		} else if (chip == Chip.PURIFY_DOMAIN) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.purify_domain.1",
					number(c.chipPurifyExtraSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.purify_domain.2",
					number(c.chipPurifyRecordCap)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.purify_domain.3",
					number(c.chipPurifyNovaAt), number(c.chipPurifyNovaRadius)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.purify_domain.4",
					number(c.chipPurifyNovaDamage)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.purify_domain.5",
					number(c.chipPurifyBonusAt)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.purify_domain.6",
					decimal(c.chipPurifyDamagePerRecord)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.purify_domain.7",
					number(c.chipPurifyCleanseAt)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.purify_domain.8"));
		} else if (chip == Chip.THUNDER_VOICE) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.thunder_voice.1",
					percent(c.chipThunderRate)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.thunder_voice.2",
					percent(c.chipThunderMagPenalty)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.thunder_voice.3"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.thunder_voice.4",
					number(c.chipThunderDashBlocks)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.thunder_voice.5",
					number(c.chipThunderDashAmmo)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.thunder_voice.6",
					number(c.chipThunderCooldownSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.thunder_voice.7",
					String.valueOf(c.chipThunderHitsPerSecond)));
			// 主动技能芯片共用的结尾：一次按键只有一颗芯片的技能会放出来
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.thunder_voice.8"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.thunder_voice.9"));
		} else if (chip == Chip.WIND_BLESSING) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.wind_blessing.1"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.wind_blessing.2"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.wind_blessing.3"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.wind_blessing.4",
					percent(c.chipWindGuardSpeed), number(c.chipWindGuardSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.wind_blessing.5"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.wind_blessing.6"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.wind_blessing.7",
					number(c.chipWindGuardCooldownSeconds)));
			// 主动技能芯片共用的结尾：一次按键只有一颗芯片的技能会放出来
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.wind_blessing.8"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.wind_blessing.9"));
		} else if (chip == Chip.FERVOR) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.fervor.1",
					percent(c.chipFervorBase)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.fervor.2",
					percent(c.chipFervorPerHit)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.fervor.3",
					percent(c.chipFervorCapPerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.fervor.4",
					number(c.chipFervorResetSeconds)));
		} else if (chip == Chip.FLAME_ROAR) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.1",
					percent(c.chipRoarSpeedPenalty)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.2",
					number(c.chipRoarLingeringSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.3",
					percent(c.chipRoarHeadDamagePerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.4"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.5",
					percent(c.chipRoarFakeHeadChance)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.6",
					percent(c.chipRoarFakeHeadPerCrit)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.7",
					percent(c.chipRoarFakeHeadBonusCap)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.8"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.9"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.10"));
			// 「原本的400%」是倍率乘 100，percent 正好干这件事
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.11",
					number(c.chipRoarEmpowerSeconds), percent(c.chipRoarDamageMultiplier)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.12",
					String.valueOf(c.chipRoarKillsToRefresh)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.13",
					String.valueOf(c.chipRoarHeadshotsToRefresh)));
			// 主动技能芯片共用的结尾：一次按键只有一颗芯片的技能会放出来
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.14"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.flame_roar.15"));
		} else if (chip == Chip.HUNTING_MOMENT) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.1"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.2"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.3", percent(c.chipHuntRecordRatio)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.4"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.5"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.6", String.valueOf(c.chipHuntBonusStacks)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.7", percent(c.chipHuntBonusDamage)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.8", percent(c.chipHuntBonusCrit)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.9", percent(c.chipHuntBonusSpeed)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.10", String.valueOf(c.chipHuntBurstStacks)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.11", number(c.chipHuntBurstSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.12", percent(c.chipHuntBurstFireRate)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hunting_moment.13"));
		} else if (chip == Chip.FIRE_RATE_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.fire_rate_mod", percent(c.chipBlueFireRate)));
		} else if (chip == Chip.CRIT_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.crit_mod", percent(c.chipBlueCrit)));
		} else if (chip == Chip.DAMAGE_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.damage_mod", percent(c.chipBlueDamage)));
		} else if (chip == Chip.HEAD_DAMAGE_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.head_damage_mod", percent(c.chipBlueHeadDamage)));
		} else if (chip == Chip.CRIT_DAMAGE_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.crit_damage_mod", percent(c.chipBlueCritDamage)));
		} else if (chip == Chip.RELOAD_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.reload_mod", percent(c.chipBlueReload)));
		} else if (chip == Chip.MAG_SIZE_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.mag_size_mod", percent(c.chipBlueMagSize)));
		} else if (chip == Chip.SPREAD_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.spread_mod", percent(c.chipBlueSpread)));
		} else if (chip == Chip.BULLET_SPEED_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.bullet_speed_mod", percent(c.chipBlueBulletSpeed)));
		} else if (chip == Chip.ARMOR_PIERCE_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.armor_pierce_mod", percent(c.chipBlueArmorPierce)));
		} else if (chip == Chip.EXPLOSION_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.explosion_mod", percent(c.chipBlueExplosion)));
		} else if (chip == Chip.PIERCE_MOD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.pierce_mod",
					String.valueOf(c.chipBluePierce)));
		} else if (chip == Chip.CLOSE_QUARTERS) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.close_quarters.1",
					number(c.chipPurpleCloseRange), percent(c.chipPurpleCloseDamage)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.close_quarters.2",
					percent(c.chipPurpleCloseCritDamage)));
		} else if (chip == Chip.FOCUSED_STRIKE) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.focused_strike",
					percent(c.chipPurpleFocusHealthRatio), percent(c.chipPurpleFocusDamage)));
		} else if (chip == Chip.RHYTHM_SHOT) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rhythm_shot.1",
					percent(c.chipPurpleRhythmPerHit)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rhythm_shot.2",
					percent(c.chipPurpleRhythmCap)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rhythm_shot.3",
					number(c.chipPurpleRhythmResetSeconds)));
		} else if (chip == Chip.UNSTOPPABLE) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.unstoppable",
					percent(c.chipPurpleUnstoppableHealthRatio), percent(c.chipPurpleUnstoppableCrit)));
		} else if (chip == Chip.FEARLESS) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.fearless.1",
					percent(c.chipPurpleFearlessDamageTaken)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.fearless.2",
					percent(c.chipPurpleFearlessDamage)));
		} else if (chip == Chip.NIMBLE_STEP) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.nimble_step.1",
					number(c.chipPurpleNimbleArmorPenalty)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.nimble_step.2",
					percent(c.chipPurpleNimbleSpeed)));
		} else if (chip == Chip.LAST_STAND) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.last_stand",
					percent(c.chipPurpleLastStandHealthRatio), percent(c.chipPurpleLastStandDamage)));
		} else if (chip == Chip.LONG_SHOT) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.long_shot",
					number(c.chipPurpleLongShotRange), percent(c.chipPurpleLongShotDamage)));
		} else if (chip == Chip.KILLING_SPREE) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.killing_spree",
					number(c.chipPurpleSpreeSeconds), percent(c.chipPurpleSpreeDamage)));
		} else if (chip == Chip.OPENING_STRIKE) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.opening_strike",
					number(c.chipPurpleOpeningSeconds), percent(c.chipPurpleOpeningDamage)));
		} else if (chip == Chip.HOLD_BREATH) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hold_breath.1",
					number(c.chipPurpleHoldStillSeconds), percent(c.chipPurpleHoldStillDamage)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hold_breath.2"));
		} else if (chip == Chip.BUDGET_SHOT) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.budget_shot",
					percent(c.chipPurpleLowMagRatio), percent(c.chipPurpleLowMagDamage)));
		} else if (chip == Chip.WIND_PACT) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.wind_pact"));
		} else if (chip == Chip.WIND_GRACE) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.wind_grace"));
		} else if (chip == Chip.STEADFAST_OATH) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.steadfast_oath.1",
					percent(c.chipPurpleOathReduction)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.steadfast_oath.2"));
		} else if (chip == Chip.HARDENED_ARMOR) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.hardened_armor",
					number(c.chipPurpleHardenedArmor)));
		} else if (chip == Chip.TEAMWORK) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.teamwork.1",
					number(c.chipPurpleTeamworkRadius)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.teamwork.2",
					percent(c.chipPurpleTeamworkPerPlayer)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.teamwork.3",
					percent(c.chipPurpleTeamworkCap)));
		} else if (chip == Chip.RIME) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rime.1",
					percent(c.chipRimeSlowPerStack), number(c.chipRimeMaxStacks)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rime.2",
					number(c.chipRimeSlowSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rime.3",
					number(c.chipRimeFreezeFloor)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rime.4"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rime.5",
					number(c.chipRimeWaveRadius), number(c.chipRimeWaveSeconds),
					number(c.chipRimeWaveCooldownSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rime.6"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rime.7"));
		} else if (chip == Chip.TIDE) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.tide.1",
					number(c.chipTideHealPerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.tide.2"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.tide.3",
					number(c.chipTideActiveHealPerLevel), number(c.chipTideActiveCooldownSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.tide.4"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.tide.5"));
		} else if (chip == Chip.VINE) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.vine.1",
					number(c.chipVineQuickenBonusPerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.vine.2",
					number(c.chipVineBloomRadiusPerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.vine.3"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.vine.4",
					number(c.chipVineRootSecondsPerLevel), number(c.chipVineRootCooldownSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.vine.5"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.vine.6"));
		} else if (chip == Chip.ROCK_WARD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rock_ward.1",
					number(c.chipRockHeartBonusPerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rock_ward.2",
					percent(c.chipRockDamageReduction)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rock_ward.3"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rock_ward.4",
					number(c.chipRockActiveHeartsPerLevel), number(c.chipRockActiveCooldownSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rock_ward.5"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.rock_ward.6"));
		} else if (chip == Chip.REAP) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.reap",
					percent(c.chipReapHealthRatio), percent(c.chipReapDamagePerLevel)));
		} else if (chip == Chip.MEDAL) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.medal.1",
					percent(c.chipMedalDamagePerLevel), number(c.chipMedalMaxStacks)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.medal.2",
					number(c.chipMedalResetSeconds)));
		} else if (chip == Chip.SALVAGE) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.salvage",
					number(c.chipSalvageAmmoPerLevel)));
		} else if (chip == Chip.TAC_RELOAD) {
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.tac_reload.1",
					percent(c.chipTacReloadPerLevel)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.tac_reload.2",
					percent(c.chipTacReloadDamage), number(c.chipTacReloadBonusSeconds)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.tac_reload.3"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.tac_reload.4",
					number(c.chipTacReloadSkillCooldown)));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.tac_reload.5"));
			tooltip.add(Component.translatable("item.tacz_gun_level.chip.tac_reload.6"));
		}
	}

	/** 0.055 -> "5.5%" */
	private static String percent(double ratio) {
		return number(ratio * 100) + "%";
	}

	private static String number(double value) {
		return value == Math.floor(value)
				? String.valueOf((long) value)
				: String.format(Locale.ROOT, "%.1f", value);
	}

	/** 0.25 -> "0.25"：点数档位需要两位小数，number 的 %.1f 会把 0.25 显示成 0.3 */
	private static String decimal(double value) {
		if (value == Math.floor(value)) {
			return String.valueOf((long) value);
		}
		String text = String.format(Locale.ROOT, "%.2f", value);
		return text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
	}
}
