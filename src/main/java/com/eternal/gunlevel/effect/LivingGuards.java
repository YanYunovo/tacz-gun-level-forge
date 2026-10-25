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
	 * 紫芯片·不破誓约的 15% 减免也挂在这里（同一个事件做减法，两颗同装时乘法叠加）。
	 */
	private static void onBravery(LivingHurtEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		ItemStack gun = player.getMainHandItem();
		float amount = event.getAmount();
		if (com.eternal.gunlevel.item.GunChipData.holdsChip(gun, Chip.FEARLESS)) {
			amount *= 1.0F + (float) GunLevelMod.CONFIG.chipPurpleFearlessDamageTaken;
		}
		if (com.eternal.gunlevel.item.GunChipData.holdsChip(gun, Chip.STEADFAST_OATH)) {
			amount *= 1.0F - (float) GunLevelMod.CONFIG.chipPurpleOathReduction;
		}
		// 荣誉勋章：自己受到伤害立刻整条清零（用户口径）
		MedalState.onPlayerDamaged(player);
		event.setAmount(amount);
	}

	/**
	 * 免疫摔落伤害的两颗芯片：灵风之祝（风元素枪 + 芯片，见 {@link WindGuardState#passiveActive}）
	 * 与紫芯片·风灵契约（风元素核芯 + 芯片，同样只看主手）。整个事件取消，连伤害源都不产生。
	 */
	private static void onFall(LivingFallEvent event) {
		if (WindGuardState.passiveActive(event.getEntity())
				|| WindGuardState.purpleWindActive(event.getEntity(), Chip.WIND_PACT)) {
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
