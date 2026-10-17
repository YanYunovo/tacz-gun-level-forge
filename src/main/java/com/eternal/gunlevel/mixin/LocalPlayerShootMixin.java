package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.effect.LevelScaling;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端射速窗口：本地也会按冷却决定能否开火，不一起缩放的话服务端加速没有体感。
 */
@Mixin(com.tacz.guns.client.gameplay.LocalPlayerShoot.class)
public abstract class LocalPlayerShootMixin {

	@Shadow(remap = false)
	private LocalPlayer player;

	@Inject(method = "getCoolDown(Lcom/tacz/guns/api/item/IGun;Lnet/minecraft/world/item/ItemStack;"
			+ "Lcom/tacz/guns/resource/pojo/data/gun/GunData;)J", at = @At("HEAD"), remap = false)
	private void gunlevel$beginCoolDown(IGun gun, ItemStack stack, GunData data,
			CallbackInfoReturnable<Long> cir) {
		LevelScaling.beginWindow(LevelScaling.levelOfStack(stack), player);
	}

	@Inject(method = "getCoolDown(Lcom/tacz/guns/api/item/IGun;Lnet/minecraft/world/item/ItemStack;"
			+ "Lcom/tacz/guns/resource/pojo/data/gun/GunData;)J", at = @At("RETURN"), remap = false)
	private void gunlevel$endCoolDown(IGun gun, ItemStack stack, GunData data,
			CallbackInfoReturnable<Long> cir) {
		LevelScaling.endWindow();
	}

	@Inject(method = "getClientShootCoolDown", at = @At("HEAD"), remap = false)
	private void gunlevel$beginLocalCoolDown(CallbackInfoReturnable<Long> cir) {
		LevelScaling.beginWindow(LevelScaling.levelOfStack(player.getMainHandItem()), player);
	}

	@Inject(method = "getClientShootCoolDown", at = @At("RETURN"), remap = false)
	private void gunlevel$endLocalCoolDown(CallbackInfoReturnable<Long> cir) {
		LevelScaling.endWindow();
	}
}
