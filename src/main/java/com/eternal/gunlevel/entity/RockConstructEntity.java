package com.eternal.gunlevel.entity;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 岩元素造物（战术芯片·凝晶召唤）：一只泥土皮肤的僵尸，自己走去把附近敌对生物的注意力吸走，
 * 并给圈内的玩家抗性提升。被攻击三次就崩解，存在期间不能再召唤第二只。
 */
public class RockConstructEntity extends Zombie {
	private static final String OWNER_KEY = "GunLevelSummonOwner";
	private static final String CHIPS_KEY = "GunLevelSummonChips";
	private static final String HITS_KEY = "GunLevelSummonHits";

	/** 与岩元素附着同款琥珀色尘埃 */
	private static final ParticleOptions DUST =
			new DustParticleOptions(new org.joml.Vector3f(0.92F, 0.76F, 0.28F), 1.2F);

	/** 光环与牵绳检查的执行间隔（tick）：远短于发放时长，圈里的 buff 不会闪断 */
	private static final int SUPPORT_INTERVAL = 10;

	/**
	 * 索敌的间隔（tick）。之所以自己索敌、不用原版的 ActiveTargetGoal：
	 * 它的 TargetPredicate 是 `createAttackable()`，里面 `respectsVisibility=true`，
	 * 也就是**隔墙/转角就选不出目标** —— 造物会干站着直到真的看见怪，这就是实测的「生成后呆滞」。
	 * 而且 goal 的 canStart 只在「服务端 tick + 实体 id」为偶数的那一半 tick 里评估（Mob.tickNewAi）。
	 * 它的活是把怪引过来，看不见也该往那个方向绕，所以按半径直取最近的目标。
	 */
	private static final int SEEK_INTERVAL = 5;

	/**
	 * 每只造物只按「主人」记一位：召唤时查这张表判断是否已存在，造物自己每 tick 把映射抢回来
	 * （区块重载后拿到的是新实例，靠这里补登记），所以不需要额外的计时清理。
	 */
	private static final ConcurrentHashMap<UUID, UUID> SUMMONS = new ConcurrentHashMap<>();

	private UUID ownerId;
	/** 召唤那一刻枪上装了几颗芯片：1 颗给抗性提升 I，2 颗给 II */
	private int chipCount = 1;
	private int hitsTaken;
	private long nextSupportAt;
	private long nextSeekAt;

	public RockConstructEntity(EntityType<RockConstructEntity> type, Level world) {
		super(type, world);
	}

	/** 结晶反应触发召唤；已有造物在场时返回 null（用户口径：存在期间无法重复召唤，这一次直接忽略） */
	public static RockConstructEntity summon(ServerLevel world, LivingEntity owner, int chips) {
		if (world == null || owner == null || owner.isRemoved()) {
			return null;
		}
		if (find(world, owner.getUUID()) != null) {
			return null;
		}
		RockConstructEntity construct = GunLevelMod.ROCK_CONSTRUCT.create(world);
		if (construct == null) {
			return null;
		}
		construct.ownerId = owner.getUUID();
		construct.chipCount = Math.max(1, chips);
		placeBesideOwner(world, owner, construct);
		construct.setPersistenceRequired();
		world.addFreshEntity(construct);
		SUMMONS.put(construct.ownerId, construct.getUUID());
		return construct;
	}

	/**
	 * 出生点挪到主人身前的落脚格上。原来直接放在玩家脚底，和玩家的碰撞箱重叠会被顶到玩家头上，
	 * 看起来就是「造物浮空」；往下最多试 3 格、往前最多试 3 格，都找不到才退回主人原地。
	 * 必须用 {@code refreshPositionAndAngles} 而不是 {@code setPos}：后者在 1.20.1 是「只写坐标」的裸 setter，
	 * 不会重算碰撞箱，碰撞箱会一直留在构造时的原点，于是所有按 getBoundingBox() 取的半径扫描都扫到空地。
	 */
	private static void placeBesideOwner(ServerLevel world, LivingEntity owner, RockConstructEntity construct) {
		Vec3 look = owner.getLookAngle();
		int feet = Mth.floor(owner.getY());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (double step = 1.0; step <= 3.0; step += 1.0) {
			double x = owner.getX() + look.x() * step;
			double z = owner.getZ() + look.z() * step;
			int columnX = Mth.floor(x);
			int columnZ = Mth.floor(z);
			for (int dy = 0; dy >= -3; --dy) {
				cursor.set(columnX, feet + dy + 1, columnZ);
				if (!world.isEmptyBlock(cursor)) {
					continue;
				}
				cursor.set(columnX, feet + dy, columnZ);
				if (!world.isEmptyBlock(cursor)) {
					continue;
				}
				cursor.set(columnX, feet + dy - 1, columnZ);
				if (world.isEmptyBlock(cursor)) {
					continue;
				}
				construct.absMoveTo(x, feet + dy, z, owner.getYRot(), 0.0F);
				return;
			}
		}
		construct.absMoveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0.0F);
	}

	/** 表里的 UUID 在这个世界里查得到、且还活着，才算造物在场 */
	private static RockConstructEntity find(ServerLevel world, UUID ownerId) {
		UUID id = SUMMONS.get(ownerId);
		if (id == null) {
			return null;
		}
		Entity live = world.getEntity(id);
		if (live instanceof RockConstructEntity construct && construct.isAlive()) {
			return construct;
		}
		SUMMONS.remove(ownerId, id);
		return null;
	}

	@Override
	public void tick() {
		super.tick();
		Level world = this.level();
		if (world.isClientSide || this.ownerId == null) {
			return;
		}
		// 区块重载后拿到的是新实例，靠这里把主人到造物的映射重新抢回来
		SUMMONS.put(this.ownerId, this.getUUID());
		// 嘲讽每 tick 都推一次，不给它们的目标选择器留出「覆盖回去」的空档
		tauntMonsters();
		long t = world.getGameTime();
		if (t >= this.nextSeekAt) {
			this.nextSeekAt = t + SEEK_INTERVAL;
			seekNearestMonster();
		}
		if (t >= this.nextSupportAt) {
			this.nextSupportAt = t + SUPPORT_INTERVAL;
			if (outOfLeash(world)) {
				crumble();
				return;
			}
			grantResistance();
		}
	}

	/**
	 * 造物自己会走去找怪挨打。索敌不挂在目标选择器上（原因见 SEEK_INTERVAL），
	 * 这里只留「记仇」那条：谁打了它它就打谁。行为器负责走过去和出手，
	 * 没有目标时靠闲逛把视线/距离铺开，好让索敌那一环能碰上怪。
	 * 不调 super.initGoals()，带上破门、逛村庄、啃海龟蛋这些僵尸习惯都没必要。
	 */
	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05, true));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
	}

	/**
	 * 牵绳半径内挑最近的敌对生物当目标，不看视线；目标还有效就别动它（重设目标会让它重新算路，
	 * 表现出来就是一顿一顿的原地摇摆）。半径用的就是牵绳那个数，所以它不会追着怪跑出圈。
	 * 有目标但压根没在寻路时补一次移动指令 —— 原版行为器只在 4~10 tick 的节律里重算路，
	 * 空的那几 tick 由这里填上。
	 */
	private void seekNearestMonster() {
		double r = GunLevelMod.CONFIG.crystalSummonLeashRadius;
		LivingEntity current = this.getTarget();
		if (current != null && isSeekable(current) && current.distanceToSqr(this) <= r * r) {
			moveToward(current);
			return;
		}
		Entity nearest = null;
		double best = Double.MAX_VALUE;
		for (Entity candidate : this.level().getEntities(this,
				this.getBoundingBox().inflate(r, r, r))) {
			if (!isSeekable(candidate)) {
				continue;
			}
			double d = candidate.distanceToSqr(this);
			if (d < best) {
				best = d;
				nearest = candidate;
			}
		}
		if (nearest != null) {
			LivingEntity target = (LivingEntity) nearest;
			if (target != current) {
				this.setTarget(target);
			}
			moveToward(target);
		} else if (current != null) {
			// 圈内没怪了：清掉目标，否则它会一直盯着圈外那只不动
			this.setTarget(null);
		}
	}

	private void moveToward(LivingEntity target) {
		if (this.getNavigation().isDone() && this.distanceToSqr(target) > 9.0) {
			this.getNavigation().moveTo(target, 1.05);
		}
	}

	/** 能当索敌候选：敌对生物、活着、不是别人的造物 */
	private boolean isSeekable(Entity entity) {
		return entity instanceof Monster && entity instanceof LivingEntity
				&& entity.isAlive() && !(entity instanceof RockConstructEntity);
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	/** 站在河里也不能变成溺尸：外形和归属都得是造物 */
	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	/**
	 * 只数「被攻击」的次数，血量全程不参与（用户口径：被打三次就死）。
	 * 玩家造成的攻击不计入：子弹会穿透它，元素反应的范围伤害也会打到它，
	 * 算进去等于射手一轮输出先把自己的造物打碎；环境伤害（火/摔落等）同样不计。
	 */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (this.level().isClientSide || this.isRemoved() || amount <= 0.0F) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof LivingEntity) || attacker instanceof Player) {
			return false;
		}
		if (this.ownerId != null && attacker.getUUID().equals(this.ownerId)) {
			return false;
		}
		this.hitsTaken++;
		this.hurtTime = this.hurtDuration = 8;
		spawnDust(8);
		if (this.hitsTaken >= Math.max(1, GunLevelMod.CONFIG.crystalSummonHits)) {
			crumble();
		}
		return false;
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (this.ownerId != null) {
			SUMMONS.remove(this.ownerId, this.getUUID());
		}
		super.remove(reason);
	}

	/** 次数用完：崩解散去，不掉落、不给经验 */
	private void crumble() {
		this.level().playSound(null, this.blockPosition(), SoundEvents.STONE_BREAK,
				SoundSource.HOSTILE, 0.9F, 0.8F);
		spawnDust(24);
		this.discard();
	}

	private void spawnDust(int count) {
		if (!(this.level() instanceof ServerLevel serverWorld)) {
			return;
		}
		serverWorld.sendParticles(DUST, this.getX(), this.getY() + this.getBbHeight() * 0.5,
				this.getZ(), count, 0.35, 0.5, 0.35, 0.02);
	}

	/**
	 * 把半径内敌对生物的注意力抢过来。光调 setTarget 会被它们自己的目标选择器下一 tick 覆盖回去
	 * （僵尸那条追玩家的索敌是优先级 2），所以走原版的寻仇通道：setAttacker 把 attacker 和
	 * lastAttackedTime 一起写进去，HurtByTargetGoal（优先级 1）读到就认造物当目标，并且会自己维持住。
	 * setTarget 是兜底，给史莱姆这种压根没有寻仇 goal 的怪。
	 */
	private void tauntMonsters() {
		double r = GunLevelMod.CONFIG.crystalSummonTauntRadius;
		AABB box = this.getBoundingBox().inflate(r, r * 0.7, r);
		for (Entity near : this.level().getEntities(this, box,
				e -> e instanceof Mob && e.distanceToSqr(this) <= r * r)) {
			Mob mob = (Mob) near;
			if (mob.getTarget() == this || !(mob instanceof Monster)) {
				continue;
			}
			// 别去打扰别人的造物：它自己就在打怪，抢它目标等于把怪喂给它
			if (mob instanceof RockConstructEntity) {
				continue;
			}
			mob.setLastHurtByMob(this);
			mob.setTarget(this);
		}
	}

	/**
	 * 牵绳：离最近玩家超过半径就自己消散（用户 2026-10-12 补的设定）。
	 * 按「最近玩家」而不是「主人」量：主人下线或换维度时按 UUID 查不到人，等于没有限制，
	 * 造物会永远留在那儿。这个维度一个玩家都没有就不检查（多半是主人刚下线，回来还得继续用）。
	 */
	private boolean outOfLeash(Level world) {
		if (!(world instanceof ServerLevel serverWorld)) {
			return false;
		}
		List<ServerPlayer> players = serverWorld.players();
		if (players.isEmpty()) {
			return false;
		}
		double r = GunLevelMod.CONFIG.crystalSummonLeashRadius;
		double nearest = Double.MAX_VALUE;
		for (ServerPlayer player : players) {
			double d = player.distanceToSqr(this);
			if (d < nearest) {
				nearest = d;
			}
		}
		return nearest > r * r;
	}

	/** 圈内玩家：抗性提升，等级按召唤时的芯片数（1 颗 = 抗性提升 I，2 颗 = II） */
	private void grantResistance() {
		if (!(this.level() instanceof ServerLevel serverWorld)) {
			return;
		}
		double r = GunLevelMod.CONFIG.crystalSummonBuffRadius;
		int amplifier = Math.max(0, this.chipCount - 1);
		int duration = (int) Math.max(1L,
				Math.round(GunLevelMod.CONFIG.crystalSummonBuffSeconds * 20));
		for (ServerPlayer player : serverWorld.players()) {
			if (player.isAlive() && player.distanceToSqr(this) <= r * r) {
				player.addEffect(new MobEffectInstance(
						MobEffects.DAMAGE_RESISTANCE, duration, amplifier));
			}
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		if (this.ownerId != null) {
			nbt.putString(OWNER_KEY, this.ownerId.toString());
		}
		nbt.putInt(CHIPS_KEY, this.chipCount);
		nbt.putInt(HITS_KEY, this.hitsTaken);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.contains(OWNER_KEY)) {
			try {
				this.ownerId = UUID.fromString(nbt.getString(OWNER_KEY));
			} catch (IllegalArgumentException e) {
				this.ownerId = null;
			}
		}
		this.chipCount = Math.max(1, nbt.getInt(CHIPS_KEY));
		this.hitsTaken = nbt.getInt(HITS_KEY);
		if (this.ownerId != null) {
			SUMMONS.put(this.ownerId, this.getUUID());
		}
	}

	/** 换服/退服时清表：造物的归属只活在当前这一局里 */
	public static void clear() {
		SUMMONS.clear();
	}
}
