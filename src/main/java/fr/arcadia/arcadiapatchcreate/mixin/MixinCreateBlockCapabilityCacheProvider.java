package fr.arcadia.arcadiapatchcreate.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.arcadia.arcadiapatchcreate.runtime.CapabilityCacheGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Turns a fatal capability cache access into a missing capability.
 *
 * <p>See {@link CapabilityCacheGuard} for why Create's own guard is not enough. Only the known
 * stale-cache failure is absorbed; every other exception propagates unchanged.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.ICapabilityProvider$BlockCapabilityCacheProvider", remap = false)
public abstract class MixinCreateBlockCapabilityCacheProvider {

    @WrapMethod(method = "getCapability", require = 0, remap = false)
    private Object arcadiaPatchCreate$guardInvalidCache(Operation<Object> original) {
        try {
            return original.call();
        } catch (IllegalStateException exception) {
            if (!CapabilityCacheGuard.isStaleCacheAccess(exception)) {
                throw exception;
            }
            return null;
        }
    }
}
