package fr.arcadia.arcadiapatchcreate.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.arcadia.arcadiapatchcreate.runtime.ItemDrainLookupReuse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity", remap = false)
public abstract class MixinItemDrainBlockEntity {

    @WrapMethod(method = "continueProcessing", require = 0, remap = false)
    private boolean arcadiaPatchCreate$guardLookupFrame(Operation<Boolean> original) {
        Object frame = ItemDrainLookupReuse.enterFrame();
        if (frame == null) {
            return original.call();
        }
        try {
            return original.call();
        } finally {
            ItemDrainLookupReuse.exitFrame(frame);
        }
    }

    @WrapOperation(
        method = "continueProcessing",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/fluids/transfer/GenericItemEmptying;canItemBeEmptied(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Z"
        ),
        require = 0,
        remap = false
    )
    private boolean arcadiaPatchCreate$markEligibilityProbe(
        Level level,
        ItemStack stack,
        Operation<Boolean> original
    ) {
        Object probe = ItemDrainLookupReuse.beginProbe(level, stack);
        if (probe == null) {
            return original.call(level, stack);
        }
        try {
            boolean result = original.call(level, stack);
            ItemDrainLookupReuse.finishProbe(probe, result);
            return result;
        } catch (Throwable throwable) {
            ItemDrainLookupReuse.abortProbe(probe);
            throw throwable;
        }
    }
}
