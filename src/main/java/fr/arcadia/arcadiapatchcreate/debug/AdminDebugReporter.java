package fr.arcadia.arcadiapatchcreate.debug;

import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AdminDebugReporter {

    private AdminDebugReporter() {
    }

    public static void sendToSource(CommandSourceStack source) {
        double mspt = PatchRuntime.getCurrentMspt(source.getServer());
        int interval = PatchRuntime.resolveGlobalInterval(source.getServer());
        sendLine(source, "Arcadia Patch Create - debug dump");
        sendLine(source, "Master: " + onOff(PatchRuntime.isMasterPatchEnabled()));
        sendLine(
            source,
            "Throttle: mode=" + PatchRuntime.describeGlobalThrottleMode()
                + " interval=" + interval
                + " simulatedMspt=" + (PatchRuntime.getSimulatedMspt() == null ? "off" : format(PatchRuntime.getSimulatedMspt()))
                + " currentMspt=" + format(mspt)
        );
        sendLine(
            source,
            "Belt: configured=" + onOff(PatchRuntime.isBeltPatchConfiguredEnabled())
                + " available=" + yesNo(PatchRuntime.isBeltPatchAvailable())
                + " effective=" + onOff(PatchRuntime.isBeltPatchEnabled())
                + " skips=" + compact(PatchRuntime.getBeltSkips())
        );
        sendLine(
            source,
            "Fluid: configured=" + onOff(PatchRuntime.isFluidPatchConfiguredEnabled())
                + " available=" + yesNo(PatchRuntime.isFluidPatchAvailable())
                + " effective=" + onOff(PatchRuntime.isFluidPatchEnabled())
                + " skips=" + compact(PatchRuntime.getFluidSkips())
                + " failures=" + compact(PatchRuntime.getFluidInspectionFailures())
                + " compactions=" + compact(PatchRuntime.getFluidMapCompactions())
        );
        sendLine(
            source,
            "Factory Gauge: configured=" + onOff(PatchRuntime.isFactoryGaugeConfiguredEnabled())
                + " effective=" + onOff(PatchRuntime.isFactoryGaugeEnabled())
                + " interval=" + PatchRuntime.resolveGlobalInterval(source.getServer())
                + " skips=" + compact(PatchRuntime.getFactoryGaugeSkips())
                + " forced=" + compact(PatchRuntime.getFactoryGaugeForcedRuns())
        );
        sendLine(
            source,
            "CreateHeatJS: configured=" + onOff(PatchRuntime.isHeatJsPatchConfiguredEnabled())
                + " available=" + yesNo(PatchRuntime.isHeatJsPatchAvailable())
                + " effective=" + onOff(PatchRuntime.isHeatJsPatchEnabled())
                + " hits=" + compact(PatchRuntime.getHeatJsCacheHits())
                + " misses=" + compact(PatchRuntime.getHeatJsCacheMisses())
                + " invalidations=" + compact(PatchRuntime.getHeatJsCacheInvalidations())
                + " failures=" + compact(PatchRuntime.getHeatJsFailures())
        );
        sendLine(
            source,
            "Item Drain: configured=" + onOff(PatchRuntime.isItemDrainPatchConfiguredEnabled())
                + " available=" + yesNo(PatchRuntime.isItemDrainPatchAvailable())
                + " effective=" + onOff(PatchRuntime.isItemDrainPatchEnabled())
                + " captures=" + compact(PatchRuntime.getItemDrainCaptures())
                + " reuses=" + compact(PatchRuntime.getItemDrainReuses())
                + " fallbacks=" + compact(PatchRuntime.getItemDrainFallbacks())
        );
        sendLine(
            source,
            "Behaviour dispatch: configured=" + onOff(PatchRuntime.isBehaviourDispatchPatchConfiguredEnabled())
                + " available=" + yesNo(PatchRuntime.isBehaviourDispatchPatchAvailable())
                + " effective=" + onOff(PatchRuntime.isBehaviourDispatchPatchEnabled())
                + " ticks=" + compact(PatchRuntime.getBehaviourDispatches())
        );
        sendLine(
            source,
            "Crafter signal: configured=" + onOff(PatchRuntime.isCrafterSignalPatchConfiguredEnabled())
                + " available=" + yesNo(PatchRuntime.isCrafterSignalPatchAvailable())
                + " effective=" + onOff(PatchRuntime.isCrafterSignalPatchEnabled())
                + " reuses=" + compact(PatchRuntime.getCrafterSignalReuses())
                + " reads=" + compact(PatchRuntime.getCrafterSignalReads())
                + " invalidations=" + compact(PatchRuntime.getCrafterSignalInvalidations())
        );
        sendLine(
            source,
            "Create Drops: configured=" + onOff(PatchRuntime.isCreatePhysicalItemsFastDespawnConfiguredEnabled())
                + " effective=" + onOff(PatchRuntime.isCreatePhysicalItemsFastDespawnEnabled())
                + " delay=" + (PatchRuntime.getCreatePhysicalItemsDespawnTicks() / 20) + "s"
                + " marked=" + compact(PatchRuntime.getCreatePhysicalItemMarks())
        );
    }

    public static void sendToPlayer(ServerPlayer player) {
        CommandSourceStack source = player.createCommandSourceStack();
        sendToSource(source);
    }

    private static void sendLine(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private static String yesNo(boolean available) {
        return available ? "YES" : "NO";
    }

    private static String compact(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0D);
        }
        if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0D);
        }
        if (value >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0D);
        }
        return Long.toString(value);
    }
}
