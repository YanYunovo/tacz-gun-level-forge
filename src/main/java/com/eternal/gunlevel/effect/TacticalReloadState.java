package com.eternal.gunlevel.effect;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.util.ChipHints;
import com.eternal.gunlevel.util.TaczCompat;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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

	/** 快速装填还剩多少 tick（≤0 = 可用）：全局主动技能锁用它判断什么时候解锁 */
	public static long remaining(LivingEntity player, long now) {
		Skill skill = player == null ? null : SKILLS.get(player.getUUID());
		return skill == null ? 0L : Math.max(0L, skill.readyAt - now);
	}

	public static void tick(MinecraftServer server) {
		PREVIOUS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		// 「快速装填已就绪」提示
		server.getPlayerList().getPlayers().forEach(player -> {
			Skill skill = SKILLS.get(player.getUUID());
			if (skill == null || skill.announced || player.level().getGameTime() < skill.readyAt) {
				return;
			}
			skill.announced = true;
			ChipHints.show(player, Chip.TAC_RELOAD, ChipHints.SKILL_READY);
		});
	}

	public static void clear() {
		PREVIOUS.clear();
		SKILLS.clear();
	}

	// ------------------------------------------------------------------ 主动

	private static final class Skill {
		long readyAt;
		boolean announced;
	}

	/** 战术换弹的主动冷却（按玩家记） */
	private static final Map<UUID, Skill> SKILLS = new ConcurrentHashMap<>();

	/**
	 * 主动「快速装填」：把主弹匣立刻填满。不需要元素核芯，装了芯片就能放。
	 * 实现是把弹药数直接顶到容量 —— TACZ 的 tickReloadState 在下一拍发现弹匣已满，
	 * 会自然走 FINISHING → NOT_RELOADING 收尾，换弹动画照常播完但不再吃子弹。
	 */
	public static void quickReload(ServerPlayer player) {
		Skill skill = SKILLS.computeIfAbsent(player.getUUID(), id -> new Skill());
		long now = player.level().getGameTime();
		if (now < skill.readyAt) {
			return;
		}
		ItemStack gun = player.getMainHandItem();
		IGun iGun = IGun.getIGunOrNull(gun);
		if (iGun == null) {
			return;
		}
		int capacity = TaczCompat.magazineCapacity(gun);
		if (capacity <= 0) {
			return;
		}
		iGun.setCurrentAmmoCount(gun, capacity);
		skill.readyAt = now + Math.max(1,
				Math.round(GunLevelMod.CONFIG.chipTacReloadSkillCooldown * 20));
		SkillLock.arm(player, Chip.TAC_RELOAD);
		skill.announced = false;
	}
}
