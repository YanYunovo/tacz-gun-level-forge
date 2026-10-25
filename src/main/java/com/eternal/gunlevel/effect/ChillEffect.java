package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 凝寒（凛冬之啸的叠层 debuff）：**独立的状态效果**，与原版缓慢互不相干、可以叠加 ——
 * 用户口径「凝寒 buff 记得做成独立 buff，要与缓慢可以叠加」。
 *
 * <p>减速走状态效果自带的属性修正：每层 −{@code chipRimeSlowPerStack}
 * （MULTIPLY_TOTAL，原版按层数放大）。一层 15%、两层 30%；
 * 原版缓慢是另一条修正，同时挂着就叠加。
 */
public class ChillEffect extends MobEffect {

	public ChillEffect() {
		super(MobEffectCategory.HARMFUL, 0x9FD8F0);
		this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
				"e7c1f2a0-8b3d-4c5e-9a16-2f4d8b7c5a31",
				-GunLevelMod.CONFIG.chipRimeSlowPerStack,
				AttributeModifier.Operation.MULTIPLY_TOTAL);
	}

	@Override
	public void applyEffectTick(LivingEntity entity, int amplifier) {
	}
}
