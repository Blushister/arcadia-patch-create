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
@Mixin(targets = "com.simibubi.create.content.kinetics.belt.transport.BeltInventory", remap = false)
public abstract class MixinBeltInventory {

    @Shadow
    private List<?> items;

    @Shadow
    private List<?> toInsert;

    @Shadow
    private List<?> toRemove;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadiaPatchCreate$skipEmptyBeltTick(CallbackInfo ci) {
        if (!PatchRuntime.isBeltPatchEnabled()) {
            return;
        }
        // Empty belts still reach the server tick path even though there is no transport work to perform.
        if (items.isEmpty() && toInsert.isEmpty() && toRemove.isEmpty()) {
            PatchRuntime.incrementBeltSkips();
            ci.cancel();
        }
    }
}
