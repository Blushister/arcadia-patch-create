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
    private static final String ARM_BLOCK_ENTITY =
        "com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity";
    private static final String ARM_INTERACTION_POINT =
        "com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint";

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
    private static final String ARM_BLOCK_ENTITY_6_0_10_SHA256 =
        "983bf6da7afd388d02f2b5b48a8d52543eb447337baebf8559f85429f2bb46b9";
    private static final String ARM_INTERACTION_POINT_6_0_10_SHA256 =
        "0368158ead8633259ca9fffe568e486828b354e00158206701b2c8839712cb74";

    private static final Member GET_WORLD = new Member("getWorld", "()Lnet/minecraft/world/level/Level;");
    private static final Member GET_POS = new Member("getPos", "()Lnet/minecraft/core/BlockPos;");
    private static final Member TICK = new Member("tick", "()V");
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

    private static final Member SEARCH_FOR_ITEM = new Member("searchForItem", "()V");
    private static final Member SIMULATE_INSERTION = new Member(
        "simulateInsertion",
        "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"
    );
    private static final Member GET_DISTRIBUTABLE_AMOUNT = new Member(
        "getDistributableAmount",
        "(Lcom/simibubi/create/content/kinetics/mechanicalArm/ArmInteractionPoint;I)I"
    );

    private static final Invocation SIMULATE_INSERTION_CALL = new Invocation(
        ARM_BLOCK_ENTITY,
        SIMULATE_INSERTION.name(),
        SIMULATE_INSERTION.descriptor()
    );
    private static final Invocation DISTRIBUTABLE_AMOUNT_CALL = new Invocation(
        ARM_BLOCK_ENTITY,
        GET_DISTRIBUTABLE_AMOUNT.name(),
        GET_DISTRIBUTABLE_AMOUNT.descriptor()
    );
    private static final Invocation SIMULATED_INSERT_CALL = new Invocation(
        ARM_INTERACTION_POINT,
        "insert",
        "(Lcom/simibubi/create/content/kinetics/mechanicalArm/ArmBlockEntity;"
            + "Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"
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
            case "MixinArmBlockEntity" -> isArmTargetCompatible();
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
     * The memoization is only valid while {@code simulateInsertion} stays a pure dry run
     * driven by {@code searchForItem}. Both classes are fingerprinted, and the call chain
     * searchForItem -> getDistributableAmount -> simulateInsertion -> insert(simulate) is
     * verified, so any upstream restructuring disables the patch instead of altering it.
     */
    public static boolean isArmTargetCompatible() {
        ClassShape arm = shape(ARM_BLOCK_ENTITY);
        ClassShape point = shape(ARM_INTERACTION_POINT);
        return arm.hasFingerprint(ARM_BLOCK_ENTITY_6_0_10_SHA256)
            && point.hasFingerprint(ARM_INTERACTION_POINT_6_0_10_SHA256)
            && arm.hasField(new Member("outputs", "Ljava/util/List;"))
            && arm.hasField(new Member("inputs", "Ljava/util/List;"))
            && arm.hasMethod(SEARCH_FOR_ITEM)
            && arm.hasMethod(SIMULATE_INSERTION)
            && arm.hasMethod(GET_DISTRIBUTABLE_AMOUNT)
            && arm.hasInvocation(SEARCH_FOR_ITEM, DISTRIBUTABLE_AMOUNT_CALL)
            && arm.hasInvocation(GET_DISTRIBUTABLE_AMOUNT, SIMULATE_INSERTION_CALL)
            && arm.hasInvocation(SIMULATE_INSERTION, SIMULATED_INSERT_CALL);
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
