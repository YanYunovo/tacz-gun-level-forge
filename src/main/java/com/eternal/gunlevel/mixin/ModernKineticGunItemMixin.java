package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.effect.LevelScaling;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * 弹道窗口：TACZ 在这里读取 BulletData.getSpeed 与 GunData.getInaccuracy，
 * 窗口内等级缩放会同时作用到实际初速与散布。
 */
@Mixin(com.tacz.guns.item.ModernKineticGunItem.class)
public abstract class ModernKineticGunItemMixin {

	@Inject(method = "shoot(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;"
			+ "Ljava/util/function/Supplier;Ljava/util/function/Supplier;Lnet/minecraft/world/entity/LivingEntity;)V",
			at = @At("HEAD"), remap = false)
	private void gunlevel$beginWindow(com.tacz.guns.entity.shooter.ShooterDataHolder data, ItemStack gun,
			Supplier<Float> yaw, Supplier<Float> pitch, LivingEntity shooter, CallbackInfo ci) {
		LevelScaling.beginWindow(LevelScaling.levelOfStack(gun), shooter);
	}

	@Inject(method = "shoot(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;"
			+ "Ljava/util/function/Supplier;Ljava/util/function/Supplier;Lnet/minecraft/world/entity/LivingEntity;)V",
			at = @At("RETURN"), remap = false)
	private void gunlevel$endWindow(com.tacz.guns.entity.shooter.ShooterDataHolder data, ItemStack gun,
			Supplier<Float> yaw, Supplier<Float> pitch, LivingEntity shooter, CallbackInfo ci) {
		LevelScaling.endWindow();
	}
}
