package fr.arcadia.arcadiapatchcreate.runtime;

import fr.arcadia.arcadiapatchcreate.bridge.RedstoneLinkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether a Redstone Link still needs to notify its neighbours at the end of its tick.
 *
 * <p>The state is captured before Create recomputes the signal and compared once, right before the
 * first notification. Both notifications of a tick share that single decision, so the pair is never
 * split - either both fire or neither does, exactly as Create intends.
 *
 * <p>Block entity ticking is single threaded and the whole window lives inside one {@code tick}
 * call, so a plain field is enough. Any doubt - missing block entity, absent bridge, exception -
 * resolves to notifying, which is Create's own behaviour.
 */
public final class RedstoneLinkNotificationSupport {

    private static final int UNKNOWN = Integer.MIN_VALUE;

    private static RedstoneLinkBridge link;
    private static BlockState stateBefore;
    private static ServerLevel level;
    private static BlockPos pos;
    private static int signalBefore = UNKNOWN;
    private static boolean decided;
    private static boolean notify = true;

    private RedstoneLinkNotificationSupport() {
    }

    public static void beginTick(BlockState state, ServerLevel serverLevel, BlockPos blockPos) {
        reset();
        if (!PatchRuntime.isRedstoneLinkPatchEnabled() || serverLevel == null || blockPos == null) {
            return;
        }
        try {
            BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
            if (!(blockEntity instanceof RedstoneLinkBridge bridge)) {
                return;
            }
            link = bridge;
            level = serverLevel;
            pos = blockPos.immutable();
            stateBefore = state;
            signalBefore = bridge.arcadiaPatchCreate$getTransmittedSignal();
        } catch (RuntimeException | LinkageError exception) {
            reset();
        }
    }

    /** Returns false only when this tick provably changed nothing. */
    public static boolean shouldNotify() {
        if (link == null) {
            return true;
        }
        if (decided) {
            return notify;
        }
        decided = true;
        try {
            // BlockStates are interned, so identity comparison is exact here.
            notify = link.arcadiaPatchCreate$getTransmittedSignal() != signalBefore
                || level.getBlockState(pos) != stateBefore;
        } catch (RuntimeException | LinkageError exception) {
            notify = true;
        }
        if (!notify) {
            PatchRuntime.incrementRedstoneLinkSkips();
        } else {
            PatchRuntime.incrementRedstoneLinkNotifications();
        }
        return notify;
    }

    public static void endTick() {
        reset();
    }

    private static void reset() {
        link = null;
        stateBefore = null;
        level = null;
        pos = null;
        signalBefore = UNKNOWN;
        decided = false;
        notify = true;
    }
}
