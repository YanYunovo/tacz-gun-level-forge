package com.eternal.gunlevel.effect;

import java.util.Locale;

/**
 * 战术芯片：装在枪械上的自建配件，槽位数由永恒改装等级决定（LV3 一颗、LV5 两颗），
 * 在芯片装配台安装/拆卸，效果在子弹命中结算里生效。
 */
public enum Chip {
	/** 连环暴击 */
	CHAIN_CRIT(ChipTier.GOLD),
	/** 破甲强攻 */
	ARMOR_BREAK(ChipTier.GOLD),
	/** 高能弹匣：弹匣余量足够时加基础伤害与伤害加成，常驻条件、不掷骰，所以没有触发提示 */
	HIGH_MAG(ChipTier.GOLD),
	/** 极光追击：累计命中攒满后下一次换弹变成特殊换弹（弹匣翻倍 + 射速加成），这一匣期间不再攒命中 */
	AURORA(ChipTier.GOLD),
	/** 强击连发：每次命中有几率追加一次不消耗子弹的额外攻击 */
	BARRAGE(ChipTier.GOLD),
	/**
	 * 势如破竹：对近距目标造成伤害后开一档 6 秒的加成（暴击率 + 非头部伤害），
	 * 逐秒衰减 1/6，期间再命中不重开，只有击杀才把档位刷回满。
	 */
	MOMENTUM(ChipTier.GOLD),
	/**
	 * 凝晶召唤：触发结晶反应时在射手脚下召唤一只岩元素造物，替玩家挨打并给圈内玩家抗性提升。
	 * 造物存在期间不能再召唤第二只。
	 */
	CRYSTAL_SUMMON(ChipTier.GOLD),
	/**
	 * 净化领域：苏生领域（绽放）的强化芯片。装它时绽放给的领域多持续一段时间，
	 * 并在领域存续期间记录圈内全体玩家的血量变动值（回血与掉血都按绝对值进账，有上限），
	 * 记录值分三档点亮：够一档炸一次固定伤害、够二档圈内玩家攻击附加伤害、够三档圈内玩家免疫负面效果。
	 * 三档都只在领域还在的时候有效，领域一消散记录值和档位全部清零。
	 */
	PURIFY_DOMAIN(ChipTier.GOLD),
	/**
	 * 灵风之祝：风元素枪械的被动 + 第一颗带主动技能的芯片。
	 * 手持装了它的风枪能二段跳、不吃摔落；按技能键开「风的护佑」，
	 * 期间移速提升、下一次扩散把牵引的目标直接拽到反应目标那一格。
	 */
	WIND_BLESSING(ChipTier.GOLD),
	/**
	 * 迅雷之音：射速提升、主弹匣容量换射速；装雷核芯时多一个主动技能「迅捷闪冲」——
	 * 按键向面朝方向冲刺并立刻补一发弹匣的零头，命中攒次数抵冷却。
	 */
	THUNDER_VOICE(ChipTier.GOLD),
	/**
	 * 愈战愈勇：命中才算进入战斗，第一次命中给一档基础伤害加成，
	 * 之后每次命中再叠一层（按永恒改装等级放大），封顶也按等级算；
	 * 连续一段时间没有命中（脱战）就把层数清零。
	 */
	FERVOR(ChipTier.GOLD),
	/**
	 * 烈焰咆哮：拿着就慢（切走还要再拖 3 秒），换一份按等级放大的爆头增伤，
	 * 并且有小概率把身体命中判成爆头；装火核芯时多一个主动技能「咆哮一击」——
	 * 把这一匣压成只剩一发特殊弹，五秒内打出它吃 400% 基础伤害，
	 * 用完要靠这把枪再打满 3 次击杀或 5 次爆头才刷新。
	 */
	FLAME_ROAR(ChipTier.GOLD),
	/**
	 * 狩猎时刻：命中给目标挂一个标记（不会转移给别人），之后每次打中被标记的目标都攒一层
	 * 记录值（该次伤害的 10%）与命中次数；目标血量跌到记录值以下就立刻按固定伤害收割。
	 * 次数到 15 给一档伤害/暴击/移速加成（跟着标记走），到 30 开 3 秒「不耗弹 + 射速」爆发，
	 * 爆发结束结算记录伤害并收尾。切枪提前收尾只按 50% 结算，脱战 5 秒直接清标记。
	 */
	HUNTING_MOMENT(ChipTier.GOLD),
	/** 射速改造：固定提升 5% 射速，不乘永恒改装等级 */
	FIRE_RATE_MOD(ChipTier.BLUE),
	/** 暴击改造：固定提升 5% 暴击率 */
	CRIT_MOD(ChipTier.BLUE),
	/** 伤害改造：固定提升 5% 伤害加成 */
	DAMAGE_MOD(ChipTier.BLUE),
	/** 头部伤害改造：固定提升 10% 头部伤害加成 */
	HEAD_DAMAGE_MOD(ChipTier.BLUE),
	/** 暴击伤害改造：固定提升 10% 暴击伤害 */
	CRIT_DAMAGE_MOD(ChipTier.BLUE);

	/** 品级决定贴图、奖池与稀有度；槽位不分品级（LV3 一颗、LV5 两颗，蓝金混装） */
	private final ChipTier tier;

	Chip(ChipTier tier) {
		this.tier = tier;
	}

	public ChipTier tier() {
		return tier;
	}

	public String displayName() {
		return switch (this) {
			case CHAIN_CRIT -> "连环暴击";
			case ARMOR_BREAK -> "破甲强攻";
			case HIGH_MAG -> "高能弹匣";
			case AURORA -> "极光追击";
			case BARRAGE -> "强击连发";
			case MOMENTUM -> "势如破竹";
			case CRYSTAL_SUMMON -> "凝晶召唤";
			case PURIFY_DOMAIN -> "净化领域";
			case WIND_BLESSING -> "灵风之祝";
			case THUNDER_VOICE -> "迅雷之音";
			case FERVOR -> "愈战愈勇";
			case FLAME_ROAR -> "烈焰咆哮";
			case HUNTING_MOMENT -> "狩猎时刻";
			case FIRE_RATE_MOD -> "射速改造";
			case CRIT_MOD -> "暴击改造";
			case DAMAGE_MOD -> "伤害改造";
			case HEAD_DAMAGE_MOD -> "头部伤害改造";
			case CRIT_DAMAGE_MOD -> "暴击伤害改造";
		};
	}

	/**
	 * 有没有主动技能（要玩家按键放的那种）。按键一次只交给「槽位顺序里第一颗有主动技能的芯片」，
	 * 见 {@code WindGuardState#requestSkill}。
	 */
	public boolean hasActiveSkill() {
		return this == WIND_BLESSING || this == THUNDER_VOICE || this == FLAME_ROAR;
	}

	/** 主动技能冷却完毕的提示文案：每颗芯片各说各的，别拿风的句子去提示雷 */
	public String skillReadyKey() {
		return switch (this) {
			case WIND_BLESSING -> "msg.tacz_gun_level.wind.ready";
			case THUNDER_VOICE -> "msg.tacz_gun_level.thunder.ready";
			case FLAME_ROAR -> "msg.tacz_gun_level.roar.ready";
			default -> "msg.tacz_gun_level.skill.ready";
		};
	}

	/** 短名 lang key（HUD 提示用，跟着客户端语言走） */
	public String nameKey() {
		return "chip.tacz_gun_level." + name().toLowerCase(Locale.ROOT);
	}

	/** 注册名，与物品 id、物品模型文件名一致 */
	public String itemId() {
		return "tactical_chip_" + name().toLowerCase(Locale.ROOT);
	}
}
