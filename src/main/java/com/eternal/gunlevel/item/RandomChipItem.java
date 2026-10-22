package com.eternal.gunlevel.item;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.effect.ChipTier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 随机X芯片：右键抽一颗本品级的芯片，同时附赠一个对应的破碎芯片。
 *
 * <p>奖池取 {@link ChipTier#chips()}（按品级过滤 {@link Chip} 枚举），所以新增芯片只改枚举，
 * 这里自动进池，不用回来加名单；金蓝各抽各的也是在这里天然分开的，蓝包里抽不出金芯片。
 */
public class RandomChipItem extends Item {
	private final ChipTier tier;

	public RandomChipItem(ChipTier tier) {
		super(new Item.Properties().stacksTo(16));
		this.tier = tier;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		if (!level.isClientSide) {
			List<Chip> pool = tier.chips();
			Chip drawn = pool.get(level.getRandom().nextInt(pool.size()));
			give(user, new ItemStack(GunLevelMod.CHIPS[drawn.ordinal()]));
			give(user, new ItemStack(GunLevelMod.BROKEN_CHIP[tier.ordinal()]));
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
		tooltip.add(Component.translatable("item.tacz_gun_level." + tier.randomChipId() + ".desc"));
	}
}
