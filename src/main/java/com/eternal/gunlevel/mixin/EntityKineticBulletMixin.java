package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.AuroraState;
import com.eternal.gunlevel.effect.BloomDomain;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.effect.ChipState;
import com.eternal.gunlevel.effect.CombatState;
import com.eternal.gunlevel.effect.Element;
import com.eternal.gunlevel.effect.FervorState;
import com.eternal.gunlevel.effect.HuntState;
import com.eternal.gunlevel.effect.LevelScaling;
import com.eternal.gunlevel.effect.MomentumState;
import com.eternal.gunlevel.effect.RhythmState;
import com.eternal.gunlevel.item.GunAuroraData;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.item.GunCoreData;
import com.eternal.gunlevel.item.GunRoarData;
import com.eternal.gunlevel.util.ChipHints;
import com.eternal.gunlevel.util.TaczCompat;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet.MaybeMultipartEntity;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumSet;

/**
 * 子弹实体：构造时记录枪械等级（客户端/服务端都要，穿甲数值会随生成数据同步），
 * 命中时按等级结算伤害倍率与武器面板暴击，并触发撕裂弹流血、元素附着与反应、战术芯片效果。
 *
 * <p>锚点是那个 protected 的全参构造（两个 public 构造都转调它），1.1.7 的 Forge 版在这里
 * 就把开火那把枪的 ItemStack 传进来了，所以元素/芯片/等级直接读它，不必再回射手背包里找枪。
 */
@Mixin(com.tacz.guns.entity.EntityKineticBullet.class)
public abstract class EntityKineticBulletMixin {

	@Shadow(remap = false)
	private float speed;
	@Shadow(remap = false)
	private int pierce;
	@Shadow(remap = false)
	private boolean isTracerAmmo;
	/** 重武器（榴弹/火箭）爆炸那段的伤害，来自 BulletData 的 ExplosionData */
	@Shadow(remap = false)
	private float explosionDamage;
	/**
	 * 真实伤害结算入口（护甲两段拆分、击退都在里面）。
	 * 强击连发的额外攻击直接把它再调一遍，所以额外攻击天然「吃同样的护甲与武器穿甲、
	 * 同样触发命中效果、同样攒命中」，而扣弹药不在这条链路上，等于不消耗子弹。
	 */
	@Shadow(remap = false)
	private void tacAttackEntity(MaybeMultipartEntity target, float damage,
			Pair<DamageSource, DamageSource> sources) {
	}

	@Unique
	private int gunlevel$level;
	@Unique
	private LivingEntity gunlevel$shooter;
	@Unique
	private boolean gunlevel$crit;
	@Unique
	private float gunlevel$critChance;
	@Unique
	private Element gunlevel$element = Element.NONE;
	/** 连环暴击：是否装了、发射时射手是否在激化窗口里 */
	@Unique
	private boolean gunlevel$chainCrit;
	@Unique
	private boolean gunlevel$empowered;
	/** 激化窗口在这发子弹第一次命中时开掉，穿透多个目标不重复开 */
	@Unique
	private boolean gunlevel$chainCritProcced;
	/** 破甲强攻：是否装了、这一发是否触发 */
	@Unique
	private boolean gunlevel$armorBreak;
	@Unique
	private boolean gunlevel$pierceProc;
	/** 高能弹匣：是否装了、这一发打出时弹匣余量是否充足（常驻条件，不掷骰） */
	@Unique
	private boolean gunlevel$highMag;
	/** 极光追击：是否装了（命中记账按射手累计，见 AuroraState） */
	@Unique
	private boolean gunlevel$aurora;
	/** 极光追击：这发子弹是不是从极光匣里打出去的（生成时按那把枪的标记定格），这种命中不记账 */
	@Unique
	private boolean gunlevel$auroraMag;
	/** 穿透多个目标时一发子弹只攒一次命中，和连环暴击一样按发计，不按命中实体数计 */
	@Unique
	private boolean gunlevel$auroraCounted;
	/** 迅雷之音：是否装了、一发子弹只抵一次冷却（和极光一样按发计） */
	@Unique
	private boolean gunlevel$thunder;
	@Unique
	private boolean gunlevel$thunderCounted;
	/** 愈战愈勇：是否装了、这一发是否已经记过层（穿透多个目标只叠一层，和极光一样按发计） */
	@Unique
	private boolean gunlevel$fervor;
	@Unique
	private boolean gunlevel$fervorCounted;
	/** 烈焰咆哮：是否装了（爆头增伤与按枪的刷新计数都看它） */
	/** 狩猎时刻：是否装了（标记、记录、两档阈值都按射手记，见 HuntState） */
	@Unique
	private boolean gunlevel$hunt;
	@Unique
	private boolean gunlevel$flameRoar;
	/** 烈焰咆哮：这一发是不是咆哮匣里那颗特殊弹（生成那一刻按枪上的截止时刻定格） */
	@Unique
	private boolean gunlevel$roarRound;
	/**
	 * 烈焰咆哮：这一发最终算不算爆头 —— 真爆头，或被概率判成爆头。
	 * 概率只掷一次并记在这里，命中结算那侧要用同一个结果计数，不能各掷各的。
	 */
	@Unique
	private boolean gunlevel$headHit;
	/** 发射那把枪的 id：按枪记的刷新计数要在命中那一刻再找到那把枪 */
	@Unique
	private ResourceLocation gunlevel$gunId;
	/** 强击连发：是否装了 */
	@Unique
	private boolean gunlevel$barrage;
	/**
	 * 发射那把枪装的芯片全集：元素反应那一侧要用它判断凝晶召唤（要不要召唤、造物给几级抗性）。
	 * 上面那些按芯片分的布尔是结算路径上的热路径 shortcut，两者都在构造时定格。
	 */
	@Unique
	private EnumSet<Chip> gunlevel$chips = EnumSet.noneOf(Chip.class);
	/** 势如破竹：是否装了 */
	@Unique
	private boolean gunlevel$momentum;
	/** 势如破竹：这发子弹生成那一刻射手的加成档位（0=没有，1=满值，逐秒掉 1/6），定格后结算不再读窗口 */
	@Unique
	private float gunlevel$momentumPower;
	/** 势如破竹：这发是不是打头。TACZ 在 onHitEntity 里就把爆头倍率乘进伤害了，只能在那个时刻抓标记 */
	@Unique
	private boolean gunlevel$headshot;
	/** 节奏射击：是否装了 */
	@Unique
	private boolean gunlevel$rhythm;
	/** 节奏射击：这一发是否已经记过层（穿透多个目标只叠一层），同时兼作「这发没落空」的标记 */
	@Unique
	private boolean gunlevel$rhythmCounted;
	/** 出膛那一刻掷暴击用的骰值：无坚不摧要在命中时按目标血量抬高及格线，不能到那时再补掷一次 */
	@Unique
	private float gunlevel$critRoll;
	/** 这一次命中的目标（onHitEntity 开头抓），抵近射击的距离与两边的血量口径都从它算 */
	@Unique
	private Entity gunlevel$hitTarget;
	/** 正在结算强击连发的额外攻击：这一层不再掷骰、伤害也不再重复缩放（传进去的已经是最终值） */
	@Unique
	private boolean gunlevel$barraging;
	/** 这一发实际乘上去的暴击倍率（未暴击恒为 1），额外攻击按它把自己那份暴击加成除回去 */
	@Unique
	private float gunlevel$critApplied = 1.0F;

	@Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;"
			+ "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
			+ "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;"
			+ "Lnet/minecraft/resources/ResourceLocation;Z"
			+ "Lcom/tacz/guns/resource/pojo/data/gun/GunData;"
			+ "Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V", at = @At("TAIL"), remap = false)
	private void gunlevel$applyLevel(EntityType<? extends Projectile> type, Level world,
			LivingEntity shooter, ItemStack gunStack, ResourceLocation ammoId, ResourceLocation gunId,
			ResourceLocation gunDisplayId, boolean tracer, GunData gunData, BulletData data,
			CallbackInfo ci) {
		this.gunlevel$shooter = shooter;
		// 元素与芯片都只来自枪上装的配件，与等级无关
		this.gunlevel$element = GunCoreData.get(gunStack);
		EnumSet<Chip> chips = GunChipData.get(gunStack);
		this.gunlevel$chainCrit = chips.contains(Chip.CHAIN_CRIT);
		this.gunlevel$armorBreak = chips.contains(Chip.ARMOR_BREAK);
		// 高能弹匣：余量判定在这发子弹生成时定格。TACZ 的 LivingEntityShoot 先扣弹再异步建子弹实体，
		// 所以这里读到的已经是减去这一发后的数量，配 >= 才等于玩家口径的「开火时弹匣里多于 20 发」。
		this.gunlevel$highMag = chips.contains(Chip.HIGH_MAG) && gunlevel$hasAmmoMargin(gunStack);
		this.gunlevel$aurora = chips.contains(Chip.AURORA);
		// 极光匣的标记在那把枪的 NBT 上，此刻定格：这一匣打出去的命中不参与攒下一匣
		this.gunlevel$auroraMag = GunAuroraData.isOn(gunStack);
		this.gunlevel$barrage = chips.contains(Chip.BARRAGE);
		this.gunlevel$momentum = chips.contains(Chip.MOMENTUM);
		this.gunlevel$thunder = chips.contains(Chip.THUNDER_VOICE);
		this.gunlevel$fervor = chips.contains(Chip.FERVOR);
		this.gunlevel$flameRoar = chips.contains(Chip.FLAME_ROAR);
		this.gunlevel$hunt = chips.contains(Chip.HUNTING_MOMENT);
		this.gunlevel$rhythm = chips.contains(Chip.RHYTHM_SHOT);
		this.gunlevel$gunId = gunId;
		// 咆哮匣里那颗特殊弹：按枪 NBT 上的截止时刻定格。这一发是不是它，出膛这一刻就定了，
		// 之后就算 5 秒窗口过掉也不会再有一发吃到 400%（这一匣只有这一发）。
		this.gunlevel$roarRound = this.gunlevel$flameRoar
				&& GunRoarData.isEmpowered(gunStack, world.getGameTime());
		if (this.gunlevel$roarRound && !world.isClientSide) {
			// 特殊弹出膛这一刻就把咆哮匣抹掉：容量立刻回到正常值，
			// 也保证「只有这一发吃 400%」—— 下一发生成时标记已经不在了
			GunRoarData.endMag(gunStack);
		}
		this.gunlevel$chips = chips;

		int level = LevelScaling.levelOfStack(gunStack);
		if (level <= 0) {
			return;
		}
		this.gunlevel$level = level;
		if (LevelScaling.currentLevel() <= 0) {
			// 射击窗口内 BulletData 已给出缩放后的初速，不再重复乘
			this.speed *= (float) LevelScaling.speedScale(level);
		}

		if (level >= 5 && GunLevelMod.CONFIG.lv5Effect == 1) {
			this.pierce += 2;
			this.isTracerAmmo = true;
		}
		// 暴击：武器面板暴击率（改造基础 5% + LV5 15% + 连环暴击 2%×等级），一发子弹掷一次
		double chance = LevelScaling.critChance(level, this.gunlevel$chainCrit);
		if (this.gunlevel$chainCrit) {
			this.gunlevel$empowered = !world.isClientSide
					&& ChipState.isEmpowered(shooter, world.getGameTime());
			if (this.gunlevel$empowered) {
				chance += GunLevelMod.CONFIG.chipEmpoweredChancePerLevel * level;
			}
		}
		// 势如破竹：把射手此刻的加成档位定格在这一发上，暴击率和非头部增伤都乘它。
		// 定格（而不是结算时再读窗口）才能让同一发的递归和跨 tick 的结算不自我放大。
		if (this.gunlevel$momentum && !world.isClientSide) {
			double power = MomentumState.strength(shooter, world.getGameTime());
			this.gunlevel$momentumPower = (float) power;
			chance += GunLevelMod.CONFIG.chipMomentumCritChancePerLevel * level * power;
		}
		// 蓝芯片·暴击改造：定值加在同一次掷骰上
		if (chips.contains(Chip.CRIT_MOD)) {
			chance += GunLevelMod.CONFIG.chipBlueCrit;
		}
		// 狩猎时刻：攒够 15 次命中的那档暴击率加成，加在同一次掷骰上
		if (this.gunlevel$hunt) {
			chance += HuntState.critBonus(shooter, world.getGameTime());
		}
		this.gunlevel$critChance = (float) chance;
		// 只掷骰，不开激化窗口：空开枪不该触发连环暴击，生效挪到命中结算里。
		// 骰值也留着：紫芯片·无坚不摧要在命中那一刻按目标血量把及格线抬高，
		// 拿同一枚骰子比两次，等于「对该目标暴击率 +20%」，而不是补掷一次概率。
		this.gunlevel$critRoll = world.getRandom().nextFloat();
		this.gunlevel$crit = this.gunlevel$critRoll < this.gunlevel$critChance;
		// 破甲强攻：每发子弹掷一次概率（每级放大），触发那发在命中结算里同时吃穿甲与增伤
		if (this.gunlevel$armorBreak && world.getRandom().nextFloat()
				< (float) (GunLevelMod.CONFIG.chipArmorPierceChancePerLevel * level)) {
			this.gunlevel$pierceProc = true;
		}
		// 重武器的爆炸是独立的一段伤害：它不走 tacAttackEntity，而是把 explosionDamage
		// 当爆炸威力交给 ProjectileExplosion，所以等级加成要在这里补到字段上，
		// 命中实体/命中方块/引信到点三个爆炸入口会自动跟着变。爆炸只吃等级倍率，不跟暴击。
		if (this.explosionDamage > 0) {
			this.explosionDamage *= (float) LevelScaling.damageScale(level);
			// 重武器的爆炸段不走 tacAttackEntity，所以 400% 要单独乘到这个字段上，
			// 否则火箭筒这类「基础伤害改 400%」就只体现在直击那一段
			if (this.gunlevel$roarRound) {
				this.explosionDamage *= (float) GunLevelMod.CONFIG.chipRoarDamageMultiplier;
			}
		}
	}

	/**
	 * 势如破竹只加「非头部」伤害，而 TACZ 在 onHitEntity 里就把爆头倍率乘进了伤害值，
	 * 到 tacAttackEntity 时已经看不出来这一发是不是打头，所以标记只能在这里抓。
	 * handler 必须把目标的三个参数写全（少一个就是 InvalidInjectionException，游戏直接起不来），
	 * 后两个 Vec3 用不上也不能省。
	 */
	@Inject(method = "onHitEntity", at = @At("HEAD"), remap = false)
	private void gunlevel$catchHeadshot(TacHitResult hit, Vec3 hitPos, Vec3 cameraPos,
			CallbackInfo ci) {
		this.gunlevel$headshot = hit.isHeadshot();
		// 目标也要在这里抓：伤害缩放挂在 tacAttackEntity 的参数上，那时已经拿不到命中上下文，
		// 而抵近射击（距离）与无坚不摧（目标血量）都要在缩放那一刻读数
		this.gunlevel$hitTarget = hit.getEntity();
	}

	/**
	 * 节奏射击的「落空」：这一发打在方块上、并且从没记过层，说明它一发都没打中人 → 整条清零。
	 * 子弹飞出寿命（朝天开一枪）不经过这里，那一发由 3 秒脱战兜底，所以层数最迟 3 秒后也会掉。
	 */
	@Inject(method = "onHitBlock", at = @At("HEAD"), remap = false)
	private void gunlevel$onBlockHitMiss(BlockHitResult hit, Vec3 pos, Vec3 cameraPos,
			CallbackInfo ci) {
		if (this.gunlevel$rhythm && !this.gunlevel$rhythmCounted && this.gunlevel$shooter != null
				&& !this.gunlevel$shooter.level().isClientSide) {
			RhythmState.reset(this.gunlevel$shooter);
		}
	}

	/**
	 * 实际伤害唯一入口：TACZ 的 getDamage 在枪械带距离伤害表时会无视 damageAmount，
	 * 所以直接缩放传入的伤害，基础加成和暴击都落在这里。
	 */
	@ModifyVariable(method = "tacAttackEntity", at = @At("HEAD"), argsOnly = true,
			ordinal = 0, remap = false)
	private float gunlevel$scaledDamage(float damage) {
		float scaled = this.gunlevel$applyScale(damage);
		if (this.gunlevel$pierceProc && !this.gunlevel$barraging) {
			// 触发强攻那发同时走穿甲和增伤：增伤乘在整段伤害上，两段都算到
			scaled *= (float) GunLevelMod.CONFIG.chipArmorPierceMultiplier;
		}
		return scaled;
	}

	/**
	 * 破甲强攻：在武器自带穿甲之上再加一档，不是顶成 1。TACZ 把一次命中拆成两段伤害——
	 * 吃护甲那段拿 damage*(1-r)，无视护甲那段拿 damage*r，所以 r 就是穿甲比例。
	 * 默认包 23 把枪自带 r0 跨 0~0.75，顶满等于给零穿甲的手枪微冲直接翻倍、
	 * 又让 hk_g3/ai_awp/m95 这些高穿甲枪一点加成都吃不到，加值能把逐枪跨度压掉一半左右。
	 * index 6 是 tacAttackEntity 里那个截断后的比例（局部表：1=目标、2=damage、3=伤害源对、
	 * 4/5=两段各自的 DamageSource、6=clamp(armorIgnore,0,1) 即 r、7=1-r），
	 * 挂在它唯一的赋值上（@At("STORE")），不碰 MC 符号，省得依赖 refmap。
	 */
	@ModifyVariable(method = "tacAttackEntity", at = @At("STORE"), index = 6, remap = false)
	private float gunlevel$forceArmorPierce(float ratio) {
		return this.gunlevel$pierceProc
				? Math.min(1.0F, ratio + (float) GunLevelMod.CONFIG.chipArmorPierceBonus)
				: ratio;
	}

	@Unique
	private static boolean gunlevel$hasAmmoMargin(ItemStack gunStack) {
		if (gunStack.isEmpty()) {
			return false;
		}
		IGun gun = IGun.getIGunOrNull(gunStack);
		return gun != null && gun.getCurrentAmmoCount(gunStack) >= GunLevelMod.CONFIG.chipHighMagAmmo;
	}

	@Unique
	private boolean gunlevel$rollFakeHead() {
		if (this.gunlevel$headshot || this.gunlevel$shooter == null) {
			return false;
		}
		var c = GunLevelMod.CONFIG;
		// critChance 是小数（0.20 = 20%），配置里那档写的是「每 1% 暴击率加多少」，所以先乘 100；
		// 封顶只封「靠暴击率挣来的那部分」，初始那 10% 是芯片自带的，不受影响。
		double bonus = this.gunlevel$critChance * 100.0 * c.chipRoarFakeHeadPerCrit;
		double chance = c.chipRoarFakeHeadChance + Math.min(bonus, c.chipRoarFakeHeadBonusCap);
		return this.gunlevel$shooter.level().getRandom().nextFloat() < (float) chance;
	}

	/** 抵近射击的距离口径：命中这一刻射手到目标的直线距离，和势如破竹用同一个判据 */
	@Unique
	private boolean gunlevel$withinCloseRange() {
		double range = GunLevelMod.CONFIG.chipPurpleCloseRange;
		return this.gunlevel$shooter != null && this.gunlevel$hitTarget != null
				&& this.gunlevel$hitTarget.distanceToSqr(this.gunlevel$shooter) <= range * range;
	}

	/** 无坚不摧的血量口径：目标当前生命 / 上限。打的不是活物就当 0，不触发 */
	@Unique
	private double gunlevel$targetHealthRatio() {
		if (!(this.gunlevel$hitTarget instanceof LivingEntity victim)) {
			return 0.0;
		}
		return victim.getHealth() / (float) Math.max(1.0F, victim.getMaxHealth());
	}

	/** 凝神打击的血量口径：射手自己当前生命 / 上限 */
	@Unique
	private double gunlevel$shooterHealthRatio() {
		if (this.gunlevel$shooter == null) {
			return 0.0;
		}
		return this.gunlevel$shooter.getHealth()
				/ (float) Math.max(1.0F, this.gunlevel$shooter.getMaxHealth());
	}

	@Unique
	private float gunlevel$applyScale(float damage) {
		if (this.gunlevel$level <= 0 || damage <= 0) {
			return damage;
		}
		// 强击连发的额外攻击：传进来的就是这一次命中的最终伤害，别再乘一遍等级/暴击/高能弹匣
		if (this.gunlevel$barraging) {
			return damage;
		}
		// 咆哮一击：这一发是特殊弹，面板基础伤害直接改成原本的 400%。
		// 乘在高能弹匣那些点数之前 —— 那几点是固定加法，不该跟着翻四倍。
		if (this.gunlevel$roarRound) {
			damage *= (float) GunLevelMod.CONFIG.chipRoarDamageMultiplier;
		}
		// 高能弹匣的点数加在面板伤害那一档，所以要在等级倍率之前进账（会被 damageScale 再乘一遍）
		float base = this.gunlevel$highMag
				? damage + (float) LevelScaling.highMagBasePoints(this.gunlevel$level)
				: damage;
		float scaled = base * (float) LevelScaling.damageScale(this.gunlevel$level);
		// 紫芯片·抵近射击：命中这一刻射手到目标的距离在 5 格内才算近距（和势如破竹同一口径），
		// 增伤与「暴击伤害增加」两条都受这个条件管
		boolean close = this.gunlevel$chips.contains(Chip.CLOSE_QUARTERS)
				&& gunlevel$withinCloseRange();
		// 紫芯片·无坚不摧：目标血量还在 60% 以上时，这一发的暴击率多一档。
		// 拿出膛那枚骰值去比更高的及格线，不补掷第二次，所以原本就暴击的不会被弄没。
		if (!this.gunlevel$crit && this.gunlevel$chips.contains(Chip.UNSTOPPABLE)
				&& gunlevel$targetHealthRatio() > GunLevelMod.CONFIG.chipPurpleUnstoppableHealthRatio
				&& this.gunlevel$critRoll
						< this.gunlevel$critChance + GunLevelMod.CONFIG.chipPurpleUnstoppableCrit) {
			this.gunlevel$crit = true;
		}
		this.gunlevel$critApplied = 1.0F;
		if (this.gunlevel$crit) {
			this.gunlevel$critApplied = (float) (LevelScaling.critMultiplier(
					this.gunlevel$level, this.gunlevel$empowered)
					// 蓝芯片·暴击伤害改造：定值加在倍率上（1.5 起的话就是 1.6）
					+ (this.gunlevel$chips.contains(Chip.CRIT_DAMAGE_MOD)
							? GunLevelMod.CONFIG.chipBlueCritDamage : 0.0)
					// 紫芯片·抵近射击：只有近距那一发吃
					+ (close ? GunLevelMod.CONFIG.chipPurpleCloseCritDamage : 0.0));
			scaled *= this.gunlevel$critApplied;
		}
		if (this.gunlevel$highMag) {
			scaled *= (float) LevelScaling.highMagBonusScale(this.gunlevel$level);
		}
		// 势如破竹的非头部伤害加成：爆头那发跳过（伤害里已经含了 TACZ 的爆头倍率）
		if (this.gunlevel$momentumPower > 0 && !this.gunlevel$headshot) {
			scaled *= 1.0F + (float) (GunLevelMod.CONFIG.chipMomentumDamagePerLevel
					* this.gunlevel$level * this.gunlevel$momentumPower);
		}
		// 愈战愈勇：读射手此刻的层数。不用像势如破竹那样在生成时定格 —— 层数是在命中结算里才 +1 的，
		// 所以这一发读到的是「截至上一发」的层数，同一发不会自己给自己叠；爆头那发照吃（它不挑部位）。
		if (this.gunlevel$fervor && this.gunlevel$shooter != null) {
			double fervor = FervorState.bonus(this.gunlevel$shooter,
					this.gunlevel$shooter.level().getGameTime(), this.gunlevel$level);
			if (fervor > 0) {
				scaled *= 1.0F + (float) fervor;
			}
		}
		// 蓝芯片·伤害改造：定值一档，和等级倍率同区连乘
		if (this.gunlevel$chips.contains(Chip.DAMAGE_MOD)) {
			scaled *= 1.0F + (float) GunLevelMod.CONFIG.chipBlueDamage;
		}
		// 紫芯片·抵近射击的增伤与凝神打击的满血增伤：同样是定值一档连乘
		if (close) {
			scaled *= 1.0F + (float) GunLevelMod.CONFIG.chipPurpleCloseDamage;
		}
		if (this.gunlevel$chips.contains(Chip.FOCUSED_STRIKE)
				&& gunlevel$shooterHealthRatio() > GunLevelMod.CONFIG.chipPurpleFocusHealthRatio) {
			scaled *= 1.0F + (float) GunLevelMod.CONFIG.chipPurpleFocusDamage;
		}
		// 紫芯片·节奏射击：读射手此刻的层数（叠层在命中结算里，落空或脱战 3 秒整条清零）。
		// 强击连发的额外攻击在上面已经 return，不会再吃一遍这档加成。
		if (this.gunlevel$rhythm && this.gunlevel$shooter != null) {
			scaled *= 1.0F + (float) RhythmState.bonus(this.gunlevel$shooter,
					this.gunlevel$shooter.level().getGameTime());
		}
		// 紫芯片·勇者无惧：+20% 伤害。代价（受伤放大 15%）在 LivingGuards 的 LivingHurtEvent 里，
		// 两边都只认主手这把枪，所以收益与代价天然对称
		if (this.gunlevel$chips.contains(Chip.FEARLESS)) {
			scaled *= 1.0F + (float) GunLevelMod.CONFIG.chipPurpleFearlessDamage;
		}
		// 狩猎时刻：标记期间攒够 15 次命中就给一档通用伤害加成（跟着标记走，切枪/脱战就没了）
		if (this.gunlevel$hunt && this.gunlevel$shooter != null) {
			double bonus = HuntState.damageBonus(this.gunlevel$shooter,
					this.gunlevel$shooter.level().getGameTime());
			if (bonus > 0) {
				scaled *= 1.0F + (float) bonus;
			}
		}
		// 烈焰咆哮：爆头那发再吃一档按等级的增伤。没打中头时先按概率判一次「算不算打中头」，
		// 概率吃这一发的总暴击率（面板 + LV5 + 芯片常驻 + 激化 + 势如破竹），加成部分单独封顶。
		// 这个转化只管这颗芯片自己的增伤与刷新计数 —— 势如破竹那档「非头部」仍看 TACZ 的真实爆头标记，
		// 免得一颗芯片悄悄改了另一颗的判定口径。
		if (this.gunlevel$flameRoar) {
			this.gunlevel$headHit = this.gunlevel$headshot || gunlevel$rollFakeHead();
			if (this.gunlevel$headHit) {
				scaled *= 1.0F + (float) (GunLevelMod.CONFIG.chipRoarHeadDamagePerLevel
						* this.gunlevel$level);
			}
		} else {
			this.gunlevel$headHit = this.gunlevel$headshot;
		}
		// 蓝芯片·头部伤害改造：只看 TACZ 的真实爆头标记（烈焰咆哮那个概率转化不算给它）
		if (this.gunlevel$headshot && this.gunlevel$chips.contains(Chip.HEAD_DAMAGE_MOD)) {
			scaled *= 1.0F + (float) GunLevelMod.CONFIG.chipBlueHeadDamage;
		}
		// 净化领域：射手站在已点亮的苏生领域里时，这一发末尾再附加 0.02×等级×记录值 点伤害。
		// 加在整条倍率链的最后，所以它不吃暴击、也不再被等级倍率乘一遍；强击连发那次额外攻击
		// 走不到这里（上面已经 return），符合「额外攻击不重复吃加成」的既有口径。
		if (this.gunlevel$shooter != null) {
			scaled += (float) BloomDomain.purifyAttackBonus(this.gunlevel$shooter, this.gunlevel$level);
		}
		return scaled;
	}

	/**
	 * 命中结算：撕裂弹流血、元素附着与反应、蒸发/融化/超载增伤。
	 * 放在方法末尾，此时 damage 局部变量已经是上面缩放过的真实伤害，不会再乘第二遍。
	 * 反应伤害归因到射手，并按这里的暴击率各自掷骰（融化除外）。
	 * 打中的是盔甲架那种「本体 + 部件」的目标时按本体算（部件没有位置也没有血量）。
	 */
	@Inject(method = "tacAttackEntity", at = @At("RETURN"), remap = false)
	private void gunlevel$onHit(MaybeMultipartEntity multipart, float damage,
			Pair<DamageSource, DamageSource> sources, CallbackInfo ci) {
		Entity target = multipart.core();
		if (this.gunlevel$level <= 0 && this.gunlevel$element == Element.NONE) {
			return;
		}
		// 强击连发的额外攻击是在这个方法内部递归进来的，飘字与粒子只在最外层那一次发，免得一行刷两遍
		boolean outer = !this.gunlevel$barraging;
		// 连环暴击：这发确实是暴击并且真的打中东西才开激化窗口，穿透多个目标也只开一次
		if (this.gunlevel$crit && this.gunlevel$chainCrit && !this.gunlevel$chainCritProcced
				&& target.level() instanceof ServerLevel) {
			this.gunlevel$chainCritProcced = true;
			if (ChipState.onCrit(this.gunlevel$shooter, target.level().getGameTime())) {
				ChipHints.show(this.gunlevel$shooter, Chip.CHAIN_CRIT);
			}
		}
		// 极光追击：确实打中东西才攒一次命中（空开枪不计），刚攒满那一刻提示换弹可用
		if (this.gunlevel$aurora && !this.gunlevel$auroraCounted
				&& target.level() instanceof ServerLevel) {
			this.gunlevel$auroraCounted = true;
			// 命中账记在**这把枪**上：只有装着极光追击的那把枪才攒（用户口径「武器累计命中」）
			ItemStack auroraGun = TaczCompat.findGunStack(this.gunlevel$shooter, this.gunlevel$gunId);
			if (AuroraState.onHit(this.gunlevel$shooter, auroraGun, this.gunlevel$auroraMag)) {
				ChipHints.show(this.gunlevel$shooter, Chip.AURORA, ChipHints.AURORA_READY);
			}
		}
		// 迅雷之音：命中抵冷却，空开枪不白送
		if (this.gunlevel$thunder && !this.gunlevel$thunderCounted
				&& target.level() instanceof ServerLevel) {
			this.gunlevel$thunderCounted = true;
			com.eternal.gunlevel.effect.ThunderVoiceState.onHit(this.gunlevel$shooter);
		}
		// 愈战愈勇：按发记一层（穿透到的后续目标不重复叠），刚进入战斗那一刻飘一行；
		// 之后每叠一层都不飘字 —— 连射时每发都飘会把提示糊成一片（高能弹匣同样的理由）。
		if (this.gunlevel$fervor && !this.gunlevel$fervorCounted
				&& target.level() instanceof ServerLevel) {
			this.gunlevel$fervorCounted = true;
			if (FervorState.onHit(this.gunlevel$shooter, target.level().getGameTime())) {
				ChipHints.show(this.gunlevel$shooter, Chip.FERVOR);
			}
		}
		// 节奏射击：按发计层（穿透到的后续目标不重复叠，和愈战愈勇同口径）。
		// 不飘字 —— 连射时每发都飘会把提示糊成一片（高能弹匣同样的理由）。
		if (this.gunlevel$rhythm && !this.gunlevel$rhythmCounted
				&& target.level() instanceof ServerLevel) {
			this.gunlevel$rhythmCounted = true;
			RhythmState.onHit(this.gunlevel$shooter, target.level().getGameTime());
		}
		// 势如破竹：对近距目标造成伤害才开加成窗口（远狙不触发）。窗口没开着时才新开，
		// 开着的时候只有击杀能把它拉回满值；穿透多个目标按「一次造成伤害」算，所以也限 outer。
		if (outer && this.gunlevel$momentum && target.level() instanceof ServerLevel
				&& this.gunlevel$shooter != null
				&& target.distanceToSqr(this.gunlevel$shooter)
						<= GunLevelMod.CONFIG.chipMomentumRange * GunLevelMod.CONFIG.chipMomentumRange) {
			boolean killed = target instanceof LivingEntity victim && !victim.isAlive();
			if (MomentumState.onHit(this.gunlevel$shooter, target.level().getGameTime(), killed)) {
				ChipHints.show(this.gunlevel$shooter, Chip.MOMENTUM);
			}
		}
		// 狩猎时刻：命中就是这一轮的输入（第一枪挂标记，之后每次打中被标记的目标记账并查收割）。
		// 强击连发的额外攻击也算一次命中 —— 它确实又打了一遍伤害，记进账是应该的。
		if (this.gunlevel$hunt && this.gunlevel$shooter instanceof ServerPlayer hunter
				&& target instanceof LivingEntity victim && target.level() instanceof ServerLevel) {
			HuntState.onHit(hunter, victim, damage);
		}
		// 烈焰咆哮：击杀与爆头都记在**这把枪**上（用户口径「此武器击杀3次」，两把火枪各攒各的）。
		// 按发计：强击连发的额外攻击是递归进来的（outer=false），不重复叠计数。
		if (this.gunlevel$flameRoar && outer && this.gunlevel$shooter != null
				&& target.level() instanceof ServerLevel) {
			ItemStack gun = TaczCompat.findGunStack(this.gunlevel$shooter, this.gunlevel$gunId);
			boolean killed = target instanceof LivingEntity victim && !victim.isAlive();
			boolean refreshed = killed && GunRoarData.onKill(gun);
			refreshed |= this.gunlevel$headHit && GunRoarData.onHeadshot(gun);
			if (refreshed) {
				ChipHints.show(this.gunlevel$shooter, Chip.FLAME_ROAR, ChipHints.SKILL_READY);
			}
		}
		// 反应暴击是独立属性：初始跟随武器面板，概率封顶 20%、倍率封顶 150%
		CombatState.onBulletHit(target, this.gunlevel$shooter, this.gunlevel$element,
				this.gunlevel$level, damage,
				(float) LevelScaling.reactionCritChance(this.gunlevel$level), this.gunlevel$chips);

		if (this.gunlevel$pierceProc && outer) {
			ChipHints.show(this.gunlevel$shooter, Chip.ARMOR_BREAK);
		}

		if (this.gunlevel$crit && outer && target.level() instanceof ServerLevel serverWorld) {
			Vec3 pos = target.position().add(0, target.getBbHeight() * 0.5, 0);
			serverWorld.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x(), pos.y(), pos.z(),
					8, 0.15, 0.2, 0.15, 0.0);
		}

		// 强击连发：这一次命中有几率再结算一遍（不消耗子弹）。掷骰在命中这一刻，所以空开枪不吃概率；
		// 额外攻击递归走同一条结算，因此它同样触发元素反应与芯片命中效果，
		// 但它自己那一次不再掷骰（barraging 挡着），也就不会滚成几何级数。
		// 伤害要把这一发乘上去的暴击倍率除回去：描述里的「本次命中的 %1$s」是未暴击口径，额外攻击自己不吃暴击。
		// 极光追击的「每发只攒一次」标志要先放开：那条规则管的是穿透多个目标（一颗子弹算一发），
		// 而额外攻击是一次新的攻击，该攒。放开后穿透到的后续目标仍不会重复攒（递归里又置回了 true）。
		if (outer && this.gunlevel$barrage
				&& target.level().getRandom().nextFloat()
				< (float) (GunLevelMod.CONFIG.chipBarrageChancePerLevel * this.gunlevel$level)
				&& !target.isRemoved()) {
			this.gunlevel$barraging = true;
			// 额外攻击是一次新的攻击：极光命中和愈战愈勇的层数都该再记一次
			// （这两个标志管的是「同一发穿透多个目标只记一次」）
			this.gunlevel$auroraCounted = false;
			this.gunlevel$fervorCounted = false;
			try {
				this.tacAttackEntity(multipart, damage / this.gunlevel$critApplied
						* (float) GunLevelMod.CONFIG.chipBarrageDamageRatio, sources);
			} finally {
				this.gunlevel$barraging = false;
			}
			ChipHints.show(this.gunlevel$shooter, Chip.BARRAGE);
		}
	}
}
