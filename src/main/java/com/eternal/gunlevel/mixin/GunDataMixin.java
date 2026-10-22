package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.effect.LevelScaling;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 射击窗口内（LevelScaling.currentLevel() > 0）按等级缩放射速与散布，
 * 另外按窗口里那个射手身上的激化层数再缩一次射速（激化与等级无关）。
 */
@Mixin(GunData.class)
public abstract class GunDataMixin {

	@Inject(method = "getShootInterval", at = @At("RETURN"), cancellable = true, remap = false)
	private void gunlevel$interval(CallbackInfoReturnable<Long> cir) {
		long interval = cir.getReturnValue();
		int level = LevelScaling.currentLevel();
		net.minecraft.world.entity.LivingEntity shooter = LevelScaling.currentShooter();
		double scale = level > 0 ? LevelScaling.intervalScale(level) : 1.0;
		scale *= LevelScaling.quickenIntervalScale(shooter) * LevelScaling.auroraIntervalScale(shooter, level);
		// 迅雷之音的射速加成与等级无关，装了就吃
		scale *= com.eternal.gunlevel.effect.ThunderVoiceState.intervalScale(shooter);
		// 狩猎时刻：只有爆发窗口那 3 秒吃这一档（标记没了或次数没到 30 就是 1.0）
		if (shooter != null) {
			scale *= com.eternal.gunlevel.effect.HuntState.intervalScale(shooter,
					shooter.level().getGameTime());
		}
		if (interval <= 0 || scale == 1.0) {
			return;
		}
		// 缩放后至少留 1 tick，别让高射速枪的冷却直接归零变成每 tick 一发
		cir.setReturnValue(Math.max(1L, (long) (interval * scale)));
	}

	@Inject(method = "getInaccuracy(Lcom/tacz/guns/resource/pojo/data/gun/InaccuracyType;)F",
			at = @At("RETURN"), cancellable = true, remap = false)
	private void gunlevel$inaccuracy(InaccuracyType type, CallbackInfoReturnable<Float> cir) {
		int level = LevelScaling.currentLevel();
		if (level > 0) {
			cir.setReturnValue((float) (cir.getReturnValue() * LevelScaling.spreadScale(level)));
		}
	}
}
