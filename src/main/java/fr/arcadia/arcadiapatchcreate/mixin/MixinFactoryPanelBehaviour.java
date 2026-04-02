package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.runtime.FactoryGaugeThrottleSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour", remap = false)
public abstract class MixinFactoryPanelBehaviour {

    @Unique
    private int arcadiaPatchCreate$factoryGaugeCooldown;

    @Inject(method = "tickStorageMonitor", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadiaPatchCreate$throttleStableFactoryGaugeMonitor(CallbackInfo ci) {
        if (!FactoryGaugeThrottleSupport.canInspect()) {
            return;
        }

        if (!FactoryGaugeThrottleSupport.shouldSkip(this, arcadiaPatchCreate$factoryGaugeCooldown)) {
            arcadiaPatchCreate$factoryGaugeCooldown = 0;
            return;
        }

        arcadiaPatchCreate$factoryGaugeCooldown++;
        ci.cancel();
    }
}
