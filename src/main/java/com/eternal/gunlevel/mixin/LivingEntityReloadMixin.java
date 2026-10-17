package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.effect.AuroraState;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.effect.LevelScaling;
import com.eternal.gunlevel.item.GunAuroraData;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.util.TaczCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 每 tick 的换弹状态推进同样处于“射击窗口”，使 GunReloadTime 的读取获得等级加成。
 * 另外承担极光追击的两件事：换弹真的开始那一刻结算极光匣（结束旧的、需要就开新的），
 * 以及极光匣期间把这一匣的容量翻倍。
 */
@Mixin(com.tacz.guns.entity.shooter.LivingEntityReload.class)
public abstract class LivingEntityReloadMixin {

	@Shadow(remap = false)
	private LivingEntity shooter;

	@Inject(method = "tickReloadState", at = @At("HEAD"), remap = false)
	private void gunlevel$beginWindow(CallbackInfoReturnable<com.tacz.guns.api.entity.ReloadState> cir) {
		ItemStack main = shooter.getMainHandItem();
		LevelScaling.beginWindow(TaczCompat.isGun(main) ? GunLevelData.getLevel(main) : 0);
	}

	@Inject(method = "tickReloadState", at = @At("RETURN"), remap = false)
	private void gunlevel$endWindow(CallbackInfoReturnable<com.tacz.guns.api.entity.ReloadState> cir) {
		LevelScaling.endWindow();
	}

	/**
	 * 极光匣结算。挂在 {@code lambda$reload$0} 末尾而不是 reload() 开头：
	 * 那个 lambda 里有「正在换弹 / 射击冷却 / 弹药不够」好几处提前 return，
	 * 只有走到底才是真的开始换弹，否则就绪会被白白消耗掉、还平白送人一匣加速。
	 *
	 * 这里同时是极光匣唯一的结束点：任何一次换弹开头都把标记抹掉，所以这一匣不管是被打完的
	 * 还是被换掉的，加成都不会跟着下一匣继续存在；而攒满就绪的那次换弹则重新打上标记，
	 * 于是这次换弹自己的后续 tickReloadState 就会看到容量翻倍。
	 */
	@Inject(method = "lambda$reload$0", at = @At("TAIL"), remap = false)
	private void gunlevel$auroraReload(CallbackInfo ci) {
		if (shooter.level().isClientSide) {
			return;
		}
		ItemStack main = shooter.getMainHandItem();
		// 换弹就意味着上一匣结束：先抹掉标记，再决定这一匣是不是新的极光匣
		GunAuroraData.set(main, false);
		int level = LevelScaling.levelOfStack(main);
		if (level > 0 && GunChipData.get(main).contains(Chip.AURORA) && AuroraState.isReady(shooter)) {
			AuroraState.consume(shooter);
			GunAuroraData.set(main, true);
		}
		AuroraState.syncCarrier(shooter);
	}
}
