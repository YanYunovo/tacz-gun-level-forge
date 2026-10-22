# TACZ-元素升级 · Forge 版（tacz_gun_level）

模组显示名 **「TACZ-元素升级」**，仓库 `tacz-gun-level-forge`（GitHub 的仓库名只收 ASCII，中文名只进标题与描述）。
TACZ（永恒枪械工坊：零）**官方只更新 Forge**，所以这个工程是 Fabric 版（`tacz-gun-level`）的 Forge 移植：
玩法、数值、芯片、元素反应、三台机器、金芯片获取链**与 Fabric 版逐条对齐**，
差异全在平台层（注册、映射、事件、网络、mixin 装配）。
每项效果的推导过程和实测数字写在 Fabric 版仓库的 README 里，这里只记 Forge 侧的实现约束。

## 运行环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Loader | Forge **47.3.22**（`loaderVersion = "[47,)"`） |
| 前置模组 | 官方 TACZ 1.20.1（实测 1.1.6 / 1.1.7-hotfix）+ Geckolib 4.4.9（TACZ 自己的前置） |
| Java | 17（构建工具链必须是 JDK17，见下） |

成品：`build/libs/tacz-gun-level-1.0.0.jar`，放进实例的 `mods/` 即可。
产物文件名跟着 `archives_base_name=tacz-gun-level`，**与仓库名不同是有意的**：`mod_id=tacz_gun_level` 是枪 NBT 的键名
和 assets/data 的命名空间，跟着改名会让已升级的枪掉回 LV1。
**没装 TACZ 也不会崩**：前置检测走 `Class.forName("com.tacz.guns.api.item.IGun", false, loader)`，
缺席时只出方块与物品，所有 TACZ 相关逻辑短路（启动日志会打一行「前置 TACZ：未加载」）。

## 玩法速览

- **等级**：枪械升级台把枪升到 LV1~LV5（升级组件 I~IV 各 1 个逐级往上），等级写枪 NBT `tacz_gun_level`。
  每级伤害 +10%、弹速 +5%、射速 +8%、散布 −10%、换弹 −5%；LV3 撕裂弹流血，LV5 永恒贯穿（+2 段穿透、曳光、面板暴击 +15%）。
- **元素**：元素合成台做七元素核芯（雷/火/水/冰/草/风/岩）装到枪上，命中触发蒸发、融化、超载、感电、超导、
  绽放、燃烧、激化、扩散、结晶。元素只由核芯提供，与等级无关。
- **芯片**：芯片装配台装/拆金芯片，LV3 一颗、LV5 两颗，同款不重复。十三颗：
  连环暴击、破甲强攻、高能弹匣、极光追击、强击连发、势如破竹、凝晶召唤、净化领域、灵风之祝、迅雷之音、愈战愈勇、
  烈焰咆哮、狩猎时刻（其中灵风之祝、迅雷之音、烈焰咆哮带主动技能，默认按 V）。
- **金芯片获取链**：四角金锭 + 十字红石 + 中心下界合金 → **随机金芯片**；右键它 → 1 颗随机金芯片 + 1 个破碎的金芯片；
  任意一颗金芯片分解成 4 个破碎的；9 个破碎的 → **金芯片自选包**，右键开界面自选一颗（选完自动关界面）。
- **调试命令**（OP 2）：`/gunlevel set <1-5>`、`/gunlevel info`。
- **配置**：`config/tacz-gun-level.json`，所有数值（含每颗芯片的）都是配置项，tooltip 里的数字直接由它们算出。

## 移植时的平台差异（改这块之前先看）

### 映射：yarn → official

Loom 用 yarn，FG6 用 Mojang 官方映射，所以 Java 源码整体改过一遍名（`Identifier`→`ResourceLocation`、
`getStack`→`getItem`、`addStatusEffect`→`addEffect`、`Vec3d`→`Vec3` 且分量从字段变方法 `x()/y()/z()` 等）。
**注册、GUI、网络这三类是结构性差异，不是改名能覆盖的**，各自重写：

- 注册：eager 静态字段 → 全部在 `RegisterEvent` 的 supplier 里**现场 new**（`() -> UPGRADE_TABLE = new UpgradeTableBlock(TABLE)`）。
  提前 new 会撞 `IllegalStateException: Registry is already frozen`（就这么崩过一次，报错外观是
  「TACZ-元素升级 has failed to load correctly / ExceptionInInitializerError」）。
  其它文件对 `GunLevelMod.CORE_FIRE` 这类字段的引用一行都没改，字段本身留到注册回调里才赋值。
- `RegisterEvent#getRegistryKey()` 判等必须用 `ForgeRegistries.Keys.X.equals(...)` —— `ResourceKey` **不能 `==`**。
  创造页不在 `ForgeRegistries.Keys` 里，它注册到原版 `Registries.CREATIVE_MODE_TAB`。
- 菜单：`IForgeMenuType.create((syncId, inv, buf) -> new XMenu(syncId, inv))`。
- 实体：`EntityType.Builder.of(...).sized().fireImmune().clientTrackingRange().build("name")`
  （1.20.1 这个 `build(String)` 已 deprecated，要 `@SuppressWarnings("deprecation")`）；
  属性表不在 builder 上，走 `EntityAttributeCreationEvent` + `Zombie.createAttributes().build()`。
- 配置目录：`FMLPaths.CONFIGDIR.get()`；游戏事件挂 `MinecraftForge.EVENT_BUS`
  （`TickEvent.ServerTickEvent` 要自己判 `phase == END`），注册/属性这类挂 mod bus。
- 网络：`SimpleChannel#registerMessage` 的 handler 第二参是 **`Supplier<NetworkEvent.Context>`**，不是 Context 本身，
  照 Fabric 习惯写 `ctx.enqueueWork(...)` 会「找不到符号」，要 `ctx.get().xxx`。
- 命令 API 在 1.20.1 官方映射里叫 `hasPermission(int)` / `getPlayerOrException()` / `sendSuccess(Supplier,boolean)` /
  `sendFailure(Component)` —— **不是** `hasPermissionLevel/getPlayerOrThrow/sendFeedback/sendError`（那是 yarn 或更新版本的名字）。

### 原版注入换成 Forge 原生事件

Fabric 版有三个注入原版类的 mixin（免摔落、免疫负面效果、往原版「选项」界面加设置按钮）。
Forge 全都有原生事件，就没必要碰原版类（原版方法名得走 refmap，dev 与正式两边名字不同，少一条映射就是启动崩）：

| Fabric | Forge |
| --- | --- |
| `LivingEntityFallImmunityMixin`（`computeFallDamage` HEAD 返回 0） | `effect/LivingGuards` 挂 `LivingFallEvent` → `setCanceled(true)`，连伤害源都不产生 |
| `LivingEntityDebuffImmunityMixin`（`addStatusEffect(instance, cause)` 返回 false） | 同文件挂 `MobEffectEvent.Applicable` → `Event.Result.DENY`，只挡 `MobEffectCategory.HARMFUL`，正面与中立照常 |
| `client/ScreenDrawableChildAccessor`（accessor 调 `Screen#addDrawableChild`） | `client/GunLevelClient` 挂 `ScreenEvent.Init.Post` + `event.addListener(...)`。Fabric 那边不能用 `Screens.getButtons(screen)` 的包装列表（它按 `drawables` 算追加下标，而带标签页的选项界面 `drawables` 比 `children` 多一项，点标签就 `IndexOutOfBoundsException`），Forge 的事件内部走的正是原版 `addRenderableWidget` 那条路，不踩这个坑 |

所以 Forge 的 mixin 配置里**只剩 TACZ 的注入**（8 个服务端 + 2 个客户端），比 Fabric 少 3 个类。

### mixin 装配

```gradle
plugins { id 'net.minecraftforge.gradle' version '[6.0.16,6.2)' }
repositories {
    maven { url = 'https://maven.minecraftforge.net/' }
    maven { url = 'https://repo.spongepowered.org/repository/maven-public/' }  // :processor 只在这
}
dependencies {
    annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'
    compileOnly files('../forge-deps/tacz-1.20.1-1.1.7-hotfix.jar')
    compileOnly files('../forge-deps/geckolib-forge-1.20.1-4.4.9.jar')
}
tasks.named('compileJava') {
    dependsOn 'createMcpToSrg'                       // refmap 要 FG 的 named→SRG 映射
    options.compilerArgs += [
        '-AreobfTsrgFile='  + file("${buildDir}/createMcpToSrg/output.tsrg").absolutePath,
        '-AoutRefMapFile='  + file("${buildDir}/classes/java/main/${project.mod_id}.refmap.json").absolutePath,
    ]
}
jar { manifest { attributes(['MixinConfigs': "${project.mod_id}.mixins.json"]) } }
```

mixin 配置里也要写 `"refmap": "tacz_gun_level.refmap.json"`。注入 TACZ 自身成员的部分照 Fabric 的规矩：
TACZ 自己的方法/字段用真名 + `remap = false`，**描述符里出现 MC 类型的注入点必须能落进 refmap**，
否则运行时匹配不上（编译是不报错的）。

### 容量与弹药

- 弹匣容量唯一真值是 `AttachmentDataUtils.getAmmoCountWithAttachment(ItemStack, GunData)I`。
  极光匣的 ×2 与迅雷之音的 −20% 在**同一个注入点**里连乘；
  `ModernKineticGunScriptAPI.getMaxAmmoCount` 只是它的显示包装，挂那里只会改面板数字。
- 迅雷之音的「立刻补 10 发」允许顶破面板上限，所以不走换弹抽弹那条链路，直接 `IGun#setCurrentAmmoCount(当前 + 10)`。

## 构建

前置 jar 放在**工程同级**的 `../forge-deps/`（`build.gradle` 用 `compileOnly files(...)` 引它，仓库里没有）：

```
E:\mc-mods\forge-deps\tacz-1.20.1-1.1.7-hotfix.jar
E:\mc-mods\forge-deps\geckolib-forge-1.20.1-4.4.9.jar
```

```bash
# FG6 内部要 languageVersion=17 的工具链；本机默认 JDK20，所以 gradle 进程本身也得跑在 17 上
export JAVA_HOME='<一个 JDK17>'
# Forge 与 Fabric 两套 loom/FG 缓存必须分开，别在同一个 shell 里连着跑两个工程
export GRADLE_USER_HOME='<本机 Forge 缓存目录>'

./gradlew.bat build
```

`gradle.properties` 里已经写好了那台机器的 JDK17 安装路径（`org.gradle.java.installations.paths`），
换机器就改成自己的。首次构建要联网：ForgeGradle 与 userdev 依赖要拉，
`gradlew` 本身的 gradle-8.5 发行版也是**按 `GRADLE_USER_HOME` 缓存**的
（换个 `GRADLE_USER_HOME` 就得重新下一份，急着离线时可以直接用本机已缓存的
`~/.gradle/wrapper/dists/gradle-8.5-bin/*/gradle-8.5/bin/gradle.bat` 代替 `gradlew.bat`）。

验收产物时**别只看编译通过**：用 `javap -p -c` 看 class 里的成员名有没有被重映射成 SRG（`m_xxxx_`/`f_xxxx_`），
这能确认 mixin 目标在正式环境里对得上。

## 自测的限制（重要）

**`runClient` 测不了 TACZ 联动。** FG6 的 dev 运行环境是 official 名，而发布出去的 TACZ jar 是 SRG 名：
把生产版 TACZ 塞进 `run/mods` 会当场崩在 `@Inject` 描述符不匹配上。
所以平台层（注册、GUI、命令、客户端）能在 dev run 里验，
**等级加成 / 元素反应 / 芯片效果必须装进一个真的 Forge 47.3.x + TACZ 实例里验**。

调试 mixin 时 `runs.configureEach` 里已开 `mixin.debug.export`（导出到 `run/.mixin.out`），
启动日志里搜 `was not applied` / `InvalidInjectionException` ——
`@Inject` 的 handler 参数表少写一个目标参数就是 `InvalidInjectionException`，游戏直接起不来。
