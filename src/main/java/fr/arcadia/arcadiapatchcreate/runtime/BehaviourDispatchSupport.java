package fr.arcadia.arcadiapatchcreate.runtime;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import fr.arcadia.arcadiapatchcreate.bridge.BlockEntityBehaviourBridge;
import java.util.Map;

/**
 * Decides whether Create's behaviour collection can be ticked directly instead of
 * through {@code forEachBehaviour(Consumer)}.
 *
 * <p>Whether the bridge is present is a global property: the mixin is applied to
 * {@code BlockEntityBehaviour}, the common base class of every behaviour. It is therefore
 * verified once, on the first collection seen, rather than on every element of every tick.
 * If that check ever fails, the fast path stays off for the rest of the session and Create's
 * original dispatch is used everywhere - a partially ticked collection is never possible.
 */
public final class BehaviourDispatchSupport {

    private static volatile boolean bridgeChecked;
    private static volatile boolean bridgeUsable;

    private BehaviourDispatchSupport() {
    }

    public static boolean canDispatch(Map<?, ?> behaviours) {
        if (!PatchRuntime.isBehaviourDispatchPatchEnabled()) {
            return false;
        }
        if (!bridgeChecked) {
            verifyBridge(behaviours);
        }
        return bridgeUsable;
    }

    public static void recordDispatch() {
        PatchRuntime.incrementBehaviourDispatches();
    }

    private static synchronized void verifyBridge(Map<?, ?> behaviours) {
        if (bridgeChecked) {
            return;
        }
        boolean usable = true;
        try {
            for (Object behaviour : behaviours.values()) {
                if (!(behaviour instanceof BlockEntityBehaviourBridge)) {
                    usable = false;
                    break;
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            usable = false;
        }

        bridgeUsable = usable;
        bridgeChecked = true;
        if (!usable) {
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] Create behaviours are not bridged. "
                    + "Keeping the original behaviour dispatch."
            );
        }
    }
}
