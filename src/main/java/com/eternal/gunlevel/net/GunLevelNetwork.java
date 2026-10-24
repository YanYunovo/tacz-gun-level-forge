package com.eternal.gunlevel.net;

import com.eternal.gunlevel.GunLevelMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 芯片相关的三个自建包。Forge 没有 Fabric「随手写一个 buf」的口子，每种消息都得先注册编解码器，
 * 所以集中在这一处，别处只调 send* 方法。
 *
 * <p>客户端的收包处理走 {@link #onChipHint} / {@link #onWindGuard} 两个静态钩子：
 * 由客户端入口在启动时赋值。通用侧代码不直接引用客户端类，专用服务端才不会因类加载炸掉。
 */
public final class GunLevelNetwork {
	private static final String PROTOCOL = "1";
	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(GunLevelMod.MOD_ID, "chip"),
			() -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	/** 客户端赋值；没赋值时收到包就忽略（专用服务端不会走到这里） */
	public static Consumer<ChipHint> onChipHint;
	public static Consumer<Boolean> onWindGuard;
	public static Consumer<Double> onThunderDash;
	/** S2C：烈焰咆哮的移速惩罚要在客户端也挂一份（原版不同步玩家属性） */
	public static Consumer<Boolean> onRoarSpeed;
	/** S2C：狩猎时刻那档移速加成的两端同步 */
	public static Consumer<Boolean> onHuntSpeed;
	/** S2C：灵动步伐那对修正（移速 +20% / 护甲 −4）的两端同步 */
	public static Consumer<Boolean> onNimble;
	/** S2C：硬化护甲那档护甲 +4 的两端同步 */
	public static Consumer<Boolean> onHardened;

	/** S2C：经验条上方那行小字（哪颗芯片 + 哪种文案） */
	public record ChipHint(int chipOrdinal, int kind) {
	}

	/** C2S：玩家按了芯片主动技能键，能不能放由服务端裁决 */
	public record ChipSkillRequest() {
	}

	/** C2S：金芯片自选包里点了第几颗（{@code Chip#ordinal()}），给哪颗由服务端裁决 */
	public record ChipSelectRequest(int chipOrdinal) {
		static void encode(ChipSelectRequest msg, FriendlyByteBuf buf) {
			buf.writeVarInt(msg.chipOrdinal());
		}

		static ChipSelectRequest decode(FriendlyByteBuf buf) {
			return new ChipSelectRequest(buf.readVarInt());
		}
	}

	/** S2C：服务端批了迅捷闪冲，客户端自己朝面朝方向冲这几格（玩家移动是客户端预测的） */
	public record ThunderDash(double blocks) {
		static void encode(ThunderDash msg, FriendlyByteBuf buf) {
			buf.writeDouble(msg.blocks());
		}

		static ThunderDash decode(FriendlyByteBuf buf) {
			return new ThunderDash(buf.readDouble());
		}
	}

	/** S2C：风的护佑的移速修正要在客户端也挂一份（原版不同步玩家属性） */
	public record WindGuardSync(boolean on) {
	}

	/** S2C：烈焰咆哮的移速惩罚，两端各挂同一个 UUID 的修正 */
	public record RoarSpeedSync(boolean on) {
	}

	/** S2C：狩猎时刻 15 层那档移速加成，同样是两端各挂一次 */
	public record HuntSpeedSync(boolean on) {
	}

	/** S2C：灵动步伐的移速与护甲修正，一个布尔管一对（两者必须同时给、同时收） */
	public record NimbleSync(boolean on) {
	}

	/** S2C：硬化护甲的护甲修正 */
	public record HardenedSync(boolean on) {
	}

	private GunLevelNetwork() {
	}

	/** 在模组构造阶段调用一次 */
	public static void init() {
		CHANNEL.registerMessage(0, ChipHint.class,
				(msg, buf) -> {
					buf.writeVarInt(msg.chipOrdinal());
					buf.writeVarInt(msg.kind());
				},
				buf -> new ChipHint(buf.readVarInt(), buf.readVarInt()),
				(msg, ctx) -> {
					ctx.get().enqueueWork(() -> {
						if (onChipHint != null) {
							onChipHint.accept(msg);
						}
					});
					ctx.get().setPacketHandled(true);
				}, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		CHANNEL.registerMessage(1, ChipSkillRequest.class,
				(msg, buf) -> {
				},
				buf -> new ChipSkillRequest(),
				(msg, ctx) -> {
					ServerPlayer player = ctx.get().getSender();
					if (player != null) {
						// play 包的 handler 已经在服务端线程上
						com.eternal.gunlevel.effect.WindGuardState.requestSkill(player);
					}
					ctx.get().setPacketHandled(true);
				}, Optional.of(NetworkDirection.PLAY_TO_SERVER));
		CHANNEL.registerMessage(3, ChipSelectRequest.class,
				(msg, buf) -> buf.writeVarInt(msg.chipOrdinal()),
				buf -> new ChipSelectRequest(buf.readVarInt()),
				(msg, ctx) -> {
					ServerPlayer player = ctx.get().getSender();
					if (player != null) {
						com.eternal.gunlevel.menu.ChipSelectMenu.choose(player, msg.chipOrdinal());
					}
					ctx.get().setPacketHandled(true);
				}, Optional.of(NetworkDirection.PLAY_TO_SERVER));
		CHANNEL.registerMessage(4, ThunderDash.class,
				(msg, buf) -> buf.writeDouble(msg.blocks()),
				buf -> new ThunderDash(buf.readDouble()),
				(msg, ctx) -> {
					ctx.get().enqueueWork(() -> {
						if (onThunderDash != null) {
							onThunderDash.accept(msg.blocks());
						}
					});
					ctx.get().setPacketHandled(true);
				}, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		CHANNEL.registerMessage(2, WindGuardSync.class,
				(msg, buf) -> buf.writeBoolean(msg.on()),
				buf -> new WindGuardSync(buf.readBoolean()),
				(msg, ctx) -> {
					ctx.get().enqueueWork(() -> {
						if (onWindGuard != null) {
							onWindGuard.accept(msg.on());
						}
					});
					ctx.get().setPacketHandled(true);
				}, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		CHANNEL.registerMessage(5, RoarSpeedSync.class,
				(msg, buf) -> buf.writeBoolean(msg.on()),
				buf -> new RoarSpeedSync(buf.readBoolean()),
				(msg, ctx) -> {
					ctx.get().enqueueWork(() -> {
						if (onRoarSpeed != null) {
							onRoarSpeed.accept(msg.on());
						}
					});
					ctx.get().setPacketHandled(true);
				}, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		CHANNEL.registerMessage(6, HuntSpeedSync.class,
				(msg, buf) -> buf.writeBoolean(msg.on()),
				buf -> new HuntSpeedSync(buf.readBoolean()),
				(msg, ctx) -> {
					ctx.get().enqueueWork(() -> {
						if (onHuntSpeed != null) {
							onHuntSpeed.accept(msg.on());
						}
					});
					ctx.get().setPacketHandled(true);
				}, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		CHANNEL.registerMessage(7, NimbleSync.class,
				(msg, buf) -> buf.writeBoolean(msg.on()),
				buf -> new NimbleSync(buf.readBoolean()),
				(msg, ctx) -> {
					ctx.get().enqueueWork(() -> {
						if (onNimble != null) {
							onNimble.accept(msg.on());
						}
					});
					ctx.get().setPacketHandled(true);
				}, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		CHANNEL.registerMessage(8, HardenedSync.class,
				(msg, buf) -> buf.writeBoolean(msg.on()),
				buf -> new HardenedSync(buf.readBoolean()),
				(msg, ctx) -> {
					ctx.get().enqueueWork(() -> {
						if (onHardened != null) {
							onHardened.accept(msg.on());
						}
					});
					ctx.get().setPacketHandled(true);
				}, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
	}

	public static void sendHint(ServerPlayer player, int chipOrdinal, int kind) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChipHint(chipOrdinal, kind));
	}

	public static void sendChipSelect(int chipOrdinal) {
		CHANNEL.sendToServer(new ChipSelectRequest(chipOrdinal));
	}

	public static void sendSkillRequest() {
		CHANNEL.sendToServer(new ChipSkillRequest());
	}

	public static void sendThunderDash(ServerPlayer player, double blocks) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ThunderDash(blocks));
	}

	public static void sendGuard(ServerPlayer player, boolean on) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WindGuardSync(on));
	}

	/** 烈焰咆哮：把移速惩罚同步给客户端那份属性 */
	public static void sendRoarSpeed(ServerPlayer player, boolean on) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RoarSpeedSync(on));
	}

	public static void sendHuntSpeed(ServerPlayer player, boolean on) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HuntSpeedSync(on));
	}

	public static void sendNimble(ServerPlayer player, boolean on) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NimbleSync(on));
	}

	public static void sendHardened(ServerPlayer player, boolean on) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HardenedSync(on));
	}
}
