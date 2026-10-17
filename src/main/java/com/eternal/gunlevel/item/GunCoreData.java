package com.eternal.gunlevel.item;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Element;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 枪械上的元素核芯读写：元素改造完全由核芯决定，与枪械等级无关。
 */
public final class GunCoreData {
	public static final String TAG = "tacz_gun_level_core";

	private GunCoreData() {
	}

	public static Element get(ItemStack gun) {
		if (gun.isEmpty()) {
			return Element.NONE;
		}
		CompoundTag nbt = gun.getTag();
		if (nbt == null) {
			return Element.NONE;
		}
		String name = nbt.getString(TAG);
		if (name.isEmpty()) {
			return Element.NONE;
		}
		try {
			return Element.valueOf(name);
		} catch (IllegalArgumentException e) {
			return Element.NONE;
		}
	}

	public static void set(ItemStack gun, Element element) {
		if (element == null || element == Element.NONE) {
			gun.getOrCreateTag().remove(TAG);
			return;
		}
		gun.getOrCreateTag().putString(TAG, element.name());
	}

	public static boolean isCore(ItemStack stack) {
		return stack.getItem() instanceof ElementCoreItem;
	}

	/** 手持物品若是元素核芯则返回其元素，否则 NONE */
	public static Element elementOfStack(ItemStack stack) {
		return isCore(stack) ? ((ElementCoreItem) stack.getItem()).element() : Element.NONE;
	}

	/** 元素对应的核芯物品；NONE 返回 null */
	public static Item coreItem(Element element) {
		return switch (element) {
			case THUNDER -> GunLevelMod.CORE_THUNDER;
			case FIRE -> GunLevelMod.CORE_FIRE;
			case WATER -> GunLevelMod.CORE_WATER;
			case ICE -> GunLevelMod.CORE_ICE;
			case GRASS -> GunLevelMod.CORE_GRASS;
			case WIND -> GunLevelMod.CORE_WIND;
			case ROCK -> GunLevelMod.CORE_ROCK;
			case NONE -> null;
		};
	}

	public static ItemStack newCore(Element element) {
		Item item = coreItem(element);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}
}
