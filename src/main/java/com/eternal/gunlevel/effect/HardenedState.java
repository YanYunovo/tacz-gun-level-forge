package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.net.GunLevelNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 硬化护甲的被动：主手拿着装了这颗芯片的枪时，护甲值 +{@code chipPurpleHardenedArmor}。
 * 形状照抄灵动步伐（{@link NimbleState}）：服务端那份真参与减伤，客户端那份管 HUD 那排
 * 护甲图标（原版没有玩家属性的 S2C 同步包，所以走 {@link GunLevelNetwork} 的
 * {@code HardenedSync}），两端同一个 UUID，切枪同时收、不留拖尾。
 */
public final class HardenedState {
	private static final UUID ARMOR_ID = UUID.nameUUIDFromBytes(
			"tacz_gun_level:hardened_armor".getBytes(StandardCharsets.UTF_8));
	private static final String ARMOR_NAME = "tacz_gun_level_hardened_armor";

	private static final class Stance {
		boolean on;
	}

	private static final Map<UUID, Stance> STANCES = new ConcurrentHashMap<>();

	private HardenedState() {
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
			GunLevelNetwork.sendHardened(player, should);
		}
		STANCES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	/** 这把枪是不是装了硬化护甲（被动只看芯片，不看元素、不看等级） */
	static boolean carries(ItemStack gun) {
		return !gun.isEmpty() && GunChipData.get(gun).contains(Chip.HARDENED_ARMOR);
	}

	/** 挂/摘修正：先无条件摘一次再按需挂，重复调用不会叠出两份 */
	private static void apply(LivingEntity entity, boolean on) {
		AttributeInstance instance = entity.getAttributes().getInstance(Attributes.ARMOR);
		if (instance == null) {
			return;
		}
		instance.removeModifier(ARMOR_ID);
		if (on) {
			instance.addTransientModifier(new AttributeModifier(ARMOR_ID, ARMOR_NAME,
					GunLevelMod.CONFIG.chipPurpleHardenedArmor,
					AttributeModifier.Operation.ADDITION));
		}
	}

	/** 客户端那一份：S2C 收到后给本地玩家挂/摘同一个修正 */
	public static void applyClient(LivingEntity clientPlayer, boolean on) {
		if (clientPlayer != null) {
			apply(clientPlayer, on);
		}
	}

	public static void clear() {
		STANCES.clear();
	}
}
