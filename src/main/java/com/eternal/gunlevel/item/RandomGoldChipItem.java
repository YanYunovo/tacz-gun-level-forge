package com.eternal.gunlevel.item;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 随机金芯片：右键抽一颗金芯片，同时附赠一个破碎的金芯片。
 *
 * <p>奖池直接取 {@link GunLevelMod#CHIPS}（按 {@link com.eternal.gunlevel.effect.Chip} 枚举建的数组），
 * 所以以后新增金芯片只改枚举，这里自动进池，不用回来加名单。
 */
public class RandomGoldChipItem extends Item {
	public RandomGoldChipItem() {
		super(new Item.Properties().stacksTo(16));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		if (!level.isClientSide) {
			Item[] pool = GunLevelMod.CHIPS;
			give(user, new ItemStack(pool[level.getRandom().nextInt(pool.length)]));
			give(user, new ItemStack(GunLevelMod.BROKEN_GOLD_CHIP));
			stack.shrink(1);
		}
		return InteractionResultHolder.success(stack);
	}

	/** 优先塞进背包，背包满了才丢在脚下，别让抽到的东西凭空消失 */
	private static void give(Player player, ItemStack drop) {
		if (!player.getInventory().add(drop)) {
			player.drop(drop, false);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip,
			net.minecraft.world.item.TooltipFlag flag) {
		tooltip.add(Component.translatable("item.tacz_gun_level.random_gold_chip.desc"));
	}
}
