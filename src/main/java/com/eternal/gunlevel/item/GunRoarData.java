package com.eternal.gunlevel.item;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 烈焰咆哮的按枪状态：全部记在枪物品 NBT 上（用户口径「此武器击杀 3 次」，所以计数跟着枪走，
 * 两把装了这颗芯片的枪各攒各的）。
 *
 * <p>三个互相独立的东西：
 * <ul>
 *   <li>{@code MAG_TAG} —— 这一匣是「咆哮匣」，容量被压成 1 发。打完那一发就由射击路径抹掉；
 *       不需要处理「玩家中途换弹把它洗掉」，因为容量 1、当前 1 时 TACZ 的换弹起手检查根本不会通过。</li>
 *   <li>{@code UNTIL_TAG} —— 特殊弹 400% 基础伤害的截止时刻，子弹生成那一刻读它决定自己是不是特殊弹。</li>
 *   <li>{@code USED_TAG} + 两个计数 —— 技能用掉了没有、这把枪又打了几次击杀/爆头。</li>
 * </ul>
 *
 * <p>只读一律走 {@link ItemStack#getTag()}：{@code getOrCreateTag} 会给一张干净的枪凭空挂上空 compound，
 * 而 1.20.1 的 tooltip 只要 {@code hasTag()} 就在名字底下补一行模组名。
 */
public final class GunRoarData {
	public static final String MAG_TAG = "tacz_gun_level_roar_mag";
	public static final String UNTIL_TAG = "tacz_gun_level_roar_until";
	public static final String USED_TAG = "tacz_gun_level_roar_used";
	public static final String KILLS_TAG = "tacz_gun_level_roar_kills";
	public static final String HEADS_TAG = "tacz_gun_level_roar_heads";

	private GunRoarData() {
	}

	public static boolean isMagOn(ItemStack gun) {
		CompoundTag nbt = tag(gun);
		return nbt != null && nbt.getBoolean(MAG_TAG);
	}

	/** 特殊弹加成还有效吗（咆哮匣打完或 5 秒过掉就都不算） */
	public static boolean isEmpowered(ItemStack gun, long time) {
		CompoundTag nbt = tag(gun);
		return nbt != null && nbt.getBoolean(MAG_TAG) && time <= nbt.getLong(UNTIL_TAG);
	}

	/** 技能能不能放：没被用掉过、或者已经被击杀/爆头刷新过 */
	public static boolean isReady(ItemStack gun) {
		CompoundTag nbt = tag(gun);
		return nbt == null || !nbt.getBoolean(USED_TAG);
	}

	/** 放下技能：进「已使用」、计数清零、开咆哮匣并给特殊弹定截止时刻 */
	public static void use(ItemStack gun, long time, long empowerTicks) {
		if (gun.isEmpty()) {
			return;
		}
		CompoundTag nbt = gun.getOrCreateTag();
		nbt.putBoolean(USED_TAG, true);
		nbt.putInt(KILLS_TAG, 0);
		nbt.putInt(HEADS_TAG, 0);
		nbt.putBoolean(MAG_TAG, true);
		nbt.putLong(UNTIL_TAG, time + empowerTicks);
	}

	/** 特殊弹已经出膛：这一匣不再特殊（容量回到正常值） */
	public static void endMag(ItemStack gun) {
		CompoundTag nbt = tag(gun);
		if (nbt != null) {
			nbt.remove(MAG_TAG);
			nbt.remove(UNTIL_TAG);
		}
	}

	/** 击杀数 +1，够数就刷新技能；返回 true 表示这一刻刚刷新，调用方据此提示 */
	public static boolean onKill(ItemStack gun) {
		return bump(gun, KILLS_TAG, GunLevelMod.CONFIG.chipRoarKillsToRefresh);
	}

	/** 爆头数 +1（真爆头与概率转化来的都算），够数就刷新技能 */
	public static boolean onHeadshot(ItemStack gun) {
		return bump(gun, HEADS_TAG, GunLevelMod.CONFIG.chipRoarHeadshotsToRefresh);
	}

	private static boolean bump(ItemStack gun, String tag, int need) {
		if (gun.isEmpty() || need <= 0) {
			return false;
		}
		CompoundTag nbt = tag(gun);
		if (nbt == null || !nbt.getBoolean(USED_TAG)) {
			// 还没用掉过就不攒，免得攒一堆用不上的数
			return false;
		}
		int value = nbt.getInt(tag) + 1;
		if (value < need) {
			nbt.putInt(tag, value);
			return false;
		}
		nbt.putBoolean(USED_TAG, false);
		nbt.putInt(KILLS_TAG, 0);
		nbt.putInt(HEADS_TAG, 0);
		return true;
	}

	private static CompoundTag tag(ItemStack gun) {
		return gun.isEmpty() ? null : gun.getTag();
	}
}
