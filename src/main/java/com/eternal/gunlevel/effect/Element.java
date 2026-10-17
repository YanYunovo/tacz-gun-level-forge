package com.eternal.gunlevel.effect;

/** 元素核芯提供的七种元素；NONE 表示该枪没有元素。 */
public enum Element {
	THUNDER, FIRE, WATER, ICE, GRASS, WIND, ROCK, NONE;

	public String displayName() {
		return switch (this) {
			case THUNDER -> "雷";
			case FIRE -> "火";
			case WATER -> "水";
			case ICE -> "冰";
			case GRASS -> "草";
			case WIND -> "风";
			case ROCK -> "岩";
			case NONE -> "无";
		};
	}

	/**
	 * 能否挂在目标身上。风和岩是纯后手元素：目标身上干净时命中不留任何状态，
	 * 只有已有附着时才反应（风→扩散，岩→结晶）。
	 */
	public boolean canAttach() {
		return this != NONE && this != WIND && this != ROCK;
	}
}
