package fr.arcadia.arcadiapatchcreate.runtime;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

public final class HeatJsContextCache {

    private static final String CONTEXT_CLASS_NAME = "com.xiaohunao.create_heat_js.common.HeatRecipeContext";
    private static final Object SNAPSHOT_LOCK = new Object();
    // RecipeManager keeps Object identity semantics in 1.21.1. Weak keys avoid
    // retaining complete recipe graphs across integrated/dedicated server cycles.
    private static final Map<RecipeManager, Snapshot> SNAPSHOTS = new WeakHashMap<>();

    private static volatile MethodHandle contextConstructor;
    private static volatile boolean constructorResolved;
    private static volatile boolean constructorFailureLogged;
    private static volatile boolean constructorInvocationFailed;

    private HeatJsContextCache() {
    }

    public static Object createContext(Level level, Recipe<?> recipe) {
        if (!PatchRuntime.isHeatJsPatchEnabled() || level == null || recipe == null) {
            return null;
        }

        Metadata metadata;
        try {
            Snapshot snapshot = snapshotFor(level.getRecipeManager());
            metadata = snapshot.byIdentity().get(recipe);
        } catch (RuntimeException | LinkageError exception) {
            recordFailure("Could not build the CreateHeatJS recipe metadata snapshot", exception);
            return null;
        }

        if (metadata == null) {
            PatchRuntime.incrementHeatJsCacheMisses();
            return null;
        }

        MethodHandle constructor = resolveContextConstructor();
        if (constructor == null) {
            return null;
        }

        ResourceLocation recipeTypeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        try {
            Object context = constructor.invokeExact(metadata.recipeId(), recipeTypeId);
            PatchRuntime.incrementHeatJsCacheHits();
            return context;
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable throwable) {
            if (throwable instanceof Error error && !(error instanceof LinkageError)) {
                throw error;
            }
            if (throwable instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            constructorInvocationFailed = true;
            contextConstructor = null;
            recordFailure("Could not create a fresh CreateHeatJS recipe context", throwable);
            return null;
        }
    }

    /** Publishes the post-reload snapshot before machines resume ticking. */
    public static void refresh(RecipeManager manager) {
        if (manager == null) {
            return;
        }

        boolean removed;
        synchronized (SNAPSHOT_LOCK) {
            removed = SNAPSHOTS.remove(manager) != null;
        }
        if (removed) {
            PatchRuntime.incrementHeatJsCacheInvalidations();
        }

        Snapshot snapshot;
        try {
            snapshot = buildSnapshot(manager);
        } catch (RuntimeException | LinkageError exception) {
            recordFailure("Could not refresh the CreateHeatJS recipe metadata snapshot", exception);
            return;
        }
        synchronized (SNAPSHOT_LOCK) {
            SNAPSHOTS.put(manager, snapshot);
        }
    }

    public static void invalidate(RecipeManager manager) {
        if (manager == null) {
            return;
        }
        boolean removed;
        synchronized (SNAPSHOT_LOCK) {
            removed = SNAPSHOTS.remove(manager) != null;
        }
        if (removed) {
            PatchRuntime.incrementHeatJsCacheInvalidations();
        }
    }

    private static Snapshot snapshotFor(RecipeManager manager) {
        synchronized (SNAPSHOT_LOCK) {
            Snapshot existing = SNAPSHOTS.get(manager);
            if (existing != null) {
                return existing;
            }

            Snapshot snapshot = buildSnapshot(manager);
            SNAPSHOTS.put(manager, snapshot);
            return snapshot;
        }
    }

    private static Snapshot buildSnapshot(RecipeManager manager) {
        IdentityHashMap<Recipe<?>, Metadata> metadata = new IdentityHashMap<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            metadata.putIfAbsent(holder.value(), new Metadata(holder.id()));
        }
        return new Snapshot(Collections.unmodifiableMap(metadata));
    }

    private static MethodHandle resolveContextConstructor() {
        if (constructorInvocationFailed) {
            return null;
        }
        if (constructorResolved) {
            return contextConstructor;
        }
        synchronized (HeatJsContextCache.class) {
            if (constructorResolved) {
                return contextConstructor;
            }
            try {
                Class<?> contextClass = Class.forName(
                    CONTEXT_CLASS_NAME,
                    false,
                    HeatJsContextCache.class.getClassLoader()
                );
                MethodHandle constructor = MethodHandles.publicLookup().findConstructor(
                    contextClass,
                    MethodType.methodType(void.class, ResourceLocation.class, ResourceLocation.class)
                );
                contextConstructor = constructor.asType(
                    MethodType.methodType(Object.class, ResourceLocation.class, ResourceLocation.class)
                );
            } catch (ReflectiveOperationException | LinkageError exception) {
                PatchRuntime.incrementHeatJsFailures();
                if (!constructorFailureLogged) {
                    constructorFailureLogged = true;
                    ArcadiaPatchCreate.LOGGER.warn(
                        "[ArcadiaPatchCreate] CreateHeatJS context constructor is incompatible. Using original addon logic.",
                        exception
                    );
                }
            }
            constructorResolved = true;
            return contextConstructor;
        }
    }

    private static void recordFailure(String message, Throwable throwable) {
        long failures = PatchRuntime.incrementHeatJsFailures();
        if (failures <= 3 || failures % 1_024 == 0) {
            ArcadiaPatchCreate.LOGGER.warn("[ArcadiaPatchCreate] {}. Using original addon logic.", message, throwable);
        }
    }

    private record Metadata(ResourceLocation recipeId) {
    }

    private record Snapshot(Map<Recipe<?>, Metadata> byIdentity) {
    }
}
