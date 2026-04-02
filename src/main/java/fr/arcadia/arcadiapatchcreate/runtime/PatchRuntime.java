package fr.arcadia.arcadiapatchcreate.runtime;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;

public final class PatchRuntime {

    public enum FactoryGaugeMode {
        OFF,
        STATIC,
        ADAPTIVE
    }

    private static volatile boolean beltPatchEnabled = true;
    private static volatile boolean fluidPatchEnabled = true;
    private static volatile FactoryGaugeMode factoryGaugeMode = FactoryGaugeMode.OFF;
    private static volatile int factoryGaugeStaticInterval = 2;
    private static volatile Double simulatedMspt = null;

    private static final AtomicLong beltSkips = new AtomicLong();
    private static final AtomicLong fluidSkips = new AtomicLong();
    private static final AtomicLong fluidInspectionFailures = new AtomicLong();
    private static final AtomicLong factoryGaugeSkips = new AtomicLong();
    private static final AtomicLong factoryGaugeForcedRuns = new AtomicLong();

    private static volatile Method averageTickTimeMethod;
    private static volatile boolean averageTickTimeResolved;

    private PatchRuntime() {
    }

    public static boolean isBeltPatchEnabled() {
        return beltPatchEnabled;
    }

    public static void setBeltPatchEnabled(boolean enabled) {
        beltPatchEnabled = enabled;
    }

    public static long incrementBeltSkips() {
        return beltSkips.incrementAndGet();
    }

    public static long getBeltSkips() {
        return beltSkips.get();
    }

    public static boolean isFluidPatchEnabled() {
        return fluidPatchEnabled;
    }

    public static void setFluidPatchEnabled(boolean enabled) {
        fluidPatchEnabled = enabled;
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

    public static FactoryGaugeMode getFactoryGaugeMode() {
        return factoryGaugeMode;
    }

    public static void setFactoryGaugeMode(FactoryGaugeMode mode) {
        factoryGaugeMode = mode;
    }

    public static int getFactoryGaugeStaticInterval() {
        return factoryGaugeStaticInterval;
    }

    public static void setFactoryGaugeStaticInterval(int interval) {
        factoryGaugeStaticInterval = clampInterval(interval);
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

    public static int resolveFactoryGaugeInterval(MinecraftServer server) {
        FactoryGaugeMode mode = factoryGaugeMode;
        if (mode == FactoryGaugeMode.OFF) {
            return 1;
        }
        if (mode == FactoryGaugeMode.STATIC) {
            return clampInterval(factoryGaugeStaticInterval);
        }

        double mspt = getCurrentMspt(server);
        if (mspt >= 55.0D) {
            return 5;
        }
        if (mspt >= 45.0D) {
            return 3;
        }
        if (mspt >= 35.0D) {
            return 2;
        }
        return 1;
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

    public static String describeFactoryGaugeMode() {
        FactoryGaugeMode mode = factoryGaugeMode;
        if (mode == FactoryGaugeMode.STATIC) {
            return mode.name().toLowerCase(Locale.ROOT) + "(" + factoryGaugeStaticInterval + ")";
        }
        return mode.name().toLowerCase(Locale.ROOT);
    }

    private static int clampInterval(int interval) {
        return Math.max(1, Math.min(5, interval));
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
}
