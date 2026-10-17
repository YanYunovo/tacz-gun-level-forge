package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.effect.LevelScaling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 子弹基础数据在窗口内按等级缩放：初速在射击窗口内缩放（决定弹道），
 * 伤害只在面板窗口内缩放——实际命中伤害由 EntityKineticBulletMixin 结算，
 * 两边不会在同一线程里叠加。
 */
@Mixin(com.tacz.guns.resource.pojo.data.gun.BulletData.class)
public abstract class BulletDataMixin {

	@Inject(method = "getDamageAmount", at = @At("RETURN"), cancellable = true, remap = false)
	private void gunlevel$damage(CallbackInfoReturnable<Float> cir) {
		int level = LevelScaling.currentLevel();
		if (level > 0 && LevelScaling.inDisplay()) {
			cir.setReturnValue((float) (cir.getReturnValue() * LevelScaling.damageScale(level)));
		}
	}

	@Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true, remap = false)
	private void gunlevel$speed(CallbackInfoReturnable<Float> cir) {
		int level = LevelScaling.currentLevel();
		if (level > 0) {
			cir.setReturnValue((float) (cir.getReturnValue() * LevelScaling.speedScale(level)));
		}
	}
}
