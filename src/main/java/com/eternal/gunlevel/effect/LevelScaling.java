package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.LevelConfig;
import com.eternal.gunlevel.util.TaczCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/**
 * 等级加成的运行时入口：窗口开在 TACZ 真正读取属性的调用点上
 * （射击间隔 getShootCoolDown、散布/初速 ModernKineticGunItem#shoot、
 * 换弹 tickReloadState、属性面板 ClientGunTooltip#getText）。
 * 伤害只在命中时结算（EntityKineticBulletMixin），所以面板窗口用 DISPLAY 标记，
 * 避免同一线程里既给面板缩放又给实际伤害缩放。
 */
public final class LevelScaling {
	/**
	 * ThreadLocal 窗口栈：TACZ 会在外层窗口里再调用内层读取点
	 * （shoot -> getShootCoolDown、getClientShootCoolDown -> getCoolDown），
	 * 单层值会被内层的关闭窗口提前清掉，所以必须压栈。
	 * 窗口同时记住射手，射速类 buff（激化）要从射手身上读。
	 */
	private static final ThreadLocal<java.util.ArrayDeque<Window>> WINDOWS =
			ThreadLocal.withInitial(java.util.ArrayDeque::new);

	private static final class Window {
		final int level;
		final int display;
		final LivingEntity shooter;

		Window(int level, int display, LivingEntity shooter) {
			this.level = level;
			this.display = display;
			this.shooter = shooter;
		}
	}

	private LevelScaling() {
	}

	public static void beginWindow(int level) {
		push(level, 0, null);
	}

	/** 带射手的窗口：射击间隔除了等级缩放还要读射手身上的激化层数 */
	public static void beginWindow(int level, LivingEntity shooter) {
		push(level, 0, shooter);
	}

	/** 面板窗口：显示值按等级缩放，但不影响实际结算 */
	public static void beginDisplay(int level) {
		push(level, 1, null);
	}

	private static void push(int level, int display, LivingEntity shooter) {
		WINDOWS.get().push(new Window(level, display, shooter));
	}

	public static void endWindow() {
		java.util.ArrayDeque<Window> stack = WINDOWS.get();
		if (!stack.isEmpty()) {
			stack.pop();
		}
	}

	/** 每 tick 兜底清栈，防止异常路径漏关导致等级串到别的枪械上 */
	public static void clear() {
		WINDOWS.get().clear();
	}

	public static int currentLevel() {
		java.util.ArrayDeque<Window> stack = WINDOWS.get();
		return stack.isEmpty() ? 0 : stack.peek().level;
	}

	public static boolean inDisplay() {
		java.util.ArrayDeque<Window> stack = WINDOWS.get();
		return !stack.isEmpty() && stack.peek().display == 1;
	}

	/** 当前窗口的射手（面板/换弹等窗口没有射手，返回 null） */
	public static LivingEntity currentShooter() {
		java.util.ArrayDeque<Window> stack = WINDOWS.get();
		return stack.isEmpty() ? null : stack.peek().shooter;
	}

	/** 手持物品的枪械等级（非枪械返回 0） */
	public static int levelOfStack(ItemStack stack) {
		return TaczCompat.isGun(stack) ? GunLevelData.getLevel(stack) : 0;
	}

	/** 基准为 LV1：未升级的枪械不吃任何加成 */
	private static int tier(int level) {
		return Math.max(0, level - GunLevelData.MIN_LEVEL);
	}

	public static double damageScale(int level) {
		return 1.0 + GunLevelMod.CONFIG.damageBonusPerLevel * tier(level);
	}

	public static double speedScale(int level) {
		return 1.0 + GunLevelMod.CONFIG.speedBonusPerLevel * tier(level);
	}

	/** 射击间隔缩短倍率：等级越高越小 */
	public static double intervalScale(int level) {
		return Math.max(0.4, 1.0 - GunLevelMod.CONFIG.fireRateBonusPerLevel * tier(level));
	}

	/**
	 * 激化（雷+草）折算成射击间隔倍率：射速 +16% 就是间隔乘 1/1.16，
	 * 层数（amplifier+1）线性叠加，方便以后做加激化层数的改造。
	 */
	public static double quickenIntervalScale(LivingEntity shooter) {
		if (shooter == null) {
			return 1.0;
		}
		MobEffectInstance quickening = shooter.getEffect(GunLevelMod.QUICKENING);
		if (quickening == null) {
			return 1.0;
		}
		double boost = GunLevelMod.CONFIG.quickenFireRateBonus * (quickening.getAmplifier() + 1);
		return 1.0 / (1.0 + boost);
	}

	/** 散布/后坐力缩放倍率（配置为负值=降低） */
	public static double spreadScale(int level) {
		return Math.max(0.2, 1.0 + GunLevelMod.CONFIG.dispersionBonusPerLevel * tier(level));
	}

	/** 换弹时间倍率 */
	public static double reloadScale(int level) {
		return Math.max(0.4, 1.0 + GunLevelMod.CONFIG.reloadBonusPerLevel * tier(level));
	}

	/**
	 * 武器面板暴击率：改造后基础 5%，LV5 永恒贯穿再 +15%，合计 20%。
	 * 子弹命中和反应/感电的暴击判定都只走这里，方便后续改造继续往上叠。
	 */
	public static double critChance(int level) {
		LevelConfig c = GunLevelMod.CONFIG;
		double chance = level >= c.critBaseMinLevel ? c.critChanceBase : 0.0;
		if (level >= 5 && c.lv5Effect == 1) {
			chance += c.lv5CritChance;
		}
		return chance;
	}

	/** 武器面板暴击率（含连环暴击芯片的常驻加成） */
	public static double critChance(int level, boolean chainCrit) {
		return critChance(level)
				+ (chainCrit ? GunLevelMod.CONFIG.chipCritChancePerLevel * level : 0.0);
	}

	public static double critMultiplier() {
		return GunLevelMod.CONFIG.critDamageMultiplier;
	}

	/** 暴击倍率（含激化期间的暴击伤害加成，来自连环暴击芯片） */
	public static double critMultiplier(int level, boolean empowered) {
		return critMultiplier()
				+ (empowered ? GunLevelMod.CONFIG.chipEmpoweredDamagePerLevel * level : 0.0);
	}

	/**
	 * 高能弹匣：加在基础伤害上的点数（每级）。
	 * 返回的是点数不是倍率，调用点把它加进面板伤害那一档，所以它会再被 damageScale 乘一遍。
	 */
	public static double highMagBasePoints(int level) {
		return GunLevelMod.CONFIG.chipHighMagDamagePerLevel * level;
	}

	/** 高能弹匣：余量充足那一发另开的伤害加成倍率 */
	public static double highMagBonusScale(int level) {
		return 1.0 + GunLevelMod.CONFIG.chipHighMagBonusPerLevel * level;
	}

	/**
	 * 极光追击：极光匣期间的射击间隔倍率。射速 +8%×等级 折算成间隔乘 1/(1+0.08×等级)，
	 * 效果不在（或窗口里没有等级）就原样返回 1，不干扰其他枪。
	 */
	public static double auroraIntervalScale(LivingEntity shooter, int level) {
		if (shooter == null || level <= 0 || !AuroraState.isAuroraMag(shooter)) {
			return 1.0;
		}
		return 1.0 / (1.0 + GunLevelMod.CONFIG.chipAuroraRatePerLevel * level);
	}

	/**
	 * 反应暴击率 = min(武器面板暴击率, 20%) + 改造加成。
	 * 封顶只卡跟随武器的那段初始值，改造加成叠在封顶之后。
	 */
	public static double reactionCritChance(int level) {
		LevelConfig c = GunLevelMod.CONFIG;
		return Math.min(critChance(level), c.reactionCritChanceCap) + c.reactionCritChanceBonus;
	}

	/** 反应暴击倍率 = min(武器面板爆伤, 120%) + 改造加成 */
	public static double reactionCritMultiplier() {
		LevelConfig c = GunLevelMod.CONFIG;
		return Math.min(critMultiplier(), c.reactionCritMultiplierCap) + c.reactionCritMultiplierBonus;
	}
}
