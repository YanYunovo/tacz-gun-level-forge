package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunChipData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * 不破誓约的嘲讽：每 10 tick 一拍，把主手持枪装了这颗芯片的玩家 {@code chipPurpleOathTauntRadius}
 * 格内、当前正把**某个玩家**当目标的敌对生物的目标抢到装备者身上 —— 只抢已有敌意的
 * （在打别人 = 抢过来；本来没在打人的不主动开怪，免得路过就 train 一车）。
 * 写法与凝晶召唤的嘲讽同一套：setLastHurtByMob 借优先级 1 的 RevengeGoal 按住仇恨
 * （它读 lastHurtByMob 与受击时间），setTarget 给没有寻仇 goal 的怪兜底，详见那边的踩坑记录。
 */
public final class OathState {
	private OathState() {
	}

	public static void tick(MinecraftServer server) {
		if (server.overworld().getGameTime() % 10 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!GunChipData.holdsChip(player.getMainHandItem(), Chip.STEADFAST_OATH)) {
				continue;
			}
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			double radius = GunLevelMod.CONFIG.chipPurpleOathTauntRadius;
			AABB box = player.getBoundingBox().inflate(radius);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box,
					mob -> mob instanceof Enemy && mob.getTarget() instanceof Player)) {
				mob.setLastHurtByMob(player);
				mob.setTarget(player);
			}
		}
	}
}
