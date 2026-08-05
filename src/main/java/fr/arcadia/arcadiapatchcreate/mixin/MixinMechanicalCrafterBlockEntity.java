package fr.arcadia.arcadiapatchcreate.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.arcadia.arcadiapatchcreate.bridge.CrafterSignalBridge;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops re-scanning the six neighbours of every Mechanical Crafter on every tick.
 *
 * <p>Create only uses the signal to detect a rising edge:
 * {@code if (wasPoweredBefore != level.hasNeighborSignal(pos))}. A throttle would therefore be
 * wrong - a one tick pulse would not be delayed, it would be lost. Instead the value is cached and
 * invalidated the moment a neighbour actually changes, through
 * {@code MechanicalCrafterBlock.neighborChanged}, so a pulse is still seen on the very next tick.
 *
 * <p>Profiling attributed 0.398 ms/tick to {@code hasNeighborSignal} under the crafters, 52% of
 * their whole tick, spread over 559 loaded blocks.
 *
 * <p>A forced refresh every {@value #SAFETY_INTERVAL} ticks covers the one case events cannot:
 * a modded signal source that changes without notifying its neighbours. Vanilla always notifies.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity", remap = false)
public abstract class MixinMechanicalCrafterBlockEntity implements CrafterSignalBridge {

    /** Self-healing bound, in ticks, for sources that never fire a neighbour update. */
    @Unique
    private static final int SAFETY_INTERVAL = 20;

    @Unique
    private boolean arcadiaPatchCreate$signalKnown;

    @Unique
    private boolean arcadiaPatchCreate$cachedSignal;

    @Unique
    private int arcadiaPatchCreate$ticksUntilRefresh;

    @Override
    public void arcadiaPatchCreate$invalidateSignal() {
        arcadiaPatchCreate$signalKnown = false;
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;hasNeighborSignal(Lnet/minecraft/core/BlockPos;)Z"
        ),
        require = 0,
        remap = false
    )
    private boolean arcadiaPatchCreate$reuseNeighborSignal(
        Level level,
        BlockPos pos,
        Operation<Boolean> original
    ) {
        if (!PatchRuntime.isCrafterSignalPatchEnabled() || level == null || level.isClientSide) {
            return original.call(level, pos);
        }

        if (arcadiaPatchCreate$signalKnown && arcadiaPatchCreate$ticksUntilRefresh > 0) {
            arcadiaPatchCreate$ticksUntilRefresh--;
            PatchRuntime.incrementCrafterSignalReuses();
            return arcadiaPatchCreate$cachedSignal;
        }

        boolean signal = original.call(level, pos);
        arcadiaPatchCreate$cachedSignal = signal;
        arcadiaPatchCreate$signalKnown = true;
        arcadiaPatchCreate$ticksUntilRefresh = SAFETY_INTERVAL;
        PatchRuntime.incrementCrafterSignalReads();
        return signal;
    }
}
