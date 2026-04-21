package fr.arcadia.arcadiapatchcreate.runtime;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

public final class PatchConfigStore {

    private static final String FILE_NAME = "arcadia-patch-create.properties";

    private PatchConfigStore() {
    }

    public static void loadIntoRuntime() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            saveFromRuntime();
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            PatchRuntime.applyPersistedState(
                getBoolean(properties, "master.enabled", true),
                getBoolean(properties, "belt.enabled", true),
                getBoolean(properties, "fluid.enabled", true),
                getBoolean(properties, "factoryGauge.enabled", true),
                getBoolean(properties, "chute.enabled", true),
                getBoolean(properties, "createDrops.enabled", false),
                getMode(properties.getProperty("throttle.mode", "OFF")),
                getInt(properties, "throttle.staticInterval", 2, 1, 5),
                getInt(properties, "createDrops.despawnTicks", 1200, 100, 12000)
            );
        } catch (IOException e) {
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] Could not load persisted config from {}. Using runtime defaults.",
                path,
                e
            );
        }
    }

    public static void saveFromRuntime() {
        Path path = getConfigPath();
        Properties properties = new Properties();
        properties.setProperty("master.enabled", Boolean.toString(PatchRuntime.isMasterPatchEnabled()));
        properties.setProperty("belt.enabled", Boolean.toString(PatchRuntime.isBeltPatchConfiguredEnabled()));
        properties.setProperty("fluid.enabled", Boolean.toString(PatchRuntime.isFluidPatchConfiguredEnabled()));
        properties.setProperty("factoryGauge.enabled", Boolean.toString(PatchRuntime.isFactoryGaugeConfiguredEnabled()));
        properties.setProperty("chute.enabled", Boolean.toString(PatchRuntime.isChutePatchConfiguredEnabled()));
        properties.setProperty(
            "createDrops.enabled",
            Boolean.toString(PatchRuntime.isCreatePhysicalItemsFastDespawnConfiguredEnabled())
        );
        properties.setProperty("throttle.mode", PatchRuntime.getGlobalThrottleMode().name());
        properties.setProperty("throttle.staticInterval", Integer.toString(PatchRuntime.getGlobalStaticInterval()));
        properties.setProperty("createDrops.despawnTicks", Integer.toString(PatchRuntime.getCreatePhysicalItemsDespawnTicks()));

        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Arcadia Patch Create runtime settings");
            }
        } catch (IOException e) {
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] Could not save persisted config to {}.",
                path,
                e
            );
        }
    }

    private static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static boolean getBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int getInt(Properties properties, String key, int fallback, int min, int max) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static PatchRuntime.ThrottleMode getMode(String raw) {
        try {
            return PatchRuntime.ThrottleMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return PatchRuntime.ThrottleMode.OFF;
        }
    }
}
