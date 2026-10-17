package com.eternal.gunlevel;

import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.effect.LevelScaling;
import com.eternal.gunlevel.item.GunChipData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

/**
 * 枪械等级存取：写入枪物品 NBT（随物品持久化，服务端与客户端一致）。
 */
public final class GunLevelData {
	public static final String TAG = "tacz_gun_level";
	public static final int MIN_LEVEL = 1;

	private GunLevelData() {
	}

	public static int getLevel(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		CompoundTag nbt = stack.getTag();
		if (nbt == null || !nbt.contains(TAG)) {
			return MIN_LEVEL; // 未升级的枪默认 LV1
		}
		return Math.max(MIN_LEVEL, nbt.getInt(TAG));
	}

	public static void setLevel(ItemStack stack, int level) {
		stack.getOrCreateTag().putInt(TAG, level);
	}

	public static String displayName(int level) {
		return "LV." + level;
	}

	/** 在 Lore 里写入等级、属性加成与特效说明（幂等，重复升级不会叠加多行） */
	public static void applyLore(ItemStack stack, int level) {
		CompoundTag display = stack.getOrCreateTagElement("display");
		net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
		lore.add(nbtString("§b永恒改装 " + displayName(level)));
		String stats = statLine(level);
		if (!stats.isEmpty()) {
			lore.add(nbtString("§7" + stats));
		}
		if (level >= 3 && GunLevelMod.CONFIG.lv3Effect == 1) {
			lore.add(nbtString("§6撕裂弹：命中造成流血，不占用受伤间隔"));
		}
		if (level >= 5 && GunLevelMod.CONFIG.lv5Effect == 1) {
			lore.add(nbtString("§5永恒贯穿：额外穿透多重目标"));
		}
		double critChance = LevelScaling.critChance(level,
				GunChipData.get(stack).contains(Chip.CHAIN_CRIT));
		if (critChance > 0) {
			lore.add(nbtString("§d暴击率 " + percent(critChance) + "%｜暴击伤害 "
					+ percent(LevelScaling.critMultiplier()) + "%"));
			lore.add(nbtString("§c反应暴击率 " + percent(LevelScaling.reactionCritChance(level))
					+ "%｜反应暴击伤害 " + percent(LevelScaling.reactionCritMultiplier()) + "%"));
		}
		String element = elementName(stack);
		if (element != null) {
			lore.add(nbtString("§e元素核芯：" + element + "｜与不同元素命中触发反应"));
		}
		// 芯片只标「装了哪颗」，具体数值看芯片自己的说明
		for (Chip chip : GunChipData.get(stack)) {
			lore.add(nbtString("§a已安装芯片：" + chip.displayName()));
		}
		display.put("Lore", lore);
	}

	private static net.minecraft.nbt.StringTag nbtString(String text) {
		return net.minecraft.nbt.StringTag.valueOf("{\"text\":\"" + text + "\"}");
	}

	/** 属性加成摘要，例如 “伤害 +20% · 射速 +16% · 散布 -20%” */
	public static String statLine(int level) {
		int tier = Math.max(0, level - MIN_LEVEL);
		if (tier <= 0) {
			return "";
		}
		LevelConfig c = GunLevelMod.CONFIG;
		StringBuilder sb = new StringBuilder();
		appendPercent(sb, "伤害", c.damageBonusPerLevel * tier);
		appendPercent(sb, "初速", c.speedBonusPerLevel * tier);
		appendPercent(sb, "射速", c.fireRateBonusPerLevel * tier);
		appendPercent(sb, "散布", c.dispersionBonusPerLevel * tier);
		appendPercent(sb, "换弹", c.reloadBonusPerLevel * tier);
		return sb.toString();
	}

	private static void appendPercent(StringBuilder sb, String name, double ratio) {
		if (ratio == 0) {
			return;
		}
		if (sb.length() > 0) {
			sb.append(" · ");
		}
		long percent = Math.round(ratio * 100);
		sb.append(name).append(' ').append(percent > 0 ? "+" : "").append(percent).append('%');
	}

	/** lore 用的整数百分比 */
	private static long percent(double ratio) {
		return Math.round(ratio * 100);
	}

	/** 已安装的元素核芯名（没装返回 null） */
	public static String elementName(ItemStack stack) {
		com.eternal.gunlevel.effect.Element element =
				com.eternal.gunlevel.item.GunCoreData.get(stack);
		return element == com.eternal.gunlevel.effect.Element.NONE ? null : element.displayName();
	}
}
