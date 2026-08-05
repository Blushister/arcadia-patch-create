package fr.arcadia.arcadiapatchcreate.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

public final class ItemDrainLookupReuse {

    private static final String STANDARD_EMPTYING_RECIPE =
        "com.simibubi.create.content.fluids.transfer.EmptyingRecipe";
    private static final ThreadLocal<FrameState> FRAME_STATES = new ThreadLocal<>();

    private ItemDrainLookupReuse() {
    }

    public static Object enterFrame() {
        if (!PatchRuntime.isItemDrainPatchEnabled()) {
            return null;
        }
        FrameState state = FRAME_STATES.get();
        if (state == null) {
            state = new FrameState();
            FRAME_STATES.set(state);
        }
        Frame frame = state.free.pollFirst();
        if (frame == null) {
            frame = new Frame();
        }
        state.active.push(frame);
        return frame;
    }

    public static void exitFrame(Object token) {
        if (!(token instanceof Frame frame)) {
            return;
        }
        FrameState state = FRAME_STATES.get();
        if (state == null) {
            PatchRuntime.incrementItemDrainFallbacks();
            return;
        }
        boolean removed;
        if (state.active.peek() == frame) {
            state.active.pop();
            removed = true;
        } else {
            removed = state.active.remove(frame);
            PatchRuntime.incrementItemDrainFallbacks();
        }
        if (!removed) {
            return;
        }
        clearProbe(frame);
        state.free.push(frame);
    }

    public static Object beginProbe(Level level, ItemStack stack) {
        Frame frame = currentFrame();
        if (frame == null || level == null || stack == null) {
            return null;
        }
        try {
            frame.level = level;
            frame.recipeManager = level.getRecipeManager();
            frame.recipeView = frame.recipeManager.getRecipes();
            frame.stack = stack;
            frame.stackSnapshot = null;
            frame.captured = null;
            frame.consumed = false;
            frame.probeActive = true;
            frame.canDepth = 0;
            return frame;
        } catch (RuntimeException exception) {
            clearProbe(frame);
            PatchRuntime.incrementItemDrainFallbacks();
            return null;
        }
    }

    public static void finishProbe(Object token, boolean canBeEmptied) {
        if (!(token instanceof Frame frame) || currentFrame() != frame) {
            return;
        }
        frame.probeActive = false;
        frame.canDepth = 0;
        if (!canBeEmptied) {
            frame.captured = null;
        }
    }

    public static void abortProbe(Object token) {
        if (token instanceof Frame frame && currentFrame() == frame) {
            clearProbe(frame);
        }
    }

    public static Object enterCanLookup(Level level, ItemStack stack) {
        if (!PatchRuntime.isItemDrainPatchEnabled()) {
            return null;
        }
        Frame frame = currentFrame();
        if (frame == null || !frame.probeActive || frame.level != level || frame.stack != stack) {
            return null;
        }
        frame.canDepth++;
        return frame;
    }

    public static void exitCanLookup(Object token) {
        if (token instanceof Frame frame && currentFrame() == frame && frame.canDepth > 0) {
            frame.canDepth--;
        }
    }

    public static void capturePositiveStandard(Level level, ItemStack stack, Optional<?> result) {
        if (!PatchRuntime.isItemDrainPatchEnabled()) {
            return;
        }
        Frame frame = currentFrame();
        if (frame == null
            || !frame.probeActive
            || frame.canDepth != 1
            || frame.level != level
            || frame.stack != stack
            || frame.captured != null) {
            return;
        }

        if (result == null) {
            PatchRuntime.incrementItemDrainFallbacks();
            return;
        }
        if (result.isEmpty()) {
            return;
        }
        Object value = result.get();
        if (!(value instanceof RecipeHolder<?> holder)) {
            PatchRuntime.incrementItemDrainFallbacks();
            return;
        }

        Recipe<?> recipe = holder.value();
        try {
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            if (!STANDARD_EMPTYING_RECIPE.equals(recipe.getClass().getName())
                || ingredients.size() != 1
                || ingredients.getFirst().isCustom()
                || !ingredients.getFirst().isSimple()) {
                PatchRuntime.incrementItemDrainFallbacks();
                return;
            }
            frame.stackSnapshot = stack.copy();
        } catch (RuntimeException | LinkageError exception) {
            PatchRuntime.incrementItemDrainFallbacks();
            return;
        }

        frame.captured = result;
        PatchRuntime.incrementItemDrainCaptures();
    }

    public static Optional<?> takeForSimulation(Level level, ItemStack stack, boolean simulate) {
        if (!simulate || !PatchRuntime.isItemDrainPatchEnabled()) {
            return null;
        }
        Frame frame = currentFrame();
        if (frame == null || frame.captured == null || frame.consumed) {
            return null;
        }

        try {
            if (frame.level != level
                || frame.recipeManager != level.getRecipeManager()
                || frame.recipeView != frame.recipeManager.getRecipes()
                || frame.stack != stack
                || frame.stackSnapshot == null
                || frame.stackSnapshot.getCount() != stack.getCount()
                || !ItemStack.isSameItemSameComponents(frame.stackSnapshot, stack)) {
                PatchRuntime.incrementItemDrainFallbacks();
                return null;
            }
        } catch (RuntimeException exception) {
            PatchRuntime.incrementItemDrainFallbacks();
            return null;
        }

        frame.consumed = true;
        PatchRuntime.incrementItemDrainReuses();
        return frame.captured;
    }

    private static Frame currentFrame() {
        FrameState state = FRAME_STATES.get();
        if (state == null || state.active.isEmpty()) {
            return null;
        }
        return state.active.peek();
    }

    private static void clearProbe(Frame frame) {
        frame.level = null;
        frame.recipeManager = null;
        frame.recipeView = null;
        frame.stack = null;
        frame.stackSnapshot = null;
        frame.captured = null;
        frame.consumed = false;
        frame.probeActive = false;
        frame.canDepth = 0;
    }

    private static final class Frame {

        private Level level;
        private RecipeManager recipeManager;
        private Object recipeView;
        private ItemStack stack;
        private ItemStack stackSnapshot;
        private Optional<?> captured;
        private boolean consumed;
        private boolean probeActive;
        private int canDepth;
    }

    private static final class FrameState {

        private final Deque<Frame> active = new ArrayDeque<>();
        private final Deque<Frame> free = new ArrayDeque<>();
    }
}
