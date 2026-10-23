package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 两条活体守卫。Fabric 版是靠 mixin 注入 {@code LivingEntity}，Forge 有原生事件，没必要再注入原版类
 * （原版方法名还得走 refmap，正式服和 dev 两边名字不一样，少一条映射就是启动崩）。
 */
public final class LivingGuards {
	private LivingGuards() {
	}

	public static void init(IEventBus forgeBus) {
		forgeBus.addListener(LivingGuards::onFall);
		forgeBus.addListener(LivingGuards::onEffectApplicable);
		forgeBus.addListener(LivingGuards::onBravery);
	}

	/**
	 * 勇者无惧的代价：主手拿着装了这颗芯片的枪时，受到的伤害放大 15%。
	 *
	 * <p>{@code LivingHurtEvent} 在 {@code LivingEntity#hurt} 开头、护甲与魔咒减免**之前**触发
	 * （字节码里紧跟 isInvulnerableTo 就是 ForgeHooks.onLivingHurt），Fabric 版注入的正是同一个方法
	 * 的同一个位置（yarn 里那方法叫 {@code damage}），所以两边放大的量一致。
	 */
	private static void onBravery(LivingHurtEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		ItemStack gun = player.getMainHandItem();
		if (!com.eternal.gunlevel.item.GunChipData.holdsChip(gun, Chip.FEARLESS)) {
			return;
		}
		event.setAmount(event.getAmount()
				* (1.0F + (float) GunLevelMod.CONFIG.chipPurpleFearlessDamageTaken));
	}

	/** 灵风之祝：手持装着这颗芯片的风枪时不掉血。整个事件取消，连伤害源都不产生 */
	private static void onFall(LivingFallEvent event) {
		if (WindGuardState.passiveActive(event.getEntity())) {
			event.setCanceled(true);
		}
	}

	/** 净化领域第三档：挡掉新施加的负面效果，正面与中立照常 */
	private static void onEffectApplicable(MobEffectEvent.Applicable event) {
		LivingEntity self = event.getEntity();
		MobEffectInstance instance = event.getEffectInstance();
		if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL
				&& BloomDomain.isDebuffImmune(self)) {
			event.setResult(Event.Result.DENY);
		}
	}
}
