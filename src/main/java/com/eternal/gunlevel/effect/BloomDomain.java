package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.util.ChipHints;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 苏生领域（绽放反应）：开在触发反应的玩家脚下并跟着他走，领域内的全体玩家获得生命恢复。
 * 单独一张表而不进 CombatState：领域的对象是玩家自己，而玩家不参与元素附着。
 * 装了「净化领域」芯片时，这片领域还额外记一本血量账（见 {@link #trackRecord}），
 * 按记录值点亮三档强化 —— 领域的存续时间是这三档唯一的存在条件。
 */
public final class BloomDomain {
	/** 恢复效果比领域多留的时间（tick），领域消失时不会立刻断恢复 */
	private static final int LINGER_TICKS = 20;
	/** 每 tick 沿边界撒一圈粒子 */
	private static final int RING_PARTICLES = 16;

	private static final class Domain {
		final ServerLevel world;
		final UUID ownerId;
		Vec3 pos;
		long until;
		/**
		 * 已经攒到第几级生命恢复：一次绽放 +1，封顶 {@code bloomRegenMaxLevel}。
		 * 层数握在领域上、只在 {@link #spawn} 里加，所以每 tick 的续期不会把它刷上天。
		 */
		int regenLevels = 1;
		/** 触发这一次绽放的枪上有没有「净化领域」；一旦点亮就保持（同一把枪拆芯片不影响已存在的领域） */
		boolean purify;
		/** 血量变动记录值（回血与掉血都按绝对值进账，封顶 chipPurifyRecordCap） */
		double record;
		/** 上一 tick 采样到的圈内玩家血量：只在「持续在圈内」期间累计差值，出圈就删掉这条 */
		final Map<UUID, Float> lastHealth = new ConcurrentHashMap<>();
		/** 已经为他清过负面效果的玩家（进圈/达标那一刻清一次，之后靠 addStatusEffect 的注入挡住新的） */
		final Set<UUID> cleansed = ConcurrentHashMap.newKeySet();
		/** 新星只炸一次：记录值第一次跨过 chipPurifyNovaAt 那一 tick 放掉 */
		boolean novaFired;
		/**
		 * 触发提示只飘一次：这片领域存在期间净化档位的每次变化都不再重复报，
		 * 等领域到期、下一次绽放重新建一片时才重新飘（用户口径「效果消失后再触发才会再跳」）。
		 */
		boolean hinted;
		/** 缠藤誓约的半径加成（格）；没有这颗芯片的绽放为 0 */
		double radiusBonus;

		Domain(ServerLevel world, UUID ownerId, Vec3 pos, long until) {
			this.world = world;
			this.ownerId = ownerId;
			this.pos = pos;
			this.until = until;
		}
	}

	private static final ConcurrentHashMap<UUID, Domain> DOMAINS = new ConcurrentHashMap<>();

	private BloomDomain() {
	}

	/**
	 * 绽放触发：没有就开一片，已有的话把结束时间往后推、生命恢复再叠一级（封顶 bloomRegenMaxLevel）。
	 * 层数按「绽放打了几次」算，不按「领域重开了几次」算 ——
	 * 后者的话加了持续时间的芯片（净化领域 +2 秒）会让领域一直不断气，把叠层吃掉，怎么绽放都只有一级。
	 * 枪上装了净化领域时，这一发给的时间再多 {@code chipPurifyExtraSeconds} 秒，
	 * 并且这片领域从此开始记血量账（后续用别的枪再绽放不会把它关掉）。
	 */
	public static void spawn(ServerLevel world, LivingEntity owner, EnumSet<Chip> chips,
			double radiusBonus) {
		if (world == null || owner == null) {
			return;
		}
		boolean purify = chips != null && chips.contains(Chip.PURIFY_DOMAIN);
		double seconds = GunLevelMod.CONFIG.bloomSeconds
				+ (purify ? GunLevelMod.CONFIG.chipPurifyExtraSeconds : 0.0);
		Vec3 feet = owner.position();
		long until = world.getGameTime() + ticks(seconds);
		Domain domain = DOMAINS.compute(owner.getUUID(), (id, old) -> {
			if (old == null) {
				return new Domain(world, id, feet, until);
			}
			old.pos = feet;
			old.until = Math.max(old.until, until);
			old.regenLevels = Math.min(GunLevelMod.CONFIG.bloomRegenMaxLevel, old.regenLevels + 1);
			old.radiusBonus = radiusBonus;
			return old;
		});
		domain.radiusBonus = radiusBonus;
		if (purify) {
			domain.purify = true;
			if (!domain.hinted) {
				// 一片领域只报一次：后续续期、达标、新星都不再重复飘这行小字
				domain.hinted = true;
				ChipHints.show(owner, Chip.PURIFY_DOMAIN);
			}
		}
		grant(domain, world.getGameTime());
	}

	public static void tick(MinecraftServer server) {
		if (DOMAINS.isEmpty()) {
			return;
		}
		DOMAINS.forEach((ownerId, domain) -> {
			long t = domain.world.getGameTime();
			if (t > domain.until) {
				DOMAINS.remove(ownerId, domain);
				return;
			}
			Entity owner = domain.world.getEntity(ownerId);
			if (owner != null && owner.isAlive()) {
				domain.pos = owner.position();
			}
			grant(domain, t);
			drawRing(domain);
			if (domain.purify && owner instanceof LivingEntity livingOwner) {
				trackRecord(domain, livingOwner);
			}
		});
	}

	/**
	 * 领域内玩家：把生命恢复抬到这片领域攒到的等级，只抬不降、封顶 {@code bloomRegenMaxLevel}。
	 * 每 tick 写一次是安全的（javap 实证）：原版同类 buff 合并对 duration 和 amplifier 都取 max，
	 * 所以既截不短别人给的时长，也压不下别人给的等级。
	 */
	private static void grant(Domain domain, long now) {
		int remaining = (int) Math.max(1L, domain.until - now) + LINGER_TICKS;
		int targetAmplifier = Math.min(domain.regenLevels - 1,
				Math.max(0, GunLevelMod.CONFIG.bloomRegenMaxLevel - 1));
		for (Player player : domain.world.players()) {
			if (!player.isAlive() || !inside(domain, player)) {
				continue;
			}
			MobEffectInstance current = player.getEffect(MobEffects.REGENERATION);
			int amplifier = current == null ? targetAmplifier : Math.max(current.getAmplifier(), targetAmplifier);
			int duration = current == null ? remaining : Math.max(remaining, current.getDuration());
			if (current != null && duration == current.getDuration() && amplifier == current.getAmplifier()) {
				continue;
			}
			player.addEffect(new MobEffectInstance(
					MobEffects.REGENERATION, duration, amplifier));
		}
	}

	private static boolean inside(Domain domain, Entity entity) {
		double r = GunLevelMod.CONFIG.bloomRadius + domain.radiusBonus;
		Vec3 center = domain.pos.add(0.0, 1.0, 0.0);
		return entity.distanceToSqr(center.x(), center.y(), center.z()) <= r * r;
	}

	/**
	 * 净化领域的血量账：每 tick 给圈内玩家各采一次血量，回血和掉血都按绝对值进账（用户口径），
	 * 封顶 {@code chipPurifyRecordCap}。只算「持续在圈内」那段时间 —— 出圈的人从采样表里删掉，
	 * 再进来时先种一个新基线，免得把出圈期间的变化也算进来。
	 * 记录值只涨不跌，所以 40/60 两档天然就是「点亮后保持」；而领域一到期这条记录连同档位一起消失。
	 */
	private static void trackRecord(Domain domain, LivingEntity owner) {
		List<Player> present = new ArrayList<>();
		Set<UUID> presentIds = new HashSet<>();
		double delta = 0.0;
		for (Player player : domain.world.players()) {
			if (!player.isAlive() || !inside(domain, player)) {
				continue;
			}
			present.add(player);
			presentIds.add(player.getUUID());
			Float previous = domain.lastHealth.put(player.getUUID(), player.getHealth());
			if (previous != null) {
				delta += Math.abs(player.getHealth() - previous);
			}
		}
		domain.lastHealth.keySet().removeIf(id -> !presentIds.contains(id));
		domain.cleansed.removeIf(id -> !presentIds.contains(id));
		domain.record = Math.min(GunLevelMod.CONFIG.chipPurifyRecordCap, domain.record + delta);
		if (!domain.novaFired && domain.record >= GunLevelMod.CONFIG.chipPurifyNovaAt) {
			domain.novaFired = true;
			// 新星不再单独飘提示：净化领域的行小字一片领域只报一次（见 spawn 里的 hinted 门控）
			fireNova(domain, owner);
		}
		if (domain.record >= GunLevelMod.CONFIG.chipPurifyCleanseAt) {
			for (Player player : present) {
				// 达标那一刻、以及之后每个新进来的人：先清掉身上已有的负面效果
				if (domain.cleansed.add(player.getUUID())) {
					cleanse(player);
				}
			}
		}
	}

	/** 新星：以领域圆心为球心，对半径内全部存活生物（玩家除外）来一发固定伤害 */
	private static void fireNova(Domain domain, LivingEntity owner) {
		double r = GunLevelMod.CONFIG.chipPurifyNovaRadius;
		Vec3 center = domain.pos.add(0.0, 1.0, 0.0);
		for (Entity near : domain.world.getEntities(owner, new AABB(center, center).inflate(r))) {
			if (near instanceof Player || !(near instanceof LivingEntity victim)
					|| !victim.isAlive()) {
				continue;
			}
			CombatState.applyFixedDamage(domain.world, victim, owner,
					(float) GunLevelMod.CONFIG.chipPurifyNovaDamage);
		}
		domain.world.sendParticles(ParticleTypes.END_ROD, center.x(), center.y(), center.z(),
				60, r, r * 0.5, r, 0.02);
	}

	/** 摘掉身上所有 HARMFUL 类别的效果（正面与中立的不动） */
	private static void cleanse(Player player) {
		for (MobEffectInstance instance : new ArrayList<>(player.getActiveEffects())) {
			if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
				player.removeEffect(instance.getEffect());
			}
		}
	}

	/**
	 * 40 档的附加伤害：攻击者此刻站在某个已达标的苏生领域里时，这一次攻击额外加
	 * {@code 系数 × 永恒改装等级 × 该领域当前记录值} 点伤害（点数档位，不吃暴击也不吃等级倍率）。
	 * 被多片领域同时覆盖时取最大的那颗枪伤害——记录值高的那片赢。
	 */
	public static double purifyAttackBonus(LivingEntity attacker, int level) {
		if (level <= 0 || !(attacker instanceof Player player)) {
			return 0.0;
		}
		double bonusAt = GunLevelMod.CONFIG.chipPurifyBonusAt;
		double best = 0.0;
		for (Domain domain : DOMAINS.values()) {
			if (!domain.purify || domain.record < bonusAt || domain.world != player.level()
					|| !inside(domain, player)) {
				continue;
			}
			best = Math.max(best, GunLevelMod.CONFIG.chipPurifyDamagePerRecord
					* level * domain.record);
		}
		return best;
	}

	/** 60 档的免疫：这个玩家此刻是否站在已点亮免控档的苏生领域里（只有服务端有领域表，客户端恒 false） */
	public static boolean isDebuffImmune(LivingEntity entity) {
		if (!(entity instanceof Player player) || player.level().isClientSide) {
			return false;
		}
		double cleanseAt = GunLevelMod.CONFIG.chipPurifyCleanseAt;
		for (Domain domain : DOMAINS.values()) {
			if (domain.purify && domain.record >= cleanseAt && domain.world == player.level()
					&& inside(domain, player)) {
				return true;
			}
		}
		return false;
	}

	private static void drawRing(Domain domain) {
		double r = GunLevelMod.CONFIG.bloomRadius + domain.radiusBonus;
		Vec3 center = domain.pos.add(0.0, 0.1, 0.0);
		for (int i = 0; i < RING_PARTICLES; i++) {
			double angle = i * (Math.PI * 2 / RING_PARTICLES) + domain.world.random.nextDouble() * 0.2;
			domain.world.sendParticles(ParticleTypes.HAPPY_VILLAGER,
					center.x() + Math.cos(angle) * r, center.y(), center.z() + Math.sin(angle) * r,
					1, 0.0, 0.05, 0.0, 0.0);
		}
	}

	private static long ticks(double seconds) {
		return Math.max(1, Math.round(seconds * 20));
	}

	public static int activeCount() {
		return DOMAINS.size();
	}

	public static void clear() {
		DOMAINS.clear();
	}
}
