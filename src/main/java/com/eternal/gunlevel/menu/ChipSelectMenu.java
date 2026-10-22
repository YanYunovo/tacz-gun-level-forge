package com.eternal.gunlevel.menu;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.effect.ChipTier;
import com.eternal.gunlevel.net.GunLevelNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 芯片自选包的界面，一个品级一个菜单类型。
 *
 * <p>为什么不是工作台配方：9 个破碎芯片的输入完全相同，挂 9 条同输入的配方在合成台上没法让玩家挑，
 * 拿到的永远是同一条。所以 9 破碎 → 1 自选包，选哪颗交给这个界面，点一下由服务端结算。
 *
 * <p>为什么按品级各注册一个类型：客户端只拿得到 {@code MenuType}，把品级编在类型上，
 * 界面开起来就知道该摆金芯片还是蓝芯片，不用为了传一个枚举去碰扩展数据包那套东西。
 *
 * <p>界面里带上玩家自己的背包槽不是为了让玩家整理背包，而是背包数量的同步走的就是这个容器的
 * {@code broadcastChanges}：不带槽位的话，扣掉一个自选包、多出一颗芯片这些变化客户端根本看不到。
 */
public class ChipSelectMenu extends AbstractContainerMenu implements MenuProvider {
	/** 选择区排版见 {@link #cellX(int)}：一格 20px、间隔 2px，最多两行 */
	public static final int CELL = 22;
	public static final int GRID_Y = 20;
	public static final int PANEL_WIDTH = 176;

	private static final Map<ChipTier, MenuType<ChipSelectMenu>> TYPES =
			new EnumMap<>(ChipTier.class);

	private final ChipTier tier;

	public ChipSelectMenu(int syncId, Inventory playerInventory, ChipTier tier) {
		super(TYPES.get(tier), syncId);
		this.tier = tier;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 143));
		}
	}

	public static MenuType<ChipSelectMenu> typeOf(ChipTier tier) {
		return TYPES.get(tier);
	}

	/** 注册由 GunLevelMod 在 RegisterEvent 里做，这里只是把结果按品级存起来给构造器用 */
	public static void registerType(ChipTier tier, MenuType<ChipSelectMenu> type) {
		TYPES.put(tier, type);
	}

	public List<Chip> choices() {
		return tier.chips();
	}

	public int columns() {
		return Math.max(1, Math.min(7, (choices().size() + 1) / 2));
	}

	public int cellX(int index) {
		return (PANEL_WIDTH - columns() * CELL + 2) / 2 + (index % columns()) * CELL;
	}

	public int cellY(int index) {
		return GRID_Y + (index / columns()) * CELL;
	}

	/**
	 * 服务端结算：只认「当前确实开着这个界面」的玩家，扣一个自选包、给一颗芯片。
	 * 芯片序号由客户端发来，所以这里必须自己再判一次范围，并且确认它属于这个包对应的品级。
	 */
	public static void choose(Player player, int ordinal) {
		Chip[] chips = Chip.values();
		if (ordinal < 0 || ordinal >= chips.length
				|| !(player.containerMenu instanceof ChipSelectMenu menu)) {
			return;
		}
		Chip chip = chips[ordinal];
		// 序号是客户端发的，不校验品级就能拿蓝自选包点出金芯片
		if (chip.tier() != menu.tier) {
			return;
		}
		Inventory inventory = player.getInventory();
		int slot = inventory.findSlotMatchingItem(
				new ItemStack(GunLevelMod.CHIP_PACK[menu.tier.ordinal()]));
		if (slot < 0) {
			return;
		}
		inventory.getItem(slot).shrink(1);
		ItemStack given = new ItemStack(GunLevelMod.CHIPS[chip.ordinal()]);
		if (!inventory.add(given)) {
			player.drop(given, false);
		}
		// 挑完就关：自选包一次只换一颗，留着界面只会让人以为还能再点
		player.closeContainer();
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
		return new ChipSelectMenu(syncId, playerInventory, tier);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.tacz_gun_level.chip_select_" + tier.id());
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
