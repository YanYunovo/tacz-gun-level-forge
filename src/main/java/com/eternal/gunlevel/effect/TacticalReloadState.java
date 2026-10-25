package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.tacz.guns.api.entity.ReloadState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战术换弹的「换弹完成」记账：LivingEntityReload#tickReloadState 每 tick 都会返回
 * 当前的换弹状态，这里按玩家记上一拍的状态 —— 上一拍还在换弹、这一拍变成
 * {@code NOT_RELOADING}，就是「换弹刚完成」的那一刻，把截止时刻写进**这把枪**的 NBT
 * （{@code tacz_gun_level_tac_reload_until}）。子弹出膛时读它定格（与咆哮匣同一手法），
 * 命中结算里吃「换弹后 3 秒」的伤害加成。
 *
 * <p>状态记在枪上而不是人上：换弹完成切走枪，加成留在完成换弹的那把枪上。
 */
public final class TacticalReloadState {
	public static final String TAG = "tacz_gun_level_tac_reload_until";

	private static final Map<UUID, ReloadState.StateType> PREVIOUS = new ConcurrentHashMap<>();

	private TacticalReloadState() {
	}

	/** 换弹状态推进的每一拍都喂进来（tickReloadState 的 RETURN 注入点，双端都在跑） */
	public static void onTick(LivingEntity shooter, ReloadState state) {
		if (shooter == null || shooter.level().isClientSide() || state == null) {
			return;
		}
		ReloadState.StateType type = state.getStateType();
		UUID id = shooter.getUUID();
		ReloadState.StateType previous = PREVIOUS.put(id, type);
		if (previous != null && previous.isReloading() && !type.isReloading()) {
			// 上一拍还在换弹、这一拍归位 —— 换弹刚完成
			ItemStack main = shooter.getMainHandItem();
			if (!main.isEmpty()) {
				mark(main, shooter.level().getGameTime());
			}
		}
	}

	/** 这把枪现在是否处在「换弹完成后 3 秒」的窗口里（子弹出膛时定格用） */
	public static boolean inWindow(ItemStack gun, long time) {
		if (gun.isEmpty()) {
			return false;
		}
		CompoundTag nbt = gun.getTag();
		return nbt != null && time < nbt.getLong(TAG);
	}

	private static void mark(ItemStack gun, long time) {
		long until = time + Math.max(1,
				Math.round(GunLevelMod.CONFIG.chipTacReloadBonusSeconds * 20));
		gun.getOrCreateTag().putLong(TAG, until);
	}

	public static void tick(MinecraftServer server) {
		PREVIOUS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	public static void clear() {
		PREVIOUS.clear();
	}
}
