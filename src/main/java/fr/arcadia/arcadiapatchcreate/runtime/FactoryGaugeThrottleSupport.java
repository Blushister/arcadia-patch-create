package fr.arcadia.arcadiapatchcreate.runtime;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class FactoryGaugeThrottleSupport {

    private static final Inspector INSPECTOR = Inspector.resolve();

    private FactoryGaugeThrottleSupport() {
    }

    public static boolean canInspect() {
        return INSPECTOR.available();
    }

    public static boolean shouldSkip(Object behaviour, int cooldown) {
        if (!INSPECTOR.available()) {
            return false;
        }

        try {
            Level world = INSPECTOR.getWorld(behaviour);
            if (world == null || world.isClientSide) {
                return false;
            }
            if (!INSPECTOR.isActive(behaviour)) {
                return false;
            }
            if (INSPECTOR.isRestocker(behaviour)) {
                return false;
            }
            if (INSPECTOR.getForceClearPromises(behaviour)) {
                return false;
            }
            if (INSPECTOR.getRedstonePowered(behaviour)) {
                return false;
            }
            if (INSPECTOR.getWaitingForNetwork(behaviour)) {
                return false;
            }
            if (INSPECTOR.getLastReportedUnloadedLinks(behaviour) != 0) {
                return false;
            }
            if (!INSPECTOR.getSatisfied(behaviour) || !INSPECTOR.getPromisedSatisfied(behaviour)) {
                return false;
            }
            if (INSPECTOR.getTimer(behaviour) <= 1) {
                return false;
            }

            Map<?, ?> targetedBy = INSPECTOR.getTargetedBy(behaviour);
            if (targetedBy == null || !targetedBy.isEmpty()) {
                return false;
            }

            Map<?, ?> targetedByLinks = INSPECTOR.getTargetedByLinks(behaviour);
            if (targetedByLinks == null || !targetedByLinks.isEmpty()) {
                return false;
            }

            ItemStack filter = INSPECTOR.getFilter(behaviour);
            if (filter == null || filter.isEmpty()) {
                return false;
            }

            int interval = PatchRuntime.resolveFactoryGaugeInterval(world.getServer());
            if (interval <= 1) {
                PatchRuntime.incrementFactoryGaugeForcedRuns();
                return false;
            }

            if (cooldown + 1 < interval) {
                PatchRuntime.incrementFactoryGaugeSkips();
                return true;
            }

            PatchRuntime.incrementFactoryGaugeForcedRuns();
            return false;
        } catch (ReflectiveOperationException e) {
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] Could not inspect Create Factory Gauge state. Falling back to Create logic.",
                e
            );
            return false;
        }
    }

    private static final class Inspector {

        private final Method getWorldMethod;
        private final Method isActiveMethod;
        private final Method panelBEMethod;
        private final Method getFilterMethod;
        private final Field restockerField;
        private final Field forceClearPromisesField;
        private final Field redstonePoweredField;
        private final Field waitingForNetworkField;
        private final Field lastReportedUnloadedLinksField;
        private final Field satisfiedField;
        private final Field promisedSatisfiedField;
        private final Field timerField;
        private final Field targetedByField;
        private final Field targetedByLinksField;

        private Inspector(
            Method getWorldMethod,
            Method isActiveMethod,
            Method panelBEMethod,
            Method getFilterMethod,
            Field restockerField,
            Field forceClearPromisesField,
            Field redstonePoweredField,
            Field waitingForNetworkField,
            Field lastReportedUnloadedLinksField,
            Field satisfiedField,
            Field promisedSatisfiedField,
            Field timerField,
            Field targetedByField,
            Field targetedByLinksField
        ) {
            this.getWorldMethod = getWorldMethod;
            this.isActiveMethod = isActiveMethod;
            this.panelBEMethod = panelBEMethod;
            this.getFilterMethod = getFilterMethod;
            this.restockerField = restockerField;
            this.forceClearPromisesField = forceClearPromisesField;
            this.redstonePoweredField = redstonePoweredField;
            this.waitingForNetworkField = waitingForNetworkField;
            this.lastReportedUnloadedLinksField = lastReportedUnloadedLinksField;
            this.satisfiedField = satisfiedField;
            this.promisedSatisfiedField = promisedSatisfiedField;
            this.timerField = timerField;
            this.targetedByField = targetedByField;
            this.targetedByLinksField = targetedByLinksField;
        }

        static Inspector resolve() {
            try {
                Class<?> behaviourClass = Class.forName(
                    "com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour",
                    false,
                    FactoryGaugeThrottleSupport.class.getClassLoader()
                );
                Class<?> blockEntityClass = Class.forName(
                    "com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity",
                    false,
                    FactoryGaugeThrottleSupport.class.getClassLoader()
                );

                return new Inspector(
                    behaviourClass.getMethod("getWorld"),
                    behaviourClass.getMethod("isActive"),
                    behaviourClass.getMethod("panelBE"),
                    behaviourClass.getMethod("getFilter"),
                    blockEntityClass.getField("restocker"),
                    declaredField(behaviourClass, "forceClearPromises"),
                    declaredField(behaviourClass, "redstonePowered"),
                    declaredField(behaviourClass, "waitingForNetwork"),
                    declaredField(behaviourClass, "lastReportedUnloadedLinks"),
                    declaredField(behaviourClass, "satisfied"),
                    declaredField(behaviourClass, "promisedSatisfied"),
                    declaredField(behaviourClass, "timer"),
                    declaredField(behaviourClass, "targetedBy"),
                    declaredField(behaviourClass, "targetedByLinks")
                );
            } catch (ReflectiveOperationException e) {
                ArcadiaPatchCreate.LOGGER.warn(
                    "[ArcadiaPatchCreate] Could not resolve Create Factory Gauge members. Factory Gauge throttling will stay disabled.",
                    e
                );
                return new Inspector(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            }
        }

        boolean available() {
            return getWorldMethod != null
                && isActiveMethod != null
                && panelBEMethod != null
                && getFilterMethod != null
                && restockerField != null
                && forceClearPromisesField != null
                && redstonePoweredField != null
                && waitingForNetworkField != null
                && lastReportedUnloadedLinksField != null
                && satisfiedField != null
                && promisedSatisfiedField != null
                && timerField != null
                && targetedByField != null
                && targetedByLinksField != null;
        }

        Level getWorld(Object behaviour) throws ReflectiveOperationException {
            return (Level) getWorldMethod.invoke(behaviour);
        }

        boolean isActive(Object behaviour) throws ReflectiveOperationException {
            return (Boolean) isActiveMethod.invoke(behaviour);
        }

        boolean isRestocker(Object behaviour) throws ReflectiveOperationException {
            return (Boolean) restockerField.get(panelBEMethod.invoke(behaviour));
        }

        boolean getForceClearPromises(Object behaviour) throws ReflectiveOperationException {
            return forceClearPromisesField.getBoolean(behaviour);
        }

        boolean getRedstonePowered(Object behaviour) throws ReflectiveOperationException {
            return redstonePoweredField.getBoolean(behaviour);
        }

        boolean getWaitingForNetwork(Object behaviour) throws ReflectiveOperationException {
            return waitingForNetworkField.getBoolean(behaviour);
        }

        int getLastReportedUnloadedLinks(Object behaviour) throws ReflectiveOperationException {
            return lastReportedUnloadedLinksField.getInt(behaviour);
        }

        boolean getSatisfied(Object behaviour) throws ReflectiveOperationException {
            return satisfiedField.getBoolean(behaviour);
        }

        boolean getPromisedSatisfied(Object behaviour) throws ReflectiveOperationException {
            return promisedSatisfiedField.getBoolean(behaviour);
        }

        int getTimer(Object behaviour) throws ReflectiveOperationException {
            return timerField.getInt(behaviour);
        }

        @SuppressWarnings("unchecked")
        Map<?, ?> getTargetedBy(Object behaviour) throws ReflectiveOperationException {
            return (Map<?, ?>) targetedByField.get(behaviour);
        }

        @SuppressWarnings("unchecked")
        Map<?, ?> getTargetedByLinks(Object behaviour) throws ReflectiveOperationException {
            return (Map<?, ?>) targetedByLinksField.get(behaviour);
        }

        ItemStack getFilter(Object behaviour) throws ReflectiveOperationException {
            return (ItemStack) getFilterMethod.invoke(behaviour);
        }

        private static Field declaredField(Class<?> owner, String name) throws NoSuchFieldException {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }
}
