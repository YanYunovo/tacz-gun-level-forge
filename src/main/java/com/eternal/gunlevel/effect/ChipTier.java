package com.eternal.gunlevel.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 战术芯片的品级。同一套 {@link Chip} 枚举、同一套槽位与装配台，品级只决定三件事：
 * 用哪张贴图、归哪个奖池（随机包/自选包/破碎件都按品级分）、以及物品稀有度那一行颜色。
 *
 * <p>新增一颗芯片时必须在 {@link Chip} 的构造参数里写明品级 —— 编译器会逼着表态，
 * 不会像「默认金芯片」那样把以后加的紫芯片静默归错池子。
 */
public enum ChipTier {
	/** 金芯片：稀有，走「随机金芯片 → 破碎的金芯片 → 自选包」那条链 */
	GOLD,
	/** 蓝芯片：常见，数值是定值、不乘永恒改装等级 */
	BLUE,
	/**
	 * 紫芯片：比金常见、比蓝强一档（用户 2026-10-22 定的档次顺序：蓝 &lt; 紫 &lt; 金），
	 * 数值同样是定值、不乘等级。稀有度因此排在金下面一档。
	 */
	PURPLE;
	// 再加一档只需在这里加一项：奖池、贴图、配方、创造页都按 id 自动分叉

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** 三件经济件的注册名，与物品模型文件名一致：random_gold_chip、broken_blue_chip、gold_chip_pack */
	public String randomChipId() {
		return "random_" + id() + "_chip";
	}

	public String brokenChipId() {
		return "broken_" + id() + "_chip";
	}

	public String chipPackId() {
		return id() + "_chip_pack";
	}

	/** 这一档的全部芯片，按 {@link Chip} 的声明顺序 —— 奖池、自选界面、创造分类都靠它 */
	public List<Chip> chips() {
		List<Chip> out = new ArrayList<>();
		for (Chip chip : Chip.values()) {
			if (chip.tier() == this) {
				out.add(chip);
			}
		}
		return out;
	}
}
