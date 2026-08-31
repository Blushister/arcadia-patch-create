package fr.arcadia.arcadiapatchcreate;

import fr.arcadia.arcadiapatchcreate.command.ArcadiaPatchCommands;
import fr.arcadia.arcadiapatchcreate.runtime.CreatePhysicalItemSupport;
import fr.arcadia.arcadiapatchcreate.runtime.PatchConfigStore;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ArcadiaPatchCreate.MOD_ID)
public class ArcadiaPatchCreate {

    public static final String MOD_ID = "arcadia_patch_create";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public ArcadiaPatchCreate(IEventBus modEventBus) {
        PatchConfigStore.loadIntoRuntime();
        NeoForge.EVENT_BUS.addListener(ArcadiaPatchCommands::register);
        NeoForge.EVENT_BUS.addListener(ArcadiaPatchCreate::onEntityJoinLevel);
        LOGGER.info("[ArcadiaPatchCreate] Enabled validated Create performance patches.");
        LOGGER.info(
            "[ArcadiaPatchCreate] Target availability: belt={}, fluid={}, createHeatJs={}, itemDrain={}, dispatch={}, crafter={}, redstoneLink={}, capabilityGuard={}.",
            PatchRuntime.isBeltPatchAvailable(),
            PatchRuntime.isFluidPatchAvailable(),
            PatchRuntime.isHeatJsPatchAvailable(),
            PatchRuntime.isItemDrainPatchAvailable(),
            PatchRuntime.isBehaviourDispatchPatchAvailable(),
            PatchRuntime.isCrafterSignalPatchAvailable(),
            PatchRuntime.isRedstoneLinkPatchAvailable(),
            PatchRuntime.isCapabilityGuardAvailable()
        );
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (CreatePhysicalItemSupport.shouldMark(event.getEntity())) {
            CreatePhysicalItemSupport.mark(event.getEntity());
        }
    }
}
