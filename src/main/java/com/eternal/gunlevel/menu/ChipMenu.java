package com.eternal.gunlevel.menu;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.item.GunChipData;
import com.eternal.gunlevel.util.TaczCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

/**
 * 芯片装配台的两段操作：
 * 枪械 + 战术芯片 → 装上芯片的枪（消耗芯片）；枪械 → 少一颗芯片的枪，卸下的芯片落在中间的卸出槽。
 */
public class ChipMenu extends AbstractContainerMenu {
	public static final int SLOT_GUN = 0;
	public static final int SLOT_CHIP = 1;
	public static final int SLOT_RESULT = 2;
	public static final int SLOT_UNLOAD_GUN = 3;
	public static final int SLOT_TAKEN = 4;
	public static final int SLOT_UNLOADED = 5;
	private static final int CUSTOM_SLOTS = 6;
	/** 两组三槽：枪械/芯片/成品 —— 枪械/卸出/成品 */
	public static final int[] SLOT_X = {26, 48, 70, 104, 126, 148};
	public static final int SLOT_Y = 38;
	public static final int GROUP_INSTALL_CENTER = 57;
	public static final int GROUP_UNLOAD_CENTER = 135;

	private static final int INVENTORY_Y = 85;
	private static final int HOTBAR_Y = 143;

	public static MenuType<ChipMenu> TYPE;

	public final SimpleContainer container = new SimpleContainer(CUSTOM_SLOTS);
	private final Inventory playerInventory;
	private boolean syncing;

	public ChipMenu(int syncId, Inventory playerInventory) {
		super(TYPE, syncId);
		this.playerInventory = playerInventory;
		addSlot(new FilterSlot(container, SLOT_GUN, SLOT_X[0], SLOT_Y, TaczCompat::isGun));
		addSlot(new FilterSlot(container, SLOT_CHIP, SLOT_X[1], SLOT_Y, GunChipData::isChip));
		addSlot(new OutputSlot(container, SLOT_RESULT, SLOT_X[2], SLOT_Y, this::performInstall));
		addSlot(new FilterSlot(container, SLOT_UNLOAD_GUN, SLOT_X[3], SLOT_Y, TaczCompat::isGun));
		addSlot(new FilterSlot(container, SLOT_TAKEN, SLOT_X[4], SLOT_Y, stack -> false));
		addSlot(new OutputSlot(container, SLOT_UNLOADED, SLOT_X[5], SLOT_Y, this::performUnload));
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
			ItemStack result = ItemStack.EMPTY;
			ItemStack gun = container.getItem(SLOT_GUN);
			Chip chip = GunChipData.chipOfStack(container.getItem(SLOT_CHIP));
			if (!gun.isEmpty() && chip != null && GunChipData.canInstall(gun, chip)) {
				result = gun.copy();
				GunChipData.add(result, chip);
				GunLevelData.applyLore(result, GunLevelData.getLevel(result));
			}
			if (!ItemStack.isSameItemSameTags(result, container.getItem(SLOT_RESULT))) {
				container.setItem(SLOT_RESULT, result);
			}

			ItemStack unloaded = ItemStack.EMPTY;
			ItemStack unloadGun = container.getItem(SLOT_UNLOAD_GUN);
			if (!unloadGun.isEmpty() && !GunChipData.get(unloadGun).isEmpty()) {
				unloaded = unloadGun.copy();
				GunChipData.removeLast(unloaded);
				GunLevelData.applyLore(unloaded, GunLevelData.getLevel(unloaded));
			}
			if (!ItemStack.isSameItemSameTags(unloaded, container.getItem(SLOT_UNLOADED))) {
				container.setItem(SLOT_UNLOADED, unloaded);
			}
			broadcastChanges();
		} finally {
			syncing = false;
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotId) {
		if (slotId == SLOT_RESULT || slotId == SLOT_UNLOADED) {
			return shiftTakeOutput(slotId);
		}
		Slot slot = slots.get(slotId);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getItem().copy();
		ItemStack moving = slot.getItem();

		if (slotId < CUSTOM_SLOTS) {
			if (!moveItemStackTo(moving, CUSTOM_SLOTS, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			int target = transferTarget(moving);
			if (target < 0 || !moveItemStackTo(moving, target, target + 1, false)) {
				return ItemStack.EMPTY;
			}
		}

		if (moving.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		updateResult();
		return original;
	}

	/**
	 * shift 点成品。原版 {@code clickSlot} 的 QUICK_MOVE 分支会一直重复调 quickMove，
	 * 只要槽里还是同一件物品就继续，所以这里必须真的结算（吃掉芯片/原枪），否则就是无限白拿。
	 */
	private ItemStack shiftTakeOutput(int slotId) {
		ItemStack result = container.getItem(slotId);
		if (result.isEmpty() || !canSettle(slotId)) {
			return ItemStack.EMPTY;
		}
		if (!moveItemStackTo(result.copy(), CUSTOM_SLOTS, slots.size(), false)) {
			return ItemStack.EMPTY;
		}
		ItemStack moved = result.copy();
		if (slotId == SLOT_RESULT) {
			performInstall();
		} else {
			performUnload();
		}
		return moved;
	}

	/** 和 updateResult 的判定一致：确认这次 shift 真会吃掉输入，才不会变成无限白拿 */
	private boolean canSettle(int slotId) {
		if (slotId == SLOT_RESULT) {
			ItemStack gun = container.getItem(SLOT_GUN);
			Chip chip = GunChipData.chipOfStack(container.getItem(SLOT_CHIP));
			return !gun.isEmpty() && chip != null && GunChipData.canInstall(gun, chip);
		}
		ItemStack gun = container.getItem(SLOT_UNLOAD_GUN);
		return !gun.isEmpty() && !GunChipData.get(gun).isEmpty();
	}

	/** 从背包 shift 点击时该物品应落到哪个自定义槽：枪优先给安装组，安装组占了就给拆卸组 */
	private int transferTarget(ItemStack stack) {
		if (TaczCompat.isGun(stack)) {
			return container.getItem(SLOT_GUN).isEmpty() ? SLOT_GUN : SLOT_UNLOAD_GUN;
		}
		return GunChipData.isChip(stack) ? SLOT_CHIP : -1;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		// 两格成品是幽灵物品，丢回玩家背包前先清掉；卸出槽里是真芯片，跟着容器一起退还
		container.setItem(SLOT_RESULT, ItemStack.EMPTY);
		container.setItem(SLOT_UNLOADED, ItemStack.EMPTY);
		MenuUtil.returnContents(player, container);
	}

	/** 安装：消耗 1 颗芯片，原枪被改造后的成品替换 */
	private void performInstall() {
		ItemStack chip = container.getItem(SLOT_CHIP);
		chip.shrink(1);
		container.setItem(SLOT_CHIP, chip.isEmpty() ? ItemStack.EMPTY : chip);
		container.setItem(SLOT_GUN, ItemStack.EMPTY);
		container.setItem(SLOT_RESULT, ItemStack.EMPTY);
		container.setChanged();
		updateResult();
	}

	/** 拆卸：原枪被少一颗芯片的成品替换，卸下的芯片进卸出槽 */
	private void performUnload() {
		Chip removed = GunChipData.removeLast(container.getItem(SLOT_UNLOAD_GUN));
		container.setItem(SLOT_UNLOAD_GUN, ItemStack.EMPTY);
		container.setItem(SLOT_UNLOADED, ItemStack.EMPTY);
		if (removed != null) {
			storeTaken(GunChipData.newChip(removed));
		}
		container.setChanged();
		updateResult();
	}

	/** 卸出的芯片先攒在卸出槽；槽里是别的芯片就直接塞回玩家背包，别把它变没 */
	private void storeTaken(ItemStack chip) {
		ItemStack taken = container.getItem(SLOT_TAKEN);
		if (taken.isEmpty()) {
			container.setItem(SLOT_TAKEN, chip);
			return;
		}
		if (ItemStack.isSameItemSameTags(taken, chip)) {
			taken.grow(1);
			container.setItem(SLOT_TAKEN, taken);
			return;
		}
		Player player = playerInventory.player;
		if (!player.getInventory().add(chip)) {
			player.drop(chip, false);
		}
	}

	private static class FilterSlot extends Slot {
		private final java.util.function.Predicate<ItemStack> accepted;

		FilterSlot(Container inventory, int index, int x, int y,
				java.util.function.Predicate<ItemStack> accepted) {
			super(inventory, index, x, y);
			this.accepted = accepted;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return accepted.test(stack);
		}
	}

	private static class OutputSlot extends Slot {
		private final Runnable onTake;

		OutputSlot(Container inventory, int index, int x, int y, Runnable onTake) {
			super(inventory, index, x, y);
			this.onTake = onTake;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public ItemStack remove(int amount) {
			if (!hasItem()) {
				return ItemStack.EMPTY;
			}
			ItemStack result = getItem().copy(); // 结算会清空本槽，先留一份
			onTake.run();
			return result;
		}
	}
}
