package com.eternal.gunlevel.client;

import com.eternal.gunlevel.net.GunLevelNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 冻结反应的纯客户端冰壳：只按截止刻显示，不触碰实体行为或碰撞箱。 */
public final class FrozenShellClient {
	private static final Map<UUID, Long> FROZEN_UNTIL = new HashMap<>();
	private static ClientLevel world;

	private FrozenShellClient() {
	}

	public static void update(GunLevelNetwork.FreezeSync sync) {
		FROZEN_UNTIL.merge(sync.target(), sync.until(), Math::max);
	}

	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level != world) {
			world = client.level;
			FROZEN_UNTIL.clear();
		}
		if (client.level != null) {
			long now = client.level.getGameTime();
			FROZEN_UNTIL.values().removeIf(until -> now >= until);
		}
	}

	private static boolean isFrozen(LivingEntity entity) {
		if (!entity.isAlive() || entity.isInvisible()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return false;
		}
		Long until = FROZEN_UNTIL.get(entity.getUUID());
		return until != null && client.level.getGameTime() < until;
	}

	public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
		LivingEntity entity = event.getEntity();
		if (!isFrozen(entity)) {
			return;
		}
		float pad = 0.04F;
		float width = entity.getBbWidth() + pad * 2.0F;
		float height = entity.getBbHeight() + pad * 2.0F;
		event.getPoseStack().pushPose();
		event.getPoseStack().translate(-width * 0.5F, -pad, -width * 0.5F);
		event.getPoseStack().scale(width, height, width);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
				Blocks.ICE.defaultBlockState(), event.getPoseStack(), event.getMultiBufferSource(),
				event.getPackedLight(), OverlayTexture.NO_OVERLAY);
		event.getPoseStack().popPose();
	}
}
