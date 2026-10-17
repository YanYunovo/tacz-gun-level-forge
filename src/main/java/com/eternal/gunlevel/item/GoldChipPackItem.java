package com.eternal.gunlevel.item;

import com.eternal.gunlevel.menu.ChipSelectMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 金芯片自选包：右键开一个界面，从所有金芯片里挑一颗。
 * 由 9 个破碎的金芯片合成，本身没有随机成分——玩家想要哪颗就拿哪颗。
 */
public class GoldChipPackItem extends Item {
	public GoldChipPackItem() {
		super(new Item.Properties().stacksTo(16));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
		if (!level.isClientSide) {
			// 界面自己不带槽位数据，直接把菜单类当 MenuProvider 用
			user.openMenu(new ChipSelectMenu(0, user.getInventory()));
		}
		return InteractionResultHolder.success(user.getItemInHand(hand));
	}
}
