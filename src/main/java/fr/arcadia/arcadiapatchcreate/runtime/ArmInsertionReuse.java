package fr.arcadia.arcadiapatchcreate.runtime;

import net.minecraft.world.item.ItemStack;

/**
 * Reuses the result of a Mechanical Arm output simulation inside a single
 * {@code searchForItem()} pass.
 *
 * <p>Create probes every slot of every input point and, for each one, simulates the
 * insertion against every output point. Two slots holding the same item and the same
 * count therefore run the exact same simulation twice. {@code simulateInsertion} is a
 * pure dry run - it only calls {@code insert(..., true)} and mutates nothing - so
 * returning a previously computed remainder is strictly equivalent.
 *
 * <p>The cache never survives the enclosing call: it is cleared when the frame closes,
 * and reopening a frame resets it even if the previous close was skipped by an
 * exception. Nothing is ever reused across ticks.
 */
public final class ArmInsertionReuse {

    /** Beyond this many distinct stacks the inventory is too heterogeneous to benefit. */
    private static final int MAX_ENTRIES = 16;

    private ArmInsertionReuse() {
    }

    public static Frame createFrame() {
        return new Frame();
    }

    /** Resets and opens the frame; a skipped close can never leak into the next pass. */
    public static void open(Frame frame) {
        if (frame == null) {
            return;
        }
        clear(frame);
        frame.open = PatchRuntime.isArmPatchEnabled();
    }

    public static void close(Frame frame) {
        if (frame == null) {
            return;
        }
        frame.open = false;
        clear(frame);
    }

    /** Returns a private copy of the memoized remainder, or {@code null} to run Create's simulation. */
    public static ItemStack lookup(Frame frame, ItemStack stack) {
        if (frame == null || !frame.open || stack == null || !PatchRuntime.isArmPatchEnabled()) {
            return null;
        }
        try {
            for (int i = 0; i < frame.size; i++) {
                ItemStack key = frame.keys[i];
                if (key.getCount() == stack.getCount() && ItemStack.isSameItemSameComponents(key, stack)) {
                    PatchRuntime.incrementArmSimulationReuses();
                    return frame.values[i].copy();
                }
            }
        } catch (RuntimeException exception) {
            close(frame);
            PatchRuntime.incrementArmFallbacks();
            return null;
        }
        return null;
    }

    /**
     * Snapshots the lookup key before Create's simulation runs, so a stack mutated by a
     * third-party output point can never be memoized under the wrong key. Returns
     * {@code null} when this pass will not memoize anything.
     */
    public static ItemStack snapshotKey(Frame frame, ItemStack stack) {
        if (frame == null
            || !frame.open
            || stack == null
            || frame.size >= MAX_ENTRIES
            || !PatchRuntime.isArmPatchEnabled()) {
            return null;
        }
        try {
            return stack.copy();
        } catch (RuntimeException exception) {
            close(frame);
            PatchRuntime.incrementArmFallbacks();
            return null;
        }
    }

    /** Memoizes a freshly computed remainder against a key captured before the call.
     *  Duplicate keys are ignored, so a reused result can never overwrite its own entry. */
    public static void store(Frame frame, ItemStack key, ItemStack remainder) {
        if (frame == null
            || !frame.open
            || key == null
            || remainder == null
            || frame.size >= MAX_ENTRIES
            || !PatchRuntime.isArmPatchEnabled()) {
            return;
        }
        try {
            for (int i = 0; i < frame.size; i++) {
                ItemStack existing = frame.keys[i];
                if (existing.getCount() == key.getCount() && ItemStack.isSameItemSameComponents(existing, key)) {
                    return;
                }
            }
            frame.keys[frame.size] = key;
            frame.values[frame.size] = remainder.copy();
            frame.size++;
            PatchRuntime.incrementArmSimulationCaptures();
        } catch (RuntimeException exception) {
            close(frame);
            PatchRuntime.incrementArmFallbacks();
        }
    }

    private static void clear(Frame frame) {
        for (int i = 0; i < frame.size; i++) {
            frame.keys[i] = null;
            frame.values[i] = null;
        }
        frame.size = 0;
    }

    /** Per-arm scratch space. The server ticks block entities on a single thread and
     *  {@code searchForItem} is not reentrant, so no synchronization is required. */
    public static final class Frame {

        private final ItemStack[] keys = new ItemStack[MAX_ENTRIES];
        private final ItemStack[] values = new ItemStack[MAX_ENTRIES];
        private int size;
        private boolean open;

        private Frame() {
        }
    }
}
