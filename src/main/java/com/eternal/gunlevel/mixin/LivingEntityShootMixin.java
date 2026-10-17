package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.effect.AuroraState;
import com.eternal.gunlevel.effect.CombatState;
import com.eternal.gunlevel.effect.LevelScaling;
import com.tacz.guns.api.entity.ShootResult;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端射击窗口：shoot 期间持有主手枪械等级（冻结则直接拒绝开火），
 * getShootCoolDown 期间同样开窗，使 GunData.getShootInterval 的读取吃到等级加成。
 */
@Mixin(com.tacz.guns.entity.shooter.LivingEntityShoot.class)
public abstract class LivingEntityShootMixin {

	@Shadow(remap = false)
	private LivingEntity shooter;

	@Inject(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;J)Lcom/tacz/guns/api/entity/ShootResult;",
			at = @At("HEAD"), cancellable = true, remap = false)
	private void gunlevel$beginWindow(java.util.function.Supplier<Float> yaw, java.util.function.Supplier<Float> pitch, long time, CallbackInfoReturnable<ShootResult> cir) {
		// 每次开火把「极光匣」效果对齐到手上这把枪的标记上，换了枪就不会把加速带到别的枪上
		AuroraState.syncCarrier(shooter);
		if (CombatState.isFrozen(shooter)) {
			cir.setReturnValue(ShootResult.UNKNOWN_FAIL);
			cir.cancel();
			return;
		}
		LevelScaling.beginWindow(LevelScaling.levelOfStack(shooter.getMainHandItem()), shooter);
	}

	@Inject(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;J)Lcom/tacz/guns/api/entity/ShootResult;",
			at = @At("RETURN"), remap = false)
	private void gunlevel$endWindow(java.util.function.Supplier<Float> yaw, java.util.function.Supplier<Float> pitch, long time, CallbackInfoReturnable<ShootResult> cir) {
		LevelScaling.endWindow();
	}

	@Inject(method = "getShootCoolDown()J", at = @At("HEAD"), remap = false)
	private void gunlevel$beginCoolDown(CallbackInfoReturnable<Long> cir) {
		LevelScaling.beginWindow(LevelScaling.levelOfStack(shooter.getMainHandItem()), shooter);
	}

	@Inject(method = "getShootCoolDown()J", at = @At("RETURN"), remap = false)
	private void gunlevel$endCoolDown(CallbackInfoReturnable<Long> cir) {
		LevelScaling.endWindow();
	}
}
