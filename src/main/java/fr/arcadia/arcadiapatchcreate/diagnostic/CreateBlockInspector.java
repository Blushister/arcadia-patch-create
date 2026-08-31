package fr.arcadia.arcadiapatchcreate.diagnostic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Describes what a Create machine is actually doing, which is what turns a number into a cause.
 *
 * <p>A machine sitting in the open, even powered, is almost never expensive. When one is, something
 * specific explains it: a blocked output being retried every tick, a network recomputed for nothing,
 * an addon scanning every recipe. That context is what lets a moderator report something useful
 * instead of a colour.
 *
 * <p>Everything here is read through Create's common base classes, never per machine type, so it
 * keeps working across machines and degrades to silence rather than to a wrong answer.
 */
public final class CreateBlockInspector {

    private static final String SMART_BLOCK_ENTITY =
        "com.simibubi.create.foundation.blockEntity.SmartBlockEntity";
    private static final String KINETIC_BLOCK_ENTITY =
        "com.simibubi.create.content.kinetics.base.KineticBlockEntity";

    private static volatile Class<?> smartClass;
    private static volatile Class<?> kineticClass;
    private static volatile boolean resolved;

    private CreateBlockInspector() {
    }

    public static boolean isCreateBlockEntity(BlockEntity blockEntity) {
        resolve();
        Class<?> smart = smartClass;
        return smart != null && smart.isInstance(blockEntity);
    }

    /** Adds whatever context can be read generically. Silent when nothing is available. */
    public static void describe(ServerPlayer player, BlockEntity blockEntity) {
        resolve();
        Class<?> kinetic = kineticClass;
        if (kinetic == null || !kinetic.isInstance(blockEntity)) {
            return;
        }
        try {
            float speed = (float) kinetic.getMethod("getSpeed").invoke(blockEntity);
            boolean overStressed = (boolean) kinetic.getMethod("isOverStressed").invoke(blockEntity);
            String state;
            if (overStressed) {
                state = "EN SURCHARGE — le réseau ne fournit pas assez de force";
            } else if (speed == 0.0F) {
                state = "à l'arrêt — aucune rotation";
            } else {
                state = String.format(java.util.Locale.ROOT, "%.0f RPM", Math.abs(speed));
            }
            field(player, "Cinétique", state);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Create changed shape: stay silent rather than report something wrong.
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (CreateBlockInspector.class) {
            if (resolved) {
                return;
            }
            smartClass = findClass(SMART_BLOCK_ENTITY);
            kineticClass = findClass(KINETIC_BLOCK_ENTITY);
            resolved = true;
        }
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, CreateBlockInspector.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    private static void field(ServerPlayer player, String label, String value) {
        player.sendSystemMessage(Component.literal("  " + label + " : ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE)));
    }
}
