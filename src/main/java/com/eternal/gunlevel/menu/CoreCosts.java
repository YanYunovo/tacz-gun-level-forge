package com.eternal.gunlevel.menu;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Element;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/**
 * 元素核芯的合成材料：1 个元素素材 + 1 个空核芯 → 对应元素的核芯。
 * 空核芯由原版工作台合成（四角铁锭 + 四边玻璃 + 中心紫水晶碎片），见 data 里的 empty_core 配方。
 */
public final class CoreCosts {
	private static final Map<Element, Item> MATERIALS = new EnumMap<>(Element.class);

	static {
		MATERIALS.put(Element.THUNDER, item("minecraft:glowstone_dust"));
		MATERIALS.put(Element.FIRE, item("minecraft:blaze_powder"));
		MATERIALS.put(Element.WATER, item("minecraft:prismarine_shard"));
		MATERIALS.put(Element.ICE, item("minecraft:packed_ice"));
		MATERIALS.put(Element.GRASS, item("minecraft:vine"));
		MATERIALS.put(Element.WIND, item("minecraft:feather"));
		MATERIALS.put(Element.ROCK, item("minecraft:quartz"));
	}

	private CoreCosts() {
	}

	private static Item item(String id) {
		return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
	}

	/** 该物品是哪个元素的素材；不是素材返回 NONE */
	public static Element elementOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return Element.NONE;
		}
		for (Map.Entry<Element, Item> entry : MATERIALS.entrySet()) {
			if (entry.getValue() == stack.getItem()) {
				return entry.getKey();
			}
		}
		return Element.NONE;
	}

	public static boolean isCarrier(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() == GunLevelMod.EMPTY_CORE;
	}

	public static boolean isMaterial(ItemStack stack) {
		return elementOf(stack) != Element.NONE;
	}

	public static ItemStack materialFor(Element element) {
		Item item = MATERIALS.get(element);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}
}
