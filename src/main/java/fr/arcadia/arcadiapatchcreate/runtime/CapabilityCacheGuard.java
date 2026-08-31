package fr.arcadia.arcadiapatchcreate.runtime;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;

/**
 * Keeps a stale capability cache from taking the whole server down.
 *
 * <p>NeoForge deliberately throws when {@code getCapability()} is called on a cache that is being
 * invalidated, and Create can reach that state from {@code FluidNetwork.tick}. Create 6.0.10 guards
 * it with {@code inner == null || invalid}, but the window between that test and the call is not
 * closed: a production server crashed twice on it in one night, in
 * {@code FluidNetwork.tick -> PipeConnection.manageFlows -> FluidTransportBehaviour.tick}.
 *
 * <p>Only that exact failure is swallowed, and only when it really comes from
 * {@code BlockCapabilityCache}. Anything else is rethrown untouched, so no unrelated bug is ever
 * hidden. Returning null is what Create already handles as "no capability available right now" -
 * the transfer simply resumes on a later tick instead of the server dying.
 */
public final class CapabilityCacheGuard {

    private static final String INVALID_CACHE_MESSAGE =
        "Do not call getCapability on an invalid cache or from the invalidation listener!";
    private static final String CACHE_CLASS =
        "net.neoforged.neoforge.capabilities.BlockCapabilityCache";

    private CapabilityCacheGuard() {
    }

    /**
     * @return true when this exception is the known stale-cache case and can safely be turned into
     *         a null capability; false when it must be rethrown.
     */
    public static boolean isStaleCacheAccess(IllegalStateException exception) {
        if (!PatchRuntime.isCapabilityGuardEnabled()) {
            return false;
        }
        if (!INVALID_CACHE_MESSAGE.equals(exception.getMessage())) {
            return false;
        }
        // Match the origin too: the message alone could in principle come from elsewhere.
        StackTraceElement[] trace = exception.getStackTrace();
        if (trace.length == 0 || !CACHE_CLASS.equals(trace[0].getClassName())) {
            return false;
        }

        long caught = PatchRuntime.incrementCapabilityGuardCatches();
        if (caught <= 3 || caught % 1_024 == 0) {
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] Prevented a Create crash on an invalidated capability cache "
                    + "({} so far). The capability is reported as unavailable for this tick.",
                caught
            );
        }
        return true;
    }
}
