package fr.arcadia.arcadiapatchcreate.runtime;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import fr.arcadia.arcadiapatchcreate.bridge.FluidTransportBehaviourBridge;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;

/**
 * Swaps the pipe connection map for an {@link EnumMap} right after Create builds it.
 *
 * <p>Create stores the connections in an {@code IdentityHashMap<Direction, PipeConnection>}
 * holding at most six entries, while the backing table is far larger. Its iterator has to
 * walk every empty slot looking for the next entry, and {@code tick()} iterates the map
 * four times per pipe per tick. An {@code EnumMap} walks a six element array instead.
 *
 * <p>Substitution is safe because {@code Direction} is an enum: identity and equality
 * coincide, so lookups, {@code computeIfAbsent} and {@code remove} behave identically.
 * The one observable difference is iteration order, which becomes the stable ordinal order
 * instead of an order derived from object addresses - Create never relied on the previous
 * order, which was not deterministic to begin with.
 *
 * <p>Conversion happens only when Create creates the map, never per tick, and anything
 * unexpected leaves the original map untouched.
 */
public final class FluidInterfaceMapSupport {

    private FluidInterfaceMapSupport() {
    }

    public static void compact(Object behaviour) {
        if (!PatchRuntime.isFluidPatchEnabled() || !(behaviour instanceof FluidTransportBehaviourBridge bridge)) {
            return;
        }

        Map<?, ?> current;
        try {
            current = bridge.arcadiaPatchCreate$getInterfaces();
        } catch (RuntimeException | LinkageError exception) {
            recordFailure("Could not read the Create pipe connection map");
            return;
        }
        if (current == null || current instanceof EnumMap) {
            return;
        }

        try {
            EnumMap<Direction, Object> compacted = new EnumMap<>(Direction.class);
            for (Map.Entry<?, ?> entry : current.entrySet()) {
                if (!(entry.getKey() instanceof Direction direction) || entry.getValue() == null) {
                    // An unexpected key or a null value means this map is not what we
                    // fingerprinted. Keep Create's own instance untouched.
                    return;
                }
                compacted.put(direction, entry.getValue());
            }
            bridge.arcadiaPatchCreate$setInterfaces(compacted);
            PatchRuntime.incrementFluidMapCompactions();
        } catch (RuntimeException | LinkageError exception) {
            recordFailure("Could not compact the Create pipe connection map");
        }
    }

    private static void recordFailure(String message) {
        long failures = PatchRuntime.incrementFluidInspectionFailures();
        if (failures <= 3 || failures % 1_024 == 0) {
            ArcadiaPatchCreate.LOGGER.warn("[ArcadiaPatchCreate] {}. Keeping Create's original map.", message);
        }
    }
}
