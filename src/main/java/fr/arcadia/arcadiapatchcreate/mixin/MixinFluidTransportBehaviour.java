package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.FluidTransportBehaviour", remap = false)
public abstract class MixinFluidTransportBehaviour {

    private static final long SKIP_LOG_INTERVAL = 262_144L;
    private static final BehaviourInspector BEHAVIOUR_INSPECTOR = BehaviourInspector.resolve();
    private static final ConcurrentHashMap<Class<?>, ConnectionInspector> INSPECTORS = new ConcurrentHashMap<>();
    private static final AtomicLong SKIPPED_TICKS = new AtomicLong();
    private static final AtomicLong FAILED_INSPECTIONS = new AtomicLong();

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/fluids/PipeConnection;getProvidedFluid()Lnet/neoforged/neoforge/fluids/FluidStack;",
            ordinal = 0
        ),
        cancellable = true,
        remap = false
    )
    private void arcadiaPatchCreate$skipTrulyIdlePipe(CallbackInfo ci) {
        if (!PatchRuntime.isFluidPatchEnabled()) {
            return;
        }
        if (!BEHAVIOUR_INSPECTOR.available()) {
            return;
        }

        Level world;
        BlockPos pos;
        Map<?, ?> interfaces;
        Object phase;
        try {
            world = BEHAVIOUR_INSPECTOR.getWorld(this);
            pos = BEHAVIOUR_INSPECTOR.getPos(this);
            interfaces = BEHAVIOUR_INSPECTOR.getInterfaces(this);
            phase = BEHAVIOUR_INSPECTOR.getPhase(this);
        } catch (ReflectiveOperationException e) {
            long failures = FAILED_INSPECTIONS.incrementAndGet();
            PatchRuntime.incrementFluidInspectionFailures();
            if (failures <= 3 || failures % 1024 == 0) {
                ArcadiaPatchCreate.LOGGER.warn(
                    "[ArcadiaPatchCreate] Fluid pipe behaviour inspection failed. Falling back to Create logic.",
                    e
                );
            }
            return;
        }

        if (world == null || world.isClientSide) {
            return;
        }
        if (interfaces == null || interfaces.isEmpty()) {
            return;
        }
        if (!"IDLE".equals(String.valueOf(phase))) {
            return;
        }

        for (Object connection : interfaces.values()) {
            if (connection == null) {
                return;
            }

            ConnectionInspector inspector = INSPECTORS.computeIfAbsent(connection.getClass(), ConnectionInspector::resolve);
            if (!inspector.available()) {
                long failures = FAILED_INSPECTIONS.incrementAndGet();
                PatchRuntime.incrementFluidInspectionFailures();
                if (failures <= 3 || failures % 1024 == 0) {
                    ArcadiaPatchCreate.LOGGER.warn(
                        "[ArcadiaPatchCreate] Fluid pipe idle fast-path could not inspect {} at {}. Falling back to Create logic.",
                        connection.getClass().getName(),
                        pos
                    );
                }
                return;
            }

            try {
                if (inspector.hasPressure(connection) || inspector.hasFlow(connection)) {
                    return;
                }
            } catch (ReflectiveOperationException e) {
                long failures = FAILED_INSPECTIONS.incrementAndGet();
                PatchRuntime.incrementFluidInspectionFailures();
                if (failures <= 3 || failures % 1024 == 0) {
                    ArcadiaPatchCreate.LOGGER.warn(
                        "[ArcadiaPatchCreate] Fluid pipe idle inspection failed at {}. Falling back to Create logic.",
                        pos,
                        e
                    );
                }
                return;
            }
        }

        long skipped = SKIPPED_TICKS.incrementAndGet();
        PatchRuntime.incrementFluidSkips();
        if (skipped <= 3 || skipped % SKIP_LOG_INTERVAL == 0) {
            ArcadiaPatchCreate.LOGGER.info(
                "[ArcadiaPatchCreate] Skipped {} fully idle Create fluid pipe ticks. Latest position: {}",
                skipped,
                pos
            );
        }
        ci.cancel();
    }

    private record BehaviourInspector(Method getWorldMethod, Method getPosMethod, Field interfacesField, Field phaseField) {

        static BehaviourInspector resolve() {
            try {
                Class<?> behaviourClass = Class.forName(
                    "com.simibubi.create.content.fluids.FluidTransportBehaviour",
                    false,
                    MixinFluidTransportBehaviour.class.getClassLoader()
                );
                Method getWorld = behaviourClass.getMethod("getWorld");
                Method getPos = behaviourClass.getMethod("getPos");
                Field interfaces = behaviourClass.getField("interfaces");
                Field phase = behaviourClass.getField("phase");
                return new BehaviourInspector(getWorld, getPos, interfaces, phase);
            } catch (ReflectiveOperationException e) {
                ArcadiaPatchCreate.LOGGER.warn(
                    "[ArcadiaPatchCreate] Could not resolve Create fluid behaviour members. Fluid idle fast-path will stay disabled.",
                    e
                );
                return new BehaviourInspector(null, null, null, null);
            }
        }

        boolean available() {
            return getWorldMethod != null && getPosMethod != null && interfacesField != null && phaseField != null;
        }

        Level getWorld(Object behaviour) throws ReflectiveOperationException {
            return (Level) getWorldMethod.invoke(behaviour);
        }

        BlockPos getPos(Object behaviour) throws ReflectiveOperationException {
            return (BlockPos) getPosMethod.invoke(behaviour);
        }

        @SuppressWarnings("unchecked")
        Map<?, ?> getInterfaces(Object behaviour) throws ReflectiveOperationException {
            return (Map<?, ?>) interfacesField.get(behaviour);
        }

        Object getPhase(Object behaviour) throws ReflectiveOperationException {
            return phaseField.get(behaviour);
        }
    }

    private record ConnectionInspector(Method hasPressureMethod, Method hasFlowMethod) {

        static ConnectionInspector resolve(Class<?> connectionClass) {
            try {
                Method hasPressure = connectionClass.getMethod("hasPressure");
                Method hasFlow = connectionClass.getMethod("hasFlow");
                return new ConnectionInspector(hasPressure, hasFlow);
            } catch (ReflectiveOperationException e) {
                return new ConnectionInspector(null, null);
            }
        }

        boolean available() {
            return hasPressureMethod != null && hasFlowMethod != null;
        }

        boolean hasPressure(Object connection) throws ReflectiveOperationException {
            return (Boolean) hasPressureMethod.invoke(connection);
        }

        boolean hasFlow(Object connection) throws ReflectiveOperationException {
            return (Boolean) hasFlowMethod.invoke(connection);
        }
    }
}
