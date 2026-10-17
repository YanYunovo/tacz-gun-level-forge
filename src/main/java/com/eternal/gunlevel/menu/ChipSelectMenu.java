package com.eternal.gunlevel.menu;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.net.GunLevelNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 金芯片自选包的选择界面。
 *
 * <p>为什么不是工作台配方：9 个破碎金芯片的输入完全相同，挂 9 条同输入的配方在合成台上没法让玩家挑，
 * 拿到的永远是同一条。所以 9 破碎 → 1 自选包，选哪颗交给这个界面，点一下由服务端结算。
 *
 * <p>界面里带上玩家自己的背包槽不是为了让玩家整理背包，而是背包数量的同步走的就是这个容器的
 * {@code sendContentUpdates}：不带槽位的话，扣掉一个自选包、多出一颗芯片这些变化客户端根本看不到。
 */
public class ChipSelectMenu extends AbstractContainerMenu implements MenuProvider {
	/** 选择区排版见 {@link #cellX(int)}：一格 20px、间隔 2px，最多两行 */
	public static final int CELL = 22;
	public static final int GRID_Y = 20;
	public static final int PANEL_WIDTH = 176;

	public static MenuType<ChipSelectMenu> TYPE;

	public ChipSelectMenu(int syncId, Inventory playerInventory) {
		super(TYPE, syncId);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 143));
		}
	}

	public static int columns() {
		int n = Chip.values().length;
		return Math.max(1, Math.min(7, (n + 1) / 2));
	}

	public static int gridX() {
		return (PANEL_WIDTH - columns() * CELL + 2) / 2;
	}

	public static int cellX(int index) {
		return gridX() + (index % columns()) * CELL;
	}

	public static int cellY(int index) {
		return GRID_Y + (index / columns()) * CELL;
	}

	/**
	 * 服务端结算：只认「当前确实开着这个界面」的玩家，扣一个自选包、给一颗芯片。
	 * 芯片序号由客户端发来，所以这里必须自己再判一次范围，别拿越界的下标去索引数组。
	 */
	public static void choose(Player player, int ordinal) {
		Chip[] chips = Chip.values();
		if (ordinal < 0 || ordinal >= chips.length || !(player.containerMenu instanceof ChipSelectMenu)) {
			return;
		}
		Inventory inventory = player.getInventory();
		int slot = inventory.findSlotMatchingItem(new ItemStack(GunLevelMod.GOLD_CHIP_PACK));
		if (slot < 0) {
			return;
		}
		inventory.getItem(slot).shrink(1);
		ItemStack chip = new ItemStack(GunLevelMod.CHIPS[ordinal]);
		if (!inventory.add(chip)) {
			player.drop(chip, false);
		}
		// 挑完就关：自选包一次只换一颗，留着界面只会让人以为还能再点
		player.closeContainer();
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
		return new ChipSelectMenu(syncId, playerInventory);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.tacz_gun_level.chip_select");
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	/** 没有可移动的槽位：shift 点芯片什么都不做 */
	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	/** 客户端点选后发给服务端 */
	public static void sendChoice(int ordinal) {
		GunLevelNetwork.sendChipSelect(ordinal);
	}
}
