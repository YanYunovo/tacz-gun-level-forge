package com.eternal.gunlevel.util;

import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.net.GunLevelNetwork;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

/**
 * 芯片触发提示：服务端只把「哪颗芯片触发」发给客户端，位置和文案都由客户端 HUD 决定。
 * 走 action bar 会和测试假人的 DPS 读数重叠，所以自己画。
 */
public final class ChipHints {
	/** 提示文案：「XX触发」 */
	public static final int TRIGGER = 0;
	/** 提示文案：「极光弹匣已就绪」——攒满命中时说的是「可以去换弹」，不是「这一发触发了什么」 */
	public static final int AURORA_READY = 1;
	/** 提示文案：「风的护佑已就绪」——主动技能冷却走完那一下，玩家该按的是技能键 */
	public static final int SKILL_READY = 2;

	private ChipHints() {
	}

	public static void show(Entity shooter, Chip chip) {
		show(shooter, chip, TRIGGER);
	}

	public static void show(Entity shooter, Chip chip, int kind) {
		if (shooter instanceof ServerPlayer player) {
			GunLevelNetwork.sendHint(player, chip.ordinal(), kind);
		}
	}
}
