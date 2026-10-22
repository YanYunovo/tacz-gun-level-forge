package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.effect.HuntState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 狩猎时刻爆发窗口的「不消耗弹药」。
 *
 * <p>拦的是 TACZ 自己的开关 {@code LivingEntityAmmoCheck#consumesAmmoOrNot}（创造模式免扣弹
 * 走的就是它，返回 false 会整段跳过 {@code reduceAmmoOnce}），而不是在射击结束后把弹匣数写回去：
 * 连发的后续几发是在 {@code ServerTickEvent → CycleTaskHelper.tick} 里扣的，写回只盖得住第一发；
 * 闭膛待击还会因此和「膛内有弹」的判定错位，拉栓时再扣一发。
 *
 * <p>窗口判定挂在射手身上（{@link HuntState#bursting}），不能复用射击窗口的 ThreadLocal ——
 * 扣弹发生在 CycleTask 的后续帧里，那时栈早弹干净了。
 *
 * <p>{@code shooter} 字段就声明在本类上（两个 jar 一致，都是 {@code private final LivingEntity}），
 * 所以直接 @Shadow 拿，不用绕到子类。
 */
@Mixin(com.tacz.guns.entity.shooter.LivingEntityAmmoCheck.class)
public abstract class AmmoCheckHuntMixin {

	@Shadow(remap = false)
	private LivingEntity shooter;

	@Inject(method = "consumesAmmoOrNot", at = @At("RETURN"), cancellable = true, remap = false)
	private void gunlevel$freeAmmo(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && shooter != null
				&& HuntState.bursting(shooter, shooter.level().getGameTime())) {
			cir.setReturnValue(false);
		}
	}
}
