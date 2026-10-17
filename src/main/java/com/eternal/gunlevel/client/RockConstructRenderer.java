package com.eternal.gunlevel.client;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.resources.ResourceLocation;

/**
 * 岩元素造物（战术芯片·凝晶召唤）：模型、骨架、动画全部沿用原版僵尸，只把皮肤换成泥土。
 * 所以不需要新模型文件，也不注册自己的 EntityModelLayer。
 */
public class RockConstructRenderer extends ZombieRenderer {
	private static final ResourceLocation SKIN = GunLevelMod.id("textures/entity/rock_construct.png");

	public RockConstructRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public ResourceLocation getTextureLocation(Zombie entity) {
		return SKIN;
	}
}
