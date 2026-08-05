package fr.arcadia.arcadiapatchcreate.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface PipeConnectionBridge {

    boolean arcadiaPatchCreate$isStandardIdleConnection();

    void arcadiaPatchCreate$settleIdleState(Level level, BlockPos pos);
}
