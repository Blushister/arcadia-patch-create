package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.bridge.RedstoneLinkBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity", remap = false)
public abstract class MixinRedstoneLinkBlockEntityBridge implements RedstoneLinkBridge {

    @Shadow
    private int transmittedSignal;

    @Override
    public int arcadiaPatchCreate$getTransmittedSignal() {
        return transmittedSignal;
    }
}
