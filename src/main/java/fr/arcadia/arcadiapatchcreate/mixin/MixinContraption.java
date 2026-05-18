package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.Contraption", remap = false)
public abstract class MixinContraption {

    private static final long INVALID_NBT_LOG_INTERVAL = 128L;
    private static final Method CONTRAPTION_TYPE_FROM_TYPE = resolveContraptionTypeLookup();
    private static final AtomicLong INVALID_CONTRAPTION_NBT = new AtomicLong();

    @Inject(method = "fromNBT", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void arcadiaPatchCreate$rejectUnreadableContraptionNbt(
        Level world,
        CompoundTag nbt,
        boolean spawnData,
        CallbackInfoReturnable<Object> cir
    ) {
        if (nbt == null || nbt.isEmpty()) {
            reject(cir, "missing contraption tag", "unknown");
            return;
        }

        String type = nbt.getString("Type");
        if (type == null || type.isBlank()) {
            reject(cir, "missing contraption type", "unknown");
            return;
        }

        if (!canResolveContraptionType(type)) {
            reject(cir, "unknown contraption type", type);
            return;
        }

        if (!hasReadableBlocks(nbt)) {
            reject(cir, "missing or invalid contraption block list", type);
        }
    }

    private static boolean canResolveContraptionType(String type) {
        if (CONTRAPTION_TYPE_FROM_TYPE == null) {
            return true;
        }
        try {
            return CONTRAPTION_TYPE_FROM_TYPE.invoke(null, type) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    private static boolean hasReadableBlocks(CompoundTag nbt) {
        Tag blocks = nbt.get("Blocks");
        if (blocks == null) {
            return false;
        }
        if (blocks.getId() == Tag.TAG_LIST) {
            return true;
        }
        if (blocks.getId() != Tag.TAG_COMPOUND) {
            return false;
        }

        CompoundTag compound = (CompoundTag) blocks;
        if (!compound.contains("Palette", Tag.TAG_LIST) || !compound.contains("BlockList", Tag.TAG_LIST)) {
            return false;
        }

        ListTag palette = compound.getList("Palette", Tag.TAG_COMPOUND);
        ListTag blockList = compound.getList("BlockList", Tag.TAG_COMPOUND);
        if (palette.isEmpty() && !blockList.isEmpty()) {
            return false;
        }

        for (Tag entryTag : blockList) {
            if (!(entryTag instanceof CompoundTag entry)) {
                return false;
            }
            int stateIndex = entry.getInt("State");
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                return false;
            }
            if (!entry.contains("Pos", Tag.TAG_LONG)) {
                return false;
            }
        }

        return true;
    }

    private static Method resolveContraptionTypeLookup() {
        try {
            Class<?> typeClass = Class.forName(
                "com.simibubi.create.api.contraption.ContraptionType",
                false,
                MixinContraption.class.getClassLoader()
            );
            return typeClass.getMethod("fromType", String.class);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void reject(CallbackInfoReturnable<Object> cir, String reason, String type) {
        long invalid = INVALID_CONTRAPTION_NBT.incrementAndGet();
        if (invalid <= 3 || invalid % INVALID_NBT_LOG_INTERVAL == 0) {
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] Rejected unreadable Create contraption NBT #{}: reason={} type={}",
                invalid,
                reason,
                type
            );
        }
        cir.setReturnValue(null);
    }
}
