package com.eternal.gunlevel;

import com.eternal.gunlevel.block.ChipAssemblyTableBlock;
import com.eternal.gunlevel.block.ElementSynthesisTableBlock;
import com.eternal.gunlevel.block.UpgradeTableBlock;
import com.eternal.gunlevel.effect.AuroraEffect;
import com.eternal.gunlevel.effect.AuroraState;
import com.eternal.gunlevel.effect.BloomDomain;
import com.eternal.gunlevel.effect.Chip;
import com.eternal.gunlevel.effect.ChipState;
import com.eternal.gunlevel.effect.ChipTier;
import com.eternal.gunlevel.effect.CombatState;
import com.eternal.gunlevel.effect.CrystallizationEffect;
import com.eternal.gunlevel.effect.Element;
import com.eternal.gunlevel.effect.FlameRoarState;
import com.eternal.gunlevel.effect.HuntState;
import com.eternal.gunlevel.effect.FervorState;
import com.eternal.gunlevel.effect.LevelScaling;
import com.eternal.gunlevel.effect.MomentumState;
import com.eternal.gunlevel.effect.QuickeningEffect;
import com.eternal.gunlevel.effect.ThunderVoiceState;
import com.eternal.gunlevel.effect.WindGuardState;
import com.eternal.gunlevel.entity.RockConstructEntity;
import com.eternal.gunlevel.item.BrokenChipItem;
import com.eternal.gunlevel.item.ChipPackItem;
import com.eternal.gunlevel.item.ElementCoreItem;
import com.eternal.gunlevel.item.EmptyCoreItem;
import com.eternal.gunlevel.item.RandomChipItem;
import com.eternal.gunlevel.item.TacticalChipItem;
import com.eternal.gunlevel.item.UpgradeComponentItem;
import com.eternal.gunlevel.menu.ChipMenu;
import com.eternal.gunlevel.menu.ChipSelectMenu;
import com.eternal.gunlevel.menu.SynthesisMenu;
import com.eternal.gunlevel.menu.UpgradeMenu;
import com.eternal.gunlevel.net.GunLevelNetwork;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod(GunLevelMod.MOD_ID)
public class GunLevelMod {
	public static final String MOD_ID = "tacz_gun_level";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static LevelConfig CONFIG = LevelConfig.defaults();

	private static final BlockBehaviour.Properties TABLE = BlockBehaviour.Properties.of()
			.strength(3.0F).sound(SoundType.WOOD).lightLevel(state -> 7);

	/*
	 * 下面这些对象全部在 RegisterEvent 的 supplier 里现场 new：1.20.1 的 Block / Item / EntityType /
	 * MenuType 构造时会往所属注册表塞一个 intrusive holder，而注册表只在 RegisterEvent 派发期间是解锁的。
	 * 写成静态字段的初始化表达式就等于在模组构造阶段 new，直接「Registry is already frozen」。
	 */
	public static Block UPGRADE_TABLE;
	public static Item UPGRADE_TABLE_ITEM;
	public static Block ELEMENT_TABLE;
	public static Item ELEMENT_TABLE_ITEM;
	public static Block CHIP_TABLE;
	public static Item CHIP_TABLE_ITEM;

	public static Item CORE_THUNDER;
	public static Item CORE_FIRE;
	public static Item CORE_WATER;
	public static Item CORE_ICE;
	public static Item CORE_GRASS;
	public static Item CORE_WIND;
	public static Item CORE_ROCK;
	public static Item EMPTY_CORE;

	/**
	 * 每个品级一套「随机芯片 / 破碎的芯片 / 芯片自选包」，下标用 {@link ChipTier#ordinal()}。
	 * 三件的配方与奖池都按品级分叉，以后加紫芯片只在这个枚举里加一项。
	 * 物品本身在 RegisterEvent 的 supplier 里现场 new，这里只是留位子。
	 */
	public static final Item[] RANDOM_CHIP = new Item[ChipTier.values().length];
	public static final Item[] BROKEN_CHIP = new Item[ChipTier.values().length];
	public static final Item[] CHIP_PACK = new Item[ChipTier.values().length];

	/** 永恒改装的升级材料：I~IV 分别用于升到 LV.2~LV.5 */
	public static Item COMPONENT_I;
	public static Item COMPONENT_II;
	public static Item COMPONENT_III;
	public static Item COMPONENT_IV;

	/**
	 * 战术芯片：在芯片装配台装到枪上，LV3 一颗、LV5 两颗，共用一张贴图。
	 * 下标就是 {@link Chip#ordinal()}，注册名是 {@code Chip.itemId()}，
	 * 所以新增芯片只改枚举 + 效果分支，注册和创造标签页自动跟着走。
	 */
	public static Item[] CHIPS;

	/** 激化（雷+草）buff：层数在读取射击间隔时折算成射速 */
	public static MobEffect QUICKENING;
	/** 结晶（岩+任意附着）buff：按层数给金心，见 CrystallizationEffect */
	public static MobEffect CRYSTALLIZATION;
	/** 极光弹匣（战术芯片·极光追击）buff：只给客户端预测射速用的影子，真理在枪 NBT 的极光匣标记上 */
	public static MobEffect AURORA;

	/**
	 * 岩元素造物（战术芯片·凝晶召唤）：泥土皮肤的僵尸，用原版的僵尸模型与骨架，
	 * 所以尺寸照抄僵尸，火焰免疫 + 不自然消失（见 RockConstructEntity）。
	 */
	public static EntityType<RockConstructEntity> ROCK_CONSTRUCT;

	public static MenuType<UpgradeMenu> UPGRADE_MENU_TYPE;
	public static MenuType<SynthesisMenu> SYNTHESIS_MENU_TYPE;
	public static MenuType<ChipMenu> CHIP_MENU_TYPE;

	/** 本模组的创造分类：三台机器 + 四个升级组件 + 空核芯 + 七个元素核芯 + 战术芯片，图标用火元素核芯 */
	public static CreativeModeTab ELEMENT_UPGRADE_GROUP;

	public GunLevelMod() {
		IEventBus modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
		modBus.addListener(this::onRegister);
		modBus.addListener(this::onAttributes);
		modBus.addListener(this::onCommonSetup);
		MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
		MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
		MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
		com.eternal.gunlevel.effect.LivingGuards.init(MinecraftForge.EVENT_BUS);
		if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
			com.eternal.gunlevel.client.GunLevelClient.init(modBus, MinecraftForge.EVENT_BUS);
		}
		loadConfig();
	}

	private void onCommonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
		// 三种自建包的编解码器在这里注册；客户端收包处理由客户端入口赋值到静态钩子
		event.enqueueWork(GunLevelNetwork::init);
		// 等级加成全靠 mixin 打进 TACZ 的读取点，前置没在就是「装了但什么都没发生」，先把话撂日志里
		LOGGER.info("前置 TACZ：{}", com.eternal.gunlevel.util.TaczCompat.isTaczLoaded() ? "已加载" : "未加载");
	}

	private void onRegister(RegisterEvent event) {
		if (ForgeRegistries.Keys.BLOCKS.equals(event.getRegistryKey())) {
			event.register(ForgeRegistries.Keys.BLOCKS, id("gun_upgrade_table"),
					() -> UPGRADE_TABLE = new UpgradeTableBlock(TABLE));
			event.register(ForgeRegistries.Keys.BLOCKS, id("element_synthesis_table"),
					() -> ELEMENT_TABLE = new ElementSynthesisTableBlock(TABLE));
			event.register(ForgeRegistries.Keys.BLOCKS, id("chip_assembly_table"),
					() -> CHIP_TABLE = new ChipAssemblyTableBlock(TABLE));
		} else if (ForgeRegistries.Keys.ITEMS.equals(event.getRegistryKey())) {
			CHIPS = new Item[Chip.values().length];
			event.register(ForgeRegistries.Keys.ITEMS, id("gun_upgrade_table"),
					() -> UPGRADE_TABLE_ITEM = new BlockItem(UPGRADE_TABLE, new Item.Properties()));
			event.register(ForgeRegistries.Keys.ITEMS, id("element_synthesis_table"),
					() -> ELEMENT_TABLE_ITEM = new BlockItem(ELEMENT_TABLE, new Item.Properties()));
			event.register(ForgeRegistries.Keys.ITEMS, id("chip_assembly_table"),
					() -> CHIP_TABLE_ITEM = new BlockItem(CHIP_TABLE, new Item.Properties()));
			event.register(ForgeRegistries.Keys.ITEMS, id("element_core_thunder"),
					() -> CORE_THUNDER = new ElementCoreItem(Element.THUNDER));
			event.register(ForgeRegistries.Keys.ITEMS, id("element_core_fire"),
					() -> CORE_FIRE = new ElementCoreItem(Element.FIRE));
			event.register(ForgeRegistries.Keys.ITEMS, id("element_core_water"),
					() -> CORE_WATER = new ElementCoreItem(Element.WATER));
			event.register(ForgeRegistries.Keys.ITEMS, id("element_core_ice"),
					() -> CORE_ICE = new ElementCoreItem(Element.ICE));
			event.register(ForgeRegistries.Keys.ITEMS, id("element_core_grass"),
					() -> CORE_GRASS = new ElementCoreItem(Element.GRASS));
			event.register(ForgeRegistries.Keys.ITEMS, id("element_core_wind"),
					() -> CORE_WIND = new ElementCoreItem(Element.WIND));
			event.register(ForgeRegistries.Keys.ITEMS, id("element_core_rock"),
					() -> CORE_ROCK = new ElementCoreItem(Element.ROCK));
			event.register(ForgeRegistries.Keys.ITEMS, id("empty_core"),
					() -> EMPTY_CORE = new EmptyCoreItem());
			for (ChipTier tier : ChipTier.values()) {
				event.register(ForgeRegistries.Keys.ITEMS, id(tier.randomChipId()), () -> {
					Item item = new RandomChipItem(tier);
					RANDOM_CHIP[tier.ordinal()] = item;
					return item;
				});
				event.register(ForgeRegistries.Keys.ITEMS, id(tier.brokenChipId()), () -> {
					Item item = new BrokenChipItem();
					BROKEN_CHIP[tier.ordinal()] = item;
					return item;
				});
				event.register(ForgeRegistries.Keys.ITEMS, id(tier.chipPackId()), () -> {
					Item item = new ChipPackItem(tier);
					CHIP_PACK[tier.ordinal()] = item;
					return item;
				});
			}
			event.register(ForgeRegistries.Keys.ITEMS, id("upgrade_component_i"),
					() -> COMPONENT_I = new UpgradeComponentItem(2));
			event.register(ForgeRegistries.Keys.ITEMS, id("upgrade_component_ii"),
					() -> COMPONENT_II = new UpgradeComponentItem(3));
			event.register(ForgeRegistries.Keys.ITEMS, id("upgrade_component_iii"),
					() -> COMPONENT_III = new UpgradeComponentItem(4));
			event.register(ForgeRegistries.Keys.ITEMS, id("upgrade_component_iv"),
					() -> COMPONENT_IV = new UpgradeComponentItem(5));
			for (Chip chip : Chip.values()) {
				event.register(ForgeRegistries.Keys.ITEMS, id(chip.itemId()), () -> {
						Item item = new TacticalChipItem(chip);
						CHIPS[chip.ordinal()] = item;
						return item;
					});
			}
		} else if (ForgeRegistries.Keys.MOB_EFFECTS.equals(event.getRegistryKey())) {
			event.register(ForgeRegistries.Keys.MOB_EFFECTS, id("quickening"),
					() -> QUICKENING = new QuickeningEffect());
			event.register(ForgeRegistries.Keys.MOB_EFFECTS, id("crystallization"),
					() -> CRYSTALLIZATION = new CrystallizationEffect());
			event.register(ForgeRegistries.Keys.MOB_EFFECTS, id("aurora"),
					() -> AURORA = new AuroraEffect());
		} else if (ForgeRegistries.Keys.ENTITY_TYPES.equals(event.getRegistryKey())) {
			event.register(ForgeRegistries.Keys.ENTITY_TYPES, id("rock_construct"),
					() -> ROCK_CONSTRUCT = buildRockConstruct());
		} else if (ForgeRegistries.Keys.MENU_TYPES.equals(event.getRegistryKey())) {
			event.register(ForgeRegistries.Keys.MENU_TYPES, id("gun_upgrade_table"),
					() -> UpgradeMenu.TYPE = UPGRADE_MENU_TYPE = IForgeMenuType.create(
							(syncId, inventory, buf) -> new UpgradeMenu(syncId, inventory)));
			event.register(ForgeRegistries.Keys.MENU_TYPES, id("element_synthesis_table"),
					() -> SynthesisMenu.TYPE = SYNTHESIS_MENU_TYPE = IForgeMenuType.create(
							(syncId, inventory, buf) -> new SynthesisMenu(syncId, inventory)));
			event.register(ForgeRegistries.Keys.MENU_TYPES, id("chip_assembly_table"),
					() -> ChipMenu.TYPE = CHIP_MENU_TYPE = IForgeMenuType.create(
							(syncId, inventory, buf) -> new ChipMenu(syncId, inventory)));
			// 自选界面一个品级一个类型：客户端只看类型就知道该摆哪一档的芯片
			for (ChipTier tier : ChipTier.values()) {
				event.register(ForgeRegistries.Keys.MENU_TYPES, id("chip_select_" + tier.id()), () -> {
					MenuType<ChipSelectMenu> type = IForgeMenuType.create(
							(syncId, inventory, buf) -> new ChipSelectMenu(syncId, inventory, tier));
					ChipSelectMenu.registerType(tier, type);
					return type;
				});
			}
		} else if (Registries.CREATIVE_MODE_TAB.equals(event.getRegistryKey())) {
			event.register(Registries.CREATIVE_MODE_TAB, id("element_upgrade"),
					() -> ELEMENT_UPGRADE_GROUP = buildCreativeTab());
		}
	}

	@SuppressWarnings("deprecation")
	private static EntityType<RockConstructEntity> buildRockConstruct() {
		return EntityType.Builder.of(RockConstructEntity::new, MobCategory.CREATURE)
				.sized(0.6F, 1.95F)
				.fireImmune()
				.clientTrackingRange(10)
				.build("rock_construct");
	}

	private static CreativeModeTab buildCreativeTab() {
		return CreativeModeTab.builder()
				.title(Component.translatable("itemGroup.tacz_gun_level.element_upgrade"))
				.icon(() -> new ItemStack(CORE_FIRE))
				.displayItems((params, output) -> {
					output.accept(UPGRADE_TABLE_ITEM);
					output.accept(ELEMENT_TABLE_ITEM);
					output.accept(CHIP_TABLE_ITEM);
					output.accept(COMPONENT_I);
					output.accept(COMPONENT_II);
					output.accept(COMPONENT_III);
					output.accept(COMPONENT_IV);
					output.accept(EMPTY_CORE);
					output.accept(CORE_THUNDER);
					output.accept(CORE_FIRE);
					output.accept(CORE_WATER);
					output.accept(CORE_ICE);
					output.accept(CORE_GRASS);
					output.accept(CORE_WIND);
					output.accept(CORE_ROCK);
					for (Item chip : CHIPS) {
						output.accept(chip);
					}
					for (ChipTier tier : ChipTier.values()) {
						output.accept(RANDOM_CHIP[tier.ordinal()]);
						output.accept(BROKEN_CHIP[tier.ordinal()]);
						output.accept(CHIP_PACK[tier.ordinal()]);
					}
				})
				.build();
	}

	/** 1.20.1 的属性表不在 EntityType 上，得单独挂一张（造物按僵尸那套走） */
	private void onAttributes(EntityAttributeCreationEvent event) {
		event.put(ROCK_CONSTRUCT, Zombie.createAttributes().build());
	}

	private void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		// 提前 return 会跳过收尾注入，这里兜底清空窗口栈，避免等级串到下一把枪
		LevelScaling.clear();
		CombatState.tick(event.getServer());
		BloomDomain.tick(event.getServer());
		ChipState.tick(event.getServer());
		AuroraState.tick(event.getServer());
		MomentumState.tick(event.getServer());
		WindGuardState.tick(event.getServer());
		ThunderVoiceState.tick(event.getServer());
		FlameRoarState.tick(event.getServer());
		HuntState.tick(event.getServer());
		FervorState.tick(event.getServer());
	}

	private void onServerStopping(ServerStoppingEvent event) {
		CombatState.clear();
		BloomDomain.clear();
		ChipState.clear();
		AuroraState.clear();
		MomentumState.clear();
		WindGuardState.clear();
		ThunderVoiceState.clear();
		FlameRoarState.clear();
		HuntState.clear();
		FervorState.clear();
		RockConstructEntity.clear();
	}

	private void onRegisterCommands(RegisterCommandsEvent event) {
		com.eternal.gunlevel.command.GunLevelCommand.register(event.getDispatcher());
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}

	private void loadConfig() {
		LevelConfig defaults = LevelConfig.defaults();
		try {
			Path file = configFile();
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			if (Files.exists(file)) {
				JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				JsonObject base = new Gson().toJsonTree(defaults).getAsJsonObject();
				boolean upgraded = false;
				for (String key : base.keySet()) {
					if (!json.has(key)) {
						json.add(key, base.get(key));
						upgraded = true;
					}
				}
				CONFIG = gson.fromJson(json, LevelConfig.class);
				if (upgraded) {
					// 旧配置缺字段：补齐后写回，方便直接编辑新增项（如元素分配）
					Files.writeString(file, gson.toJson(CONFIG));
				}
			} else {
				Files.createDirectories(file.getParent());
				Files.writeString(file, gson.toJson(defaults));
				CONFIG = defaults;
			}
		} catch (Exception e) {
			LOGGER.warn("配置文件读取失败，使用默认配置", e);
			CONFIG = defaults;
		}
		if (CONFIG == null) {
			CONFIG = defaults;
		}
		if (CONFIG.maxLevel < GunLevelData.MIN_LEVEL) {
			CONFIG.maxLevel = defaults.maxLevel;
		}
	}

	/** 设置界面改了数值就立刻写回，不用等重启 */
	public static void saveConfig() {
		try {
			Files.writeString(configFile(), new GsonBuilder().setPrettyPrinting().create().toJson(CONFIG));
		} catch (Exception e) {
			LOGGER.warn("配置写入失败", e);
		}
	}

	private static Path configFile() {
		return FMLPaths.CONFIGDIR.get().resolve("tacz-gun-level.json");
	}
}
