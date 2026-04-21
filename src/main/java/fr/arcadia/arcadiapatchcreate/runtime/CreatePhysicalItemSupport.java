package fr.arcadia.arcadiapatchcreate.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

public final class CreatePhysicalItemSupport {

    private static final String CREATE_PACKAGE_PREFIX = "com.simibubi.create.";
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public static final String FAST_DESPAWN_MARKER = "ArcadiaPatchCreateFastDespawn";
    public static final String FAST_DESPAWN_TICKS = "ArcadiaPatchCreateFastDespawnTicks";
    public static final String FAST_DESPAWN_DEADLINE = "ArcadiaPatchCreateFastDespawnDeadline";

    private CreatePhysicalItemSupport() {
    }

    public static boolean shouldMark(Entity entity) {
        if (!(entity instanceof ItemEntity)) {
            return false;
        }
        if (!PatchRuntime.isCreatePhysicalItemsFastDespawnEnabled()) {
            return false;
        }

        return STACK_WALKER.walk(stream -> stream
            .limit(24)
            .map(frame -> frame.getDeclaringClass().getName())
            .anyMatch(className -> className.startsWith(CREATE_PACKAGE_PREFIX)));
    }

    public static void mark(Entity entity) {
        int ticks = PatchRuntime.getCreatePhysicalItemsDespawnTicks();
        long deadline = entity.level().getGameTime() + ticks;
        setFastDespawnData(entity, ticks, deadline);
        PatchRuntime.incrementCreatePhysicalItemMarks();
    }

    public static boolean hasFastDespawn(Entity entity) {
        return entity.getPersistentData().getBoolean(FAST_DESPAWN_MARKER);
    }

    public static int getFastDespawnTicks(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.contains(FAST_DESPAWN_TICKS)) {
            return data.getInt(FAST_DESPAWN_TICKS);
        }
        return PatchRuntime.getCreatePhysicalItemsDespawnTicks();
    }

    public static long getFastDespawnDeadline(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.contains(FAST_DESPAWN_DEADLINE)) {
            return data.getLong(FAST_DESPAWN_DEADLINE);
        }
        return -1L;
    }

    public static long getEffectiveDeadline(Entity entity, long gameTime, int itemAge) {
        long deadline = getFastDespawnDeadline(entity);
        if (deadline >= 0L) {
            return deadline;
        }

        int ticks = getFastDespawnTicks(entity);
        return gameTime + Math.max(0, ticks - itemAge);
    }

    public static void setFastDespawnData(Entity entity, int ticks, long deadline) {
        CompoundTag data = entity.getPersistentData();
        data.putBoolean(FAST_DESPAWN_MARKER, true);
        data.putInt(FAST_DESPAWN_TICKS, ticks);
        data.putLong(FAST_DESPAWN_DEADLINE, deadline);
    }
}
