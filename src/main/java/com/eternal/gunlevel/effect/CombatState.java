package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.LevelConfig;
import com.eternal.gunlevel.entity.RockConstructEntity;
import com.eternal.gunlevel.util.ChipHints;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 元素改造与撕裂弹的运行时状态：附着、反应、持续伤害、冻结都在这里，
 * 由 ServerTickEvents.END_SERVER_TICK 驱动 tick()。
 *
 * 持续伤害通过“结算前把 hurtTime 清零、结算后保持为 0”实现：
 * 既不会被供应商免疫帧挡掉，也不会占用它，流血期间枪械照常输出。
 */
public final class CombatState {
	private static final ConcurrentHashMap<UUID, Combat> COMBAT = new ConcurrentHashMap<>();
	private static final UUID SLOW_ID =
			UUID.nameUUIDFromBytes("tacz_gun_level:shock_slow".getBytes(StandardCharsets.UTF_8));
	private static final UUID PIERCE_ID =
			UUID.nameUUIDFromBytes("tacz_gun_level:superconduct_pierce".getBytes(StandardCharsets.UTF_8));
	/** 持续伤害结算间隔（tick），10 = 每秒两次 */
	private static final int DOT_INTERVAL = 10;
	/** 岩的粒子：琥珀色尘埃（1.20.1 没有沙/石系粒子，用彩色 dust 最接近） */
	private static final ParticleOptions ROCK_DUST =
			new DustParticleOptions(new org.joml.Vector3f(0.92F, 0.76F, 0.28F), 1.2F);

	private CombatState() {
	}

	private static final class Combat {
		Entity entity;
		/** 伤害归属：谁开的枪。UUID 用于跨 tick 现查，引用只做兜底缓存 */
		UUID shooterId;
		LivingEntity shooter;
		/** 射手那把枪算出的反应暴击率（已封顶 20%），反应与感电持续伤害按它掷骰 */
		float critChance;
		Element attach = Element.NONE;
		long attachUntil;
		/** 扩散残留的元素份数：普通附着恒为 1，风扩散后每吃一次反应少一份 */
		int attachStacks;
		/** 当前这份附着是不是风扩散摊出来的 */
		boolean swirled;
		long swirlReadyAt;
		long bleedUntil;
		long shockUntil;
		/** 超导：目标受到的伤害穿走多少原版护甲的窗口，靠护甲属性修正实现 */
		long superconductUntil;
		boolean armorPenetrated;
		long freezeUntil;
		double freezePenalty;
		long freezeReadyAt;
		boolean slowed;
		/** 燃烧：剩余跳数 + 下一次结算时间 + 间隔，跳数用完自然结束 */
		int burnHitsLeft;
		int burnInterval;
		long burnNextAt;
	}

	private static long ticks(double seconds) {
		return Math.max(1, Math.round(seconds * 20));
	}

	private static long deadline(Level world, double seconds) {
		return world.getGameTime() + ticks(seconds);
	}

	// ------------------------------------------------------------------ 命中

	/**
	 * 子弹命中生物：附着元素并尝试反应。
	 *
	 * @param element    发射那把枪安装的元素核芯（NONE 表示无元素）
	 * @param hitDamage  这次命中的实际伤害（已含等级加成与暴击）
	 * @param critChance 这把枪的反应暴击率（跟随武器面板并已封顶 20%），反应与感电按它掷骰
	 * @param chips      发射那把枪安装的战术芯片，结晶反应要不要召唤造物、造物的 buff 强度都看它
	 */
	public static void onBulletHit(Entity target, LivingEntity shooter, Element element,
			int level, float hitDamage, float critChance, EnumSet<Chip> chips) {
		if (!(target instanceof LivingEntity victim) || victim.level().isClientSide) {
			return;
		}
		// 友伤免疫：玩家（包括射手自己）不吃撕裂弹与元素状态
		if (victim == shooter || victim instanceof net.minecraft.world.entity.player.Player) {
			return;
		}
		Level world = victim.level();
		Combat c = COMBAT.computeIfAbsent(victim.getUUID(), k -> {
			Combat fresh = new Combat();
			fresh.entity = victim;
			return fresh;
		});
		c.entity = victim;
		c.shooter = shooter;
		c.shooterId = shooter == null ? null : shooter.getUUID();
		c.critChance = critChance;

		if (level >= GunLevelMod.CONFIG.elementMinLevel && GunLevelMod.CONFIG.lv3Effect == 1) {
			// LV3 撕裂弹：命中刷新流血
			c.bleedUntil = Math.max(c.bleedUntil,
					deadline(world, GunLevelMod.CONFIG.bleedSeconds));
		}

		if (element == Element.NONE) {
			return;
		}
		// 风/岩不附着：目标身上干净时这一发什么都不留下，也不触发任何反应
		if (!element.canAttach() && c.attach == Element.NONE) {
			return;
		}
		if (c.attach == Element.NONE) {
			attachElement(c, world, element);
			sendParticles(world, victim, particleOf(element), 10);
			return;
		}
		if (c.attach == element) {
			c.attachUntil = deadline(world, GunLevelMod.CONFIG.attachSeconds);
			return;
		}

		Reaction reaction = Reaction.of(c.attach, element);
		if (reaction == null) {
			return;
		}
		if (element == Element.WIND) {
			// 扩散有内置冷却（默认 1 秒），冷却里的风弹白打，也不消耗残留的元素份数
			long t = world.getGameTime();
			if (t < c.swirlReadyAt) {
				return;
			}
			c.swirlReadyAt = t + ticks(GunLevelMod.CONFIG.swirlCooldownSeconds);
			spendSwirlStack(c, world);
		} else {
			consumePortion(c, world);
		}
		sendParticles(world, victim, particleOf(element), 10);
		react(victim, c, reaction, shooter, hitDamage, chips, level);
	}

	/** 附着元素：换元素才重新起一份，同元素只刷新时间，别把扩散残留的份数冲掉 */
	private static void attachElement(Combat c, Level world, Element element) {
		if (c.attach != element) {
			c.attachStacks = 1;
			c.swirled = false;
		}
		c.attach = element;
		c.attachUntil = deadline(world, GunLevelMod.CONFIG.attachSeconds);
	}

	private static void clearAttachment(Combat c) {
		c.attach = Element.NONE;
		c.attachStacks = 0;
		c.swirled = false;
	}

	/**
	 * 反应吃掉附着：扩散摊出来的份数一次少一份（所以残留的 3 份能被任何一对元素反复打反应），
	 * 普通附着仍然按老规矩一次即清。
	 */
	private static void consumePortion(Combat c, Level world) {
		if (!c.swirled) {
			clearAttachment(c);
			return;
		}
		c.attachStacks--;
		if (c.attachStacks <= 0) {
			clearAttachment(c);
		} else {
			c.attachUntil = deadline(world, GunLevelMod.CONFIG.attachSeconds);
		}
	}

	/**
	 * 扩散把元素摊成几份：第一次扩散把目标身上的元素换成 swirlAttachStacks 份，
	 * 之后每吃一次反应（风的扩散或其它元素的配对反应）少一份，份数见底才真的清空附着。
	 */
	private static void spendSwirlStack(Combat c, Level world) {
		c.attachStacks = c.swirled ? c.attachStacks - 1 : GunLevelMod.CONFIG.swirlAttachStacks;
		c.swirled = true;
		if (c.attachStacks <= 0) {
			clearAttachment(c);
		} else {
			c.attachUntil = deadline(world, GunLevelMod.CONFIG.attachSeconds);
		}
	}

	// ------------------------------------------------------------------ 反应

	/** 一次伤害的归属与暴击参数：归属永远是开枪的射手，暴击取射手当时的面板 */
	private record Strike(LivingEntity attacker, float critChance) {
	}

	private static void react(LivingEntity victim, Combat c, Reaction reaction,
			LivingEntity shooter, float hitDamage, EnumSet<Chip> chips, int level) {
		Level world = victim.level();
		Strike strike = new Strike(shooter, c.critChance);
		switch (reaction) {
			case OVERLOAD -> {
				// 超载：按触发那次武器伤害的比例结算范围伤害，可暴击；
				// 自行结算而不是真爆炸，保证不破坏方块，且只炸敌对目标
				double r = GunLevelMod.CONFIG.overloadRadius;
				float dmg = hitDamage * (float) GunLevelMod.CONFIG.overloadDamageRatio;
				world.playSound(null, victim.blockPosition(), SoundEvents.GENERIC_EXPLODE,
						SoundSource.HOSTILE, 1.2F, 0.85F);
				sendParticles(world, victim, ParticleTypes.EXPLOSION_EMITTER, 1);
				AABB box = victim.getBoundingBox().inflate(r, r * 0.6, r);
				for (Entity near : world.getEntitiesOfClass(Entity.class, box,
						e -> isHostileTarget(e, victim, shooter, r))) {
					applyDirectDamage(world, (LivingEntity) near, strike, dmg, true);
				}
			}
			case SHOCK -> {
				// 感电：从命中点向周围传导，范围内的敌对生物一起进入感电；同样不打自己和玩家
				double r = GunLevelMod.CONFIG.shockRadius;
				AABB box = victim.getBoundingBox().inflate(r, r * 0.6, r);
				world.playSound(null, victim.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
						SoundSource.HOSTILE, 0.4F, 1.6F);
				for (Entity near : world.getEntitiesOfClass(Entity.class, box,
						e -> isHostileTarget(e, victim, shooter, r))) {
					applyShockTo((LivingEntity) near, world, strike);
				}
			}
			case SUPERCONDUCT -> {
				// 超导：不额外掉血，只把目标打进超导窗口 —— 之后 superconductSeconds 内对它造成的伤害
				// 一律穿走 superconductArmorPierce 的原版护甲。做法是给目标的护甲属性挂一条
				// MULTIPLY_TOTAL 修正，所以枪械、近战、爆炸谁都吃得到；重复超导只刷新时间、不叠第二层。
				c.superconductUntil = Math.max(c.superconductUntil,
						deadline(world, GunLevelMod.CONFIG.superconductSeconds));
				if (!c.armorPenetrated) {
					applyArmorPierce(victim, true);
					c.armorPenetrated = true;
				}
				sendParticles(world, victim, ParticleTypes.SNOWFLAKE, 14);
				sendParticles(world, victim, ParticleTypes.ELECTRIC_SPARK, 8);
			}
			case VAPORIZE -> {
				// 蒸发：只在触发这一次结算里追加增伤，附着随即清空，不留持续 buff；可暴击
				applyDirectDamage(world, victim, strike,
						hitDamage * (float) (GunLevelMod.CONFIG.vaporizeMultiplier - 1.0), true);
				sendParticles(world, victim, ParticleTypes.ENCHANT, 12);
			}
			case MELT -> {
				// 融化：固定增伤，不吃暴击
				applyDirectDamage(world, victim, strike,
						hitDamage * (float) (GunLevelMod.CONFIG.meltMultiplier - 1.0), false);
				sendParticles(world, victim, ParticleTypes.ENCHANT, 12);
			}
			case FREEZE -> {
				long t = world.getGameTime();
				c.freezePenalty = t < c.freezeReadyAt
						? c.freezePenalty + GunLevelMod.CONFIG.freezeDecay
						: 0;
				double seconds = Math.max(chips.contains(Chip.RIME)
								? GunLevelMod.CONFIG.chipRimeFreezeFloor
								: GunLevelMod.CONFIG.freezeMinimum,
						GunLevelMod.CONFIG.freezeSeconds - c.freezePenalty);
				c.freezeUntil = Math.max(c.freezeUntil, deadline(world, seconds));
				c.freezeReadyAt = c.freezeUntil + ticks(GunLevelMod.CONFIG.freezeResetSeconds);
				sendParticles(world, victim, ParticleTypes.SNOWFLAKE, 20);
			}
			case BLOOM -> {
				// 绽放：在触发反应的玩家脚下开一片跟随他的苏生领域，领域内全体玩家回血。
				// 枪上装了净化领域的话，这片领域还要开始记血量账（芯片集合传进去判断）；
				// 缠藤誓约把领域半径按永恒改装等级放大
				if (world instanceof ServerLevel serverWorld) {
					double radiusBonus = chips.contains(Chip.VINE)
							? GunLevelMod.CONFIG.chipVineBloomRadiusPerLevel * level : 0.0;
					BloomDomain.spawn(serverWorld, shooter, chips, radiusBonus);
				}
				sendParticles(world, victim, ParticleTypes.HAPPY_VILLAGER, 20);
			}
			case BURN -> {
				// 燃烧：burnSeconds 内共 burnHits 跳固定伤害，每跳补一份火附着（不暴击）
				c.burnInterval = Math.max(1, (int) (ticks(GunLevelMod.CONFIG.burnSeconds)
						/ Math.max(1, GunLevelMod.CONFIG.burnHits)));
				c.burnHitsLeft = GunLevelMod.CONFIG.burnHits;
				c.burnNextAt = world.getGameTime() + c.burnInterval;
				sendParticles(world, victim, ParticleTypes.FLAME, 16);
			}
			case QUICKEN -> {
				// 激化：给玩家一层激化 buff，射速在读取射击间隔时折算；重复激化只刷新时间。
				// 缠藤誓约把持续时间按永恒改装等级拉长
				applyQuicken(shooter, chips.contains(Chip.VINE) ? level : 0);
				sendParticles(world, victim, ParticleTypes.ELECTRIC_SPARK, 16);
			}
			case CRYSTALLIZE -> {
				// 结晶：不掉血，只给触发反应的射手叠金心；附着按普通反应照常消耗。
				// 磐岩壁垒把金心上限按永恒改装等级放大。
				// 凝晶召唤：装了这颗芯片才召唤岩元素造物，造物强度看枪上总共装了几颗。
				// 已有造物在场时 summon 返回 null（用户口径：存在期间无法重复召唤，这一次忽略）
				applyCrystallize(shooter, chips.contains(Chip.ROCK_WARD) ? level : 0);
				if (chips.contains(Chip.CRYSTAL_SUMMON) && world instanceof ServerLevel serverWorld
						&& RockConstructEntity.summon(serverWorld, shooter, chips.size()) != null) {
					ChipHints.show(shooter, Chip.CRYSTAL_SUMMON);
				}
				sendParticles(world, victim, ParticleTypes.TOTEM_OF_UNDYING, 16);
			}
			case SWIRL -> {
				// 扩散：以反应目标为圆心的群伤（不暴击），范围与牵引一致，同时把圈内生物朝圆心拽一段
				double r = GunLevelMod.CONFIG.swirlRadius;
				AABB box = victim.getBoundingBox().inflate(r, r * 0.6, r);
				Vec3 center = victim.position();
				// 风的护佑期间这一发是强化版：牵引不走固定格数，直接把整段距离跑完，
				// 只留出 keepGap 那点间距（AABB 一重叠原版互推会立刻把它们再挤开）
				boolean gathered = WindGuardState.consumeGather(shooter);
				double pullBlocks = gathered ? Double.MAX_VALUE
						: GunLevelMod.CONFIG.swirlPullBlocks;
				for (Entity near : world.getEntitiesOfClass(Entity.class, box,
						e -> isHostileTarget(e, victim, shooter, r))) {
					applyDirectDamage(world, (LivingEntity) near, strike,
							(float) GunLevelMod.CONFIG.swirlDamage, false);
					// 原版按攻击者位置结算 0.4 强度的受击击退，方向正好背离射手，
					// 会把刚拽过来的怪又推回去，所以吃完伤害立刻清掉水平速度
					Vec3 knockback = near.getDeltaMovement();
					near.setDeltaMovement(0.0, knockback.y(), 0.0);
					near.hurtMarked = true;
					pullToward(world, near, center, pullBlocks,
							victim.getBbWidth() * 0.5 + near.getBbWidth() * 0.5 + 0.1);
				}
				sendParticles(world, victim, ParticleTypes.CLOUD, 20);
			}
		}
		// 潮汐律令：触发任意元素反应回血（内置冷却，防连续反应刷血）
		if (chips.contains(Chip.TIDE) && shooter instanceof ServerPlayer tideHolder) {
			ElementSkillState.onReactionHeal(tideHolder, level);
		}
	}

	/**
	 * 牵引：直接走一段位移，并在被拽过的路上留一尾云。不再给初速——初速每 tick 被摩擦吃掉，
	 * 按 0.91 折算的一格实测只剩三分之一格。move() 自己会沿墙裁剪、会上台阶。
	 * keepGap 是落到圆心前留出的间距：AABB 一重叠，原版实体互推会立刻把它们再挤开，
	 * 看着就像"聚过来又被弹飞"。
	 */
	private static void pullToward(Level world, Entity target, Vec3 center, double blocks,
			double keepGap) {
		double dx = center.x() - target.getX();
		double dz = center.z() - target.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-3) {
			return;
		}
		double step = Math.min(blocks, Math.max(0.0, len - keepGap));
		if (step < 1.0E-3) {
			return;
		}
		Vec3 from = target.position();
		target.move(MoverType.SELF, new Vec3(dx / len * step, 0.0, dz / len * step));
		spawnPullTrail(world, from, dx / len, dz / len, step, target.getBbHeight() * 0.5);
		sendParticles(world, target, ParticleTypes.CLOUD, 6);
	}

	/** 尾迹：沿它刚被拽过来的那条线铺一串云，越靠近起点越稀，一眼看得出方向 */
	private static void spawnPullTrail(Level world, Vec3 from, double dirX, double dirZ,
			double step, double height) {
		if (!(world instanceof ServerLevel sw)) {
			return;
		}
		for (int i = 1; i <= 4; i++) {
			double along = step * i / 4.0;
			sw.sendParticles(ParticleTypes.CLOUD,
					from.x() + dirX * along, from.y() + height, from.z() + dirZ * along,
					1, 0.05, 0.05, 0.05, 0.01);
		}
	}

	/** 上一层/续期激化：只有一层，重复反应刷新持续时间；缠藤誓约把持续时间按等级拉长 */
	private static void applyQuicken(LivingEntity shooter, int vineLevel) {
		if (shooter == null || shooter.isRemoved()) {
			return;
		}
		int duration = (int) ticks(GunLevelMod.CONFIG.quickenSeconds
				+ GunLevelMod.CONFIG.chipVineQuickenBonusPerLevel * vineLevel);
		MobEffectInstance current = shooter.getEffect(GunLevelMod.QUICKENING);
		if (current != null) {
			duration = Math.max(duration, current.getDuration());
		}
		shooter.addEffect(new MobEffectInstance(GunLevelMod.QUICKENING, duration, 0));
	}

	/**
	 * 叠结晶：1~4 级每次 +1 层、持续 crystallizeSeconds；叠满 crystallizeMaxLevel 那一级
	 * 固定持续 crystallizeMaxSeconds，这期间再打结晶无效（不涨层也不续时间），
	 * buff 结束后自然从 0 重新叠。金心由效果本身按层数发放，见 CrystallizationEffect。
	 * 磐岩壁垒把金心上限加 {@code rockLevel} 颗（层随金心数换算）。
	 */
	/**
	 * 结晶金心的上限（颗）：基础档位 × 每档颗数，磐岩壁垒再按护盾折算加回来（1 金心 = 2 护盾）。
	 * 「岩铠」也用它封顶，两处必须同源。
	 */
	public static double crystallizationCapHearts(int rockLevel) {
		LevelConfig c = GunLevelMod.CONFIG;
		return c.crystallizeMaxLevel * c.crystallizeHeartsPerLevel
				+ c.chipRockShieldCapPerLevel * rockLevel / 2.0;
	}

	private static void applyCrystallize(LivingEntity player, int rockLevel) {
		if (player == null || player.isRemoved()) {
			return;
		}
		LevelConfig c = GunLevelMod.CONFIG;
		double cap = crystallizationCapHearts(rockLevel);
		MobEffectInstance current = player.getEffect(GunLevelMod.CRYSTALLIZATION);
		int stacks = current == null ? 0 : current.getAmplifier() + 1;
		// 每次反应固定加 2 颗心；上限不一定被 2 整除（满级 15），最后一档就只补差额
		double nowHearts = Math.min(cap, c.crystallizeHeartsPerLevel * stacks);
		double nextHearts = Math.min(cap, c.crystallizeHeartsPerLevel * (stacks + 1));
		// 到顶后不再涨心，只把持续时间续回满档 —— 否则这一次反应就白打了
		boolean capped = nextHearts <= nowHearts;
		int nextStacks = capped ? stacks : stacks + 1;
		double seconds = nextHearts >= cap ? c.crystallizeMaxSeconds : c.crystallizeSeconds;
		// 上限按「触发这一枪」的等级定格写进效果侧：换枪后不重算上限，已给的心不会被漏记
		CrystallizationEffect.setCap(player.getUUID(), (float) cap);
		player.addEffect(new MobEffectInstance(GunLevelMod.CRYSTALLIZATION,
				(int) ticks(seconds), nextStacks - 1));
	}

	/** 反应范围伤害/传导的目标判定：敌对生物可以，射手自己和其他玩家一律排除 */
	private static boolean isHostileTarget(Entity e, LivingEntity center, LivingEntity shooter, double r) {
		if (!(e instanceof LivingEntity living) || !living.isAlive()) {
			return false;
		}
		if (living == shooter || living instanceof net.minecraft.world.entity.player.Player) {
			return false;
		}
		return living.distanceToSqr(center) <= r * r;
	}

	/** 把一个目标推进感电状态（持续伤害 + 减速），并记住伤害归属与暴击率 */
	private static void applyShockTo(LivingEntity target, Level world, Strike strike) {
		Combat c = COMBAT.computeIfAbsent(target.getUUID(), k -> {
			Combat fresh = new Combat();
			fresh.entity = target;
			return fresh;
		});
		c.entity = target;
		c.shooter = strike.attacker();
		c.shooterId = strike.attacker() == null ? null : strike.attacker().getUUID();
		c.critChance = strike.critChance();
		c.shockUntil = Math.max(c.shockUntil, deadline(world, GunLevelMod.CONFIG.shockSeconds));
		if (!c.slowed) {
			applySlow(target, true);
			c.slowed = true;
		}
		sendParticles(world, target, ParticleTypes.ELECTRIC_SPARK, 12);
	}

	public static boolean isFrozen(Entity target) {
		Combat c = COMBAT.get(target.getUUID());
		return c != null && target.level().getGameTime() < c.freezeUntil;
	}

	public static Element attachedElement(Entity target) {
		Combat c = COMBAT.get(target.getUUID());
		return c == null ? Element.NONE : c.attach;
	}

	// ------------------------------------------------------------------ 伤害

	/**
	 * 不占用受害者受伤间隔的额外伤害。归属始终是射手：indirect_magic 和 magic 一样在
	 * damage_type/bypasses_armor 里（无视护甲与盾牌），但保护类附魔仍减免，且击杀计入射手。
	 * allowCrit 时按反应暴击率掷骰（跟随武器面板、封顶 20%），倍率用反应爆伤（封顶 150%）。
	 */
	private static void applyDirectDamage(Level world, LivingEntity target, Strike strike,
			float amount, boolean allowCrit) {
		if (world.isClientSide || amount <= 0) {
			return;
		}
		LivingEntity attacker = strike.attacker();
		boolean attributed = attacker != null && !attacker.isRemoved();
		if (allowCrit && attributed && world.getRandom().nextFloat() < strike.critChance()) {
			amount *= (float) LevelScaling.reactionCritMultiplier();
			sendParticles(world, target, ParticleTypes.ENCHANTED_HIT, 6);
		}
		applyFixedDamage(world, target, attributed ? attacker : null, amount);
	}

	/**
	 * 固定伤害的公用出口：归因给 attacker（拿不到就用无归属的 magic），走 indirect_magic
	 * 所以无视护甲与盾牌，且不占用受害者的受伤间隔。苏生领域的新星也用它。
	 */
	public static void applyFixedDamage(Level world, LivingEntity target, LivingEntity attacker,
			float amount) {
		if (world.isClientSide || amount <= 0) {
			return;
		}
		boolean attributed = attacker != null && !attacker.isRemoved();
		DamageSource source = attributed
				? world.damageSources().indirectMagic(attacker, attacker)
				: world.damageSources().magic();
		target.hurtTime = 0;
		target.hurt(source, amount);
		target.hurtTime = 0;
	}

	/** 持续伤害也要算玩家造成：每 tick 按 UUID 现查射手，查不到退回缓存引用 */
	private static LivingEntity resolveShooter(Level world, Combat c) {
		if (c.shooterId != null && world instanceof ServerLevel serverWorld) {
			Entity live = serverWorld.getEntity(c.shooterId);
			if (live instanceof LivingEntity living && living.isAlive()) {
				c.shooter = living;
				return living;
			}
		}
		return c.shooter != null && !c.shooter.isRemoved() ? c.shooter : null;
	}

	// ------------------------------------------------------------------ tick

	public static void tick(MinecraftServer server) {
		if (COMBAT.isEmpty()) {
			return;
		}
		COMBAT.forEach((uuid, c) -> {
			if (!(c.entity instanceof LivingEntity victim) || victim.isRemoved() || !victim.isAlive()) {
				if (c.entity instanceof LivingEntity dead) {
					if (c.slowed) {
						applySlow(dead, false);
					}
					if (c.armorPenetrated) {
						applyArmorPierce(dead, false);
					}
				}
				COMBAT.remove(uuid, c);
				return;
			}
			Level world = victim.level();
			if (world.isClientSide) {
				return;
			}
			long t = world.getGameTime();

			if (c.attach != Element.NONE && t > c.attachUntil) {
				c.attach = Element.NONE;
			}

			boolean bleeding = t < c.bleedUntil;
			boolean shocked = t < c.shockUntil;
			if (c.slowed && !shocked) {
				applySlow(victim, false);
				c.slowed = false;
			}
			if (c.armorPenetrated && t >= c.superconductUntil) {
				applyArmorPierce(victim, false);
				c.armorPenetrated = false;
			}
			if ((bleeding || shocked) && t % DOT_INTERVAL == 0) {
				Strike strike = new Strike(resolveShooter(world, c), c.critChance);
				if (bleeding) {
					// 流血不暴击
					applyDirectDamage(world, victim, strike,
							(float) (GunLevelMod.CONFIG.bleedDamagePerSecond * DOT_INTERVAL / 20.0),
							false);
				}
				if (shocked) {
					// 感电每次结算都按武器面板暴击率掷骰
					applyDirectDamage(world, victim, strike,
							(float) (GunLevelMod.CONFIG.shockDamagePerSecond * DOT_INTERVAL / 20.0),
							true);
				}
				sendParticles(world, victim,
						shocked ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CRIT, 4);
			}

			if (t < c.freezeUntil) {
				freezeInPlace(victim);
			}

			// 燃烧：按自己的间隔逐跳结算，跳数用完自然结束
			if (c.burnHitsLeft > 0 && t >= c.burnNextAt) {
				c.burnHitsLeft--;
				c.burnNextAt = t + c.burnInterval;
				applyDirectDamage(world, victim,
						new Strike(resolveShooter(world, c), c.critChance),
						(float) GunLevelMod.CONFIG.burnDamagePerHit, false);
				// 每一跳都补一份火附着，方便后续用水/雷继续打反应
				attachElement(c, world, Element.FIRE);
				sendParticles(world, victim, ParticleTypes.FLAME, 6);
			}

			if (!bleeding && !shocked && !c.armorPenetrated && c.burnHitsLeft <= 0
					&& t >= c.freezeUntil && c.attach == Element.NONE) {
				COMBAT.remove(uuid, c);
			}
		});
	}

	private static void freezeInPlace(LivingEntity victim) {
		Vec3 v = victim.getDeltaMovement();
		if (v.horizontalDistanceSqr() > 1.0E-4) {
			victim.setDeltaMovement(new Vec3(0, v.y(), 0));
			victim.hurtMarked = true;
		}
		if (victim instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null) {
			mob.setTarget(null);
		}
	}

	// ------------------------------------------------------------------ 属性修正（感电减速 / 超导穿甲）

	private static void applySlow(LivingEntity victim, boolean on) {
		AttributeInstance instance = victim.getAttributes()
				.getInstance(Attributes.MOVEMENT_SPEED);
		if (instance == null) {
			return;
		}
		instance.removeModifier(SLOW_ID);
		if (on) {
			instance.addTransientModifier(new AttributeModifier(SLOW_ID, "tacz_gun_level_shock_slow",
					-GunLevelMod.CONFIG.shockSlow, AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	/**
	 * 超导的护甲修正：挂在目标的护甲属性上而不是挂在某一次伤害上，
	 * 所以窗口内枪械、近战、爆炸对它造成的伤害一律少算 30% 护甲的减免。
	 * 先摘后挂，重复超导只会刷新时间、不会叠出第二层。
	 */
	private static void applyArmorPierce(LivingEntity victim, boolean on) {
		AttributeInstance instance = victim.getAttributes()
				.getInstance(Attributes.ARMOR);
		if (instance == null) {
			return;
		}
		instance.removeModifier(PIERCE_ID);
		if (on) {
			instance.addTransientModifier(new AttributeModifier(PIERCE_ID,
					"tacz_gun_level_superconduct_pierce", -GunLevelMod.CONFIG.superconductArmorPierce,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	// ------------------------------------------------------------------ 表现

	private static void sendParticles(Level world, Entity target, ParticleOptions effect, int count) {
		if (world instanceof ServerLevel sw) {
			Vec3 pos = target.position().add(0, target.getBbHeight() * 0.5, 0);
			sw.sendParticles(effect, pos.x(), pos.y(), pos.z(), count, 0.3, 0.4, 0.3, 0.0);
		}
	}

	private static ParticleOptions particleOf(Element element) {
		return switch (element) {
			case THUNDER -> ParticleTypes.ELECTRIC_SPARK;
			case FIRE -> ParticleTypes.FLAME;
			case WATER -> ParticleTypes.BUBBLE;
			case ICE -> ParticleTypes.SNOWFLAKE;
			case GRASS -> ParticleTypes.HAPPY_VILLAGER;
			case WIND -> ParticleTypes.CLOUD;
			case ROCK -> ROCK_DUST;
			case NONE -> ParticleTypes.SMOKE;
		};
	}

	public static int trackedCount() {
		return COMBAT.size();
	}

	public static void clear() {
		COMBAT.clear();
	}
}
