package com.eternal.gunlevel.menu;

import com.eternal.gunlevel.GunLevelData;
import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Element;
import com.eternal.gunlevel.item.GunCoreData;
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
 * 元素合成台的两段操作：
 * 素材 + 空核芯 → 元素核芯；枪械 + 元素核芯 → 带元素的枪（安装即消耗，可覆盖旧核芯）。
 */
public class SynthesisMenu extends AbstractContainerMenu {
	public static final int SLOT_MATERIAL = 0;
	public static final int SLOT_CARRIER = 1;
	public static final int SLOT_CORE_RESULT = 2;
	public static final int SLOT_GUN = 3;
	public static final int SLOT_CORE = 4;
	public static final int SLOT_GUN_RESULT = 5;
	private static final int CUSTOM_SLOTS = 6;
	/** 两组三槽：素材/空核芯/核芯 —— 枪械/核芯/成品 */
	public static final int[] SLOT_X = {26, 48, 70, 104, 126, 148};
	public static final int SLOT_Y = 38;
	public static final int GROUP_CORE_CENTER = 57;
	public static final int GROUP_INSTALL_CENTER = 135;

	public static MenuType<SynthesisMenu> TYPE;

	/** 原版三行箱子的背包坐标：下半段底图从 y+71 起，槽框顶线在贴图源 v=139，格因此落在 85 */
	private static final int INVENTORY_Y = 85;
	private static final int HOTBAR_Y = 143;

	public final SimpleContainer container = new SimpleContainer(CUSTOM_SLOTS);
	private boolean syncing;

	public SynthesisMenu(int syncId, Inventory playerInventory) {
		super(TYPE, syncId);
		addSlot(new InsertSlot(container, SLOT_MATERIAL, SLOT_X[0], SLOT_Y, CoreCosts::isMaterial));
		addSlot(new InsertSlot(container, SLOT_CARRIER, SLOT_X[1], SLOT_Y, CoreCosts::isCarrier));
		addSlot(new OutputSlot(container, SLOT_CORE_RESULT, SLOT_X[2], SLOT_Y, this::performCraft));
		addSlot(new InsertSlot(container, SLOT_GUN, SLOT_X[3], SLOT_Y, TaczCompat::isGun));
		addSlot(new InsertSlot(container, SLOT_CORE, SLOT_X[4], SLOT_Y, GunCoreData::isCore));
		addSlot(new OutputSlot(container, SLOT_GUN_RESULT, SLOT_X[5], SLOT_Y, this::performInstall));
		addPlayerInventory(playerInventory);
		container.addListener(inventory -> updateResult());
	}


	private void addPlayerInventory(Inventory playerInventory) {
		// 背包坐标照抄原版三行箱子：下半段底图从 y+71 起，槽框顶线落在 84，格子本身在 85
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 143));
		}
	}

	public void updateResult() {
		if (syncing) {
			return; // setStack 会再触发监听器，避免递归
		}
		syncing = true;
		try {
			Element element = CoreCosts.elementOf(container.getItem(SLOT_MATERIAL));
			ItemStack core = element != Element.NONE
					&& CoreCosts.isCarrier(container.getItem(SLOT_CARRIER))
					? GunCoreData.newCore(element) : ItemStack.EMPTY;
			if (!ItemStack.isSameItemSameTags(core, container.getItem(SLOT_CORE_RESULT))) {
				container.setItem(SLOT_CORE_RESULT, core);
			}

			ItemStack gun = container.getItem(SLOT_GUN);
			Element installed = GunCoreData.elementOfStack(container.getItem(SLOT_CORE));
			ItemStack gunResult = ItemStack.EMPTY;
			if (!gun.isEmpty() && installed != Element.NONE) {
				gunResult = gun.copy();
				GunCoreData.set(gunResult, installed);
				GunLevelData.applyLore(gunResult, GunLevelData.getLevel(gunResult));
			}
			if (!ItemStack.isSameItemSameTags(gunResult, container.getItem(SLOT_GUN_RESULT))) {
				container.setItem(SLOT_GUN_RESULT, gunResult);
			}
			broadcastChanges();
		} finally {
			syncing = false;
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotId) {
		if (slotId == SLOT_CORE_RESULT || slotId == SLOT_GUN_RESULT) {
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
	 * 只要槽里还是同一件物品就继续，所以这里必须真的结算（吃掉素材/空核芯/核芯），
	 * 否则就是无限白拿。成品恒为 1 件：insertItem 要么整份塞进去要么完全没动。
	 * 合成核芯那格结算后若还有素材会再生成一格同样的核芯，于是连点连造，素材用完自然停下。
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
		if (slotId == SLOT_CORE_RESULT) {
			performCraft();
		} else {
			performInstall();
		}
		return moved;
	}

	/** 和 updateResult 的判定一致：确认这次 shift 真会吃掉输入，才不会变成无限白拿 */
	private boolean canSettle(int slotId) {
		return slotId == SLOT_CORE_RESULT
				? CoreCosts.elementOf(container.getItem(SLOT_MATERIAL)) != Element.NONE
						&& CoreCosts.isCarrier(container.getItem(SLOT_CARRIER))
				: !container.getItem(SLOT_GUN).isEmpty()
						&& GunCoreData.elementOfStack(container.getItem(SLOT_CORE)) != Element.NONE;
	}

	/** 从背包 shift 点击时该物品应落到哪个自定义槽 */
	private static int transferTarget(ItemStack stack) {
		if (TaczCompat.isGun(stack)) {
			return SLOT_GUN;
		}
		if (GunCoreData.isCore(stack)) {
			return SLOT_CORE;
		}
		if (CoreCosts.isMaterial(stack)) {
			return SLOT_MATERIAL;
		}
		if (CoreCosts.isCarrier(stack)) {
			return SLOT_CARRIER;
		}
		return -1;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		// 成品是幽灵物品，丢回玩家背包前先清掉
		container.setItem(SLOT_CORE_RESULT, ItemStack.EMPTY);
		container.setItem(SLOT_GUN_RESULT, ItemStack.EMPTY);
		MenuUtil.returnContents(player, container);
	}

	/** 合成一个核芯：消耗 1 素材 + 1 空核芯 */
	private void performCraft() {
		consume(SLOT_MATERIAL);
		consume(SLOT_CARRIER);
		container.setItem(SLOT_CORE_RESULT, ItemStack.EMPTY);
		container.setChanged();
		updateResult();
	}

	/** 安装核芯：消耗 1 核芯，原枪被改造后的成品替换 */
	private void performInstall() {
		consume(SLOT_CORE);
		container.setItem(SLOT_GUN, ItemStack.EMPTY);
		container.setItem(SLOT_GUN_RESULT, ItemStack.EMPTY);
		container.setChanged();
		updateResult();
	}

	private void consume(int slot) {
		ItemStack stack = container.getItem(slot);
		stack.shrink(1);
		if (stack.isEmpty()) {
			container.setItem(slot, ItemStack.EMPTY);
		}
	}

	private static class InsertSlot extends Slot {
		private final java.util.function.Predicate<ItemStack> accepted;

		InsertSlot(Container inventory, int index, int x, int y,
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
