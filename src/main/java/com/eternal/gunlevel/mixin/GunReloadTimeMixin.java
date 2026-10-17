package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.effect.LevelScaling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 换弹时间按等级缩短（仅射击/换弹窗口内生效）。
 */
@Mixin(com.tacz.guns.resource.pojo.data.gun.GunReloadTime.class)
public abstract class GunReloadTimeMixin {

	@Inject(method = "getEmptyTime", at = @At("RETURN"), cancellable = true, remap = false)
	private void gunlevel$empty(CallbackInfoReturnable<Float> cir) {
		int level = LevelScaling.currentLevel();
		if (level > 0) {
			cir.setReturnValue((float) (cir.getReturnValue() * LevelScaling.reloadScale(level)));
		}
	}

	@Inject(method = "getTacticalTime", at = @At("RETURN"), cancellable = true, remap = false)
	private void gunlevel$tactical(CallbackInfoReturnable<Float> cir) {
		int level = LevelScaling.currentLevel();
		if (level > 0) {
			cir.setReturnValue((float) (cir.getReturnValue() * LevelScaling.reloadScale(level)));
		}
	}
}
