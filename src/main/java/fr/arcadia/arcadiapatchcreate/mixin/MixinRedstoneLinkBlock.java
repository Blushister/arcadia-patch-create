package fr.arcadia.arcadiapatchcreate.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.arcadia.arcadiapatchcreate.runtime.RedstoneLinkNotificationSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Redstone Links from notifying their neighbours when nothing changed.
 *
 * <p>Create's {@code tick} calls {@code blockUpdated} twice unconditionally, and
 * {@code neighborChanged} schedules a tick whenever a neighbour updates. Put together, a group of
 * links keeps waking each other up over a signal that never moves. On a production server, 104
 * links cost 1.12 ms/tick - almost as much as 1 263 belts - and 0.455 of that was the notification
 * itself, over half of it spent posting the neighbour-notify event.
 *
 * <p>Both notifications are skipped only when the transmitted signal and the block state are both
 * unchanged over the tick. Since {@code blockUpdated} exists to announce a change, announcing
 * nothing is a no-op. Any real transition restores them on the spot, and a receiver link is never
 * affected because Create returns before this code.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.redstone.link.RedstoneLinkBlock", remap = false)
public abstract class MixinRedstoneLinkBlock {

    @Inject(method = "tick", at = @At("HEAD"), require = 0, remap = false)
    private void arcadiaPatchCreate$captureBefore(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random,
        CallbackInfo ci
    ) {
        RedstoneLinkNotificationSupport.beginTick(state, level, pos);
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0, remap = false)
    private void arcadiaPatchCreate$clearAfter(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random,
        CallbackInfo ci
    ) {
        RedstoneLinkNotificationSupport.endTick();
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;blockUpdated"
                + "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V"
        ),
        require = 0,
        remap = false
    )
    private void arcadiaPatchCreate$skipRedundantNotification(
        ServerLevel level,
        BlockPos notifyPos,
        Block block,
        Operation<Void> original
    ) {
        if (RedstoneLinkNotificationSupport.shouldNotify()) {
            original.call(level, notifyPos, block);
        }
    }
}
