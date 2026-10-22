package com.eternal.gunlevel.item;

import net.minecraft.world.item.Item;

/**
 * 破碎的X芯片：拆本品级芯片的产物，9 个合成一个对应的自选包。
 * 本身没有行为，哪个品级由注册名和配方决定，见 data 里的 chip_into_broken_*。
 */
public class BrokenChipItem extends Item {
	public BrokenChipItem() {
		super(new Item.Properties().stacksTo(64));
	}
}
