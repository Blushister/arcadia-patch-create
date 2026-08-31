package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.diagnostic.DiagnosticProbe;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Times a block entity's complete tick while an analysis targets it.
 *
 * <p>Measuring inside Create's own {@code SmartBlockEntity.tick} would be wrong: machines override
 * it and call {@code super.tick()} first, so a hook there only sees the inherited part and reports
 * a fraction of the real cost. The chunk ticker wraps the whole call, whatever the class does, and
 * covers every mod rather than Create alone.
 *
 * <p>Cost when idle: one static boolean read per block entity per tick, and nothing else.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class MixinBlockEntityTickerProbe {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void arcadiaPatchCreate$probeStart(CallbackInfo ci) {
        if (DiagnosticProbe.isActive() && (Object) this instanceof TickingBlockEntity ticking) {
            DiagnosticProbe.enter(ticking);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void arcadiaPatchCreate$probeEnd(CallbackInfo ci) {
        if (DiagnosticProbe.isActive() && (Object) this instanceof TickingBlockEntity ticking) {
            DiagnosticProbe.exit(ticking);
        }
    }
}
