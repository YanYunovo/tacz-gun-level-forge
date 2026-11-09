package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.net.GunLevelNetwork;
import com.eternal.gunlevel.util.ChipHints;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 战术目镜：分析点数、阈值加成、数据倾泻主动与追踪目标选择。服务端记账，客户端只读镜像。 */
public final class GogglesState {
	private static final UUID SPEED_ID = UUID.nameUUIDFromBytes(
			"tacz_gun_level:goggles_speed".getBytes(StandardCharsets.UTF_8));
	private static final String SPEED_NAME = "tacz_gun_level_goggles_speed";

	private static final class Ledger {
		double points;
		boolean active;
		boolean speedOn;
		boolean announced;
		WeakReference<LivingEntity> lastTarget = new WeakReference<>(null);
		WeakReference<LivingEntity> priorityTarget = new WeakReference<>(null);
		int syncedPoints = -1;
		boolean syncedActive;
		boolean syncedSpeed;
	}

	private static final Map<UUID, Ledger> LEDGERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> CLIENT_POINTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> CLIENT_ACTIVE = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> CLIENT_SPEED = new ConcurrentHashMap<>();
	private static MinecraftServer server;

	private GogglesState() {
	}

	private static Ledger ledger(ServerPlayer player) {
		return LEDGERS.computeIfAbsent(player.getUUID(), id -> new Ledger());
	}

	public static boolean carries(ItemStack gun) {
		return !gun.isEmpty() && GunChipData.get(gun).contains(Chip.GOGGLES);
	}

	public static void onHit(LivingEntity shooter, LivingEntity target, boolean headshot) {
		if (!(shooter instanceof ServerPlayer player) || shooter.level().isClientSide) {
			return;
		}
		Ledger ledger = ledger(player);
		double before = ledger.points;
		ledger.points = Math.min(GunLevelMod.CONFIG.chipGogglesMaxPoints,
				ledger.points + (headshot ? 2.0 : 1.0));
		if (target != null && target != shooter && target.isAlive()) {
			ledger.lastTarget = new WeakReference<>(target);
		}
		if (before < GunLevelMod.CONFIG.chipGogglesActiveThreshold
				&& ledger.points >= GunLevelMod.CONFIG.chipGogglesActiveThreshold
				&& !ledger.active && !ledger.announced) {
			ledger.announced = true;
			ChipHints.show(player, Chip.GOGGLES, ChipHints.SKILL_READY);
		}
		if (ledger.points < GunLevelMod.CONFIG.chipGogglesActiveThreshold) {
			ledger.announced = false;
		}
		sync(player);
	}

	public static double headBonus(LivingEntity shooter) {
		if (shooter == null) {
			return 0.0;
		}
		if (isActive(shooter) || points(shooter) >= GunLevelMod.CONFIG.chipGogglesHeadThreshold) {
			return GunLevelMod.CONFIG.chipGogglesHeadDamage;
		}
		return 0.0;
	}

	public static boolean speedOn(LivingEntity shooter) {
		return shooter != null && (isActive(shooter)
				|| points(shooter) >= GunLevelMod.CONFIG.chipGogglesSpeedThreshold);
	}

	public static int points(LivingEntity shooter) {
		if (shooter == null) {
			return 0;
		}
		if (shooter.level().isClientSide) {
			return CLIENT_POINTS.getOrDefault(shooter.getUUID(), 0);
		}
		Ledger ledger = LEDGERS.get(shooter.getUUID());
		return ledger == null ? 0 : (int) Math.floor(ledger.points);
	}

	public static boolean isActive(LivingEntity shooter) {
		if (shooter == null) {
			return false;
		}
		if (shooter.level().isClientSide) {
			return CLIENT_ACTIVE.getOrDefault(shooter.getUUID(), false);
		}
		Ledger ledger = LEDGERS.get(shooter.getUUID());
		return ledger != null && ledger.active;
	}

	public static boolean isClientActive(UUID player) {
		return player != null && CLIENT_ACTIVE.getOrDefault(player, false);
	}

	public static int clientPoints(UUID player) {
		return player == null ? 0 : CLIENT_POINTS.getOrDefault(player, 0);
	}

	public static LivingEntity acquireTarget(ServerPlayer player) {
		Ledger ledger = LEDGERS.get(player.getUUID());
		double range = GunLevelMod.CONFIG.chipGogglesTrackRange;
		double rangeSq = range * range;
		if (ledger != null) {
			LivingEntity priority = ledger.priorityTarget.get();
			if (priority != null && priority.isAlive() && !(priority instanceof Player)
					&& priority.distanceToSqr(player) <= rangeSq
					&& hasLineOfSight(player, priority)) {
				return priority;
			}
		}
		if (!(player.level() instanceof ServerLevel world)) {
			return null;
		}
		LivingEntity best = null;
		double bestDist = rangeSq;
		AABB box = player.getBoundingBox().inflate(range);
		for (Entity entity : world.getEntities(player, box,
				e -> e instanceof LivingEntity && !(e instanceof Player)
					&& !(e instanceof ArmorStand) && e.isAlive())) {
			LivingEntity living = (LivingEntity) entity;
			double dist = living.distanceToSqr(player);
			if (dist < bestDist && hasLineOfSight(player, living)) {
				bestDist = dist;
				best = living;
			}
		}
		return best;
	}

	public static boolean hasLineOfSight(ServerPlayer player, LivingEntity target) {
		return player.hasLineOfSight(target);
	}

	public static boolean isAimingAt(ServerPlayer player, LivingEntity target) {
		Vec3 look = player.getLookAngle();
		Vec3 toTarget = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0)
				.subtract(player.getEyePosition()).normalize();
		return look.dot(toTarget) > 0.85;
	}

	public static double ammoSaveChance(ServerPlayer player, ItemStack gun, int level, long now) {
		double base = GunLevelMod.CONFIG.chipGogglesAmmoSavePerLevel * level;
		double fireRate = BurstShotState.totalFireRateBonus(player, gun, level, now);
		double chance = base + fireRate * 100.0 * GunLevelMod.CONFIG.chipGogglesAmmoSavePerFireRate;
		return Math.min(GunLevelMod.CONFIG.chipGogglesAmmoSaveCap, chance);
	}

	public static boolean releaseIfActive(ServerPlayer player) {
		Ledger ledger = player == null ? null : LEDGERS.get(player.getUUID());
		if (ledger != null && ledger.active) {
			release(player);
			return true;
		}
		return false;
	}

	public static void toggle(ServerPlayer player) {
		ItemStack gun = player.getMainHandItem();
		if (!carries(gun)) {
			return;
		}
		Ledger ledger = ledger(player);
		if (ledger.active) {
			release(player);
			return;
		}
		if (ledger.points < GunLevelMod.CONFIG.chipGogglesActiveThreshold) {
			return;
		}
		ledger.active = true;
		ledger.announced = true;
		ledger.priorityTarget = ledger.lastTarget;
		applySpeed(player, speedOn(player));
		sync(player);
		ChipHints.show(player, Chip.GOGGLES);
	}

	public static void release(ServerPlayer player) {
		Ledger ledger = player == null ? null : LEDGERS.get(player.getUUID());
		if (ledger == null || !ledger.active) {
			return;
		}
		ledger.active = false;
		applySpeed(player, speedOn(player));
		sync(player);
	}

	public static void tick(MinecraftServer minecraftServer) {
		server = minecraftServer;
		for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
			Ledger ledger = LEDGERS.get(player.getUUID());
			if (ledger == null) {
				continue;
			}
			if (ledger.active) {
				double drain = ledger.points >= GunLevelMod.CONFIG.chipGogglesDrainThreshold
						? GunLevelMod.CONFIG.chipGogglesDrainPerSecond
						: GunLevelMod.CONFIG.chipGogglesDrainLow;
				ledger.points -= drain / 20.0;
				if (ledger.points <= 0.0) {
					ledger.points = 0.0;
					release(player);
				}
			}
			ItemStack gun = player.getMainHandItem();
			if (!player.isAlive() || !carries(gun)) {
				if (ledger.active) {
					release(player);
				}
			}
			boolean speed = speedOn(player);
			if (speed != ledger.speedOn) {
				ledger.speedOn = speed;
				applySpeed(player, speed);
			}
			sync(player);
		}
		LEDGERS.keySet().removeIf(id -> minecraftServer.getPlayerList().getPlayer(id) == null);
	}

	private static void sync(ServerPlayer player) {
		Ledger ledger = LEDGERS.get(player.getUUID());
		if (ledger == null) {
			return;
		}
		int points = (int) Math.floor(ledger.points);
		boolean speed = speedOn(player);
		if (ledger.syncedPoints == points && ledger.syncedActive == ledger.active
				&& ledger.syncedSpeed == speed) {
			return;
		}
		ledger.syncedPoints = points;
		ledger.syncedActive = ledger.active;
		ledger.syncedSpeed = speed;
		GunLevelNetwork.sendGoggles(player, points, ledger.active, speed);
	}

	private static void applySpeed(LivingEntity entity, boolean on) {
		AttributeInstance instance = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (instance == null) {
			return;
		}
		instance.removeModifier(SPEED_ID);
		if (on) {
			instance.addTransientModifier(new AttributeModifier(SPEED_ID, SPEED_NAME,
					GunLevelMod.CONFIG.chipGogglesSpeed,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	public static void applySpeedClient(Player player, boolean on) {
		if (player != null) {
			applySpeed(player, on);
		}
	}

	public static void updateClient(UUID player, int points, boolean active, boolean speed) {
		if (player == null) {
			return;
		}
		CLIENT_POINTS.put(player, Math.max(0, points));
		if (active) {
			CLIENT_ACTIVE.put(player, true);
		} else {
			CLIENT_ACTIVE.remove(player);
		}
		if (speed) {
			CLIENT_SPEED.put(player, true);
		} else {
			CLIENT_SPEED.remove(player);
		}
	}

	public static void clearClient() {
		CLIENT_POINTS.clear();
		CLIENT_ACTIVE.clear();
		CLIENT_SPEED.clear();
	}

	public static void clear() {
		LEDGERS.clear();
	}
}