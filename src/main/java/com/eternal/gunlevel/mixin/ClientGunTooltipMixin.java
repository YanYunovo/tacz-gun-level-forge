package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.effect.LevelScaling;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 枪械属性面板：生成文本时打开等级窗口，让 TACZ 显示的伤害/初速跟着等级走。
 */
@Mixin(com.tacz.guns.client.tooltip.ClientGunTooltip.class)
public abstract class ClientGunTooltipMixin {

	@Shadow(remap = false)
	private ItemStack gun;

	@Inject(method = "getText", at = @At("HEAD"), remap = false)
	private void gunlevel$beginWindow(CallbackInfo ci) {
		LevelScaling.beginDisplay(GunLevelData.getLevel(this.gun));
	}

	@Inject(method = "getText", at = @At("TAIL"), remap = false)
	private void gunlevel$endWindow(CallbackInfo ci) {
		LevelScaling.endWindow();
	}
}
