package com.eternal.gunlevel.client;

import com.eternal.gunlevel.GunLevelMod;
import com.eternal.gunlevel.effect.WindGuardState;
import com.eternal.gunlevel.menu.ChipMenu;
import com.eternal.gunlevel.menu.ChipSelectMenu;
import com.eternal.gunlevel.menu.SynthesisMenu;
import com.eternal.gunlevel.menu.UpgradeMenu;
import com.eternal.gunlevel.net.GunLevelNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端接线：只在这里引用客户端类，通用侧（{@link GunLevelMod}）只按 {@link Dist} 决定调不调，
 * 专用服务端不会加载到任何客户端类。
 */
public final class GunLevelClient {
	private final ChipHud chipHud = new ChipHud();
	private final ChipSkillKeys chipSkillKeys = new ChipSkillKeys();

	private GunLevelClient() {
	}

	public static void init(IEventBus modBus, IEventBus forgeBus) {
		GunLevelClient client = new GunLevelClient();
		modBus.addListener(client.chipSkillKeys::registerKeys);
		modBus.addListener(client::registerOverlays);
		modBus.addListener(client::registerRenderers);
		modBus.addListener(client::registerScreens);
		forgeBus.addListener(client.chipSkillKeys::onClientTick);
		forgeBus.addListener(client::addSettingsButton);
		// 收包处理：两个钩子都是客户端主线程上跑（SimpleChannel 已经 enqueueWork 过了）
		GunLevelNetwork.onChipHint = hint -> client.chipHud.show(hint.chipOrdinal(), hint.kind());
		GunLevelNetwork.onWindGuard = on -> WindGuardState.applySpeedClient(
				Minecraft.getInstance().player, on);
		GunLevelNetwork.onThunderDash = blocks -> ThunderDash.dash(Minecraft.getInstance().player, blocks);
	}

	private void registerOverlays(RegisterGuiOverlaysEvent event) {
		// 画在所有原版 overlay 之后：经验条那一层已经画完了，我们只在它上方那一块叠字
		event.registerAboveAll("chip_hint", chipHud::render);
	}

	private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(GunLevelMod.ROCK_CONSTRUCT, RockConstructRenderer::new);
	}

	private void registerScreens(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			MenuScreens.register(UpgradeMenu.TYPE, UpgradeMenuScreen::new);
			MenuScreens.register(SynthesisMenu.TYPE, SynthesisMenuScreen::new);
			MenuScreens.register(ChipMenu.TYPE, ChipMenuScreen::new);
			MenuScreens.register(ChipSelectMenu.TYPE, ChipSelectScreen::new);
		});
	}

	/**
	 * 原版「选项」界面左上角挂一个以模组命名的入口（没装 CurseForge 那种模组设置页，只能自己加）。
	 * 走 Forge 自己的 ScreenEvent：它内部用的就是原版那条 addRenderableWidget 的路子，
	 * 不会像直接改 children 列表那样把标签页的下标算歪。
	 */
	private void addSettingsButton(ScreenEvent.Init.Post event) {
		Screen screen = event.getScreen();
		if (!(screen instanceof OptionsScreen) && !(screen instanceof OptionsSubScreen)) {
			return;
		}
		Component label = Component.translatable("gui.tacz_gun_level.settings_category");
		int width = Minecraft.getInstance().font.width(label) + 8;
		event.addListener(Button.builder(label,
				button -> Minecraft.getInstance().setScreen(new GunLevelOptionsScreen(screen)))
				.bounds(4, 4, width, 20).build());
	}
}
