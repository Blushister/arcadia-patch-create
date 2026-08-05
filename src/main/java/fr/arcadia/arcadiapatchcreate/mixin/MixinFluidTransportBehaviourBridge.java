package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.bridge.FluidTransportBehaviourBridge;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.FluidTransportBehaviour", remap = false)
public abstract class MixinFluidTransportBehaviourBridge implements FluidTransportBehaviourBridge {

    @Shadow
    public Map<?, ?> interfaces;

    @Override
    public Map<?, ?> arcadiaPatchCreate$getInterfaces() {
        return interfaces;
    }

    @Override
    public void arcadiaPatchCreate$setInterfaces(Map<?, ?> replacement) {
        interfaces = replacement;
    }
}
