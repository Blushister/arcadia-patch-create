package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = "com.simibubi.create.content.kinetics.belt.transport.BeltInventory",
    priority = 1_100,
    remap = false
)
public abstract class MixinBeltInventory {

    @Shadow
    private List<?> items;

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;iterator()Ljava/util/Iterator;",
            ordinal = 0,
            shift = At.Shift.BEFORE
        ),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void arcadiaPatchCreate$skipEmptyBeltTick(CallbackInfo ci) {
        if (!PatchRuntime.isBeltPatchEnabled()) {
            return;
        }
        // Create has already processed lazy-client state, pending inserts/removals and belt reversals here.
        if (items.isEmpty()) {
            PatchRuntime.incrementBeltSkips();
            ci.cancel();
        }
    }
}
