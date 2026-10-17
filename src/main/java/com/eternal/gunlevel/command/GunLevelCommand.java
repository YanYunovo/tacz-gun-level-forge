package com.eternal.gunlevel.command;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.util.TaczCompat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * /gunlevel set <1-5>：把主手枪械设为指定等级（调试/单包地图用）。
 */
public final class GunLevelCommand {
	private GunLevelCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("gunlevel")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("set")
						.then(Commands.argument("level", IntegerArgumentType.integer(
								GunLevelData.MIN_LEVEL, GunLevelMod.CONFIG.maxLevel))
								.executes(ctx -> {
									int level = IntegerArgumentType.getInteger(ctx, "level");
									var stack = ctx.getSource().getPlayerOrException().getMainHandItem();
									if (!TaczCompat.isGun(stack)) {
										ctx.getSource().sendFailure(Component.translatable("msg.tacz_gun_level.not_gun"));
										return 0;
									}
									GunLevelData.setLevel(stack, level);
									GunLevelData.applyLore(stack, level);
									ctx.getSource().sendSuccess(() -> Component.translatable(
											"msg.tacz_gun_level.set", GunLevelData.displayName(level)), true);
									return 1;
								})))
				.then(Commands.literal("info")
						.executes(ctx -> {
							var stack = ctx.getSource().getPlayerOrException().getMainHandItem();
							if (!TaczCompat.isGun(stack)) {
								ctx.getSource().sendFailure(Component.translatable("msg.tacz_gun_level.not_gun"));
								return 0;
							}
							var id = TaczCompat.gunIdOf(stack);
							ctx.getSource().sendSuccess(() -> Component.literal(
									"枪械: " + id + "  等级: " + GunLevelData.displayName(GunLevelData.getLevel(stack))), true);
							return 1;
						})));
	}
}
