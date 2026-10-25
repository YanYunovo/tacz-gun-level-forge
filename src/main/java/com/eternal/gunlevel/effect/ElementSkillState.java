package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.item.GunCoreData;
import com.eternal.gunlevel.util.ChipHints;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 四元素金芯片的主动技能（凛冬之啸/潮汐律令/缠藤誓约/磐岩壁垒）：
 * 冷却与「已就绪」提示按（玩家, 芯片）记账；藤缚另记「最近命中的目标」。
 * 前置与灵风/迅雷/咆哮同一套：主手枪装**对应元素核芯** + 这颗芯片。
 * 只在服务端有值。
 */
public final class ElementSkillState {
	private static final class Skill {
		/** 冷却到什么时候（世界刻） */
		long readyAt;
		/** 「已就绪」那行小字只报一次 */
		boolean announced;
	}

	private static final Map<UUID, Map<Chip, Skill>> SKILLS = new ConcurrentHashMap<>();
	/** 藤缚的目标：每个玩家最近一次（外层）命中的生物 */
	private static final Map<UUID, LivingEntity> LAST_TARGET = new ConcurrentHashMap<>();
	/** 潮汐律令被动回血的上次触发时刻（内置冷却） */
	private static final Map<UUID, Long> TIDE_HEAL_AT = new ConcurrentHashMap<>();

	private ElementSkillState() {
	}

	private static int ticks(double seconds) {
		return (int) Math.max(1, Math.round(seconds * 20));
	}

	/** 前置：主手枪装对应元素核芯 + 这颗芯片 */
	private static boolean armed(Player player, Chip chip, Element element) {
		return GunCoreData.get(player.getMainHandItem()) == element
				&& GunChipData.holdsChip(player.getMainHandItem(), chip);
	}

	/** 藤缚的目标登记：外层命中就刷新（内层递归不调这里） */
	public static void recordTarget(LivingEntity shooter, LivingEntity target) {
		if (shooter != null && target != null && target.isAlive()) {
			LAST_TARGET.put(shooter.getUUID(), target);
		}
	}

	/** 潮汐律令被动：触发任意元素反应回血 1×等级 颗（内置冷却，防连续反应刷血） */
	public static void onReactionHeal(ServerPlayer player, int level) {
		long now = player.level().getGameTime();
		Long last = TIDE_HEAL_AT.get(player.getUUID());
		if (last != null && now - last < Math.round(
				GunLevelMod.CONFIG.chipTideHealCooldownSeconds * 20)) {
			return;
		}
		TIDE_HEAL_AT.put(player.getUUID(), now);
		player.heal((float) (GunLevelMod.CONFIG.chipTideHealPerLevel * level));
	}

	/** 玩家按键：由 {@link WindGuardState#requestSkill} 按槽位顺序分发过来 */
	public static void requestSkill(ServerPlayer player, Chip chip) {
		switch (chip) {
			case RIME -> rimeWave(player);
			case TIDE -> tideSurge(player);
			case VINE -> vineBind(player);
			case ROCK_WARD -> rockWard(player);
			default -> {
			}
		}
	}

	/** 寒潮：半径内全部敌对生物吃满凝寒层数 */
	private static void rimeWave(ServerPlayer player) {
		if (!armed(player, Chip.RIME, Element.ICE)) {
			return;
		}
		Skill skill = ready(player, Chip.RIME, GunLevelMod.CONFIG.chipRimeWaveCooldownSeconds);
		if (skill == null) {
			return;
		}
		double radius = GunLevelMod.CONFIG.chipRimeWaveRadius;
		int amp = Math.max(1, GunLevelMod.CONFIG.chipRimeMaxStacks - 1);
		int duration = ticks(GunLevelMod.CONFIG.chipRimeWaveSeconds);
		if (player.level() instanceof ServerLevel world) {
			for (Entity near : world.getEntities(player, player.getBoundingBox().inflate(radius),
					e -> e instanceof Enemy && e.isAlive())) {
				((LivingEntity) near).addEffect(new MobEffectInstance(
						GunLevelMod.CHILL, duration, amp));
			}
		}
	}

	/** 涌泉：清掉自身全部负面效果并回血 */
	private static void tideSurge(ServerPlayer player) {
		if (!armed(player, Chip.TIDE, Element.WATER)) {
			return;
		}
		Skill skill = ready(player, Chip.TIDE,
				GunLevelMod.CONFIG.chipTideActiveCooldownSeconds);
		if (skill == null) {
			return;
		}
		int level = LevelScaling.levelOfStack(player.getMainHandItem());
		player.getActiveEffects().stream()
				.filter(inst -> inst.getEffect().getCategory() == MobEffectCategory.HARMFUL)
				.map(MobEffectInstance::getEffect)
				.toList()
				.forEach(player::removeEffect);
		player.heal((float) (GunLevelMod.CONFIG.chipTideActiveHealPerLevel * level));
	}

	/** 藤缚：最近命中的目标定身（缓慢 VI，几乎无法移动） */
	private static void vineBind(ServerPlayer player) {
		if (!armed(player, Chip.VINE, Element.GRASS)) {
			return;
		}
		Skill skill = ready(player, Chip.VINE,
				GunLevelMod.CONFIG.chipVineRootCooldownSeconds);
		if (skill == null) {
			return;
		}
		LivingEntity target = LAST_TARGET.get(player.getUUID());
		if (target == null || !target.isAlive() || target.level().isClientSide()) {
			return;
		}
		int level = LevelScaling.levelOfStack(player.getMainHandItem());
		int duration = ticks(GunLevelMod.CONFIG.chipVineRootSecondsPerLevel * level);
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 6));
	}

	/** 岩铠：立刻获得一把金心（按永恒改装等级放大） */
	private static void rockWard(ServerPlayer player) {
		if (!armed(player, Chip.ROCK_WARD, Element.ROCK)) {
			return;
		}
		Skill skill = ready(player, Chip.ROCK_WARD,
				GunLevelMod.CONFIG.chipRockActiveCooldownSeconds);
		if (skill == null) {
			return;
		}
		int level = LevelScaling.levelOfStack(player.getMainHandItem());
		float points = (float) (GunLevelMod.CONFIG.chipRockActiveHeartsPerLevel * 2.0 * level);
		player.setAbsorptionAmount(player.getAbsorptionAmount() + points);
	}

	/** 记冷却；在冷却就返回 null（什么都不发生），批下来返回记录 */
	private static Skill ready(ServerPlayer player, Chip chip, double cooldownSeconds) {
		long now = player.level().getGameTime();
		Skill skill = SKILLS.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>())
				.computeIfAbsent(chip, c -> new Skill());
		if (now < skill.readyAt) {
			return null;
		}
		skill.readyAt = now + Math.max(1, Math.round(cooldownSeconds * 20));
		skill.announced = false;
		return skill;
	}

	public static void tick(MinecraftServer server) {
		if (SKILLS.isEmpty()) {
			return;
		}
		server.getPlayerList().getPlayers().forEach(player -> {
			Map<Chip, Skill> mine = SKILLS.get(player.getUUID());
			if (mine == null) {
				return;
			}
			long now = player.level().getGameTime();
			mine.forEach((chip, skill) -> {
				if (!skill.announced && now >= skill.readyAt) {
					skill.announced = true;
					ChipHints.show(player, chip, ChipHints.SKILL_READY);
				}
			});
		});
		SKILLS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	public static void clear() {
		SKILLS.clear();
		LAST_TARGET.clear();
		TIDE_HEAL_AT.clear();
	}
}
