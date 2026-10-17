package com.eternal.gunlevel.menu;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/**
 * 各级升级所需材料（单一物品类型 + 数量）与特效名称。
 */
public final class UpgradeCosts {

	public record Cost(Item item, int count) {
	}

	private UpgradeCosts() {
	}

	/** 从 level 升到 level+1 的成本；null 表示已满级 */
	public static Cost costFor(int level) {
		return switch (level) {
			case 1 -> new Cost(GunLevelMod.COMPONENT_I, 1);
			case 2 -> new Cost(GunLevelMod.COMPONENT_II, 1);
			case 3 -> new Cost(GunLevelMod.COMPONENT_III, 1);
			case 4 -> new Cost(GunLevelMod.COMPONENT_IV, 1);
			default -> null;
		};
	}

	public static boolean matches(int level, ItemStack material) {
		Cost cost = costFor(level);
		return cost != null && !material.isEmpty()
				&& material.getItem() == cost.item()
				&& material.getCount() >= cost.count();
	}

	/** 是不是某一级的升级材料（材料槽的插入过滤） */
	public static boolean isAnyMaterial(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		for (int level = 1; level <= 4; level++) {
			Cost cost = costFor(level);
			if (cost != null && stack.getItem() == cost.item()) {
				return true;
			}
		}
		return false;
	}

	public static Component effectName(int level) {
		return switch (level) {
			case 3 -> Component.translatable("effect.tacz_gun_level.lv3");
			case 5 -> Component.translatable("effect.tacz_gun_level.lv5");
			default -> Component.empty();
		};
	}

	/** tooltip/lore 用：等级描述文本 */
	public static Component loreFor(int level) {
		net.minecraft.network.chat.MutableComponent base = Component.translatable("tooltip.tacz_gun_level.level()", GunLevelData.displayName(level));
		Component fx = effectName(level);
		return fx.getString().isEmpty() ? base : base.append(Component.literal("\n")).append(fx);
	}
}
