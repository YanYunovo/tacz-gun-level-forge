package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunChipData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * 抱团取暖的光环：以「主手持枪装了这颗芯片的玩家」为圆心，数半径内**除装备者以外**的玩家数，
 * 半径内的**全体玩家**（含装备者自己）射击伤害每人 +{@code chipPurpleTeamworkPerPlayer}，
 * 封顶 {@code chipPurpleTeamworkCap}。多个光环同时罩住同一个射手时取最高的一档，不叠加
 * （两颗光环叠乘会滚得没有上限感，用户口径也没说叠加）。
 *
 * <p>光环跟人不跟枪：加成落在「命中那一刻站在光环里」的射手身上（哪怕他自己没装这颗芯片），
 * 所以挂在 gunlevel$scaledDamage 而不是 applyScale 的枪械芯片链里 —— 那条链在枪没升级时会整个跳过。
 */
public final class TeamworkState {
	private TeamworkState() {
	}

	public static double bonus(LivingEntity shooter) {
		if (shooter == null || shooter.level().isClientSide()) {
			return 0.0;
		}
		double radius = GunLevelMod.CONFIG.chipPurpleTeamworkRadius;
		List<Player> nearby = shooter.level().getEntitiesOfClass(Player.class,
				shooter.getBoundingBox().inflate(radius), player -> true);
		double best = 0.0;
		for (Player holder : nearby) {
			if (!GunChipData.holdsChip(holder.getMainHandItem(), Chip.TEAMWORK)) {
				continue;
			}
			int others = 0;
			for (Player other : shooter.level().getEntitiesOfClass(Player.class,
					holder.getBoundingBox().inflate(radius), player -> true)) {
				if (other != holder) {
					others++;
				}
			}
			best = Math.max(best, Math.min(GunLevelMod.CONFIG.chipPurpleTeamworkCap,
					GunLevelMod.CONFIG.chipPurpleTeamworkPerPlayer * others));
		}
		return best;
	}
}
