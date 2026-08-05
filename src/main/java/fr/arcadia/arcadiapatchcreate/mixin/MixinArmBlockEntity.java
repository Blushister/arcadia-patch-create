package fr.arcadia.arcadiapatchcreate.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.arcadia.arcadiapatchcreate.runtime.ArmInsertionReuse;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity", remap = false)
public abstract class MixinArmBlockEntity {

    @Unique
    private ArmInsertionReuse.Frame arcadiaPatchCreate$simulationFrame;

    /**
     * Opens a memoization window for the duration of one search pass. Create probes every
     * slot of every input point here, so identical stacks would otherwise re-run the same
     * output simulation. The frame is closed in a finally block, and reopening resets it.
     */
    @WrapMethod(method = "searchForItem", require = 0, remap = false)
    private void arcadiaPatchCreate$guardSimulationFrame(Operation<Void> original) {
        ArmInsertionReuse.Frame frame = arcadiaPatchCreate$simulationFrame;
        if (frame == null) {
            frame = ArmInsertionReuse.createFrame();
            arcadiaPatchCreate$simulationFrame = frame;
        }
        ArmInsertionReuse.open(frame);
        try {
            original.call();
        } finally {
            ArmInsertionReuse.close(frame);
        }
    }

    /**
     * Returns the remainder already computed for an identical stack in this pass.
     * {@code simulateInsertion} only performs simulated inserts, so the result depends
     * solely on the stack and on the output points, neither of which changes mid-pass.
     */
    @WrapMethod(method = "simulateInsertion", require = 0, remap = false)
    private ItemStack arcadiaPatchCreate$reuseSimulation(ItemStack stack, Operation<ItemStack> original) {
        ArmInsertionReuse.Frame frame = arcadiaPatchCreate$simulationFrame;
        ItemStack memoized = ArmInsertionReuse.lookup(frame, stack);
        if (memoized != null) {
            return memoized;
        }
        ItemStack key = ArmInsertionReuse.snapshotKey(frame, stack);
        ItemStack remainder = original.call(stack);
        ArmInsertionReuse.store(frame, key, remainder);
        return remainder;
    }
}
