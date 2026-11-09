package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.item.GunCoreData;
import com.eternal.gunlevel.item.GunExtremeData;
import com.eternal.gunlevel.net.GunLevelNetwork;
import com.eternal.gunlevel.util.ChipHints;
import com.eternal.gunlevel.util.TaczCompat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 极限火力：主动定点射击状态、连击层数和子弹回合记账。 */
public final class ExtremeFireState {
	private static final UUID SPEED_MODIFIER_ID = UUID.nameUUIDFromBytes(
			"tacz_gun_level:extreme_fire_speed".getBytes(StandardCharsets.UTF_8));
	private static final String SPEED_MODIFIER_NAME = "tacz_gun_level_extreme_fire";
	private static final int MAX_TRACKED_ROUNDS = 1024;

	private static final class Mode {
		boolean active;
		int stacks;
		int level;
		ItemStack gun;
	}

	private static final class Round {
		final UUID shooter;
		final int level;
		final WeakReference<Entity> bullet;
		boolean active;
		boolean hit;
		boolean explosionHit;

		Round(Entity bullet, UUID shooter, int level) {
			this.bullet = new WeakReference<>(bullet);
			this.shooter = shooter;
			this.level = level;
			this.active = true;
		}
	}

	private static final Map<UUID, Mode> MODES = new ConcurrentHashMap<>();
	private static final Map<UUID, Round> ROUNDS = Collections.synchronizedMap(
			new LinkedHashMap<UUID, Round>(16, 0.75F, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<UUID, Round> eldest) {
					return size() > MAX_TRACKED_ROUNDS;
				}
			});
	private static final Map<UUID, Boolean> CLIENT_ACTIVE = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> CLIENT_STACKS = new ConcurrentHashMap<>();
	private static MinecraftServer server;

	private ExtremeFireState() {
	}

	public static boolean carries(ItemStack gun) {
		return GunChipData.holdsChip(gun, Chip.EXTREME_FIRE);
	}

	public static boolean canActivate(ItemStack gun) {
		return carries(gun) && GunCoreData.get(gun) == Element.FIRE;
	}

	public static boolean releaseIfActive(ServerPlayer player) {
		Mode mode = player == null ? null : MODES.get(player.getUUID());
		if (mode != null && mode.active) {
			release(player);
			return true;
		}
		return false;
	}

	public static void toggle(ServerPlayer player) {
		ItemStack gun = player.getMainHandItem();
		if (!canActivate(gun)) {
			return;
		}
		Mode mode = MODES.computeIfAbsent(player.getUUID(), id -> new Mode());
		if (mode.active) {
			release(player);
			return;
		}
		mode.active = true;
		mode.stacks = 0;
		mode.level = Math.max(1, LevelScaling.levelOfStack(gun));
		mode.gun = gun;
		// 定点弹匣必须立刻只剩 1 发；否则启用前保留的整匣会继续按普通枪打法打空。
		int current = TaczCompat.currentAmmoCount(gun);
		if (current > 1) {
			FlameRoarState.refundToReserve(player, gun, current - 1);
			TaczCompat.setAmmoCount(gun, 1);
		}
		GunExtremeData.set(gun, true);
		player.getInventory().setChanged();
		refillOneFromReserve(player, gun);
		applySpeed(player, true);
		sync(player);
		ChipHints.show(player, Chip.EXTREME_FIRE);
		if (player.level() instanceof ServerLevel world) {
			world.sendParticles(ParticleTypes.FLAME, player.getX(),
					player.getY() + player.getBbHeight() * 0.4, player.getZ(),
					26, 0.55, 0.25, 0.55, 0.03);
			world.sendParticles(ParticleTypes.SMOKE, player.getX(),
					player.getY() + player.getBbHeight() * 0.2, player.getZ(),
					10, 0.45, 0.15, 0.45, 0.02);
		}
	}

	public static void release(ServerPlayer player) {
		Mode mode = player == null ? null : MODES.get(player.getUUID());
		if (mode == null || !mode.active) {
			return;
		}
		mode.active = false;
		mode.stacks = 0;
		if (mode.gun != null) {
			GunExtremeData.set(mode.gun, false);
		}
		mode.gun = null;
		player.getInventory().setChanged();
		UUID playerId = player.getUUID();
		synchronized (ROUNDS) {
			for (Round round : ROUNDS.values()) {
				if (playerId.equals(round.shooter)) {
					round.active = false;
				}
			}
		}
		applySpeed(player, false);
		sync(player);
	}

	public static boolean isActive(LivingEntity entity) {
		if (entity == null) {
			return false;
		}
		if (entity.level().isClientSide) {
			return GunExtremeData.isOn(entity.getMainHandItem());
		}
		if (entity instanceof ServerPlayer player) {
			Mode mode = MODES.get(player.getUUID());
			return mode != null && mode.active && canActivate(player.getMainHandItem());
		}
		return GunExtremeData.isOn(entity.getMainHandItem());
	}

	public static int stacks(LivingEntity entity) {
		if (entity == null) {
			return 0;
		}
		if (entity.level().isClientSide) {
			return CLIENT_STACKS.getOrDefault(entity.getUUID(), 0);
		}
		Mode mode = MODES.get(entity.getUUID());
		return mode == null || !mode.active ? 0 : mode.stacks;
	}

	public static double totalFireRateBonus(LivingEntity entity) {
		if (!isActive(entity)) {
			return 0.0;
		}
		int level = LevelScaling.levelOfStack(entity.getMainHandItem());
		double levelRate = GunLevelMod.CONFIG.chipExtremeFireRate;
		double stacksRate = GunLevelMod.CONFIG.chipExtremeFireRatePerStackPerLevel * level * stacks(entity);
		return levelRate + Math.min(GunLevelMod.CONFIG.chipExtremeFireRateCap, stacksRate);
	}

	public static double intervalScale(LivingEntity entity) {
		double bonus = totalFireRateBonus(entity);
		return bonus <= 0 ? 1.0 : 1.0 / (1.0 + bonus);
	}

	public static double explosionRadiusScale(ServerPlayer player) {
		Mode mode = player == null ? null : MODES.get(player.getUUID());
		if (mode == null || !mode.active) {
			return 1.0;
		}
		double bonus = GunLevelMod.CONFIG.chipExtremeExplosionRadiusPerStackPerLevel
				* mode.level * mode.stacks;
		return 1.0 + Math.min(GunLevelMod.CONFIG.chipExtremeExplosionRadiusCap, bonus);
	}

	public static void onBulletSpawn(Entity bullet, ServerPlayer player, int level) {
		if (bullet == null || player == null || player.level().isClientSide) {
			return;
		}
		Mode mode = MODES.get(player.getUUID());
		if (mode != null && mode.active) {
			ROUNDS.put(bullet.getUUID(), new Round(bullet, player.getUUID(), Math.max(1, level)));
		}
	}

	/**
	 * TACZ 已先扣掉定点弹匣里唯一的一发；从备弹补回 1 发。霰弹等同一次开火生成多颗子弹时，
	 * 第一颗补满后剩余子弹看到弹匣已经恢复，不再重复扣备弹。
	 */
	public static void refillOneFromReserve(ServerPlayer player, ItemStack gun) {
		if (player == null || gun == null || gun.isEmpty()) {
			return;
		}
		if (TaczCompat.currentAmmoCount(gun) >= 1) {
			return;
		}
		if (FlameRoarState.reserve(player, gun) <= 0) {
			return;
		}
		FlameRoarState.takeFromReserve(player, gun, 1);
		TaczCompat.setAmmoCount(gun, 1);
	}

	public static boolean countDirectHit(Entity bullet, ServerPlayer shooter) {
		if (bullet == null || shooter == null) {
			return false;
		}
		Round round = ROUNDS.get(bullet.getUUID());
		if (round == null || !round.active) {
			return false;
		}
		round.hit = true;
		increment(round.shooter);
		return true;
	}

	public static void countExplosionHit(Entity bullet) {
		if (bullet == null) {
			return;
		}
		Round round = ROUNDS.get(bullet.getUUID());
		if (round == null || !round.active || round.explosionHit) {
			return;
		}
		round.explosionHit = true;
		round.hit = true;
		increment(round.shooter);
	}

	public static void onBulletMiss(Entity bullet) {
		if (bullet == null) {
			return;
		}
		Round round = ROUNDS.remove(bullet.getUUID());
		if (round != null && round.active && !round.hit) {
			resetStacks(round.shooter);
		}
	}

	public static void tick(MinecraftServer minecraftServer) {
		server = minecraftServer;
		synchronized (ROUNDS) {
			Iterator<Map.Entry<UUID, Round>> it = ROUNDS.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Round> entry = it.next();
				Round round = entry.getValue();
				Entity bullet = round.bullet.get();
				if (bullet == null || bullet.isRemoved()) {
					if (round.active && !round.hit) {
						resetStacks(round.shooter);
					}
					it.remove();
				}
			}
		}
		if (MODES.isEmpty() && ROUNDS.isEmpty()) {
			return;
		}
		for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
			Mode mode = MODES.get(player.getUUID());
			if (mode == null || !mode.active) {
				continue;
			}
			ItemStack gun = player.getMainHandItem();
			if (!player.isAlive() || mode.gun != gun || !canActivate(gun)) {
				release(player);
				GunExtremeData.set(gun, false);
				player.getInventory().setChanged();
			}
		}
		MODES.keySet().removeIf(id -> minecraftServer.getPlayerList().getPlayer(id) == null);
	}

	public static void clearClient() {
		CLIENT_ACTIVE.clear();
		CLIENT_STACKS.clear();
	}

	public static void updateClient(UUID player, boolean active, int stacks) {
		if (player == null) {
			return;
		}
		if (active) {
			CLIENT_ACTIVE.put(player, true);
			CLIENT_STACKS.put(player, Math.max(0, stacks));
		} else {
			CLIENT_ACTIVE.remove(player);
			CLIENT_STACKS.remove(player);
		}
	}

	private static void increment(UUID playerId) {
		Mode mode = MODES.get(playerId);
		if (mode != null && mode.active) {
			mode.stacks++;
			ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
			if (player != null) {
				sync(player);
			}
		}
	}

	private static void resetStacks(UUID playerId) {
		Mode mode = MODES.get(playerId);
		if (mode != null && mode.active) {
			mode.stacks = 0;
			ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
			if (player != null) {
				sync(player);
			}
		}
	}

	private static void sync(ServerPlayer player) {
		Mode mode = MODES.get(player.getUUID());
		boolean active = mode != null && mode.active;
		int stacks = active ? mode.stacks : 0;
		GunLevelNetwork.sendExtremeFire(player, active, stacks);
	}

	private static void applySpeed(LivingEntity entity, boolean on) {
		AttributeInstance instance = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (instance == null) {
			return;
		}
		instance.removeModifier(SPEED_MODIFIER_ID);
		if (on) {
			instance.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID,
					SPEED_MODIFIER_NAME, -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	/** 客户端收到开关后挂/摘同一份移速修正。 */
	public static void applySpeedClient(Player player, boolean on) {
		if (player != null) {
			applySpeed(player, on);
		}
	}

	public static void clear() {
		MODES.clear();
		ROUNDS.clear();
	}
}
