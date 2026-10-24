package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.effect.LevelScaling;
import com.eternal.gunlevel.item.GunChipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 换弹时间按等级缩短（仅换弹窗口内生效）；
 * 蓝·换弹改造的定值一档与等级同区连乘 —— 换弹窗口现在带着射手（见 LivingEntityReloadMixin），
 * 所以能读到主手那把枪的芯片。
 */
@Mixin(com.tacz.guns.resource.pojo.data.gun.GunReloadTime.class)
public abstract class GunReloadTimeMixin {

	@Inject(method = "getEmptyTime", at = @At("RETURN"), cancellable = true, remap = false)
	private void gunlevel$empty(CallbackInfoReturnable<Float> cir) {
		gunlevel$scale(cir);
	}

	@Inject(method = "getTacticalTime", at = @At("RETURN"), cancellable = true, remap = false)
	private void gunlevel$tactical(CallbackInfoReturnable<Float> cir) {
		gunlevel$scale(cir);
	}

	private static void gunlevel$scale(CallbackInfoReturnable<Float> cir) {
		int level = LevelScaling.currentLevel();
		double scale = level > 0 ? LevelScaling.reloadScale(level) : 1.0;
		// 蓝·换弹改造：定值一档，和等级无关
		net.minecraft.world.entity.LivingEntity shooter = LevelScaling.currentShooter();
		if (shooter != null && GunChipData.get(shooter.getMainHandItem()).contains(Chip.RELOAD_MOD)) {
			scale *= 1.0 - GunLevelMod.CONFIG.chipBlueReload;
		}
		if (scale == 1.0) {
			return;
		}
		cir.setReturnValue((float) (cir.getReturnValue() * scale));
	}
}
