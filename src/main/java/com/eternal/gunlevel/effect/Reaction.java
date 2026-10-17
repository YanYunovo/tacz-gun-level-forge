package com.eternal.gunlevel.effect;

/**
 * 两种不同元素同时附着时触发的反应。所有反应都会消耗附着（走 consumePortion）。
 */
public enum Reaction {
	OVERLOAD,
	SHOCK,
	SUPERCONDUCT,
	VAPORIZE,
	MELT,
	FREEZE,
	BLOOM,
	BURN,
	QUICKEN,
	SWIRL,
	CRYSTALLIZE;

	public String displayName() {
		return switch (this) {
			case OVERLOAD -> "超载";
			case SHOCK -> "感电";
			case SUPERCONDUCT -> "超导";
			case VAPORIZE -> "蒸发";
			case MELT -> "融化";
			case FREEZE -> "冻结";
			case BLOOM -> "绽放";
			case BURN -> "燃烧";
			case QUICKEN -> "激化";
			case SWIRL -> "扩散";
			case CRYSTALLIZE -> "结晶";
		};
	}

	/** 无序组合查表；同一元素或空元素返回 null（不反应） */
	public static Reaction of(Element a, Element b) {
		if (a == null || b == null || a == Element.NONE || b == Element.NONE || a == b) {
			return null;
		}
		// 风碰任何已附着的元素都是扩散，不参与其它配对
		if (a == Element.WIND || b == Element.WIND) {
			return SWIRL;
		}
		// 岩同理：碰任何已附着的元素都是结晶，本身不附着、不参与其它配对
		if (a == Element.ROCK || b == Element.ROCK) {
			return CRYSTALLIZE;
		}
		Element low = a.ordinal() <= b.ordinal() ? a : b;
		Element high = a.ordinal() <= b.ordinal() ? b : a;
		return switch (low) {
			case THUNDER -> switch (high) {
				case FIRE -> OVERLOAD;
				case WATER -> SHOCK;
				case ICE -> SUPERCONDUCT;
				case GRASS -> QUICKEN;
				default -> null;
			};
			case FIRE -> switch (high) {
				case WATER -> VAPORIZE;
				case ICE -> MELT;
				case GRASS -> BURN;
				default -> null;
			};
			case WATER -> switch (high) {
				case ICE -> FREEZE;
				case GRASS -> BLOOM;
				default -> null;
			};
			default -> null;
		};
	}
}
