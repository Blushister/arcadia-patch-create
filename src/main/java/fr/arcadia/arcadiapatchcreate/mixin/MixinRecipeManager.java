package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.runtime.HeatJsContextCache;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecipeManager.class, priority = 1_100)
public abstract class MixinRecipeManager {

    @Inject(method = "apply", at = @At("TAIL"), require = 0)
    private void arcadiaPatchCreate$refreshHeatJsAfterApply(CallbackInfo ci) {
        HeatJsContextCache.refresh((RecipeManager) (Object) this);
    }

    @Inject(method = "replaceRecipes", at = @At("TAIL"), require = 0)
    private void arcadiaPatchCreate$refreshHeatJsAfterReplaceRecipes(CallbackInfo ci) {
        HeatJsContextCache.refresh((RecipeManager) (Object) this);
    }
}
