package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.item.GunCoreData;
import com.eternal.gunlevel.item.GunRoarData;
import com.eternal.gunlevel.net.GunLevelNetwork;
import com.eternal.gunlevel.util.ChipHints;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 烈焰咆哮的被动：拿着这把枪就慢 40%，切走武器后这个减速还要再拖 3 秒才消失。
 *
 * <p>拖尾是故意的 —— 它是这颗芯片的代价，能靠切枪白嫖掉就等于没有代价。
 * 所以这里按玩家记「最后一次拿着这把枪是哪一刻」，只要「正拿着」或「刚放下不到 3 秒」就挂着减速。
 *
 * <p>和风的护佑一样，移速修正必须**两端各挂一次**：1.20.1 玩家移动由客户端预测，
 * 只挂服务端等于没挂（客户端还按自己的属性算加速），而原版没有玩家属性的 S2C 同步包，
 * 所以走 {@code GunLevelNetwork.RoarSpeedSync} 那一个 boolean 包，两端用同一个修正 UUID。
 */
public final class FlameRoarState {
	private static final UUID SPEED_MODIFIER_ID = UUID.nameUUIDFromBytes(
			"tacz_gun_level:roar_speed".getBytes(StandardCharsets.UTF_8));
	private static final String SPEED_MODIFIER_NAME = "tacz_gun_level_roar_speed";

	private static final class Slow {
		/** 最后一次拿着装了这颗芯片的主手枪是哪一刻 */
		long lastHeldAt;
		/** 减速现在挂着没有（只在真的翻转时才发包） */
		boolean on;
	}

	private static final Map<UUID, Slow> SLOWS = new ConcurrentHashMap<>();

	private FlameRoarState() {
	}

	/** 这把枪是不是装了烈焰咆哮（被动只看芯片，不看元素） */
	public static boolean carries(ItemStack gun) {
		return !gun.isEmpty() && GunChipData.get(gun).contains(Chip.FLAME_ROAR);
	}

	public static void tick(MinecraftServer server) {
		long linger = Math.max(1, Math.round(GunLevelMod.CONFIG.chipRoarLingeringSeconds * 20));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean holding = carries(player.getMainHandItem());
			Slow slow = SLOWS.get(player.getUUID());
			if (slow == null) {
				if (!holding) {
					continue;
				}
				slow = new Slow();
				SLOWS.put(player.getUUID(), slow);
			}
			long now = player.level().getGameTime();
			if (holding) {
				slow.lastHeldAt = now;
			}
			boolean should = holding || now - slow.lastHeldAt <= linger;
			if (should == slow.on) {
				continue;
			}
			slow.on = should;
			applySpeed(player, should);
			syncSpeed(player, should);
		}
		SLOWS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	/**
	 * 咆哮一击：把这一匣重铸成「只剩一发」的特殊弹匣 —— 匣里原有的子弹退回后备，
	 * 再从后备真实扣掉一发装进去，然后给这一发挂上 400% 基础伤害的截止时刻。
	 *
	 * <p>全程绕开 TACZ 的换弹状态机（它做不到「只装一发、立刻」）。状态机在 FINISHING 是按
	 * 「容量 − 当前」补弹的，而咆哮匣期间容量被我们顶成 1、当前也是 1，它算出来的需求量就是 0，
	 * 所以就算玩家在换弹中途按了技能，也不会被覆盖、更不会重复扣弹。
	 *
	 * <p>扣弹/退弹自己扫玩家背包，判兼容只用 TACZ 的 {@code IAmmo#isAmmoOfGun} 与
	 * {@code IAmmoBox#isAmmoBoxOfGun}（弹匣盒按盒内计数算），不按物品 id 硬猜。
	 */
	public static void roar(ServerPlayer player) {
		ItemStack gun = player.getMainHandItem();
		if (!carries(gun) || GunCoreData.get(gun) != Element.FIRE || !GunRoarData.isReady(gun)) {
			return;
		}
		IGun iGun = IGun.getIGunOrNull(gun);
		ResourceLocation ammoId = ammoId(gun);
		int load = GunLevelMod.CONFIG.chipRoarMagAmmo;
		if (iGun == null || ammoId == null) {
			// 认不出弹型就整个不放：宁可什么都不发生，也不能把匣里的子弹凭空吞掉
			return;
		}
		// 正在换弹时不动弹匣：状态机在 FEEDING→FINISHING 那一跳会按「容量 − 当前」补弹并写回，
		// 我们抢在它中间写会打架
		ReloadState.StateType state = IGunOperator.fromLivingEntity(player)
				.getSynReloadState().getStateType();
		if (state != ReloadState.StateType.NOT_RELOADING) {
			return;
		}
		int inMagazine = Math.max(0, iGun.getCurrentAmmoCount(gun));
		if (inMagazine < load && reserve(player, gun) + inMagazine < load) {
			// 匣里没弹、背包也没这种弹 ⇒ 这一发特殊弹无从装填
			return;
		}
		if (inMagazine > 0) {
			returnToReserve(player, ammoId, inMagazine);
		}
		takeFromReserve(player, gun, load);
		iGun.setCurrentAmmoCount(gun, load);
		GunRoarData.use(gun, player.level().getGameTime(), Math.max(1,
				Math.round(GunLevelMod.CONFIG.chipRoarEmpowerSeconds * 20)));
		ChipHints.show(player, Chip.FLAME_ROAR);
	}

	/** 背包里这种弹还有多少发：普通弹药按堆叠数，弹匣盒按盒内存量 */
	private static int reserve(Player player, ItemStack gun) {
		int total = 0;
		for (ItemStack stack : player.getInventory().items) {
			total += countIn(stack, gun);
		}
		return total;
	}

	private static int countIn(ItemStack stack, ItemStack gun) {
		if (stack.isEmpty()) {
			return 0;
		}
		IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
		if (ammo != null && ammo.isAmmoOfGun(gun, stack)) {
			return stack.getCount();
		}
		if (stack.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(gun, stack)) {
			return box.getAmmoCount(stack);
		}
		return 0;
	}

	private static void returnToReserve(ServerPlayer player, ResourceLocation ammoId, int rounds) {
		// 造弹只能走 AmmoItemBuilder：TACZ 全模组共用一个弹药物品，弹型写在 NBT 的 AmmoId 上，
		// 所以 new ItemStack(某个物品) 造不出子弹；堆叠上限也得先带上弹型才读得到（按弹种 json 的 stack_size）
		int max = Math.max(1,
				AmmoItemBuilder.create().setId(ammoId).setCount(1).build().getMaxStackSize());
		int left = rounds;
		while (left > 0) {
			int size = Math.min(left, max);
			left -= size;
			ItemStack stack = AmmoItemBuilder.create().setId(ammoId).setCount(size).build();
			// add 塞不下的部分留在 stack 里并返回 false，剩下的掉在脚下，绝不凭空消失
			if (!player.getInventory().add(stack) && !stack.isEmpty()) {
				player.drop(stack, false);
			}
		}
	}

	private static void takeFromReserve(Player player, ItemStack gun, int amount) {
		int left = amount;
		List<ItemStack> items = player.getInventory().items;
		for (int i = 0; i < items.size() && left > 0; i++) {
			ItemStack stack = items.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
			if (ammo != null && ammo.isAmmoOfGun(gun, stack)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
				if (stack.isEmpty()) {
					items.set(i, ItemStack.EMPTY);
				}
				continue;
			}
			if (stack.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(gun, stack)) {
				int have = box.getAmmoCount(stack);
				int take = Math.min(left, have);
				box.setAmmoCount(stack, have - take);
				left -= take;
			}
		}
	}

	/**
	 * 这把枪用的弹型 id。注意它是**逻辑弹型**而不是物品注册 id —— TACZ 所有弹药共用一个弹药物品，
	 * 弹型写在 NBT 的 AmmoId 上，所以拿这个 id 去查物品注册表只会拿到空气。
	 */
	private static ResourceLocation ammoId(ItemStack gun) {
		IGun iGun = IGun.getIGunOrNull(gun);
		if (iGun == null) {
			return null;
		}
		return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
				.map(index -> index.getGunData().getAmmoId())
				.orElse(null);
	}

	public static void clear() {
		SLOWS.clear();
	}

	private static void applySpeed(LivingEntity entity, boolean on) {
		AttributeInstance instance = entity.getAttributes().getInstance(Attributes.MOVEMENT_SPEED);
		if (instance == null) {
			return;
		}
		instance.removeModifier(SPEED_MODIFIER_ID);
		if (on) {
			// MULTIPLY_TOTAL 配负值就是按比例扣：-0.40 ⇒ 只剩 60%
			instance.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID,
					SPEED_MODIFIER_NAME, -GunLevelMod.CONFIG.chipRoarSpeedPenalty,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	/** 客户端那一份：S2C 收到后给本地玩家挂/摘同一个修正 */
	public static void applySpeedClient(LivingEntity clientPlayer, boolean on) {
		if (clientPlayer != null) {
			applySpeed(clientPlayer, on);
		}
	}

	private static void syncSpeed(ServerPlayer player, boolean on) {
		GunLevelNetwork.sendRoarSpeed(player, on);
	}
}
