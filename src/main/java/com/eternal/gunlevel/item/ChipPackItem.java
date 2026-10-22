package com.eternal.gunlevel.item;

import com.eternal.gunlevel.effect.ChipTier;
import com.eternal.gunlevel.menu.ChipSelectMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * X芯片自选包：右键开一个界面，从本品级的所有芯片里挑一颗。
 * 由 9 个对应的破碎芯片合成，本身没有随机成分——玩家想要哪颗就拿哪颗。
 */
public class ChipPackItem extends Item {
	private final ChipTier tier;

	public ChipPackItem(ChipTier tier) {
		super(new Item.Properties().stacksTo(16));
		this.tier = tier;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
		if (!level.isClientSide) {
			// 界面自己不带槽位数据，直接把菜单类当 MenuProvider 用，品级交给菜单实例
			user.openMenu(new ChipSelectMenu(0, user.getInventory(), tier));
		}
		return InteractionResultHolder.success(user.getItemInHand(hand));
	}
}
