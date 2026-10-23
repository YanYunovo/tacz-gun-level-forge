package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.net.GunLevelNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灵动步伐的被动：主手拿着装了这颗芯片的枪时，移动速度 +20%、护甲值 −4。
 *
 * <p>两个修正都要**两端各挂一次**：移速那边是 1.20.1 玩家移动由客户端预测，只挂服务端等于没挂；
 * 护甲这边服务端那份才真正参与减伤，客户端那份只管 HUD 上那排护甲图标 —— 不挂就会出现
 * 「图标显示满甲、实际少吃 4 点」的错觉。原版没有玩家属性的 S2C 同步包，所以走
 * {@link GunLevelNetwork} 的 {@code NimbleSync}，两端用同一对 UUID。
 *
 * <p>和烈焰咆哮的减速不同，这里不留切枪拖尾：这颗芯片的收益与代价是同时给、同时收的，
 * 切走枪就该两边一起还原（咆哮那 40% 是纯代价，能靠切枪甩掉就等于没有代价）。
 */
public final class NimbleState {
	private static final UUID SPEED_ID = UUID.nameUUIDFromBytes(
			"tacz_gun_level:nimble_speed".getBytes(StandardCharsets.UTF_8));
	private static final UUID ARMOR_ID = UUID.nameUUIDFromBytes(
			"tacz_gun_level:nimble_armor".getBytes(StandardCharsets.UTF_8));
	private static final String SPEED_NAME = "tacz_gun_level_nimble_speed";
	private static final String ARMOR_NAME = "tacz_gun_level_nimble_armor";

	private static final class Stance {
		/** 现在挂着没有（只在真的翻转时才动属性、才发包） */
		boolean on;
	}

	private static final Map<UUID, Stance> STANCES = new ConcurrentHashMap<>();

	private NimbleState() {
	}

	/** 这把枪是不是装了灵动步伐（被动只看芯片，不看元素、不看等级） */
	public static boolean carries(ItemStack gun) {
		return !gun.isEmpty() && GunChipData.get(gun).contains(Chip.NIMBLE_STEP);
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean should = carries(player.getMainHandItem());
			Stance stance = STANCES.get(player.getUUID());
			if (stance == null) {
				if (!should) {
					continue;
				}
				stance = new Stance();
				STANCES.put(player.getUUID(), stance);
			}
			if (should == stance.on) {
				continue;
			}
			stance.on = should;
			apply(player, should);
			GunLevelNetwork.sendNimble(player, should);
		}
		STANCES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	/** 挂/摘两个修正：先无条件摘一次再按需挂，重复调用不会叠出两份 */
	private static void apply(LivingEntity entity, boolean on) {
		applyModifier(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_NAME,
				on ? GunLevelMod.CONFIG.chipPurpleNimbleSpeed : 0.0,
				AttributeModifier.Operation.MULTIPLY_TOTAL);
		applyModifier(entity, Attributes.ARMOR, ARMOR_ID, ARMOR_NAME,
				on ? -GunLevelMod.CONFIG.chipPurpleNimbleArmorPenalty : 0.0,
				AttributeModifier.Operation.ADDITION);
	}

	private static void applyModifier(LivingEntity entity, Attribute attribute, UUID id, String name,
			double value, AttributeModifier.Operation operation) {
		AttributeInstance instance = entity.getAttributes().getInstance(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(id);
		if (value != 0.0) {
			instance.addTransientModifier(new AttributeModifier(id, name, value, operation));
		}
	}

	/** 客户端那一份：S2C 收到后给本地玩家挂/摘同一对修正 */
	public static void applyClient(LivingEntity clientPlayer, boolean on) {
		if (clientPlayer != null) {
			apply(clientPlayer, on);
		}
	}

	public static void clear() {
		STANCES.clear();
	}
}
