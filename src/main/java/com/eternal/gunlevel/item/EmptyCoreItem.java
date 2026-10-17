package com.eternal.gunlevel.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.List;

/** 空核芯：元素合成台合成元素核芯用的载体，原版工作台用铁锭+玻璃+紫水晶碎片做出来 */
public class EmptyCoreItem extends Item {
	public EmptyCoreItem() {
		super(new Item.Properties().stacksTo(64));
	}

	@Override
	public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.tacz_gun_level.empty_core.desc"));
	}
}
