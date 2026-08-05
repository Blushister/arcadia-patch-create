package fr.arcadia.arcadiapatchcreate.runtime;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import fr.arcadia.arcadiapatchcreate.bootstrap.ArcadiaMixinPlugin;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;

public final class PatchRuntime {

    private static final boolean BELT_PATCH_AVAILABLE = ArcadiaMixinPlugin.isBeltTargetCompatible();
    private static final boolean FLUID_PATCH_AVAILABLE = ArcadiaMixinPlugin.isFluidTargetCompatible();
    private static final boolean HEAT_JS_PATCH_AVAILABLE = ArcadiaMixinPlugin.isHeatJsTargetCompatible();
    private static final boolean ITEM_DRAIN_PATCH_AVAILABLE = ArcadiaMixinPlugin.isItemDrainTargetCompatible();
    private static final boolean CRAFTER_SIGNAL_AVAILABLE =
        ArcadiaMixinPlugin.isCrafterSignalTargetCompatible();
    private static final boolean BEHAVIOUR_DISPATCH_AVAILABLE =
        ArcadiaMixinPlugin.isBehaviourDispatchTargetCompatible();

    public enum ThrottleMode {
        OFF,
        STATIC,
        ADAPTIVE
    }

    // --- State flags ---
    private static volatile boolean masterPatchEnabled = true;
    private static volatile boolean beltPatchEnabled = true;
    private static volatile boolean fluidPatchEnabled = true;
    private static volatile boolean factoryGaugeEnabled = true;
    private static volatile boolean heatJsPatchEnabled = true;
    private static volatile boolean itemDrainPatchEnabled = true;
    private static volatile boolean behaviourDispatchEnabled = true;
    private static volatile boolean crafterSignalEnabled = true;
    private static volatile boolean createPhysicalItemsFastDespawnEnabled = false;

    // --- Throttle configuration ---
    private static volatile ThrottleMode globalThrottleMode = ThrottleMode.OFF;
    private static volatile int globalStaticInterval = 2;
    private static volatile Double simulatedMspt = null;
    private static volatile int createPhysicalItemsDespawnTicks = 1_200;

    // --- MSPT reflection ---
    // The compile classpath exposes Yarn names, but the deployed server is remapped to
    // official Mojang names, so a single hardcoded name never resolves in production.
    // Probe every known accessor and remember the unit divisor of the one that answered.
    private static final MsptAccessor[] MSPT_ACCESSORS = {
        new MsptAccessor("getAverageTickTimeNanos", 1_000_000.0D), // Mojang, nanoseconds
        new MsptAccessor("getAverageNanosPerTick", 1_000_000.0D),  // Yarn, nanoseconds
        new MsptAccessor("getCurrentSmoothedTickTime", 1.0D),      // Mojang, milliseconds
        new MsptAccessor("getAverageTickTime", 1.0D)               // Yarn, milliseconds
    };

    private static volatile Method averageTickTimeMethod;
    private static volatile double averageTickTimeDivisor = 1.0D;
    private static volatile boolean averageTickTimeResolved;
    private static volatile boolean averageTickTimeFailureLogged;

    // --- Counters ---
    private static final AtomicLong beltSkips = new AtomicLong();
    private static final AtomicLong fluidSkips = new AtomicLong();
    private static final AtomicLong fluidInspectionFailures = new AtomicLong();
    private static final AtomicLong factoryGaugeSkips = new AtomicLong();
    private static final AtomicLong factoryGaugeForcedRuns = new AtomicLong();
    private static final AtomicLong heatJsCacheHits = new AtomicLong();
    private static final AtomicLong heatJsCacheMisses = new AtomicLong();
    private static final AtomicLong heatJsCacheInvalidations = new AtomicLong();
    private static final AtomicLong heatJsFailures = new AtomicLong();
    private static final AtomicLong itemDrainCaptures = new AtomicLong();
    private static final AtomicLong itemDrainReuses = new AtomicLong();
    private static final AtomicLong itemDrainFallbacks = new AtomicLong();
    private static final AtomicLong fluidMapCompactions = new AtomicLong();
    private static final AtomicLong behaviourDispatches = new AtomicLong();
    private static final AtomicLong crafterSignalReuses = new AtomicLong();
    private static final AtomicLong crafterSignalReads = new AtomicLong();
    private static final AtomicLong crafterSignalInvalidations = new AtomicLong();
    private static final AtomicLong createPhysicalItemMarks = new AtomicLong();

    private PatchRuntime() {
    }

    // --- Master ---

    public static boolean isMasterPatchEnabled() {
        return masterPatchEnabled;
    }

    public static void setMasterPatchEnabled(boolean enabled) {
        masterPatchEnabled = enabled;
        PatchConfigStore.saveFromRuntime();
    }

    // --- Belt ---

    public static boolean isBeltPatchEnabled() {
        return masterPatchEnabled && beltPatchEnabled && BELT_PATCH_AVAILABLE;
    }

    public static boolean isBeltPatchAvailable() {
        return BELT_PATCH_AVAILABLE;
    }

    public static boolean isBeltPatchConfiguredEnabled() {
        return beltPatchEnabled;
    }

    public static void setBeltPatchEnabled(boolean enabled) {
        beltPatchEnabled = enabled;
        PatchConfigStore.saveFromRuntime();
    }

    public static long incrementBeltSkips() {
        return beltSkips.incrementAndGet();
    }

    public static long getBeltSkips() {
        return beltSkips.get();
    }

    // --- Fluid ---

    public static boolean isFluidPatchEnabled() {
        return masterPatchEnabled && fluidPatchEnabled && FLUID_PATCH_AVAILABLE;
    }

    public static boolean isFluidPatchAvailable() {
        return FLUID_PATCH_AVAILABLE;
    }

    public static boolean isFluidPatchConfiguredEnabled() {
        return fluidPatchEnabled;
    }

    public static void setFluidPatchEnabled(boolean enabled) {
        fluidPatchEnabled = enabled;
        PatchConfigStore.saveFromRuntime();
    }

    public static long incrementFluidSkips() {
        return fluidSkips.incrementAndGet();
    }

    public static long getFluidSkips() {
        return fluidSkips.get();
    }

    public static long incrementFluidInspectionFailures() {
        return fluidInspectionFailures.incrementAndGet();
    }

    public static long getFluidInspectionFailures() {
        return fluidInspectionFailures.get();
    }

    // --- Factory Gauge ---

    public static boolean isFactoryGaugeEnabled() {
        return masterPatchEnabled && factoryGaugeEnabled;
    }

    public static boolean isFactoryGaugeConfiguredEnabled() {
        return factoryGaugeEnabled;
    }

    public static void setFactoryGaugeEnabled(boolean enabled) {
        factoryGaugeEnabled = enabled;
        PatchConfigStore.saveFromRuntime();
    }

    public static long incrementFactoryGaugeSkips() {
        return factoryGaugeSkips.incrementAndGet();
    }

    public static long getFactoryGaugeSkips() {
        return factoryGaugeSkips.get();
    }

    public static long incrementFactoryGaugeForcedRuns() {
        return factoryGaugeForcedRuns.incrementAndGet();
    }

    public static long getFactoryGaugeForcedRuns() {
        return factoryGaugeForcedRuns.get();
    }

    // --- CreateHeatJS recipe context ---

    public static boolean isHeatJsPatchEnabled() {
        return masterPatchEnabled && heatJsPatchEnabled && HEAT_JS_PATCH_AVAILABLE;
    }

    public static boolean isHeatJsPatchAvailable() {
        return HEAT_JS_PATCH_AVAILABLE;
    }

    public static boolean isHeatJsPatchConfiguredEnabled() {
        return heatJsPatchEnabled;
    }

    public static void setHeatJsPatchEnabled(boolean enabled) {
        heatJsPatchEnabled = enabled;
        PatchConfigStore.saveFromRuntime();
    }

    public static long incrementHeatJsCacheHits() {
        return heatJsCacheHits.incrementAndGet();
    }

    public static long getHeatJsCacheHits() {
        return heatJsCacheHits.get();
    }

    public static long incrementHeatJsCacheMisses() {
        return heatJsCacheMisses.incrementAndGet();
    }

    public static long getHeatJsCacheMisses() {
        return heatJsCacheMisses.get();
    }

    public static long incrementHeatJsCacheInvalidations() {
        return heatJsCacheInvalidations.incrementAndGet();
    }

    public static long getHeatJsCacheInvalidations() {
        return heatJsCacheInvalidations.get();
    }

    public static long incrementHeatJsFailures() {
        return heatJsFailures.incrementAndGet();
    }

    public static long getHeatJsFailures() {
        return heatJsFailures.get();
    }

    // --- Item Drain recipe lookup ---

    public static boolean isItemDrainPatchEnabled() {
        return masterPatchEnabled && itemDrainPatchEnabled && ITEM_DRAIN_PATCH_AVAILABLE;
    }

    public static boolean isItemDrainPatchAvailable() {
        return ITEM_DRAIN_PATCH_AVAILABLE;
    }

    public static boolean isItemDrainPatchConfiguredEnabled() {
        return itemDrainPatchEnabled;
    }

    public static void setItemDrainPatchEnabled(boolean enabled) {
        itemDrainPatchEnabled = enabled;
        PatchConfigStore.saveFromRuntime();
    }

    public static long incrementItemDrainCaptures() {
        return itemDrainCaptures.incrementAndGet();
    }

    public static long getItemDrainCaptures() {
        return itemDrainCaptures.get();
    }

    public static long incrementItemDrainReuses() {
        return itemDrainReuses.incrementAndGet();
    }

    public static long getItemDrainReuses() {
        return itemDrainReuses.get();
    }

    public static long incrementItemDrainFallbacks() {
        return itemDrainFallbacks.incrementAndGet();
    }

    public static long getItemDrainFallbacks() {
        return itemDrainFallbacks.get();
    }

    public static long incrementFluidMapCompactions() {
        return fluidMapCompactions.incrementAndGet();
    }

    public static long getFluidMapCompactions() {
        return fluidMapCompactions.get();
    }

    // --- SmartBlockEntity behaviour dispatch ---

    public static boolean isBehaviourDispatchPatchEnabled() {
        return masterPatchEnabled && behaviourDispatchEnabled && BEHAVIOUR_DISPATCH_AVAILABLE;
    }

    public static boolean isBehaviourDispatchPatchAvailable() {
        return BEHAVIOUR_DISPATCH_AVAILABLE;
    }

    public static boolean isBehaviourDispatchPatchConfiguredEnabled() {
        return behaviourDispatchEnabled;
    }

    public static void setBehaviourDispatchPatchEnabled(boolean enabled) {
        behaviourDispatchEnabled = enabled;
        PatchConfigStore.saveFromRuntime();
    }

    public static long incrementBehaviourDispatches() {
        return behaviourDispatches.incrementAndGet();
    }

    public static long getBehaviourDispatches() {
        return behaviourDispatches.get();
    }

    // --- Mechanical Crafter redstone signal cache ---

    public static boolean isCrafterSignalPatchEnabled() {
        return masterPatchEnabled && crafterSignalEnabled && CRAFTER_SIGNAL_AVAILABLE;
    }

    public static boolean isCrafterSignalPatchAvailable() {
        return CRAFTER_SIGNAL_AVAILABLE;
    }

    public static boolean isCrafterSignalPatchConfiguredEnabled() {
        return crafterSignalEnabled;
    }

    public static void setCrafterSignalPatchEnabled(boolean enabled) {
        crafterSignalEnabled = enabled;
        PatchConfigStore.saveFromRuntime();
    }

    public static long incrementCrafterSignalReuses() {
        return crafterSignalReuses.incrementAndGet();
    }

    public static long getCrafterSignalReuses() {
        return crafterSignalReuses.get();
    }

    public static long incrementCrafterSignalReads() {
        return crafterSignalReads.incrementAndGet();
    }

    public static long getCrafterSignalReads() {
        return crafterSignalReads.get();
    }

    public static long incrementCrafterSignalInvalidations() {
        return crafterSignalInvalidations.incrementAndGet();
    }

    public static long getCrafterSignalInvalidations() {
        return crafterSignalInvalidations.get();
    }

    // --- Create Physical Items Fast Despawn ---

    public static boolean isCreatePhysicalItemsFastDespawnEnabled() {
        return masterPatchEnabled && createPhysicalItemsFastDespawnEnabled;
    }

    public static boolean isCreatePhysicalItemsFastDespawnConfiguredEnabled() {
        return createPhysicalItemsFastDespawnEnabled;
    }

    public static void setCreatePhysicalItemsFastDespawnEnabled(boolean enabled) {
        createPhysicalItemsFastDespawnEnabled = enabled;
        PatchConfigStore.saveFromRuntime();
    }

    public static int getCreatePhysicalItemsDespawnTicks() {
        return createPhysicalItemsDespawnTicks;
    }

    public static void setCreatePhysicalItemsDespawnTicks(int ticks) {
        createPhysicalItemsDespawnTicks = Math.max(100, Math.min(12_000, ticks));
        PatchConfigStore.saveFromRuntime();
    }

    public static long incrementCreatePhysicalItemMarks() {
        return createPhysicalItemMarks.incrementAndGet();
    }

    public static long getCreatePhysicalItemMarks() {
        return createPhysicalItemMarks.get();
    }

    // --- Global Throttle ---

    public static ThrottleMode getGlobalThrottleMode() {
        return globalThrottleMode;
    }

    public static void setGlobalThrottleMode(ThrottleMode mode) {
        globalThrottleMode = mode;
        PatchConfigStore.saveFromRuntime();
    }

    public static int getGlobalStaticInterval() {
        return globalStaticInterval;
    }

    public static void setGlobalStaticInterval(int interval) {
        globalStaticInterval = clampInterval(interval);
        PatchConfigStore.saveFromRuntime();
    }

    public static Double getSimulatedMspt() {
        return simulatedMspt;
    }

    public static void setSimulatedMspt(Double mspt) {
        simulatedMspt = mspt;
    }

    public static void clearSimulatedMspt() {
        simulatedMspt = null;
    }

    public static int resolveGlobalInterval(MinecraftServer server) {
        if (!masterPatchEnabled) {
            return 1;
        }
        ThrottleMode mode = globalThrottleMode;
        if (mode == ThrottleMode.OFF) {
            return 1;
        }
        if (mode == ThrottleMode.STATIC) {
            return clampInterval(globalStaticInterval);
        }
        double mspt = getCurrentMspt(server);
        if (mspt >= 55.0D) return 5;
        if (mspt >= 45.0D) return 3;
        if (mspt >= 35.0D) return 2;
        return 1;
    }

    public static String describeGlobalThrottleMode() {
        ThrottleMode mode = globalThrottleMode;
        if (mode == ThrottleMode.STATIC) {
            return mode.name().toLowerCase(Locale.ROOT) + "(" + globalStaticInterval + ")";
        }
        return mode.name().toLowerCase(Locale.ROOT);
    }

    public static boolean isThrottleActive() {
        return masterPatchEnabled && globalThrottleMode != ThrottleMode.OFF;
    }

    public static double getCurrentMspt(MinecraftServer server) {
        Double simulated = simulatedMspt;
        if (simulated != null) {
            return simulated.doubleValue();
        }
        if (server == null) {
            return 0.0D;
        }
        try {
            Method method = resolveAverageTickTimeMethod(server.getClass());
            if (method != null) {
                Object value = method.invoke(server);
                if (value instanceof Number number) {
                    return number.doubleValue() / averageTickTimeDivisor;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 0.0D;
    }

    private static Method resolveAverageTickTimeMethod(Class<?> serverClass) {
        if (averageTickTimeResolved) {
            return averageTickTimeMethod;
        }
        synchronized (PatchRuntime.class) {
            if (averageTickTimeResolved) {
                return averageTickTimeMethod;
            }
            for (MsptAccessor accessor : MSPT_ACCESSORS) {
                try {
                    Method method = serverClass.getMethod(accessor.methodName());
                    if (Number.class.isAssignableFrom(boxed(method.getReturnType()))) {
                        averageTickTimeMethod = method;
                        averageTickTimeDivisor = accessor.divisor();
                        break;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Try the next mapping variant.
                }
            }
            if (averageTickTimeMethod == null && !averageTickTimeFailureLogged) {
                averageTickTimeFailureLogged = true;
                ArcadiaPatchCreate.LOGGER.warn(
                    "[ArcadiaPatchCreate] No average tick time accessor found on {}. "
                        + "Adaptive throttling will stay inactive; use the static mode instead.",
                    serverClass.getName()
                );
            }
            averageTickTimeResolved = true;
            return averageTickTimeMethod;
        }
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == long.class) return Long.class;
        if (type == int.class) return Integer.class;
        return type;
    }

    private record MsptAccessor(String methodName, double divisor) {
    }

    // --- Startup restore ---

    static void applyPersistedState(
        boolean masterEnabled,
        boolean beltEnabled,
        boolean fluidEnabled,
        boolean factoryEnabled,
        boolean heatJsEnabled,
        boolean itemDrainEnabled,
        boolean behaviourDispatch,
        boolean crafterSignal,
        boolean createDropsEnabled,
        ThrottleMode throttleMode,
        int staticInterval,
        int createDropsTicks
    ) {
        masterPatchEnabled = masterEnabled;
        beltPatchEnabled = beltEnabled;
        fluidPatchEnabled = fluidEnabled;
        factoryGaugeEnabled = factoryEnabled;
        heatJsPatchEnabled = heatJsEnabled;
        itemDrainPatchEnabled = itemDrainEnabled;
        behaviourDispatchEnabled = behaviourDispatch;
        crafterSignalEnabled = crafterSignal;
        createPhysicalItemsFastDespawnEnabled = createDropsEnabled;
        globalThrottleMode = throttleMode;
        globalStaticInterval = clampInterval(staticInterval);
        createPhysicalItemsDespawnTicks = Math.max(100, Math.min(12_000, createDropsTicks));
    }

    private static int clampInterval(int interval) {
        return Math.max(1, Math.min(5, interval));
    }
}
