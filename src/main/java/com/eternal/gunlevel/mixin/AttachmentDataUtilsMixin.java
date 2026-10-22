package com.eternal.gunlevel.mixin;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.item.GunAuroraData;
import com.eternal.gunlevel.effect.ThunderVoiceState;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.item.GunRoarData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 极光匣的容量翻倍。
 *
 * <p>锚点必须是 {@code AttachmentDataUtils.getAmmoCountWithAttachment}：1.1.6/1.1.7 里
 * 「这一匣装满是多少发」只有这一个来源，脚本 API 的 getMaxAmmoCount、换弹要抽的
 * getNeededAmmoAmount、以及弹药 HUD 全是从它算出来的。挂在 getMaxAmmoCount 上只能改显示，
 * 换弹实际抽多少发走的是另一条调用，翻倍就不生效（实测踩过）。
 *
 * <p>迅雷之音的「主弹匣容量减少」也挂在这儿：两颗芯片可能同时装在一把枪上（LV5 两颗），
 * 所以是按比例连乘，不是二选一。
 *
 * <p>极光那一半的判据只看这把枪自己：极光匣标记 + 枪上确实装着极光追击。标记在换弹起手那一刻打上或抹掉
 * （见 LivingEntityReloadMixin），所以翻倍精确地只跟着那一匣，之后的普通换弹立刻回到原容量。
 */
@Mixin(com.tacz.guns.util.AttachmentDataUtils.class)
public abstract class AttachmentDataUtilsMixin {

	@Inject(method = "getAmmoCountWithAttachment", at = @At("RETURN"), cancellable = true, remap = false)
	private static void gunlevel$capacity(ItemStack gun, GunData data,
			CallbackInfoReturnable<Integer> cir) {
		int capacity = cir.getReturnValue();
		if (capacity <= 0) {
			return;
		}
		// 咆哮匣直接顶成「这一匣只有一发」：它是技能造出来的特殊状态，不参与上面那些比例乘算
		if (GunRoarData.isMagOn(gun)) {
			cir.setReturnValue(Math.max(1, GunLevelMod.CONFIG.chipRoarMagAmmo));
			return;
		}
		double scale = 1.0;
		if (GunAuroraData.isOn(gun) && GunChipData.get(gun).contains(Chip.AURORA)) {
			scale *= GunLevelMod.CONFIG.chipAuroraMagMultiplier;
		}
		scale *= ThunderVoiceState.capacityScale(gun);
		if (scale == 1.0) {
			return;
		}
		cir.setReturnValue(Math.max(1, (int) Math.round(capacity * scale)));
	}

}
