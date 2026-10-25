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
 * 结晶（岩+任意已附着元素）：**每次反应叠一层，每层 2 颗金心**，上限层数由
 * {@link CombatState#crystallizationCapHearts(int)} 决定（基础 5 层 = 10 颗心，磐岩壁垒每级 +1 颗心的上限）。
 * 和原版伤害吸收共用同一条 absorptionAmount 心条。
 *
 * <p>升层时原版**只补调 addAttributeModifiers**（不会先 removeAttributeModifiers），所以每次只按
 * 「这一层该给多少」加一次，绝不能按新档位整份再加（那会变成 2→4→6→8 越叠越多）。
 */
public class CrystallizationEffect extends MobEffect {
	/** 我们还挂在心条上的吸收值（点）：被打掉多少就记少多少，到期只扣自己还在的那份 */
	private static final Map<UUID, Float> OWNED = new HashMap<>();
	/** 这一次触发定格的护盾上限（点）：由 CombatState 在反应那一刻按那一枪的等级写进来 */
	private static final Map<UUID, Float> CAP = new HashMap<>();

	public CrystallizationEffect() {
		super(MobEffectCategory.BENEFICIAL, 0xE8C65A);
	}

	/** 触发侧定格上限（点，1 颗心 = 2 点） */
	public static void setCap(UUID id, float capPoints) {
		CAP.put(id, capPoints);
	}

	/** 一层该加多少点（1 颗心 = 2 点）：固定 2 颗心，但不能把总护盾推过上限（主动技能也占这份额度） */
	public static float layerGrant(LivingEntity entity) {
		float perLayer = perLayerPoints();
		Float cap = CAP.get(entity.getUUID());
		if (cap == null) {
			return perLayer;
		}
		return Math.max(0.0F, Math.min(perLayer, cap - entity.getAbsorptionAmount()));
	}

	private static float perLayerPoints() {
		return (float) (GunLevelMod.CONFIG.crystallizeHeartsPerLevel * 2.0);
	}

	@Override
	public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
		float grant = layerGrant(entity);
		if (grant > 0.0F) {
			entity.setAbsorptionAmount(entity.getAbsorptionAmount() + grant);
			OWNED.merge(entity.getUUID(), grant, Float::sum);
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
	 * 读存档时原版直接把实例塞回状态表、不调那两个钩子，所以我们的心会留下一段没记录的历史。
	 * 每 tick 校正一次：没记录就按现有吸收值认领（上限只到自己该给的档位），
	 * 记录超过实际吸收值就压回去 —— 金心被打掉之后，到期不会顺手多扣别人的护盾。
	 */
	@Override
	public void applyEffectTick(LivingEntity entity, int amplifier) {
		UUID uuid = entity.getUUID();
		float ceiling = perLayerPoints() * (amplifier + 1);
		Float cap = CAP.get(uuid);
		if (cap != null) {
			ceiling = Math.min(ceiling, cap);
		}
		float absorption = Math.max(0.0F, entity.getAbsorptionAmount());
		Float mine = OWNED.get(uuid);
		if (mine == null) {
			OWNED.put(uuid, Math.min(ceiling, absorption));
		} else if (mine > ceiling) {
			OWNED.put(uuid, ceiling);
		} else if (mine > absorption) {
			OWNED.put(uuid, absorption);
		}
	}

	@Override
	public boolean isDurationEffectTick(int duration, int amplifier) {
		return true;
	}

	private static void revoke(LivingEntity entity) {
		Float mine = OWNED.remove(entity.getUUID());
		if (mine != null && mine > 0.0F) {
			entity.setAbsorptionAmount(Math.max(0.0F, entity.getAbsorptionAmount() - mine));
		}
	}
}
