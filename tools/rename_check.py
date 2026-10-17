"""yarn(1.20.1) -> Mojang official(1.20.1) 的**类名**批量改名，用于 Fabric→Forge 移植。

用法：
    python tools/rename_check.py            # 只校验：打印官方 jar 里查无此类的目标
    python tools/rename_check.py --apply    # 校验全过再执行改名

只管类名（import 行 + 简单名），**故意不碰**方法名、字段名和结构性差异：
注册（Registries/Registry/RegistryKey）、容器（ScreenHandler/ScreenHandlerType/NamedScreenHandlerFactory）、
创造分类（ItemGroup）、客户端 API（DrawContext/Screen/KeyBinding/网络）这些在 Forge 侧要重写而不是改名，
所以从映射表里排除，留给逐文件手工处理。改完靠 javac 的错误清单驱动下一步。

校验依据是 ForgeGradle 产出的 mapped dev jar（MC 与 Forge 的官方命名类都在里面）。
"""
import os
import re
import sys
import zipfile

MAPPED = (r"E:\mc-mods\.gradle-forge\caches\forge_gradle\minecraft_user_repo"
          r"\net\minecraftforge\forge\1.20.1-47.3.22_mapped_official_1.20.1"
          r"\forge-1.20.1-47.3.22_mapped_official_1.20.1.jar")
SRC = os.path.join("src", "main", "java")

# 这些类在 Forge 侧不是改名能解决的，脚本刻意不映射，命中就报出来提醒手工处理：
EXCLUDED = {
    "net.minecraft.registry.Registries",
    "net.minecraft.registry.Registry",
    "net.minecraft.registry.RegistryKey",
    "net.minecraft.registry.RegistryKeys",
    "net.minecraft.screen.ScreenHandler",
    "net.minecraft.screen.ScreenHandlerType",
    "net.minecraft.screen.NamedScreenHandlerFactory",
    "net.minecraft.screen.slot.Slot",
    "net.minecraft.item.ItemGroup",
    "net.minecraft.item.TooltipContext",
    "net.minecraft.client.MinecraftClient",
    "net.minecraft.client.gui.DrawContext",
    "net.minecraft.client.gui.Element",
    "net.minecraft.client.gui.screen.Screen",
    "net.minecraft.client.gui.screen.ingame.HandledScreen",
    "net.minecraft.client.gui.widget.ButtonWidget",
    "net.minecraft.client.network.ClientPlayerEntity",
    "net.minecraft.client.option.KeyBinding",
    "net.minecraft.client.util.InputUtil",
    "net.minecraft.client.render.entity.EntityRendererFactory",
    "net.minecraft.client.render.entity.ZombieEntityRenderer",
    "net.minecraft.client.item.TooltipContext",
}

# 两边全限定名本来就一致的，不需要改名也不算漏项：
KNOWN_GOOD = {
    "net.minecraft.server.MinecraftServer",
}

CLASSES = {
    "net.minecraft.world.World": "net.minecraft.world.level.Level",
    "net.minecraft.world.BlockView": "net.minecraft.world.level.BlockGetter",
    "net.minecraft.server.world.ServerWorld": "net.minecraft.server.level.ServerLevel",
    "net.minecraft.server.network.ServerPlayerEntity": "net.minecraft.server.level.ServerPlayer",
    "net.minecraft.entity.Entity": "net.minecraft.world.entity.Entity",
    "net.minecraft.entity.LivingEntity": "net.minecraft.world.entity.LivingEntity",
    "net.minecraft.entity.EntityType": "net.minecraft.world.entity.EntityType",
    "net.minecraft.entity.MovementType": "net.minecraft.world.entity.MoverType",
    "net.minecraft.entity.SpawnGroup": "net.minecraft.world.entity.MobCategory",
    "net.minecraft.entity.player.PlayerEntity": "net.minecraft.world.entity.player.Player",
    "net.minecraft.entity.player.PlayerInventory": "net.minecraft.world.entity.player.Inventory",
    "net.minecraft.entity.mob.MobEntity": "net.minecraft.world.entity.Mob",
    "net.minecraft.entity.mob.Monster": "net.minecraft.world.entity.monster.Monster",
    "net.minecraft.entity.mob.ZombieEntity": "net.minecraft.world.entity.monster.Zombie",
    "net.minecraft.entity.ai.goal.RevengeGoal": "net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal",
    "net.minecraft.entity.ai.goal.WanderAroundFarGoal": "net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal",
    "net.minecraft.entity.ai.goal.ZombieAttackGoal": "net.minecraft.world.entity.ai.goal.MeleeAttackGoal",
    "net.minecraft.entity.attribute.AttributeContainer": "net.minecraft.world.entity.ai.attributes.AttributeMap",
    "net.minecraft.entity.attribute.EntityAttributeInstance": "net.minecraft.world.entity.ai.attributes.AttributeInstance",
    "net.minecraft.entity.attribute.EntityAttributeModifier": "net.minecraft.world.entity.ai.attributes.AttributeModifier",
    "net.minecraft.entity.attribute.EntityAttributes": "net.minecraft.world.entity.ai.attributes.Attributes",
    "net.minecraft.entity.damage.DamageSource": "net.minecraft.world.damagesource.DamageSource",
    "net.minecraft.entity.damage.DamageTypes": "net.minecraft.world.damagesource.DamageTypes",
    "net.minecraft.entity.effect.StatusEffect": "net.minecraft.world.effect.MobEffect",
    "net.minecraft.entity.effect.StatusEffectCategory": "net.minecraft.world.effect.MobEffectCategory",
    "net.minecraft.entity.effect.StatusEffectInstance": "net.minecraft.world.effect.MobEffectInstance",
    "net.minecraft.entity.effect.StatusEffects": "net.minecraft.world.effect.MobEffects",
    "net.minecraft.item.BlockItem": "net.minecraft.world.item.BlockItem",
    "net.minecraft.item.Item": "net.minecraft.world.item.Item",
    "net.minecraft.item.ItemStack": "net.minecraft.world.item.ItemStack",
    "net.minecraft.item.Items": "net.minecraft.world.item.Items",
    "net.minecraft.item.ItemPlacementContext": "net.minecraft.world.item.context.BlockPlaceContext",
    "net.minecraft.item.Rarity": "net.minecraft.world.item.Rarity",
    "net.minecraft.inventory.Inventory": "net.minecraft.world.Container",
    "net.minecraft.inventory.SimpleInventory": "net.minecraft.world.SimpleContainer",
    "net.minecraft.state.StateManager": "net.minecraft.world.level.block.state.StateDefinition",
    "net.minecraft.nbt.NbtCompound": "net.minecraft.nbt.CompoundTag",
    "net.minecraft.nbt.NbtElement": "net.minecraft.nbt.Tag",
    "net.minecraft.nbt.NbtList": "net.minecraft.nbt.ListTag",
    "net.minecraft.nbt.NbtString": "net.minecraft.nbt.StringTag",
    "net.minecraft.network.PacketByteBuf": "net.minecraft.network.FriendlyByteBuf",
    "net.minecraft.particle.ParticleEffect": "net.minecraft.core.particles.ParticleOptions",
    "net.minecraft.particle.ParticleTypes": "net.minecraft.core.particles.ParticleTypes",
    "net.minecraft.particle.DustParticleEffect": "net.minecraft.core.particles.DustParticleOptions",
    "net.minecraft.sound.BlockSoundGroup": "net.minecraft.world.level.block.SoundType",
    "net.minecraft.sound.SoundCategory": "net.minecraft.sounds.SoundSource",
    "net.minecraft.sound.SoundEvents": "net.minecraft.sounds.SoundEvents",
    "net.minecraft.state.property.Properties": "net.minecraft.world.level.block.state.properties.BlockStateProperties",
    "net.minecraft.state.property.DirectionProperty": "net.minecraft.world.level.block.state.properties.DirectionProperty",
    "net.minecraft.text.Text": "net.minecraft.network.chat.Component",
    "net.minecraft.util.Identifier": "net.minecraft.resources.ResourceLocation",
    "net.minecraft.util.Rarity": "net.minecraft.world.item.Rarity",
    "net.minecraft.util.Hand": "net.minecraft.world.InteractionHand",
    "net.minecraft.util.ActionResult": "net.minecraft.world.InteractionResult",
    "net.minecraft.util.hit.BlockHitResult": "net.minecraft.world.phys.BlockHitResult",
    "net.minecraft.util.math.BlockPos": "net.minecraft.core.BlockPos",
    "net.minecraft.util.math.Box": "net.minecraft.world.phys.AABB",
    "net.minecraft.util.math.Direction": "net.minecraft.core.Direction",
    "net.minecraft.util.math.MathHelper": "net.minecraft.util.Mth",
    "net.minecraft.util.math.Vec3d": "net.minecraft.world.phys.Vec3",
    "net.minecraft.util.math.random.Random": "net.minecraft.util.RandomSource",
    "net.minecraft.util.shape.VoxelShape": "net.minecraft.world.phys.shapes.VoxelShape",
    "net.minecraft.util.shape.VoxelShapes": "net.minecraft.world.phys.shapes.Shapes",
    "net.minecraft.block.Block": "net.minecraft.world.level.block.Block",
    "net.minecraft.block.BlockState": "net.minecraft.world.level.block.state.BlockState",
    "net.minecraft.block.ShapeContext": "net.minecraft.world.phys.shapes.CollisionContext",
    "net.minecraft.block.HorizontalFacingBlock": "net.minecraft.world.level.block.HorizontalRotationBlock",
    "net.minecraft.block.entity.BlockEntity": "net.minecraft.world.level.block.entity.BlockEntity",
    "net.minecraft.server.command.CommandManager": "net.minecraft.commands.Commands",
    "net.minecraft.server.command.ServerCommandSource": "net.minecraft.commands.CommandSourceStack",
    "net.minecraft.command.CommandSource": "net.minecraft.commands.CommandSourceStack",
}


def jar_classes():
    names = set()
    with zipfile.ZipFile(MAPPED) as z:
        for info in z.namelist():
            if info.endswith(".class") and "$" not in info:
                names.add(info[:-6].replace("/", "."))
    return names


def java_files():
    out = []
    for root, _, names in os.walk(SRC):
        out += [os.path.join(root, n) for n in names if n.endswith(".java")]
    return sorted(out)


def used_imports():
    used = set()
    for path in java_files():
        for m in re.finditer(r"^import\s+(net\.minecraft\.[A-Za-z0-9_.]+);",
                             open(path, encoding="utf-8").read(), re.M):
            used.add(m.group(1))
    return used


def main():
    apply = "--apply" in sys.argv
    force = "--force" in sys.argv
    have = jar_classes()
    used = used_imports()

    unmapped = sorted(u for u in used if u not in CLASSES and u not in EXCLUDED
                      and u not in KNOWN_GOOD and u not in have)
    structural = sorted(u for u in used if u in EXCLUDED)
    bad = {k: v for k, v in CLASSES.items() if k in used and v not in have}

    for u in unmapped:
        print("UNMAPPED   %s" % u)
    for u in structural:
        print("STRUCTURAL %s  (needs a real Forge rewrite, left alone)" % u)
    for k, v in sorted(bad.items()):
        simple = v.split(".")[-1]
        hits = sorted(n for n in have if n.split(".")[-1] == simple)[:3]
        print("BAD        %-68s -> %s\n             same-name candidates: %s"
              % (k, v, hits or "none"))

    print("used_mc_imports=%d mapped=%d unmapped=%d structural=%d bad=%d"
          % (len(used), len([u for u in used if u in CLASSES]), len(unmapped),
             len(structural), len(bad)))
    if unmapped or bad or not apply:
        return 1

    skipped = []
    for path in java_files():
        text = open(path, encoding="utf-8").read()
        orig = text
        present = [y for y in CLASSES if ("import " + y + ";") in orig
                   or (y.rsplit(".", 1)[1] + ".") in orig]
        pairs = [(y.rsplit(".", 1)[1], CLASSES[y].rsplit(".", 1)[1]) for y in present]
        # 同名换包（ItemStack -> ItemStack 只是包路径变了）不算冲突，先剔掉
        changed = [(a, b) for a, b in pairs if a != b]
        # 简单名撞车（例如 PlayerInventory->Inventory 与 Inventory->Container 同时出现）时
        # 整体替换会互相污染：默认跳过这类文件交给手工处理，--force 才强行套用
        left = {a for a, _ in changed}
        right = {b for _, b in changed}
        if (len(left) != len(changed) or (left & right)) and not force:
            skipped.append(path)
            continue
        for yarn in present:
            official = CLASSES[yarn]
            text = text.replace(yarn, official)
            text = re.sub(r"\b%s\b" % re.escape(yarn.rsplit(".", 1)[1]),
                          official.rsplit(".", 1)[1], text)
        if text != orig:
            open(path, "w", encoding="utf-8", newline="\n").write(text)
            print("rewrote %s (%d classes)" % (path, len(present)))
    for path in skipped:
        print("COLLISION %s  (simple-name clash, handle by hand)" % path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
