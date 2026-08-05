package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.runtime.HeatJsContextCache;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.xiaohunao.create_heat_js.common.HeatRecipeContext", remap = false)
public abstract class MixinHeatRecipeContext {

    @Inject(
        method = "of(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/Recipe;)Lcom/xiaohunao/create_heat_js/common/HeatRecipeContext;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void arcadiaPatchCreate$useCachedMetadata(
        Level level,
        Recipe<?> recipe,
        CallbackInfoReturnable<Object> cir
    ) {
        Object context = HeatJsContextCache.createContext(level, recipe);
        if (context != null) {
            cir.setReturnValue(context);
        }
    }
}
