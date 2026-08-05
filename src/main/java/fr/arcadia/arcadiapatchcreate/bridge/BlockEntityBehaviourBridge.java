package fr.arcadia.arcadiapatchcreate.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface BlockEntityBehaviourBridge {

    Level arcadiaPatchCreate$getWorld();

    BlockPos arcadiaPatchCreate$getPos();

    /** Calls Create's own {@code tick()} without going through a Consumer indirection. */
    void arcadiaPatchCreate$tickBehaviour();
}
