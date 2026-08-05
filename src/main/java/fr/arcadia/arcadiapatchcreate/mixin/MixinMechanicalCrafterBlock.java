package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.bridge.CrafterSignalBridge;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invalidates the cached redstone signal of a crafter as soon as one of its neighbours changes.
 *
 * <p>This is what makes the cache in {@link MixinMechanicalCrafterBlockEntity} safe: the value is
 * dropped on the notification itself, so the next tick reads the world again and Create still sees
 * the rising edge of a one tick pulse.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock", remap = false)
public abstract class MixinMechanicalCrafterBlock {

    @Inject(method = "neighborChanged", at = @At("HEAD"), require = 0, remap = false)
    private void arcadiaPatchCreate$invalidateCachedSignal(
        BlockState state,
        Level level,
        BlockPos pos,
        Block neighbourBlock,
        BlockPos neighbourPos,
        boolean movedByPiston,
        CallbackInfo ci
    ) {
        if (!PatchRuntime.isCrafterSignalPatchEnabled() || level == null || level.isClientSide) {
            return;
        }
        // getBlockEntity is only paid on an actual neighbour update, never per tick.
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof CrafterSignalBridge bridge) {
            bridge.arcadiaPatchCreate$invalidateSignal();
            PatchRuntime.incrementCrafterSignalInvalidations();
        }
    }
}
