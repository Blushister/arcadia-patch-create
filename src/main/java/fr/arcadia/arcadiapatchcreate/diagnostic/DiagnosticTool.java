package fr.arcadia.arcadiapatchcreate.diagnostic;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.component.DataComponents;

/**
 * The moderator-facing analysis tool.
 *
 * <p>Uses a vanilla item carrying a marker component rather than a registered one: the mod stays
 * server-side only, so a custom item would simply not exist on the client. Same approach as the
 * admin panel, which uses a vanilla menu type.
 */
public final class DiagnosticTool {

    private static final String MARKER = "arcadia_diagnostic_tool";
    private static final int COOLDOWN_TICKS = 40;

    private static ServerPlayer pendingPlayer;
    private static ServerLevel pendingLevel;
    private static BlockPos pendingPos;
    private static int ticksLeft;

    private DiagnosticTool() {
    }

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.CLOCK);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Outil de diagnostic Arcadia")
            .withStyle(ChatFormatting.AQUA));
        return stack;
    }

    public static boolean isTool(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.CLOCK)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(MARKER);
    }

    public static boolean isBusy() {
        return pendingPlayer != null;
    }

    /**
     * Starts an analysis. The caller is responsible for having cancelled the interaction event
     * first: a left click breaks the block, and a diagnostic tool must never destroy what it looks at.
     */
    public static void begin(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (pendingPlayer != null) {
            player.sendSystemMessage(Component.literal("Une analyse est déjà en cours, patiente.")
                .withStyle(ChatFormatting.YELLOW));
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            // Nothing to time: answer immediately rather than wait five seconds for nothing.
            BlockDiagnostics.report(player, level, pos);
            return;
        }

        pendingPlayer = player;
        pendingLevel = level;
        pendingPos = pos.immutable();
        ticksLeft = DiagnosticProbe.SAMPLE_TICKS;
        DiagnosticProbe.start(level, pos, blockEntityTypeName(level, pos, blockEntity));

        player.sendSystemMessage(Component.literal("Analyse en cours… (5 secondes)")
            .withStyle(ChatFormatting.AQUA));
    }

    /** Called once per server tick while an analysis is running. */
    public static void tick() {
        if (pendingPlayer == null) {
            return;
        }
        if (--ticksLeft > 0) {
            return;
        }

        ServerPlayer player = pendingPlayer;
        ServerLevel level = pendingLevel;
        BlockPos pos = pendingPos;
        pendingPlayer = null;
        pendingLevel = null;
        pendingPos = null;

        try {
            BlockDiagnostics.report(player, level, pos);
        } catch (RuntimeException | LinkageError exception) {
            ArcadiaPatchCreate.LOGGER.warn("[ArcadiaPatchCreate] Diagnostic report failed.", exception);
            player.sendSystemMessage(Component.literal("L'analyse a échoué, réessaie.")
                .withStyle(ChatFormatting.RED));
        } finally {
            DiagnosticProbe.stop();
        }
    }

    /** Same identifier the chunk ticker reports, so peers match exactly. */
    private static String blockEntityTypeName(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
            .getKey(blockEntity.getType()).toString();
    }

    public static int cooldownTicks() {
        return COOLDOWN_TICKS;
    }
}
