package com.eternal.gunlevel.item;

import com.eternal.gunlevel.GunLevelData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

import java.util.List;

/** 升级组件：永恒改装的消耗材料，工作台合成，I~IV 分别对应升到 LV.2~LV.5 */
public class UpgradeComponentItem extends Item {
	private final int targetLevel;

	public UpgradeComponentItem(int targetLevel) {
		super(new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
		this.targetLevel = targetLevel;
	}

	public int targetLevel() {
		return targetLevel;
	}

	@Override
	public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.tacz_gun_level.upgrade_component.desc",
				GunLevelData.displayName(targetLevel)));
	}
}
