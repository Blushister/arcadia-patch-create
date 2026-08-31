package fr.arcadia.arcadiapatchcreate.diagnostic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

/**
 * Times one block entity, and every loaded block of the same type, over a short window.
 *
 * <p>Measuring the target alone would say nothing: 118 us means one thing for a lone reactor
 * controller and another for a crafter whose 542 peers sit at 1 us. So the same window also times
 * the peers, and the report compares the target against their median.
 *
 * <p>The hot path stays a single static boolean read. When no analysis runs, every block entity
 * pays that check and nothing more - far cheaper than instrumenting the whole world permanently.
 * A single tick is meaningless, so the probe averages over {@link #SAMPLE_TICKS}.
 */
public final class DiagnosticProbe {

    /** Roughly five seconds, enough for the averages to settle. */
    public static final int SAMPLE_TICKS = 100;

    /** Beyond this, timing every peer would cost more than the problem being diagnosed. */
    private static final int MAX_PEERS = 512;

    private static volatile boolean active;

    private static Level level;
    private static BlockPos pos;
    private static String typeName;

    private static long targetNanos;
    private static int targetSamples;

    private static final Map<Object, long[]> PEERS = new IdentityHashMap<>();

    private static long enteredAt;
    private static int depth;
    private static Object measuring;

    private DiagnosticProbe() {
    }

    /** Hot path: one static read per block entity per tick. */
    public static boolean isActive() {
        return active;
    }

    public static synchronized void start(Level targetLevel, BlockPos targetPos, String targetTypeName) {
        level = targetLevel;
        pos = targetPos.immutable();
        typeName = targetTypeName;
        targetNanos = 0L;
        targetSamples = 0;
        PEERS.clear();
        depth = 0;
        measuring = null;
        active = true;
    }

    public static synchronized void stop() {
        active = false;
        level = null;
        pos = null;
        typeName = null;
        depth = 0;
        measuring = null;
    }

    public static void enter(TickingBlockEntity ticking) {
        if (!active || depth != 0 || ticking == null || ticking.isRemoved()) {
            return;
        }
        boolean isTarget = pos.equals(ticking.getPos());
        if (!isTarget) {
            if (!java.util.Objects.equals(ticking.getType(), typeName)) {
                return;
            }
            if (PEERS.size() >= MAX_PEERS && !PEERS.containsKey(ticking)) {
                return;
            }
        }
        depth = 1;
        measuring = ticking;
        enteredAt = System.nanoTime();
    }

    public static void exit(TickingBlockEntity ticking) {
        if (!active || depth != 1 || ticking != measuring) {
            return;
        }
        long elapsed = System.nanoTime() - enteredAt;
        depth = 0;
        measuring = null;

        if (pos.equals(ticking.getPos())) {
            targetNanos += elapsed;
            targetSamples++;
            return;
        }
        long[] slot = PEERS.computeIfAbsent(ticking, key -> new long[2]);
        slot[0] += elapsed;
        slot[1]++;
    }

    public static int samples() {
        return targetSamples;
    }

    /** Average microseconds per tick for the analysed block, or -1 when it never ticked. */
    public static double averageMicros() {
        int taken = targetSamples;
        return taken == 0 ? -1.0D : (targetNanos / (double) taken) / 1000.0D;
    }

    public static int peerCount() {
        return PEERS.size();
    }

    /** Median cost of the loaded blocks of the same type, in microseconds. */
    public static synchronized double peerMedianMicros() {
        List<Double> costs = new ArrayList<>(PEERS.size());
        for (long[] slot : PEERS.values()) {
            if (slot[1] > 0L) {
                costs.add((slot[0] / (double) slot[1]) / 1000.0D);
            }
        }
        if (costs.isEmpty()) {
            return -1.0D;
        }
        costs.sort(Double::compareTo);
        return costs.get(costs.size() / 2);
    }
}
