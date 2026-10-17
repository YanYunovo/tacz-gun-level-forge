package com.eternal.gunlevel.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 极光弹匣：给客户端预测射击间隔用的影子。真正的状态记在枪物品 NBT 上
 * （{@link com.eternal.gunlevel.item.GunAuroraData}），服务端每次开火把它对齐过来，
 * 所以这个效果恒定是无限时长、不会出现「一匣没打完就掉加成」。
 * 射速加成在读取射击间隔时按永恒改装等级折算（LevelScaling#auroraIntervalScale），
 * 它本身不加任何属性。
 */
public class AuroraEffect extends MobEffect {
	public AuroraEffect() {
		super(MobEffectCategory.BENEFICIAL, 0x66E1D6);
	}
}
