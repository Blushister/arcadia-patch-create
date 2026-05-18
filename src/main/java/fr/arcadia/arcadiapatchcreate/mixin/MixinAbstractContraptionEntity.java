package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.AbstractContraptionEntity", remap = false)
public abstract class MixinAbstractContraptionEntity {

    private static final long UNREADABLE_LOG_INTERVAL = 128L;
    private static final Method CONTRAPTION_FROM_NBT = resolveContraptionFromNbt();
    private static final AtomicLong UNREADABLE_CONTRAPTIONS = new AtomicLong();

    @Inject(
        method = "readAdditional",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void arcadiaPatchCreate$readAdditionalSafely(CompoundTag compound, boolean spawnData, CallbackInfo ci) {
        ci.cancel();
        if (compound.isEmpty()) {
            return;
        }

        Entity self = (Entity) (Object) this;
        Object contraption = readContraptionSafely(self.level(), compound.getCompound("Contraption"), spawnData);
        if (contraption == null) {
            logUnreadableContraption("Discarding Create contraption entity with unreadable NBT", null, null);
            self.discard();
            return;
        }

        try {
            Field contraptionField = findField(self.getClass(), "contraption");
            contraptionField.setAccessible(true);
            contraptionField.set(this, contraption);

            Field entityField = findField(contraption.getClass(), "entity");
            entityField.setAccessible(true);
            entityField.set(contraption, this);

            Field stalledField = findField(self.getClass(), "STALLED");
            stalledField.setAccessible(true);
            @SuppressWarnings("unchecked")
            EntityDataAccessor<Boolean> stalledAccessor = (EntityDataAccessor<Boolean>) stalledField.get(null);
            self.getEntityData().set(stalledAccessor, compound.getBoolean("Stalled"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            logUnreadableContraption("Create contraption entity read assignment failed", null, e);
            self.discard();
        }
    }

    private static Object readContraptionSafely(Level world, CompoundTag nbt, boolean spawnData) {
        if (CONTRAPTION_FROM_NBT == null) {
            logUnreadableContraption("Could not resolve Create Contraption.fromNBT", nbt, null);
            return null;
        }

        try {
            return CONTRAPTION_FROM_NBT.invoke(null, world, nbt, spawnData);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            logUnreadableContraption("Create contraption NBT failed to deserialize", nbt, cause);
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logUnreadableContraption("Create contraption NBT could not be deserialized", nbt, e);
            return null;
        }
    }

    private static Method resolveContraptionFromNbt() {
        try {
            Class<?> contraptionClass = Class.forName(
                "com.simibubi.create.content.contraptions.Contraption",
                false,
                MixinAbstractContraptionEntity.class.getClassLoader()
            );
            return contraptionClass.getMethod("fromNBT", Level.class, CompoundTag.class, boolean.class);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void logUnreadableContraption(String message, CompoundTag nbt, Throwable throwable) {
        long failures = UNREADABLE_CONTRAPTIONS.incrementAndGet();
        if (failures > 3 && failures % UNREADABLE_LOG_INTERVAL != 0) {
            return;
        }

        String type = nbt == null ? "unknown" : nbt.getString("Type");
        if (throwable == null) {
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] {}. type={} failures={}",
                message,
                type,
                failures
            );
            return;
        }

        ArcadiaPatchCreate.LOGGER.warn(
            "[ArcadiaPatchCreate] {}. type={} failures={}",
            message,
            type,
            failures,
            throwable
        );
    }
}
