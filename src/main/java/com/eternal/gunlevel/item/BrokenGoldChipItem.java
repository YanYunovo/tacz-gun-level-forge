package com.eternal.gunlevel.item;

import net.minecraft.world.item.Item;

/**
 * 破碎的金芯片：拆金芯片的产物，9 个在工作台里可以自选合成一颗金芯片。
 * 本身没有行为，配方见 data 里的 chip_from_broken_*。
 */
public class BrokenGoldChipItem extends Item {
	public BrokenGoldChipItem() {
		super(new Item.Properties().stacksTo(64));
	}
}
