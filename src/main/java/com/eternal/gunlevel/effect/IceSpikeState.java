package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 刺骨冰锥：冰雾减速、刺骨寒意独立层与三层最终伤害触发。只在服务端记账。 */
public final class IceSpikeState {
	private static final UUID SLOW_ID = UUID.nameUUIDFromBytes(
			"tacz_gun_level:ice_spike_slow".getBytes(StandardCharsets.UTF_8));
	private static final String SLOW_NAME = "tacz_gun_level_ice_spike_slow";
	private static final int FOG_APPLY_INTERVAL = 5;
	private static final int MAX_FOGS = 1024;

	private static final class Layer {
		final UUID shooterId;
		final WeakReference<LivingEntity> shooter;
		final float totalDamage;
		final int totalPulses;
		float remaining;
		int pulsesLeft;
		long nextTickAt;
		long endAt;

		Layer(UUID shooterId, LivingEntity shooter, float totalDamage, int totalPulses,
				long now, long endAt) {
			this.shooterId = shooterId;
			this.shooter = new WeakReference<>(shooter);
			this.totalDamage = totalDamage;
			this.remaining = totalDamage;
			this.totalPulses = totalPulses;
			this.pulsesLeft = totalPulses;
			this.nextTickAt = now + 20L;
			this.endAt = endAt;
		}
	}

	private static final class TargetState {
		final WeakReference<LivingEntity> target;
		final List<Layer> layers = new ArrayList<>();
		boolean bonusConsumed;

		TargetState(LivingEntity target) {
			this.target = new WeakReference<>(target);
		}
	}

	private static final class FogZone {
		final WeakReference<ServerLevel> world;
		final UUID shooterId;
		final double x;
		final double y;
		final double z;
		final double radius;
		final long endAt;
		long nextApplyAt;

		FogZone(ServerLevel world, Vec3 pos, UUID shooterId, double radius,
				long now, long endAt) {
			this.world = new WeakReference<>(world);
			this.shooterId = shooterId;
			this.x = pos.x;
			this.y = pos.y;
			this.z = pos.z;
			this.radius = radius;
			this.endAt = endAt;
			this.nextApplyAt = now;
		}
	}

	private static final class Slow {
		final WeakReference<LivingEntity> target;
		long until;

		Slow(LivingEntity target) {
			this.target = new WeakReference<>(target);
		}
	}

	private static final class TrailPoint {
		final Vec3 pos;
		final long at;

		TrailPoint(Vec3 pos, long at) {
			this.pos = pos;
			this.at = at;
		}
	}

	private static final Map<UUID, TargetState> TARGETS = new ConcurrentHashMap<>();
	private static final List<FogZone> FOGS = Collections.synchronizedList(new ArrayList<>());
	private static final Map<UUID, Slow> SLOWS = new ConcurrentHashMap<>();
	private static final Map<UUID, TrailPoint> TRAILS = new ConcurrentHashMap<>();

	private IceSpikeState() {
	}

	private static int maxStacks() {
		return Math.max(1, GunLevelMod.CONFIG.chipIceSpikeMaxStacks);
	}

	private static int prune(TargetState state, long now) {
		Iterator<Layer> it = state.layers.iterator();
		while (it.hasNext()) {
			Layer layer = it.next();
			if (layer.pulsesLeft <= 0) {
				it.remove();
			}
		}
		if (state.layers.size() < maxStacks()) {
			state.bonusConsumed = false;
		}
		return state.layers.size();
	}

	private static TargetState state(LivingEntity target) {
		return TARGETS.computeIfAbsent(target.getUUID(), id -> new TargetState(target));
	}

	/** 三层且本次满层后尚未触发过：消耗一次触发资格，本发最终伤害 ×2。 */
	public static boolean triggerBonus(LivingEntity target, long now) {
		if (target == null || !target.isAlive() || target instanceof Player) {
			return false;
		}
		TargetState state = TARGETS.get(target.getUUID());
		if (state == null) {
			return false;
		}
		int stacks = prune(state, now);
		if (stacks < maxStacks() || state.bonusConsumed) {
			return false;
		}
		state.bonusConsumed = true;
		if (target.level() instanceof ServerLevel world) {
			world.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(),
					target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					18, 0.5, 0.6, 0.5, 0.02);
			world.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(),
					target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					10, 0.4, 0.5, 0.4, 0.1);
		}
		return true;
	}

	/** 命中叠一层刺骨寒意；满三层后不再叠，直到有一层自然消失。 */
	public static void onHit(LivingEntity target, LivingEntity shooter, float finalDamage, long now) {
		if (target == null || shooter == null || finalDamage <= 0
				|| !target.isAlive() || target instanceof Player) {
			return;
		}
		TargetState state = state(target);
		if (prune(state, now) >= maxStacks()) {
			return;
		}
		int pulses = Math.max(1, GunLevelMod.CONFIG.chipIceSpikeDotPulses);
		long endAt = now + Math.max(1L, Math.round(GunLevelMod.CONFIG.chipIceSpikeDotSeconds * 20.0D));
		state.layers.add(new Layer(shooter.getUUID(), shooter,
				finalDamage * (float) GunLevelMod.CONFIG.chipIceSpikeDotRatio, pulses, now, endAt));
		if (target.level() instanceof ServerLevel world) {
			world.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(),
					target.getY() + target.getBbHeight() * 0.7, target.getZ(),
					8, 0.35, 0.45, 0.35, 0.01);
		}
	}

	/** 子弹沿途留冰雾；由子弹 tick 按间隔调用。 */
	public static void onBulletTrail(Entity bullet, LivingEntity shooter, long now) {
		if (bullet == null || shooter == null || !(bullet.level() instanceof ServerLevel world)) {
			return;
		}
		Vec3 pos = bullet.position();
		FogZone zone = new FogZone(world, pos, shooter.getUUID(),
				Math.max(0.25, GunLevelMod.CONFIG.chipIceSpikeFogRadius), now,
				now + Math.max(1L, Math.round(GunLevelMod.CONFIG.chipIceSpikeFogSeconds * 20.0D)));
		if (FOGS.size() >= MAX_FOGS) {
			FOGS.remove(0);
		}
		FOGS.add(zone);

		TrailPoint previous = TRAILS.put(bullet.getUUID(), new TrailPoint(pos, now));
		spawnFogBurst(world, pos);
		if (previous != null && now - previous.at <= 6
				&& previous.pos.distanceToSqr(pos) > 0.15D) {
			spawnFogBurst(world, new Vec3(
					(previous.pos.x + pos.x) * 0.5D,
					(previous.pos.y + pos.y) * 0.5D,
					(previous.pos.z + pos.z) * 0.5D));
		}
		long cutoff = now - 100L;
		TRAILS.entrySet().removeIf(entry -> entry.getValue().at < cutoff);
	}

	private static void spawnFogBurst(ServerLevel world, Vec3 pos) {
		world.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 5,
				0.35, 0.12, 0.35, 0.005);
		world.sendParticles(ParticleTypes.WHITE_ASH, pos.x, pos.y, pos.z, 6,
				0.45, 0.18, 0.45, 0.005);
		world.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 4,
				0.35, 0.12, 0.35, 0.01);
	}

	public static void tick(MinecraftServer server) {
		tickTargets(server);
		tickFogs();
		tickSlows();
	}

	private static void tickTargets(MinecraftServer server) {
		Iterator<Map.Entry<UUID, TargetState>> it = TARGETS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, TargetState> entry = it.next();
			TargetState state = entry.getValue();
			LivingEntity target = state.target.get();
			if (target == null || target.isRemoved() || !target.isAlive()) {
				it.remove();
				continue;
			}
			long now = target.level().getGameTime();
			prune(state, now);
			Iterator<Layer> layers = state.layers.iterator();
			while (layers.hasNext()) {
				Layer layer = layers.next();
				while (layer.pulsesLeft > 0 && now >= layer.nextTickAt) {
					float amount = layer.pulsesLeft == 1
							? layer.remaining
							: layer.totalDamage / layer.totalPulses;
					amount = Math.min(amount, layer.remaining);
					layer.remaining -= amount;
					layer.pulsesLeft--;
					layer.nextTickAt += 20L;
					if (amount > 0) {
						LivingEntity shooter = resolveShooter(server, layer);
						CombatState.applyFixedDamage(target.level(), target, shooter, amount);
					}
					if (!target.isAlive()) {
						break;
					}
				}
				if (layer.pulsesLeft <= 0 || !target.isAlive()) {
					layers.remove();
				}
			}
			if (state.layers.isEmpty() || !target.isAlive()) {
				it.remove();
			}
		}
	}

	private static LivingEntity resolveShooter(MinecraftServer server, Layer layer) {
		if (layer.shooterId != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(layer.shooterId);
			if (player != null) {
				return player;
			}
		}
		LivingEntity cached = layer.shooter.get();
		return cached != null && !cached.isRemoved() ? cached : null;
	}

	private static void tickFogs() {
		Iterator<FogZone> it = FOGS.iterator();
		while (it.hasNext()) {
			FogZone zone = it.next();
			ServerLevel world = zone.world.get();
			if (world == null) {
				it.remove();
				continue;
			}
			long now = world.getGameTime();
			if (now >= zone.endAt) {
				it.remove();
				continue;
			}
			if (now < zone.nextApplyAt) {
				continue;
			}
			zone.nextApplyAt = now + FOG_APPLY_INTERVAL;
			double r = zone.radius;
			AABB box = new AABB(zone.x - r, zone.y - r, zone.z - r,
					zone.x + r, zone.y + r, zone.z + r);
			for (Entity entity : world.getEntities((Entity) null, box,
					e -> e instanceof LivingEntity living && living.isAlive()
							&& !(living instanceof Player))) {
				LivingEntity living = (LivingEntity) entity;
				if (living.distanceToSqr(zone.x, zone.y, zone.z) > r * r) {
					continue;
				}
				applySlow(living, now);
			}
			world.sendParticles(ParticleTypes.CLOUD, zone.x, zone.y, zone.z, 2,
					r * 0.3, 0.05, r * 0.3, 0.005);
			world.sendParticles(ParticleTypes.WHITE_ASH, zone.x, zone.y, zone.z, 2,
					r * 0.35, 0.08, r * 0.35, 0.003);
			world.sendParticles(ParticleTypes.SNOWFLAKE, zone.x, zone.y, zone.z, 1,
					r * 0.3, 0.05, r * 0.3, 0.005);
		}
	}

	private static void applySlow(LivingEntity target, long now) {
		Slow slow = SLOWS.computeIfAbsent(target.getUUID(), id -> new Slow(target));
		boolean wasActive = slow.until > now;
		slow.until = now + Math.max(1L,
				Math.round(GunLevelMod.CONFIG.chipIceSpikeSlowSeconds * 20.0D));
		if (!wasActive) {
			applySlowModifier(target, true);
		}
	}

	private static void applySlowModifier(LivingEntity target, boolean on) {
		AttributeInstance instance = target.getAttribute(Attributes.MOVEMENT_SPEED);
		if (instance == null) {
			return;
		}
		instance.removeModifier(SLOW_ID);
		if (on) {
			instance.addTransientModifier(new AttributeModifier(SLOW_ID, SLOW_NAME,
					-GunLevelMod.CONFIG.chipIceSpikeSlow,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	private static void tickSlows() {
		Iterator<Map.Entry<UUID, Slow>> it = SLOWS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Slow> entry = it.next();
			Slow slow = entry.getValue();
			LivingEntity target = slow.target.get();
			if (target == null || target.isRemoved() || !target.isAlive()
					|| target.level().getGameTime() >= slow.until) {
				if (target != null && !target.isRemoved()) {
					applySlowModifier(target, false);
				}
				it.remove();
			}
		}
	}

	public static void clear() {
		TARGETS.clear();
		FOGS.clear();
		SLOWS.clear();
		TRAILS.clear();
	}
}