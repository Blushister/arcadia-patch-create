package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.bridge.PipeConnectionBridge;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.PipeConnection", remap = false)
public abstract class MixinPipeConnectionBridge implements PipeConnectionBridge {

    private static final String STANDARD_PIPE_CONNECTION = "com.simibubi.create.content.fluids.PipeConnection";

    @Shadow
    private Optional<?> source;

    @Shadow
    private Optional<?> network;

    @Shadow
    public abstract boolean hasPressure();

    @Shadow
    public abstract boolean hasFlow();

    @Shadow
    public abstract boolean determineSource(Level level, BlockPos pos);

    @Override
    public boolean arcadiaPatchCreate$isStandardIdleConnection() {
        return STANDARD_PIPE_CONNECTION.equals(getClass().getName()) && !hasPressure() && !hasFlow();
    }

    @Override
    public void arcadiaPatchCreate$settleIdleState(Level level, BlockPos pos) {
        // manageFlows() always discards the retained Layer III network before
        // returning for a connection with neither flow nor pressure.
        network = Optional.empty();
        if (source.isEmpty()) {
            determineSource(level, pos);
        }
    }
}
