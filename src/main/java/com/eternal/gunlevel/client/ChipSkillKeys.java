package com.eternal.gunlevel.client;

import com.eternal.gunlevel.effect.WindGuardState;
import com.eternal.gunlevel.net.GunLevelNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 芯片主动技能的客户端接线：一个技能键（默认 V，可在「控制」里改）把按键请求交给服务端结算，
 * 再加风元素枪的二段跳。
 *
 * <p>二段跳只做在客户端：1.20.1 玩家的移动由客户端预测、服务端接受客户端上报的位置，
 * 在服务端给玩家加初速反而会被下一帧的位置包抹掉。免摔落伤害那条才需要服务端说了算。
 */
public final class ChipSkillKeys {
	/** 与原版起跳同一档初速（LivingEntity#getJumpVelocity 是 protected，取不出来就照抄这个数） */
	private static final double AIR_JUMP_VELOCITY = 0.42;

	private final KeyMapping skillKey = new KeyMapping(
			"key.tacz_gun_level.chip_skill", GLFW.GLFW_KEY_V, "key.category.tacz_gun_level");
	/** 这一段腾空已经用过的气跳次数，落地清零 */
	private boolean airJumpUsed;
	/** 跳跃键上一 tick 的状态：自己盯上升沿，不去 consume 原版那一份点击 */
	private boolean jumpWasDown;

	public void registerKeys(RegisterKeyMappingsEvent event) {
		event.register(skillKey);
	}

	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		while (skillKey.consumeClick()) {
			if (client.screen == null) {
				// 只报「按下了技能键」，能不能放、放哪一颗都由服务端裁决
				GunLevelNetwork.sendSkillRequest();
			}
		}
		airJump(client);
	}

	private void airJump(Minecraft client) {
		// 按键状态每 tick 都要读一次，否则界面里放开的那一下会被当成回来后重新按了一次
		boolean down = client.options.keyJump.isDown();
		boolean pressed = down && !jumpWasDown;
		jumpWasDown = down;
		if (client.screen != null || !canAirJump(client.player)) {
			return;
		}
		if (client.player.onGround()) {
			airJumpUsed = false;
			return;
		}
		// 只有已经开始下落了才给第二次跳：上升途中把 y 重新设成起跳初速，只是把这一跳拉长，
		// 看着就是「跳跃高度增加」而不是二段跳（用户实测到的正是这个）
		if (airJumpUsed || !pressed || client.player.getDeltaMovement().y > 0.0
				|| client.player.getAbilities().flying || client.player.isFallFlying()
				|| client.player.isInWater() || client.player.onClimbable()) {
			return;
		}
		airJumpUsed = true;
		var velocity = client.player.getDeltaMovement();
		client.player.setDeltaMovement(velocity.x(), AIR_JUMP_VELOCITY, velocity.z());
		client.player.hurtMarked = true;
		client.player.fallDistance = 0.0F;
	}

	/** 二段跳的两颗芯片：灵风之祝（风元素枪）或紫芯片·风灵祝福（风元素核芯 + 芯片，只看主手） */
	private static boolean canAirJump(net.minecraft.world.entity.LivingEntity entity) {
		return WindGuardState.passiveActive(entity)
				|| WindGuardState.purpleWindActive(entity,
						com.eternal.gunlevel.effect.Chip.WIND_GRACE);
	}
}
