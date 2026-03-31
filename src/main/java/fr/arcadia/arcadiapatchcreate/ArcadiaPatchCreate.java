package fr.arcadia.arcadiapatchcreate;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ArcadiaPatchCreate.MOD_ID)
public class ArcadiaPatchCreate {

    public static final String MOD_ID = "arcadia_patch_create";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public ArcadiaPatchCreate(IEventBus modEventBus) {
        LOGGER.info("[ArcadiaPatchCreate] Enabled validated Create performance patches.");
    }
}
