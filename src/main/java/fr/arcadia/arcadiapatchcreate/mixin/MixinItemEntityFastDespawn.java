package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.runtime.CreatePhysicalItemSupport;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class MixinItemEntityFastDespawn {

    @Shadow
    private int age;

    @Inject(method = "tick", at = @At("TAIL"))
    private void arcadiaPatchCreate$discardMarkedCreateDropsEarlier(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!CreatePhysicalItemSupport.hasFastDespawn(self)) {
            return;
        }

        long deadline = CreatePhysicalItemSupport.getEffectiveDeadline(self, self.level().getGameTime(), age);
        if (self.level().getGameTime() >= deadline) {
            self.discard();
        }
    }

    @Inject(method = "tryToMerge", at = @At("TAIL"))
    private void arcadiaPatchCreate$preserveEarliestCreateDropDeadline(ItemEntity other, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!CreatePhysicalItemSupport.hasFastDespawn(self) && !CreatePhysicalItemSupport.hasFastDespawn(other)) {
            return;
        }
        if (self.level().isClientSide) {
            return;
        }
        if (self.isRemoved() == other.isRemoved()) {
            return;
        }

        long gameTime = self.level().getGameTime();
        long selfDeadline = CreatePhysicalItemSupport.hasFastDespawn(self)
            ? CreatePhysicalItemSupport.getEffectiveDeadline(self, gameTime, self.getAge())
            : Long.MAX_VALUE;
        long otherDeadline = CreatePhysicalItemSupport.hasFastDespawn(other)
            ? CreatePhysicalItemSupport.getEffectiveDeadline(other, gameTime, other.getAge())
            : Long.MAX_VALUE;
        long earliestDeadline = Math.min(selfDeadline, otherDeadline);
        if (earliestDeadline == Long.MAX_VALUE) {
            return;
        }

        int selfTicks = CreatePhysicalItemSupport.hasFastDespawn(self)
            ? CreatePhysicalItemSupport.getFastDespawnTicks(self)
            : Integer.MAX_VALUE;
        int otherTicks = CreatePhysicalItemSupport.hasFastDespawn(other)
            ? CreatePhysicalItemSupport.getFastDespawnTicks(other)
            : Integer.MAX_VALUE;
        int shortestTicks = Math.min(selfTicks, otherTicks);
        if (shortestTicks == Integer.MAX_VALUE) {
            shortestTicks = CreatePhysicalItemSupport.getFastDespawnTicks(self);
        }

        ItemEntity survivor = self.isRemoved() ? other : self;
        CreatePhysicalItemSupport.setFastDespawnData(survivor, shortestTicks, earliestDeadline);
    }
}
