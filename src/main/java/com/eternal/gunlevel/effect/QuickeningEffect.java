package com.eternal.gunlevel.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 激化（雷+草）：只作为“身上有几层激化”的载体，射速加成在读取射击间隔时按层数折算，
 * 所以不走属性修饰符。
 */
public class QuickeningEffect extends MobEffect {
	public QuickeningEffect() {
		super(MobEffectCategory.BENEFICIAL, 0xA8C63C);
	}
}
