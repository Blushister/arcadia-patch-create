package fr.arcadia.arcadiapatchcreate.runtime;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;

public final class PatchRuntime {

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
    private static volatile boolean chutePatchEnabled = true;
    private static volatile boolean createPhysicalItemsFastDespawnEnabled = false;

    // --- Throttle configuration ---
    private static volatile ThrottleMode globalThrottleMode = ThrottleMode.OFF;
    private static volatile int globalStaticInterval = 2;
    private static volatile Double simulatedMspt = null;
    private static volatile int createPhysicalItemsDespawnTicks = 1_200;

    // --- MSPT reflection (getAverageTickTime exists at runtime via NeoForge, not in compile classpath) ---
    private static volatile Method averageTickTimeMethod;
    private static volatile boolean averageTickTimeResolved;

    // --- Counters ---
    private static final AtomicLong beltSkips = new AtomicLong();
    private static final AtomicLong fluidSkips = new AtomicLong();
    private static final AtomicLong fluidInspectionFailures = new AtomicLong();
    private static final AtomicLong factoryGaugeSkips = new AtomicLong();
    private static final AtomicLong factoryGaugeForcedRuns = new AtomicLong();
    private static final AtomicLong chuteProbeSkips = new AtomicLong();
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
        return masterPatchEnabled && beltPatchEnabled;
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
        return masterPatchEnabled && fluidPatchEnabled;
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

    // --- Chute ---

    public static boolean isChutePatchEnabled() {
        return masterPatchEnabled && chutePatchEnabled;
    }

    public static boolean isChutePatchConfiguredEnabled() {
        return chutePatchEnabled;
    }

    public static void setChutePatchEnabled(boolean enabled) {
        chutePatchEnabled = enabled;
        PatchConfigStore.saveFromRuntime();
    }

    public static long incrementChuteProbeSkips() {
        return chuteProbeSkips.incrementAndGet();
    }

    public static long getChuteProbeSkips() {
        return chuteProbeSkips.get();
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
                    return number.doubleValue();
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
            try {
                averageTickTimeMethod = serverClass.getMethod("getAverageTickTime");
            } catch (NoSuchMethodException ignored) {
                averageTickTimeMethod = null;
            }
            averageTickTimeResolved = true;
            return averageTickTimeMethod;
        }
    }

    // --- Startup restore ---

    static void applyPersistedState(
        boolean masterEnabled,
        boolean beltEnabled,
        boolean fluidEnabled,
        boolean factoryEnabled,
        boolean chuteEnabled,
        boolean createDropsEnabled,
        ThrottleMode throttleMode,
        int staticInterval,
        int createDropsTicks
    ) {
        masterPatchEnabled = masterEnabled;
        beltPatchEnabled = beltEnabled;
        fluidPatchEnabled = fluidEnabled;
        factoryGaugeEnabled = factoryEnabled;
        chutePatchEnabled = chuteEnabled;
        createPhysicalItemsFastDespawnEnabled = createDropsEnabled;
        globalThrottleMode = throttleMode;
        globalStaticInterval = clampInterval(staticInterval);
        createPhysicalItemsDespawnTicks = Math.max(100, Math.min(12_000, createDropsTicks));
    }

    private static int clampInterval(int interval) {
        return Math.max(1, Math.min(5, interval));
    }
}
