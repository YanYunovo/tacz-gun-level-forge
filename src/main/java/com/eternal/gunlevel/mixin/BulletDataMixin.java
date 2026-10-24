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
		double scale = level > 0 ? LevelScaling.speedScale(level) : 1.0;
		// 蓝·弹速改造：定值一档（射击窗口带着射手，读主手那把枪；窗口外创建的子弹由构造器补）
		net.minecraft.world.entity.LivingEntity shooter = LevelScaling.currentShooter();
		if (shooter != null && GunChipData.get(shooter.getMainHandItem()).contains(Chip.BULLET_SPEED_MOD)) {
			scale *= 1.0 + GunLevelMod.CONFIG.chipBlueBulletSpeed;
		}
		if (scale != 1.0) {
			cir.setReturnValue((float) (cir.getReturnValue() * scale));
		}
	}
}
