package com.eternal.gunlevel.menu;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.util.TaczCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;

public class UpgradeMenu extends AbstractContainerMenu {
	public static final int SLOT_GUN = 0;
	public static final int SLOT_MATERIAL = 1;
	public static final int SLOT_RESULT = 2;
	public static final int SLOT_GUN_X = 44;
	public static final int SLOT_MAT_X = 72;
	public static final int SLOT_RESULT_X = 122;
	public static final int SLOT_Y = 38;
	private static final int CUSTOM_SLOTS = 3;

	/** 原版三行箱子的背包坐标：下半段底图从 y+71 起，槽框顶线在贴图源 v=139，格因此落在 85 */
	private static final int INVENTORY_Y = 85;
	private static final int HOTBAR_Y = 143;

	public static MenuType<UpgradeMenu> TYPE;

	public final SimpleContainer container = new SimpleContainer(CUSTOM_SLOTS);
	private boolean syncing;

	public UpgradeMenu(int syncId, Inventory playerInventory) {
		super(TYPE, syncId);
		addSlot(new GunSlot(container, SLOT_GUN, SLOT_GUN_X, SLOT_Y));
		addSlot(new MaterialSlot(container, SLOT_MATERIAL, SLOT_MAT_X, SLOT_Y));
		addSlot(new ResultSlot(container, SLOT_RESULT, SLOT_RESULT_X, SLOT_Y));
		addPlayerInventory(playerInventory);
		container.addListener(inventory -> updateResult());
	}


	private void addPlayerInventory(Inventory playerInventory) {
		// 背包坐标照抄原版三行箱子：下半段底图从 y+71 起，槽框顶线落在 84，格子本身在 85
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
		}
	}

	public void updateResult() {
		if (syncing) {
			return; // setStack 会再触发监听器，避免递归
		}
		syncing = true;
		try {
			ItemStack gun = container.getItem(SLOT_GUN);
			ItemStack material = container.getItem(SLOT_MATERIAL);
			ItemStack result = ItemStack.EMPTY;
			if (TaczCompat.isGun(gun)) {
				int level = GunLevelData.getLevel(gun);
				if (level < GunLevelMod.CONFIG.maxLevel && UpgradeCosts.matches(level, material)) {
					result = gun.copy();
					result.setCount(1);
					int newLevel = level + 1;
					GunLevelData.setLevel(result, newLevel);
					GunLevelData.applyLore(result, newLevel);
				}
			}
			if (!ItemStack.isSameItemSameTags(result, container.getItem(SLOT_RESULT))) {
				container.setItem(SLOT_RESULT, result);
				broadcastChanges();
			}
		} finally {
			syncing = false;
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotId) {
		if (slotId == SLOT_RESULT) {
			return shiftTakeResult();
		}
		Slot slot = slots.get(slotId);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack slotStack = slot.getItem();
		ItemStack original = slotStack.copy();

		if (slotId < CUSTOM_SLOTS) {
			if (!moveItemStackTo(slotStack, CUSTOM_SLOTS, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			int to = TaczCompat.isGun(slotStack) ? SLOT_GUN + 1 : SLOT_RESULT;
			if (!moveItemStackTo(slotStack, SLOT_GUN, to, false)) {
				return ItemStack.EMPTY;
			}
		}

		if (slotStack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		updateResult();
		return original;
	}

	/**
	 * shift 点成品。原版 {@code clickSlot} 的 QUICK_MOVE 分支会反复调 quickMove，
	 * 只要槽里还是同一件物品就继续，所以这里必须真的结算一次（吃掉材料和原枪），
	 * 否则就是无限白拿成品。成品恒为 1 件：insertItem 要么整份塞进去要么完全没动，
	 * 放不下就直接返回，不会出现「塞了一半又没扣材料」。
	 */
	private ItemStack shiftTakeResult() {
		ItemStack result = container.getItem(SLOT_RESULT);
		if (result.isEmpty() || !canSettle()) {
			return ItemStack.EMPTY;
		}
		if (!moveItemStackTo(result.copy(), CUSTOM_SLOTS, slots.size(), false)) {
			return ItemStack.EMPTY;
		}
		ItemStack moved = result.copy();
		performUpgrade();
		return moved;
	}

	/** 和 updateResult 的判定一致：确认这次 shift 真会吃掉材料和原枪，才不会变成无限白拿 */
	private boolean canSettle() {
		ItemStack gun = container.getItem(SLOT_GUN);
		return TaczCompat.isGun(gun)
				&& UpgradeCosts.matches(GunLevelData.getLevel(gun), container.getItem(SLOT_MATERIAL));
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.setItem(SLOT_RESULT, ItemStack.EMPTY); // 成品为幽灵物品，丢弃前清除
		MenuUtil.returnContents(player, container);
	}

	/** 完成一次升级：消耗材料与枪，产出升级后的枪 */
	private void performUpgrade() {
		ItemStack gun = container.getItem(SLOT_GUN);
		ItemStack material = container.getItem(SLOT_MATERIAL);
		ItemStack result = container.getItem(SLOT_RESULT);
		if (result.isEmpty() || gun.isEmpty()) {
			return;
		}
		UpgradeCosts.Cost cost = UpgradeCosts.costFor(GunLevelData.getLevel(gun));
		if (cost == null) {
			return;
		}
		material.shrink(cost.count());
		if (material.isEmpty()) {
			container.setItem(SLOT_MATERIAL, ItemStack.EMPTY);
		}
		container.setItem(SLOT_GUN, ItemStack.EMPTY); // 原枪被重铸为成品
		container.setItem(SLOT_RESULT, ItemStack.EMPTY);
		container.setChanged();
		updateResult();
		broadcastChanges();
	}

	private class ResultSlot extends Slot {
		ResultSlot(Container inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return hasItem();
		}

		@Override
		public ItemStack remove(int amount) {
			ItemStack result = getItem().copy();
			if (!result.isEmpty()) {
				performUpgrade();
			}
			return result;
		}
	}

	private static class GunSlot extends Slot {
		GunSlot(Container inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return TaczCompat.isGun(stack);
		}
	}

	/** 只收升级材料：否则 shift 点枪时会把枪塞进材料槽 */
	private static class MaterialSlot extends Slot {
		MaterialSlot(Container inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return UpgradeCosts.isAnyMaterial(stack);
		}
	}
}
