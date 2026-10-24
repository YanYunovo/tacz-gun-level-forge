package com.eternal.gunlevel;

/**
 * 等级属性加成与元素改造配置。
 * 负值表示降低该项数值（散布、换弹时间）。缺少的字段由 loadConfig 用默认值补齐。
 */
public class LevelConfig {
	public int maxLevel = 5;
	public double damageBonusPerLevel = 0.10;
	public double speedBonusPerLevel = 0.05;      // 子弹初速
	public double fireRateBonusPerLevel = 0.08;   // 射速（缩短射击间隔）
	public double dispersionBonusPerLevel = -0.10; // 散布降低
	public double reloadBonusPerLevel = -0.05;     // 换弹时间缩短
	public int lv3Effect = 1;   // 1=撕裂弹（流血）
	public int lv5Effect = 1;   // 1=永恒贯穿

	/** 撕裂弹生效的最低等级（元素改造与等级无关，只看是否装了元素核芯） */
	public int elementMinLevel = 3;

	/** LV3 撕裂弹：命中后流血（持续伤害不暴击） */
	public double bleedDamagePerSecond = 2.0;
	public double bleedSeconds = 4.0;

	/** 元素附着持续时间，超时自动清除 */
	public double attachSeconds = 6.0;

	/**
	 * 武器面板暴击率：改造等级达到 critBaseMinLevel 起拥有 critChanceBase，
	 * LV5 永恒贯穿再叠 lv5CritChance。倍率为 critDamageMultiplier。
	 * 后续增加暴击率/暴击伤害的改造时，改这几项就够了。
	 */
	public double critChanceBase = 0.05;
	public double lv5CritChance = 0.15;
	public double critDamageMultiplier = 1.5;
	/** 吃到基础暴击率的最低等级（LV1 是没上过升级台的枪，默认不算已改造） */
	public int critBaseMinLevel = 2;

	/**
	 * 反应暴击：初始值跟随武器面板但封顶（概率最高 20%、倍率最高 120%）；
	 * 下面两个 bonus 是「反应暴击」类改造的加成，加在封顶之后，所以 +50% 反应爆伤能突破 120%。
	 * 改造做成按枪读 NBT 时，把每把枪的加成算进这两个入口即可。
	 */
	public double reactionCritChanceCap = 0.20;
	public double reactionCritMultiplierCap = 1.20;
	public double reactionCritChanceBonus = 0.0;
	public double reactionCritMultiplierBonus = 0.0;

	/** 超载（雷+火）：范围伤害，取触发那次武器伤害的比例，不破坏方块 */
	public double overloadDamageRatio = 0.30;
	public double overloadRadius = 4.0;

	/** 感电（雷+水）：每秒伤害（可暴击）、持续秒数、移速降低比例、传导半径 */
	public double shockDamagePerSecond = 1.5;
	public double shockSeconds = 4.0;
	public double shockSlow = 0.30;
	public double shockRadius = 3.0;

	/** 蒸发（火+水）反应触发时的增伤倍率，附加部分可暴击 */
	public double vaporizeMultiplier = 1.5;
	/** 融化（火+冰）反应触发时的增伤倍率，附加部分不暴击 */
	public double meltMultiplier = 2.0;

	/**
	 * 超导（雷+冰）：目标进入超导，superconductSeconds 内**受到的一切伤害**都穿走
	 * superconductArmorPierce 的原版护甲（挂在目标的护甲属性上），重复超导只刷新时间。
	 */
	public double superconductSeconds = 3.0;
	public double superconductArmorPierce = 0.30;

	/** 冻结（水+冰）：持续秒数、每次递减、下限、脱战重置 */
	public double freezeSeconds = 1.0;
	public double freezeDecay = 0.3;
	public double freezeMinimum = 0.1;
	public double freezeResetSeconds = 5.0;

	/**
	 * 绽放（水+草）：在触发反应的玩家脚下生成跟随玩家的苏生领域，
	 * 领域内全体玩家获得生命恢复，每再绽放一次升一级（最高 bloomRegenMaxLevel），同时刷新领域时间。
	 */
	public double bloomSeconds = 3.0;
	public double bloomRadius = 5.0;
	public int bloomRegenMaxLevel = 3;

	/** 燃烧（火+草）：burnSeconds 内每 0.5 秒一跳共 burnHits 跳，每跳固定伤害并补一份火附着 */
	public double burnSeconds = 3.0;
	public int burnHits = 6;
	public double burnDamagePerHit = 1.0;

	/** 激化（雷+草）：给触发反应的玩家一层 buff，射击间隔按 1/(1+比例) 缩短，重复激化只刷新时间 */
	public double quickenSeconds = 3.0;
	public double quickenFireRateBonus = 0.16;

	/**
	 * 扩散（风+任意已附着元素）：以反应目标为圆心的群伤（不暴击，范围与牵引半径一致，玩家不吃）、
	 * 一次挪几格、扩散后残留几份被扩散元素（每份可再触发一次任意反应）、内置冷却。
	 * 风自己永远不附着，目标身上没元素时风弹完全无效果。
	 */
	public double swirlDamage = 3.0;
	public double swirlRadius = 4.0;
	public double swirlPullBlocks = 1.0;
	public int swirlAttachStacks = 3;
	public double swirlCooldownSeconds = 1.0;

	/**
	 * 结晶（岩+任意已附着元素）：不给伤害，只给触发反应的射手叠结晶层数。
	 * 每级 2 颗金心（和原版伤害吸收同一套心，可叠加），1~4 级每次 3 秒，
	 * 叠满 5 级时持续时间固定 10 秒，这期间再打结晶无效（但反应元素照常消耗），
	 * buff 结束后从 0 重新叠。
	 */
	public double crystallizeSeconds = 3.0;
	public double crystallizeMaxSeconds = 10.0;
	public int crystallizeMaxLevel = 5;
	public double crystallizeHeartsPerLevel = 2.0;

	/**
	 * 战术芯片：LV chipSlotLevel 起 1 个槽、LV chipSecondSlotLevel 起 2 个槽，
	 * 在芯片装配台安装/拆卸。下面几项就是芯片说明里的数值，按「永恒改装等级」线性放大。
	 */
	public int chipSlotLevel = 3;
	public int chipSecondSlotLevel = 5;
	/** 连环暴击：常驻暴击率加成 */
	public double chipCritChancePerLevel = 0.02;
	/** 连环暴击：暴击后进入激化的持续时间与冷却，激化期间再叠一档暴击率/暴击伤害 */
	public double chipEmpoweredSeconds = 5.0;
	public double chipEmpoweredCooldownSeconds = 5.0;
	public double chipEmpoweredChancePerLevel = 0.03;
	public double chipEmpoweredDamagePerLevel = 0.10;
	/** 破甲强攻：触发概率，按等级线性放大（LV5 = 20%）；触发率 ×（倍率−1）就是无甲目标那档固定收益 */
	public double chipArmorPierceChancePerLevel = 0.04;
	/** 破甲强攻：触发那一发的最终伤害倍率（与额外穿甲同时生效） */
	public double chipArmorPierceMultiplier = 2.0;
	/**
	 * 破甲强攻：在武器自带穿甲之上再加多少穿甲，不是顶成 1。
	 * TACZ 默认包 23 把枪的 armor_ignore 跨 0（glock/aa12/rpg7）到 0.75（m95），
	 * 顶满等于给零穿甲的小口径枪 +100% 而高穿甲枪完全吃不到；加值把逐枪跨度压掉一半左右。
	 */
	public double chipArmorPierceBonus = 0.25;
	/**
	 * 高能弹匣：这一发打完后弹匣里还剩多少发才算「余量充足」。
	 * 用 >= 而不是 > 是因为 TACZ 在 {@code LivingEntityShoot} 里先扣弹再建子弹实体，
	 * 读到的已经是扣掉这一发之后的数；配 20 就等于玩家口径的「开火时弹匣里多于 20 发」。
	 */
	public int chipHighMagAmmo = 20;
	/** 高能弹匣：加在基础伤害上的点数（每级），和武器面板伤害同区，所以它会再被等级加成乘一遍 */
	public double chipHighMagDamagePerLevel = 0.25;
	/** 高能弹匣：另开一档独立的伤害加成（每级） */
	public double chipHighMagBonusPerLevel = 0.025;
	/**
	 * 极光追击：累计多少次命中才攒满「极光弹匣已就绪」。
	 * 只有真正打中目标才算（空开枪不计），且极光匣期间不累计，所以要打完一匣再重新攒。
	 */
	public int chipAuroraHits = 20;
	/**
	 * 极光追击：极光匣期间的射速加成（每级），LV5 = +40%。
	 * 不计换弹时间的口径下，m249 约 +34%、30 发步枪约 +27%、rpk 约 +30%，
	 * 逐枪差异来自「加速窗口占整个周期的比例」，弹匣越大越划算，见 tools/_aurora.py。
	 */
	public double chipAuroraRatePerLevel = 0.08;
	/** 极光追击：特殊换弹后弹匣容量翻几倍（翻倍不加伤害，只是把加速窗口拉长） */
	public double chipAuroraMagMultiplier = 2.0;
	/**
	 * 强击连发：每次命中追加一次额外攻击的概率（每级）。LV5 = 30%。
	 * 额外攻击是把这一次的命中原样再结算一遍（同样吃护甲与武器自带穿甲、同样攒命中、同样触发
	 * 元素反应与芯片命中效果），但不消耗子弹，所以提升率恒等于「概率 × 额外攻击伤害比例」，
	 * 逐枪都一样，不像别的芯片要看弹匣大小 —— 想要 +30% 就配 30% × 100%。
	 */
	public double chipBarrageChancePerLevel = 0.06;
	/** 强击连发：额外攻击的伤害是这一次命中的百分之几 */
	public double chipBarrageDamageRatio = 1.0;
	/**
	 * 势如破竹：命中时对射手开一档加成。
	 * 加成随「永恒改装等级」线性放大：暴击率 +2%×等级（LV5 = 10%）、
	 * 非头部伤害 +9.6%×等级（LV5 = 48%，2026-10-11 用户从 26% 改口到 48%）。
	 * 触发条件只有射程：目标必须在 chipMomentumRange 格内。
	 */
	public double chipMomentumRange = 10.0;
	/**
	 * 势如破竹：加成窗口长度（秒）。这 6 秒既是生效时间也是冷却 ——
	 * 期间再命中不会重新给满，只按 chipMomentumSeconds 逐秒衰减掉 1/6；
	 * 只有击杀才把档位在刷新回满（照样继续逐秒衰减）。
	 */
	public double chipMomentumSeconds = 6.0;
	public double chipMomentumCritChancePerLevel = 0.02;
	public double chipMomentumDamagePerLevel = 0.096;
	/** 凝晶召唤：岩元素造物嘲讽的半径（格），圈内敌对生物会把目标换成它 */
	public double crystalSummonTauntRadius = 5.0;
	/** 凝晶召唤：抗性提升光环的半径（格），以造物为圆心 */
	public double crystalSummonBuffRadius = 8.0;
	/**
	 * 凝晶召唤：造物能承受几次攻击。用户口径就是「被攻击三次后死去」，
	 * 所以它不看血量，只数次数（玩家和射手造成的攻击不计入，见 RockConstructEntity）。
	 */
	public int crystalSummonHits = 3;
	/**
	 * 凝晶召唤：每次发放抗性提升时给多长（秒）。光环每 10 tick 续一次，
	 * 所以离开半径后最多再残留这个时长，写太短会一顿一顿地闪 buff 图标。
	 */
	public double crystalSummonBuffSeconds = 3.0;
	/**
	 * 凝晶召唤：造物的牵绳半径（格）。离最近玩家超过这个距离就自动消散（用户 2026-10-12 补的设定），
	 * 同时它也是造物主动索敌的最大半径 —— 追出这个圈就不追了，两个口径必须是同一个数，
	 * 否则会出现「追出去但还没被收回」的中间态。
	 */
	public double crystalSummonLeashRadius = 15.0;
	/**
	 * 净化领域：装配这颗芯片时，绽放给的苏生领域多续这么多秒（用户口径「持续时间增加 2 秒」）。
	 * 它是「维持领域」这条玩法的来源 —— 三档强化都只在领域还在的时候生效。
	 */
	public double chipPurifyExtraSeconds = 2.0;
	/**
	 * 净化领域：血量变动记录的封顶值。圈内全体玩家的回血和掉血都按绝对值进账（用户口径「回复+受伤都算」），
	 * 到顶就不再涨，后面两档能不能吃满取决于这个数。
	 */
	public double chipPurifyRecordCap = 100.0;
	/** 净化领域：记录值到这一档，对领域内全体非玩家单位炸一次固定伤害（一片领域只炸一次） */
	public double chipPurifyNovaAt = 20.0;
	/** 净化领域：新星伤害的半径（格），比领域本身大，以领域圆心算 */
	public double chipPurifyNovaRadius = 8.0;
	/** 净化领域：新星那一下的固定伤害点数（走 indirect_magic，无视护甲与盾牌） */
	public double chipPurifyNovaDamage = 10.0;
	/** 净化领域：记录值到这一档，圈内玩家每次攻击开始附加额外伤害 */
	public double chipPurifyBonusAt = 40.0;
	/**
	 * 净化领域：附加伤害的系数 —— {@code 系数 × 永恒改装等级 × 当前记录值}（点数，不是倍率）。
	 * 用户口径是「0.02*永恒改造等级*血量变动值」，记录值按实时值算，所以攒得越多越疼。
	 */
	public double chipPurifyDamagePerRecord = 0.02;
	/** 净化领域：记录值到这一档，圈内玩家免疫负面状态效果（已经挂在身上的当场清掉） */
	public double chipPurifyCleanseAt = 60.0;
	/**
	 * 灵风之祝：主动技能「风的护佑」的移速提升比例（0.30 = +30%）。
	 * 走 GENERIC_MOVEMENT_SPEED 的 MULTIPLY_TOTAL 修正，而不用原版速度效果 ——
	 * 速度效果一档只有 20%，档位表达不出 30% 这个数。
	 */
	public double chipWindGuardSpeed = 0.30;
	/** 灵风之祝：风的护佑持续多少秒；护佑期间下一次扩散按强化版结算 */
	public double chipWindGuardSeconds = 10.0;
	/** 灵风之祝：风的护佑冷却（秒）。用户口径「半分钟」；冷却结束那一刻飘一行「风的护佑已就绪」 */
	public double chipWindGuardCooldownSeconds = 30.0;
	/** 迅雷之音：射速提升（换算成射击间隔 = 除以 1+此值） */
	public double chipThunderRate = 0.16;
	/** 迅雷之音：主弹匣容量减少的比例 */
	public double chipThunderMagPenalty = 0.20;
	/** 迅捷闪冲：冲刺距离（格），撞墙就停在墙前 */
	public double chipThunderDashBlocks = 3.0;
	/** 迅捷闪冲：冲刺同时立刻补进主弹匣的子弹数（不会顶过容量） */
	public int chipThunderDashAmmo = 10;
	/** 迅捷闪冲：冷却秒数 */
	public double chipThunderCooldownSeconds = 12.0;
	/** 迅捷闪冲：每命中多少次减 1 秒冷却 */
	public int chipThunderHitsPerSecond = 5;
	/**
	 * 愈战愈勇：进入战斗（第一次命中）就给的基础伤害加成。
	 * 这一档不吃永恒改装等级，所以低等级枪的起手相对更划算。
	 */
	public double chipFervorBase = 0.03;
	/** 愈战愈勇：此后每次命中再叠的比例，乘永恒改装等级 */
	public double chipFervorPerHit = 0.001;
	/** 愈战愈勇：叠加上限，乘永恒改装等级（LV5 = 75%） */
	public double chipFervorCapPerLevel = 0.15;
	/** 愈战愈勇：连续这么多秒没有命中就清零（脱战重置） */
	public double chipFervorResetSeconds = 3.0;
	/** 烈焰咆哮：拿着这把枪时的移速惩罚（0.40 = −40%） */
	public double chipRoarSpeedPenalty = 0.40;
	/** 烈焰咆哮：切走武器后移速惩罚还要拖多久（防止换枪白嫖掉惩罚） */
	public double chipRoarLingeringSeconds = 3.0;
	/** 烈焰咆哮：爆头那发的伤害加成，乘永恒改装等级 */
	public double chipRoarHeadDamagePerLevel = 0.04;
	/** 烈焰咆哮：身体命中判成爆头的初始概率 */
	public double chipRoarFakeHeadChance = 0.10;
	/** 烈焰咆哮：暴击率每 1% 给上面那个概率加多少（0.005 = 0.5%） */
	public double chipRoarFakeHeadPerCrit = 0.005;
	/** 烈焰咆哮：概率加成最多累计到多少（0.40 = +40%） */
	public double chipRoarFakeHeadBonusCap = 0.40;
	/** 咆哮一击：这一匣只装几发（特殊弹的「一」） */
	public int chipRoarMagAmmo = 1;
	/** 咆哮一击：特殊弹的基础伤害倍率（4.0 = 原本的 400%） */
	public double chipRoarDamageMultiplier = 4.0;
	/** 咆哮一击：特殊弹的基础伤害加成有效秒数 */
	public double chipRoarEmpowerSeconds = 5.0;
	/** 咆哮一击：用掉之后要这把枪再击杀多少次才刷新 */
	public int chipRoarKillsToRefresh = 3;
	/** 咆哮一击：或者再命中头部多少次才刷新 */
	public int chipRoarHeadshotsToRefresh = 5;
	/** 狩猎时刻：每次命中标记目标，把该次伤害的这个比例记进账 */
	public double chipHuntRecordRatio = 0.10;
	/** 狩猎时刻：切枪提前收尾时，记录伤害按这个比例结算（正常收尾是 1） */
	public double chipHuntSwapRatio = 0.50;
	/** 狩猎时刻：连续这么多秒没有命中被标记目标就清掉标记（脱战） */
	public double chipHuntTimeoutSeconds = 5.0;
	/** 狩猎时刻：命中次数到这一档，给伤害/暴击/移速三档加成 */
	public int chipHuntBonusStacks = 15;
	/** 狩猎时刻：三档加成的伤害提升 */
	public double chipHuntBonusDamage = 0.10;
	/** 狩猎时刻：三档加成的暴击率提升（加在武器面板那一次掷骰上） */
	public double chipHuntBonusCrit = 0.10;
	/** 狩猎时刻：三档加成的移速提升（两端各挂一次） */
	public double chipHuntBonusSpeed = 0.10;
	/** 狩猎时刻：命中次数到这一档，开爆发窗口 */
	public int chipHuntBurstStacks = 30;
	/** 狩猎时刻：爆发窗口持续秒数（期间不消耗弹药、射速提升，结束时结算记录伤害） */
	public double chipHuntBurstSeconds = 3.0;
	/** 狩猎时刻：爆发窗口的射速提升（换算成射击间隔 = 除以 1+此值） */
	public double chipHuntBurstFireRate = 0.16;
	/** 蓝芯片·射速改造：射速提升（换算成射击间隔 = 除以 1+此值），定值不乘等级 */
	public double chipBlueFireRate = 0.05;
	/** 蓝芯片·暴击改造：暴击率提升，定值不乘等级 */
	public double chipBlueCrit = 0.05;
	/** 蓝芯片·伤害改造：伤害加成，定值不乘等级 */
	public double chipBlueDamage = 0.05;
	/** 蓝芯片·头部伤害改造：头部伤害加成，定值不乘等级 */
	public double chipBlueHeadDamage = 0.10;
	/** 蓝芯片·暴击伤害改造：暴击伤害倍率的加值，定值不乘等级 */
	public double chipBlueCritDamage = 0.10;

	/** 蓝芯片·换弹改造：换弹时间缩短比例，定值不乘等级 */
	public double chipBlueReload = 0.10;
	/** 蓝芯片·弹匣扩容：主弹匣容量提升比例，定值不乘等级（与极光翻倍、迅雷减容量同一条链连乘，四舍五入取整） */
	public double chipBlueMagSize = 0.10;
	/** 蓝芯片·散布改造：散布降低比例，定值不乘等级 */
	public double chipBlueSpread = 0.10;
	/** 蓝芯片·弹速改造：子弹初速提升比例，定值不乘等级 */
	public double chipBlueBulletSpeed = 0.10;
	/** 蓝芯片·穿甲改造：常驻无视的原版护甲比例，定值不乘等级（与武器自带穿甲、破甲强攻的加值相加，封顶 100%） */
	public double chipBlueArmorPierce = 0.05;
	/** 蓝芯片·爆炸改造：重武器爆炸段伤害提升比例，定值不乘等级 */
	public double chipBlueExplosion = 0.10;
	/** 蓝芯片·穿透改造：追加的穿透段数，定值不乘等级 */
	public int chipBluePierce = 1;
	/** 紫芯片·抵近射击：算「近距」的距离（格），口径与势如破竹一致：命中时射手到目标的距离 */
	public double chipPurpleCloseRange = 5.0;
	/** 紫芯片·抵近射击：近距那一发的伤害加成 */
	public double chipPurpleCloseDamage = 0.15;
	/** 紫芯片·抵近射击：近距且真暴击时，暴击伤害倍率的加值 */
	public double chipPurpleCloseCritDamage = 0.10;
	/** 紫芯片·凝神打击：射手血量比例高于此值才吃加成 */
	public double chipPurpleFocusHealthRatio = 0.75;
	/** 紫芯片·凝神打击：加成幅度 */
	public double chipPurpleFocusDamage = 0.20;
	/** 紫芯片·节奏射击：每发命中叠一层 */
	public double chipPurpleRhythmPerHit = 0.015;
	/** 紫芯片·节奏射击：层数封顶（14 发打满） */
	public double chipPurpleRhythmCap = 0.20;
	/** 紫芯片·节奏射击：连续多少秒没有命中就整条清零（打在方块上的落空那发立刻清零，不等这里） */
	public double chipPurpleRhythmResetSeconds = 3.0;
	/** 紫芯片·无坚不摧：目标血量比例高于此值才吃暴击率加成 */
	public double chipPurpleUnstoppableHealthRatio = 0.60;
	/** 紫芯片·无坚不摧：对该目标的暴击率加值（同一枚骰子只抬高及格线，不补掷） */
	public double chipPurpleUnstoppableCrit = 0.20;
	/** 紫芯片·勇者无惧：主手持枪时的伤害加成 */
	public double chipPurpleFearlessDamage = 0.20;
	/** 紫芯片·勇者无惧：作为代价，受到的伤害也放大（护甲计算之前） */
	public double chipPurpleFearlessDamageTaken = 0.15;
	/** 紫芯片·灵动步伐：移速加成（MULTIPLY_TOTAL，两端各挂一份） */
	public double chipPurpleNimbleSpeed = 0.20;
	/** 紫芯片·灵动步伐：作为代价扣掉的护甲点数（ADDITION，属性下限 0，穿不满就归零） */
	public double chipPurpleNimbleArmorPenalty = 4.0;
	/** 芯片触发提示（经验条上方那几行小字）总开关，游戏内「设置」里也能改 */
	public boolean chipHintEnabled = true;
	/** 提示第一行离屏幕底部多少像素（原版 action bar 在 72，默认再往上抬一行） */
	public int chipHintBottomMargin = 84;

	public static LevelConfig defaults() {
		return new LevelConfig();
	}
}
