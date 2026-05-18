package fr.arcadia.arcadiapatchcreate.mixin;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.collision.ContinuousOBBCollider$ContinuousSeparationManifold", remap = false)
public interface ContinuousSeparationManifoldAccessor {

    @Accessor("axis")
    Vec3 arcadiaPatchCreate$getAxis();

    @Accessor("normalAxis")
    Vec3 arcadiaPatchCreate$getNormalAxis();
}
