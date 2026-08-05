package fr.arcadia.arcadiapatchcreate.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.arcadia.arcadiapatchcreate.bridge.BlockEntityBehaviourBridge;
import fr.arcadia.arcadiapatchcreate.runtime.BehaviourDispatchSupport;
import java.util.Map;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Removes the {@code Consumer} indirection from the hottest loop in Create.
 *
 * <p>{@code SmartBlockEntity.tick()} ends with {@code forEachBehaviour(BlockEntityBehaviour::tick)},
 * which resolves the behaviour collection, hands it a lambda, and dispatches every behaviour through
 * {@code Consumer.accept} before reaching {@code tick}. Profiling a production server attributed
 * roughly 1.55 ms/tick to that scaffolding alone - {@code forEachBehaviour} 0.733,
 * {@code SmartBlockEntity$$Lambda#accept} 0.532 and the fastutil values iterator 0.287 - across
 * thousands of block entities, for no gameplay work at all.
 *
 * <p>Iterating the same map directly and calling {@code tick()} on each behaviour runs the exact same
 * behaviours, in the same order, the same number of times. Only the plumbing disappears.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.SmartBlockEntity", remap = false)
public abstract class MixinSmartBlockEntity {

    @Shadow
    private Map<?, ?> behaviours;

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;"
                + "forEachBehaviour(Ljava/util/function/Consumer;)V"
        ),
        require = 0,
        remap = false
    )
    private void arcadiaPatchCreate$tickBehavioursDirectly(
        Object self,
        Consumer<?> action,
        Operation<Void> original
    ) {
        Map<?, ?> registered = behaviours;
        if (registered == null || registered.isEmpty() || !BehaviourDispatchSupport.canDispatch(registered)) {
            original.call(self, action);
            return;
        }

        // canDispatch() proved every value is bridged, so no cast can fail midway and
        // leave part of the collection ticked twice.
        for (Object behaviour : registered.values()) {
            ((BlockEntityBehaviourBridge) behaviour).arcadiaPatchCreate$tickBehaviour();
        }
        BehaviourDispatchSupport.recordDispatch();
    }
}
