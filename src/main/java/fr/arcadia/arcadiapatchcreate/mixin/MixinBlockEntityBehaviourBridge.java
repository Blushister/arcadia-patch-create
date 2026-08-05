package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.bridge.BlockEntityBehaviourBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour", remap = false)
public abstract class MixinBlockEntityBehaviourBridge implements BlockEntityBehaviourBridge {

    @Shadow
    public abstract Level getWorld();

    @Shadow
    public abstract BlockPos getPos();

    @Override
    public Level arcadiaPatchCreate$getWorld() {
        return getWorld();
    }

    @Override
    public BlockPos arcadiaPatchCreate$getPos() {
        return getPos();
    }
}
