package fr.arcadia.arcadiapatchcreate.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Keeps every optimization optional. Targets are inspected without loading them,
 * so an absent addon or an incompatible member layout disables the whole related
 * patch group before Mixin validates any {@code @Shadow} member.
 */
public final class ArcadiaMixinPlugin implements IMixinConfigPlugin {

    private static final String BLOCK_ENTITY_BEHAVIOUR =
        "com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour";
    private static final String FLUID_TRANSPORT = "com.simibubi.create.content.fluids.FluidTransportBehaviour";
    private static final String PIPE_CONNECTION = "com.simibubi.create.content.fluids.PipeConnection";
    private static final String BELT_INVENTORY =
        "com.simibubi.create.content.kinetics.belt.transport.BeltInventory";
    private static final String GENERIC_ITEM_EMPTYING =
        "com.simibubi.create.content.fluids.transfer.GenericItemEmptying";
    private static final String ITEM_DRAIN = "com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity";
    private static final String HEAT_CONTEXT = "com.xiaohunao.create_heat_js.common.HeatRecipeContext";
    private static final String CRAFTER_BLOCK =
        "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock";
    private static final String CRAFTER_BLOCK_ENTITY =
        "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity";
    private static final String REDSTONE_LINK_BLOCK =
        "com.simibubi.create.content.redstone.link.RedstoneLinkBlock";
    private static final String REDSTONE_LINK_BLOCK_ENTITY =
        "com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity";
    private static final String CAPABILITY_PROVIDER =
        "com.simibubi.create.foundation.ICapabilityProvider$BlockCapabilityCacheProvider";
    private static final String SMART_BLOCK_ENTITY =
        "com.simibubi.create.foundation.blockEntity.SmartBlockEntity";

    // Fingerprint the exact implementations whose remaining bytecode is skipped or
    // whose result is reused. A future upstream build may keep every signature while
    // moving an anchor or adding a side effect; in that case the patch must fail open.
    private static final String BELT_INVENTORY_6_0_10_SHA256 =
        "e35a3f7ddd316e5901c1e57a0f2e5f00c4bf46dd296287096387591117b4c2ef";
    private static final String FLUID_TRANSPORT_6_0_10_SHA256 =
        "3833e0855760f46dd5576b82ed1a91e5ab683b1598756d340a0078768a408eea";
    private static final String PIPE_CONNECTION_6_0_10_SHA256 =
        "ad781981ba11ee2e5534cd803a0da72bb2199105a6df33cb9175591f467602a2";
    private static final String BLOCK_ENTITY_BEHAVIOUR_6_0_10_SHA256 =
        "2088a6dc96882f59f4df86430fe1c0f0bd277535ba282e341755f24060e162fc";
    private static final String GENERIC_ITEM_EMPTYING_6_0_10_SHA256 =
        "33818906eeac300f28c370a0861b403db41737d3252463c6104e813c11608a72";
    private static final String ITEM_DRAIN_6_0_10_SHA256 =
        "6792918ca2e21f5149abfb69fa18e342c5f65a7d31c98f26bb7356746a3a77ae";
    private static final String HEAT_CONTEXT_0_0_6_SHA256 =
        "dc6c6212c5add4cc930ce140c51a4089649ab03e13e3a10f70ff7cca6365a891";
    private static final String CRAFTER_BLOCK_6_0_10_SHA256 =
        "4be7f4b26579904953e404257636dd85a3eecc2b8a01b6ff31afb490feb682eb";
    private static final String CRAFTER_BLOCK_ENTITY_6_0_10_SHA256 =
        "70f2a84a2223541342cc50ee28803fa24636d1f97027146eeb0e6002793e52f6";
    private static final String REDSTONE_LINK_BLOCK_6_0_10_SHA256 =
        "e6f761f516be32e05e429bcdc7b03b8c9931c762d289efafcac6e1abe43ca4f6";
    private static final String REDSTONE_LINK_BLOCK_ENTITY_6_0_10_SHA256 =
        "4b22e991c8c1c4bf091fee66fcf711890a1a5b30f65ebab08f816efb0600fd9b";
    private static final String CAPABILITY_PROVIDER_6_0_10_SHA256 =
        "83c5f5de93d2c2702f22f6f072dc9cf3ac7a705c12c34c1023224fe174e62bab";
    private static final String SMART_BLOCK_ENTITY_6_0_10_SHA256 =
        "6e5272cffba116187a6c37a36321e1e76e67bb206f8d8804d60f9a92ee1ef2f9";

    private static final Member GET_WORLD = new Member("getWorld", "()Lnet/minecraft/world/level/Level;");
    private static final Member GET_POS = new Member("getPos", "()Lnet/minecraft/core/BlockPos;");
    private static final Member TICK = new Member("tick", "()V");
    private static final Member REDSTONE_LINK_TICK = new Member(
        "tick",
        "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"
    );
    private static final Member CONTINUE_PROCESSING = new Member("continueProcessing", "()Z");
    private static final Member CAN_ITEM_BE_EMPTIED = new Member(
        "canItemBeEmptied",
        "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Z"
    );
    private static final Member EMPTY_ITEM = new Member(
        "emptyItem",
        "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Z)Lnet/createmod/catnip/data/Pair;"
    );
    private static final Member HEAT_OF = new Member(
        "of",
        "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/Recipe;)"
            + "Lcom/xiaohunao/create_heat_js/common/HeatRecipeContext;"
    );
    private static final Member HEAT_CONSTRUCTOR = new Member(
        "<init>",
        "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)V"
    );

    private static final Invocation PROVIDED_FLUID = new Invocation(
        PIPE_CONNECTION,
        "getProvidedFluid",
        "()Lnet/neoforged/neoforge/fluids/FluidStack;"
    );
    private static final Invocation FIND_RECIPE = new Invocation(
        "com.simibubi.create.AllRecipeTypes",
        "find",
        "(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"
    );
    private static final Invocation CAN_EMPTY_INVOCATION = new Invocation(
        GENERIC_ITEM_EMPTYING,
        "canItemBeEmptied",
        CAN_ITEM_BE_EMPTIED.descriptor()
    );

    private static final ClassShape MISSING = new ClassShape(false, "", Set.of(), Set.of(), Map.of());
    private static final Map<String, ClassShape> SHAPES = new ConcurrentHashMap<>();

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return switch (simpleName) {
            case "MixinBeltInventory" -> isBeltTargetCompatible();
            case "MixinBlockEntityBehaviourBridge", "MixinFluidTransportBehaviourBridge",
                "MixinPipeConnectionBridge", "MixinFluidTransportBehaviour" -> isFluidTargetCompatible();
            case "MixinGenericItemEmptying", "MixinItemDrainBlockEntity" -> isItemDrainTargetCompatible();
            case "MixinHeatRecipeContext", "MixinRecipeManager" -> isHeatJsTargetCompatible();
            case "MixinSmartBlockEntity" -> isBehaviourDispatchTargetCompatible();
            case "MixinCreateBlockCapabilityCacheProvider" -> isCapabilityGuardTargetCompatible();
            case "MixinRedstoneLinkBlock", "MixinRedstoneLinkBlockEntityBridge" ->
                isRedstoneLinkTargetCompatible();
            case "MixinMechanicalCrafterBlock", "MixinMechanicalCrafterBlockEntity" ->
                isCrafterSignalTargetCompatible();
            default -> true;
        };
    }

    public static boolean isBeltTargetCompatible() {
        ClassShape belt = shape(BELT_INVENTORY);
        return belt.hasFingerprint(BELT_INVENTORY_6_0_10_SHA256)
            && belt.hasField(new Member("items", "Ljava/util/List;"))
            && belt.hasMethod(TICK)
            && belt.hasInvocation(TICK, new Invocation("java.util.List", "iterator", "()Ljava/util/Iterator;"));
    }

    public static boolean isFluidTargetCompatible() {
        return shape(BLOCK_ENTITY_BEHAVIOUR).hasFingerprint(BLOCK_ENTITY_BEHAVIOUR_6_0_10_SHA256)
            && shape(FLUID_TRANSPORT).hasFingerprint(FLUID_TRANSPORT_6_0_10_SHA256)
            && shape(PIPE_CONNECTION).hasFingerprint(PIPE_CONNECTION_6_0_10_SHA256)
            && blockEntityBehaviourCompatible()
            && fluidBehaviourCompatible()
            && pipeConnectionCompatible()
            && shape(FLUID_TRANSPORT).hasInvocation(TICK, PROVIDED_FLUID);
    }

    public static boolean isItemDrainTargetCompatible() {
        ClassShape generic = shape(GENERIC_ITEM_EMPTYING);
        ClassShape drain = shape(ITEM_DRAIN);
        return generic.hasFingerprint(GENERIC_ITEM_EMPTYING_6_0_10_SHA256)
            && drain.hasFingerprint(ITEM_DRAIN_6_0_10_SHA256)
            && generic.hasMethod(CAN_ITEM_BE_EMPTIED)
            && generic.hasMethod(EMPTY_ITEM)
            && generic.hasInvocation(CAN_ITEM_BE_EMPTIED, FIND_RECIPE)
            && generic.hasInvocation(EMPTY_ITEM, FIND_RECIPE)
            && drain.hasMethod(CONTINUE_PROCESSING)
            && drain.hasInvocation(CONTINUE_PROCESSING, CAN_EMPTY_INVOCATION);
    }

    public static boolean isHeatJsTargetCompatible() {
        ClassShape heat = shape(HEAT_CONTEXT);
        return heat.hasFingerprint(HEAT_CONTEXT_0_0_6_SHA256)
            && heat.hasMethod(HEAT_OF)
            && heat.hasMethod(HEAT_CONSTRUCTOR);
    }

    /**
     * The direct dispatch is only equivalent while tick() delegates to forEachBehaviour and
     * behaviours live in the shadowed map. Both the class fingerprint and that exact call are
     * verified, and the bridge on BlockEntityBehaviour must be applicable too.
     */
    public static boolean isBehaviourDispatchTargetCompatible() {
        ClassShape smart = shape(SMART_BLOCK_ENTITY);
        ClassShape behaviour = shape(BLOCK_ENTITY_BEHAVIOUR);
        return smart.hasFingerprint(SMART_BLOCK_ENTITY_6_0_10_SHA256)
            && behaviour.hasFingerprint(BLOCK_ENTITY_BEHAVIOUR_6_0_10_SHA256)
            && smart.hasField(new Member("behaviours", "Ljava/util/Map;"))
            && smart.hasMethod(new Member("getAllBehaviours", "()Ljava/util/Collection;"))
            && smart.hasMethod(TICK)
            && smart.hasInvocation(TICK, new Invocation(
                SMART_BLOCK_ENTITY, "forEachBehaviour", "(Ljava/util/function/Consumer;)V"))
            && behaviour.hasMethod(TICK)
            && blockEntityBehaviourCompatible();
    }

    /**
     * The cache is only sound while tick() reads the signal through hasNeighborSignal and the
     * block still forwards neighbour updates. Both classes are fingerprinted and both anchors
     * verified, so an upstream change disables the cache rather than silencing a redstone pulse.
     */
    public static boolean isCrafterSignalTargetCompatible() {
        ClassShape block = shape(CRAFTER_BLOCK);
        ClassShape entity = shape(CRAFTER_BLOCK_ENTITY);
        return block.hasFingerprint(CRAFTER_BLOCK_6_0_10_SHA256)
            && entity.hasFingerprint(CRAFTER_BLOCK_ENTITY_6_0_10_SHA256)
            && entity.hasField(new Member("wasPoweredBefore", "Z"))
            && entity.hasMethod(TICK)
            && entity.hasInvocation(TICK, new Invocation(
                "net.minecraft.world.level.Level", "hasNeighborSignal", "(Lnet/minecraft/core/BlockPos;)Z"))
            && block.hasMethod(new Member("neighborChanged",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;"
                    + "Lnet/minecraft/core/BlockPos;Z)V"));
    }

    /**
     * Skipping the notification is only sound while tick() still calls blockUpdated after
     * recomputing the signal, and while the block entity still exposes the transmitted value.
     * Both classes are fingerprinted and both anchors verified.
     */
    public static boolean isRedstoneLinkTargetCompatible() {
        ClassShape block = shape(REDSTONE_LINK_BLOCK);
        ClassShape entity = shape(REDSTONE_LINK_BLOCK_ENTITY);
        return block.hasFingerprint(REDSTONE_LINK_BLOCK_6_0_10_SHA256)
            && entity.hasFingerprint(REDSTONE_LINK_BLOCK_ENTITY_6_0_10_SHA256)
            && entity.hasField(new Member("transmittedSignal", "I"))
            && block.hasMethod(REDSTONE_LINK_TICK)
            && block.hasInvocation(REDSTONE_LINK_TICK, new Invocation(
                "net.minecraft.server.level.ServerLevel", "blockUpdated",
                "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V"));
    }

    /** Crash guard: only needs the method to exist, and the exact class it wraps. */
    public static boolean isCapabilityGuardTargetCompatible() {
        ClassShape provider = shape(CAPABILITY_PROVIDER);
        return provider.hasFingerprint(CAPABILITY_PROVIDER_6_0_10_SHA256)
            && provider.hasMethod(new Member("getCapability", "()Ljava/lang/Object;"));
    }

    private static boolean blockEntityBehaviourCompatible() {
        ClassShape behaviour = shape(BLOCK_ENTITY_BEHAVIOUR);
        return behaviour.present() && behaviour.hasMethod(GET_WORLD) && behaviour.hasMethod(GET_POS);
    }

    private static boolean fluidBehaviourCompatible() {
        ClassShape behaviour = shape(FLUID_TRANSPORT);
        return behaviour.present()
            && behaviour.hasField(new Member("interfaces", "Ljava/util/Map;"))
            && behaviour.hasMethod(TICK);
    }

    private static boolean pipeConnectionCompatible() {
        ClassShape connection = shape(PIPE_CONNECTION);
        return connection.present()
            && connection.hasField(new Member("source", "Ljava/util/Optional;"))
            && connection.hasField(new Member("network", "Ljava/util/Optional;"))
            && connection.hasMethod(new Member("hasPressure", "()Z"))
            && connection.hasMethod(new Member("hasFlow", "()Z"))
            && connection.hasMethod(new Member(
                "determineSource",
                "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"
            ));
    }

    private static ClassShape shape(String className) {
        return SHAPES.computeIfAbsent(className, ArcadiaMixinPlugin::readShape);
    }

    private static ClassShape readShape(String className) {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream input = ArcadiaMixinPlugin.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return MISSING;
            }
            byte[] classBytes = input.readAllBytes();
            ClassNode node = new ClassNode();
            new ClassReader(classBytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            Set<Member> fields = new HashSet<>();
            node.fields.forEach(field -> fields.add(new Member(field.name, field.desc)));

            Set<Member> methods = new HashSet<>();
            Map<Member, Set<Invocation>> invocations = new HashMap<>();
            for (MethodNode method : node.methods) {
                Member member = new Member(method.name, method.desc);
                methods.add(member);
                Set<Invocation> methodInvocations = new HashSet<>();
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode methodInsn) {
                        methodInvocations.add(new Invocation(
                            methodInsn.owner.replace('/', '.'),
                            methodInsn.name,
                            methodInsn.desc
                        ));
                    }
                }
                invocations.put(member, Set.copyOf(methodInvocations));
            }
            return new ClassShape(
                true,
                sha256(classBytes),
                Set.copyOf(fields),
                Set.copyOf(methods),
                Map.copyOf(invocations)
            );
        } catch (IOException | RuntimeException | LinkageError ignored) {
            return MISSING;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private record Member(String name, String descriptor) {
    }

    private record Invocation(String owner, String name, String descriptor) {
    }

    private record ClassShape(
        boolean present,
        String fingerprint,
        Set<Member> fields,
        Set<Member> methods,
        Map<Member, Set<Invocation>> invocations
    ) {

        private boolean hasFingerprint(String expected) {
            return present && fingerprint.equals(expected);
        }

        private boolean hasField(Member member) {
            return fields.contains(member);
        }

        private boolean hasMethod(Member member) {
            return methods.contains(member);
        }

        private boolean hasInvocation(Member method, Invocation invocation) {
            return invocations.getOrDefault(method, Set.of()).contains(invocation);
        }
    }
}
