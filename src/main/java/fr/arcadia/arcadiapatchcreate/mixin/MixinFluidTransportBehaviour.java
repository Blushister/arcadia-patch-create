package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import fr.arcadia.arcadiapatchcreate.bridge.BlockEntityBehaviourBridge;
import fr.arcadia.arcadiapatchcreate.bridge.FluidTransportBehaviourBridge;
import fr.arcadia.arcadiapatchcreate.bridge.PipeConnectionBridge;
import fr.arcadia.arcadiapatchcreate.runtime.FluidInterfaceMapSupport;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.FluidTransportBehaviour", remap = false)
public abstract class MixinFluidTransportBehaviour {

    private static final long SKIP_LOG_INTERVAL = 262_144L;

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/fluids/PipeConnection;getProvidedFluid()Lnet/neoforged/neoforge/fluids/FluidStack;",
            ordinal = 0
        ),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void arcadiaPatchCreate$skipTrulyIdlePipe(CallbackInfo ci) {
        if (!PatchRuntime.isFluidPatchEnabled()) {
            return;
        }
        if (!((Object) this instanceof BlockEntityBehaviourBridge owner)
            || !((Object) this instanceof FluidTransportBehaviourBridge transport)) {
            recordBridgeFailure("Fluid transport bridge is unavailable");
            return;
        }

        Level world = owner.arcadiaPatchCreate$getWorld();
        BlockPos pos = owner.arcadiaPatchCreate$getPos();
        Map<?, ?> interfaces = transport.arcadiaPatchCreate$getInterfaces();
        if (world == null || world.isClientSide || interfaces == null || interfaces.isEmpty()) {
            return;
        }

        // This invoke is reached only after Create's WAIT_FOR_PUMPS and FLIP_FLOWS returns.
        for (Object connection : interfaces.values()) {
            if (!(connection instanceof PipeConnectionBridge pipeConnection)) {
                recordBridgeFailure("Pipe connection bridge is unavailable at " + pos);
                return;
            }
            if (!pipeConnection.arcadiaPatchCreate$isStandardIdleConnection()) {
                return;
            }
        }

        // Preserve the state changes performed by PipeConnection.manageFlows()
        // on this exact idle branch before skipping the remaining no-op loops.
        for (Object connection : interfaces.values()) {
            ((PipeConnectionBridge) connection).arcadiaPatchCreate$settleIdleState(world, pos);
        }

        long skipped = PatchRuntime.incrementFluidSkips();
        if (skipped <= 3 || skipped % SKIP_LOG_INTERVAL == 0) {
            ArcadiaPatchCreate.LOGGER.info(
                "[ArcadiaPatchCreate] Skipped {} fully idle Create fluid pipe ticks. Latest position: {}",
                skipped,
                pos
            );
        }
        ci.cancel();
    }

    /**
     * Create rebuilds the connection map here and in {@code read}. Swapping it for an
     * EnumMap once, at creation time, speeds up every later iteration - including the
     * two passes performed by the idle fast-path above.
     */
    @Inject(method = "createConnectionData", at = @At("RETURN"), require = 0, remap = false)
    private void arcadiaPatchCreate$compactCreatedInterfaces(CallbackInfo ci) {
        FluidInterfaceMapSupport.compact(this);
    }

    @Inject(
        method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private void arcadiaPatchCreate$compactLoadedInterfaces(CallbackInfo ci) {
        FluidInterfaceMapSupport.compact(this);
    }

    private static void recordBridgeFailure(String message) {
        long failures = PatchRuntime.incrementFluidInspectionFailures();
        if (failures <= 3 || failures % 1_024 == 0) {
            ArcadiaPatchCreate.LOGGER.warn("[ArcadiaPatchCreate] {}. Falling back to Create logic.", message);
        }
    }
}
