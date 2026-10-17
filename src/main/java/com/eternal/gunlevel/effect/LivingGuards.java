package com.eternal.gunlevel.effect;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingFallEvent;
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
