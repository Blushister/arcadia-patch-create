package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.collision.ContinuousOBBCollider", remap = false)
public abstract class MixinContinuousOBBCollider {

    private static final long INVALID_MANIFOLD_LOG_INTERVAL = 1024L;
    private static final AtomicLong INVALID_MANIFOLDS = new AtomicLong();

    @Redirect(
        method = "collideMany",
        at = @At(
            value = "FIELD",
            target = "Lcom/simibubi/create/foundation/collision/ContinuousOBBCollider$ContinuousSeparationManifold;axis:Lnet/minecraft/world/phys/Vec3;",
            opcode = Opcodes.GETFIELD
        ),
        require = 0,
        remap = false
    )
    private static Vec3 arcadiaPatchCreate$guardInvalidAxis(
        @Coerce Object manifold,
        @Coerce Object collidableBBs,
        @Coerce Object denseViableColliders,
        @Coerce Object obb,
        Vec3 motion,
        float entityMaxStep,
        boolean doHorizontalPass
    ) {
        Vec3 axis = ((ContinuousSeparationManifoldAccessor) manifold).arcadiaPatchCreate$getAxis();
        if (axis != null) {
            return axis;
        }

        logInvalidManifold("axis", denseViableColliders, obb, motion, entityMaxStep, doHorizontalPass);
        return Vec3.ZERO;
    }

    @Redirect(
        method = "collideMany",
        at = @At(
            value = "FIELD",
            target = "Lcom/simibubi/create/foundation/collision/ContinuousOBBCollider$ContinuousSeparationManifold;normalAxis:Lnet/minecraft/world/phys/Vec3;",
            opcode = Opcodes.GETFIELD
        ),
        require = 0,
        remap = false
    )
    private static Vec3 arcadiaPatchCreate$guardInvalidNormalAxis(
        @Coerce Object manifold,
        @Coerce Object collidableBBs,
        @Coerce Object denseViableColliders,
        @Coerce Object obb,
        Vec3 motion,
        float entityMaxStep,
        boolean doHorizontalPass
    ) {
        Vec3 normalAxis = ((ContinuousSeparationManifoldAccessor) manifold).arcadiaPatchCreate$getNormalAxis();
        if (normalAxis != null) {
            return normalAxis;
        }

        logInvalidManifold("normalAxis", denseViableColliders, obb, motion, entityMaxStep, doHorizontalPass);
        return Vec3.ZERO;
    }

    private static void logInvalidManifold(
        String missingField,
        Object denseViableColliders,
        Object obb,
        Vec3 motion,
        float entityMaxStep,
        boolean doHorizontalPass
    ) {
        long invalid = INVALID_MANIFOLDS.incrementAndGet();
        if (invalid > 3 && invalid % INVALID_MANIFOLD_LOG_INTERVAL != 0) {
            return;
        }

        ArcadiaPatchCreate.LOGGER.warn(
            "[ArcadiaPatchCreate] Skipped invalid Create contraption collision manifold #{}: missing={} context={}",
            invalid,
            missingField,
            describeContext(denseViableColliders, obb, motion, entityMaxStep, doHorizontalPass)
        );
    }

    private static String describeContext(
        Object denseViableColliders,
        Object obb,
        Vec3 motion,
        float entityMaxStep,
        boolean doHorizontalPass
    ) {
        return "obbCenter=" + invokeVec3Getter(obb, "getCenter")
            + ", colliders=" + readIntField(denseViableColliders, "size")
            + ", motion=" + motion
            + ", maxStep=" + entityMaxStep
            + ", horizontalPass=" + doHorizontalPass;
    }

    private static Vec3 invokeVec3Getter(Object target, String methodName) {
        if (target == null) {
            return Vec3.ZERO;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Vec3 vec ? vec : Vec3.ZERO;
        } catch (ReflectiveOperationException e) {
            return Vec3.ZERO;
        }
    }

    private static int readIntField(Object target, String fieldName) {
        if (target == null) {
            return -1;
        }
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }
}
