package fr.arcadia.arcadiapatchcreate.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.arcadia.arcadiapatchcreate.runtime.ItemDrainLookupReuse;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.transfer.GenericItemEmptying", remap = false)
public abstract class MixinGenericItemEmptying {

    private static final String FIND_RECIPE_TARGET =
        "Lcom/simibubi/create/AllRecipeTypes;find(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;";

    @WrapMethod(method = "canItemBeEmptied", require = 0, remap = false)
    private static boolean arcadiaPatchCreate$trackCanLookupDepth(
        Level level,
        ItemStack stack,
        Operation<Boolean> original
    ) {
        Object token = ItemDrainLookupReuse.enterCanLookup(level, stack);
        try {
            return original.call(level, stack);
        } finally {
            ItemDrainLookupReuse.exitCanLookup(token);
        }
    }

    @WrapOperation(
        method = "canItemBeEmptied",
        at = @At(value = "INVOKE", target = FIND_RECIPE_TARGET),
        require = 0,
        remap = false
    )
    private static Optional<?> arcadiaPatchCreate$capturePositiveLookup(
        @Coerce Object recipeType,
        RecipeInput input,
        Level lookupLevel,
        Operation<Optional<?>> original,
        Level level,
        ItemStack stack
    ) {
        Optional<?> result = original.call(recipeType, input, lookupLevel);
        ItemDrainLookupReuse.capturePositiveStandard(level, stack, result);
        return result;
    }

    @WrapOperation(
        method = "emptyItem",
        at = @At(value = "INVOKE", target = FIND_RECIPE_TARGET),
        require = 0,
        remap = false
    )
    private static Optional<?> arcadiaPatchCreate$reuseImmediateSimulationLookup(
        @Coerce Object recipeType,
        RecipeInput input,
        Level lookupLevel,
        Operation<Optional<?>> original,
        Level level,
        ItemStack stack,
        boolean simulate
    ) {
        Optional<?> reused = ItemDrainLookupReuse.takeForSimulation(level, stack, simulate);
        if (reused != null) {
            return reused;
        }
        return original.call(recipeType, input, lookupLevel);
    }
}
