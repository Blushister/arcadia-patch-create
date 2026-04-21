package fr.arcadia.arcadiapatchcreate.runtime;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ChuteThrottleSupport {

    private static final ChuteInspector INSPECTOR = ChuteInspector.resolve();

    private ChuteThrottleSupport() {
    }

    public static boolean canInspect() {
        return INSPECTOR.available();
    }

    public static boolean shouldSkipInputProbe(Object chute, int cooldown) {
        if (!PatchRuntime.isChutePatchEnabled()) {
            return false;
        }
        if (!PatchRuntime.isThrottleActive()) {
            return false;
        }

        try {
            Level level = INSPECTOR.getLevel(chute);
            if (level == null || level.isClientSide) {
                return false;
            }

            ItemStack item = INSPECTOR.getItem(chute);
            if (item != null && !item.isEmpty()) {
                return false;
            }

            float itemMotion = INSPECTOR.getItemMotion(chute);
            if (itemMotion == 0.0F) {
                return false;
            }

            MinecraftServer server = level.getServer();
            int interval = PatchRuntime.resolveGlobalInterval(server);
            if (interval <= 1) {
                return false;
            }

            return cooldown + 1 < interval;
        } catch (ReflectiveOperationException e) {
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] Could not inspect Create chute state. Falling back to Create logic.",
                e
            );
            return false;
        }
    }

    private record ChuteInspector(Field levelField, Field itemField, Method getItemMotionMethod) {

        static ChuteInspector resolve() {
            try {
                Class<?> chuteClass = Class.forName(
                    "com.simibubi.create.content.logistics.chute.ChuteBlockEntity",
                    false,
                    ChuteThrottleSupport.class.getClassLoader()
                );
                Field level = net.minecraft.world.level.block.entity.BlockEntity.class.getDeclaredField("level");
                level.setAccessible(true);
                Field item = chuteClass.getDeclaredField("item");
                item.setAccessible(true);
                Method getItemMotion = chuteClass.getDeclaredMethod("getItemMotion");
                getItemMotion.setAccessible(true);
                return new ChuteInspector(level, item, getItemMotion);
            } catch (ReflectiveOperationException e) {
                ArcadiaPatchCreate.LOGGER.warn(
                    "[ArcadiaPatchCreate] Could not resolve Create chute members. Chute throttle will stay disabled.",
                    e
                );
                return new ChuteInspector(null, null, null);
            }
        }

        boolean available() {
            return levelField != null && itemField != null && getItemMotionMethod != null;
        }

        Level getLevel(Object chute) throws ReflectiveOperationException {
            return (Level) levelField.get(chute);
        }

        ItemStack getItem(Object chute) throws ReflectiveOperationException {
            return (ItemStack) itemField.get(chute);
        }

        float getItemMotion(Object chute) throws ReflectiveOperationException {
            return ((Number) getItemMotionMethod.invoke(chute)).floatValue();
        }
    }
}
