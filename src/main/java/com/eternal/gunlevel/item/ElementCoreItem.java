package com.eternal.gunlevel.item;

import com.eternal.gunlevel.effect.Element;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * 元素核芯：装在枪械上的自建配件，由元素合成台安装到枪物品 NBT。
 * 七种核芯只有元素（贴图颜色）不同。
 */
public class ElementCoreItem extends Item {
	private final Element element;

	public ElementCoreItem(Element element) {
		super(new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
		this.element = element;
	}

	public Element element() {
		return element;
	}

	@Override
	public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.tacz_gun_level.element_core.desc",
				Component.translatable("element.tacz_gun_level." + element.name().toLowerCase(Locale.ROOT))));
	}
}
