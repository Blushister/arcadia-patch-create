package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.runtime.ChuteThrottleSupport;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.logistics.chute.ChuteBlockEntity", remap = false)
public abstract class MixinChuteBlockEntity {

    @Unique
    private int arcadiaPatchCreate$aboveProbeCooldown;

    @Unique
    private int arcadiaPatchCreate$belowProbeCooldown;

    @Inject(method = "handleInputFromAbove", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadiaPatchCreate$throttleInputProbeFromAbove(CallbackInfo ci) {
        if (!ChuteThrottleSupport.canInspect()) {
            return;
        }
        if (!ChuteThrottleSupport.shouldSkipInputProbe(this, arcadiaPatchCreate$aboveProbeCooldown)) {
            arcadiaPatchCreate$aboveProbeCooldown = 0;
            return;
        }

        arcadiaPatchCreate$aboveProbeCooldown++;
        PatchRuntime.incrementChuteProbeSkips();
        ci.cancel();
    }

    @Inject(method = "handleInputFromBelow", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadiaPatchCreate$throttleInputProbeFromBelow(CallbackInfo ci) {
        if (!ChuteThrottleSupport.canInspect()) {
            return;
        }
        if (!ChuteThrottleSupport.shouldSkipInputProbe(this, arcadiaPatchCreate$belowProbeCooldown)) {
            arcadiaPatchCreate$belowProbeCooldown = 0;
            return;
        }

        arcadiaPatchCreate$belowProbeCooldown++;
        PatchRuntime.incrementChuteProbeSkips();
        ci.cancel();
    }
}
