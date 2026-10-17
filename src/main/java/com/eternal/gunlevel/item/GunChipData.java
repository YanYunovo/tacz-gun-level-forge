package com.eternal.gunlevel.item;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.LevelConfig;
import com.eternal.gunlevel.effect.Chip;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 枪械上的战术芯片读写：按安装顺序存进枪物品 NBT，同一颗芯片不能重复装。
 * 槽位数由永恒改装等级决定，见 {@link #capacity(int)}。
 */
public final class GunChipData {
	public static final String TAG = "tacz_gun_level_chips";

	private GunChipData() {
	}

	public static EnumSet<Chip> get(ItemStack gun) {
		EnumSet<Chip> chips = EnumSet.noneOf(Chip.class);
		if (gun.isEmpty()) {
			return chips;
		}
		ListTag list = readOnlyList(gun);
		for (int i = 0; i < list.size(); i++) {
			Chip chip = parse(list.getString(i));
			if (chip != null) {
				chips.add(chip);
			}
		}
		return chips;
	}

	/** 只读视图：绝不能用 getOrCreateTag，那会给一张干净的stack凭空挂上空 NBT，
	 *  而 1.20.1 的 tooltip 一看到有 NBT 就在底下补一行模组名。 */
	private static ListTag readOnlyList(ItemStack gun) {
		CompoundTag nbt = gun.getTag();
		return nbt == null ? new ListTag() : nbt.getList(TAG, Tag.TAG_STRING);
	}

	/**
	 * 按安装顺序给出芯片（第一格在前）。{@link #get} 返回的是 EnumSet，顺序会按枚举序号重排，
	 * 「第一格芯片的技能优先」这种规则必须用这个有序视图。
	 */
	public static List<Chip> ordered(ItemStack gun) {
		List<Chip> chips = new ArrayList<>();
		if (gun.isEmpty()) {
			return chips;
		}
		ListTag list = readOnlyList(gun);
		for (int i = 0; i < list.size(); i++) {
			Chip chip = parse(list.getString(i));
			if (chip != null) {
				chips.add(chip);
			}
		}
		return chips;
	}

	public static void add(ItemStack gun, Chip chip) {
		readList(gun).add(StringTag.valueOf(chip.name()));
	}

	/** 卸下最后装上的那颗；没装芯片返回 null */
	public static Chip removeLast(ItemStack gun) {
		if (gun.getTag() == null) {
			return null;
		}
		ListTag list = readList(gun);
		for (int i = list.size() - 1; i >= 0; i--) {
			Chip chip = parse(list.getString(i));
			if (chip != null) {
				list.remove(i);
				if (list.isEmpty()) {
					gun.getOrCreateTag().remove(TAG);
				}
				return chip;
			}
		}
		return null;
	}

	/** 该等级能装几颗芯片（LV3 一颗、LV5 两颗） */
	public static int capacity(int level) {
		LevelConfig c = GunLevelMod.CONFIG;
		if (level >= c.chipSecondSlotLevel) {
			return 2;
		}
		return level >= c.chipSlotLevel ? 1 : 0;
	}

	/** 还能不能装这颗：槽位没满且没装过同款 */
	public static boolean canInstall(ItemStack gun, Chip chip) {
		EnumSet<Chip> installed = get(gun);
		return !installed.contains(chip) && installed.size() < capacity(GunLevelData.getLevel(gun));
	}

	public static boolean isChip(ItemStack stack) {
		return stack.getItem() instanceof TacticalChipItem;
	}

	/** 手持物品若是战术芯片则返回其类型，否则 null */
	public static Chip chipOfStack(ItemStack stack) {
		return isChip(stack) ? ((TacticalChipItem) stack.getItem()).chip() : null;
	}

	public static ItemStack newChip(Chip chip) {
		Item item = chipItem(chip);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	private static Item chipItem(Chip chip) {
		// 按注册名查，新增芯片只要在枚举里加一项并注册，不用回来改这张表
		Item item = ForgeRegistries.ITEMS.getValue(GunLevelMod.id(chip.itemId()));
		return item == null || item == Items.AIR ? null : item;
	}

	/** 拿可写的清单：标签缺失时挂一个空表进去，否则改了不生效 */
	private static ListTag readList(ItemStack gun) {
		CompoundTag nbt = gun.getOrCreateTag();
		if (nbt.get(TAG) instanceof ListTag list) {
			return list;
		}
		ListTag fresh = new ListTag();
		nbt.put(TAG, fresh);
		return fresh;
	}

	private static Chip parse(String name) {
		if (name.isEmpty()) {
			return null;
		}
		try {
			return Chip.valueOf(name);
		} catch (IllegalArgumentException e) {
			return null; // 以后删掉的芯片在旧存档里直接忽略
		}
	}
}
