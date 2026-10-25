package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 结晶（岩+任意已附着元素）：每级 2 颗金心，和原版伤害吸收共用同一条 absorptionAmount 心条。
 */
public class CrystallizationEffect extends MobEffect {
	/**
	 * 这个 buff 实际往 absorptionAmount 里塞了多少点。升层时原版传给出的两个钩子的等级是同一个新等级，
	 * 按等级加减会算成「-新 +新 = 0」，心数永远停在第一次那档、到期还按最高档多扣别人的吸收心，
	 * 所以只能自己记账，扣的时候扣记下的数。
	 */
	private static final Map<UUID, Float> GRANTED = new HashMap<>();
	/** 这一条结晶链的上限（颗）：由 CombatState 在触发那一刻按那一枪的等级写进来并定格 */
	private static final Map<UUID, Float> CAP = new HashMap<>();

	public CrystallizationEffect() {
		super(MobEffectCategory.BENEFICIAL, 0xE8C65A);
	}

	/** 触发侧定格上限（颗）：之后换枪也不会把已经给出的心漏记 */
	public static void setCap(UUID id, float capHearts) {
		CAP.put(id, capHearts);
	}

	/** 第 amplifier+1 档应当给多少**点**吸收值（1 颗心 = 2 点）；被上限截住时就是上限 */
	private static float pointsOf(UUID id, int amplifier) {
		float base = (float) (GunLevelMod.CONFIG.crystallizeHeartsPerLevel * 2.0 * (amplifier + 1));
		Float cap = CAP.get(id);
		return cap == null ? base : Math.min(base, cap * 2.0F);
	}

	@Override
	public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
		// 每次结晶反应的进账恒定（crystallizeHeartsPerLevel 颗心）：升层只补差额。
		// 原版升层只补调这个钩子（不会先调 removeAttributeModifiers），整份再加会让每颗心越叠越多。
		float target = pointsOf(entity.getUUID(), amplifier);
		float already = GRANTED.getOrDefault(entity.getUUID(), 0.0F);
		float delta = target - already;
		if (delta > 0.0F) {
			entity.setAbsorptionAmount(entity.getAbsorptionAmount() + delta);
			GRANTED.put(entity.getUUID(), target);
		}
		super.addAttributeModifiers(entity, attributes, amplifier);
	}

	@Override
	public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
		revoke(entity);
		CAP.remove(entity.getUUID());
		super.removeAttributeModifiers(entity, attributes, amplifier);
	}

	/**
	 * 读存档时原版是直接把实例塞回状态表、不调那两个钩子，所以我们的心会留下一段没记录的历史。
	 * 每 tick 校正一次：没记录就按现有吸收值认领一份（上限只到自己该给的那一档，多的仍算原版
	 * 伤害吸收的），记录超过当前档次就压回档次值。这里只改记录、不动心条。
	 */
	@Override
	public void applyEffectTick(LivingEntity entity, int amplifier) {
		UUID uuid = entity.getUUID();
		float ceiling = pointsOf(uuid, amplifier);
		Float mine = GRANTED.get(uuid);
		if (mine == null) {
			GRANTED.put(uuid, Math.min(ceiling, Math.max(0.0F, entity.getAbsorptionAmount())));
		} else if (mine > ceiling) {
			GRANTED.put(uuid, ceiling);
		}
	}

	@Override
	public boolean isDurationEffectTick(int duration, int amplifier) {
		return true;
	}

	private static void revoke(LivingEntity entity) {
		Float mine = GRANTED.remove(entity.getUUID());
		if (mine != null && mine > 0.0F) {
			entity.setAbsorptionAmount(Math.max(0.0F, entity.getAbsorptionAmount() - mine));
		}
	}
}
