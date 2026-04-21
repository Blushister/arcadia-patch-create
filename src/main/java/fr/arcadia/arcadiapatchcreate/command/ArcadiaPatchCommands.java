package fr.arcadia.arcadiapatchcreate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.arcadia.arcadiapatchcreate.debug.AdminDebugReporter;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime.ThrottleMode;
import fr.arcadia.arcadiapatchcreate.menu.ArcadiaPatchAdminMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ArcadiaPatchCommands {

    private ArcadiaPatchCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("arcadiapatchcreate")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("panel")
                    .executes(ArcadiaPatchCommands::openPanel))
                .then(Commands.literal("status")
                    .executes(ArcadiaPatchCommands::status))
                .then(Commands.literal("master")
                    .then(Commands.literal("status")
                        .executes(ArcadiaPatchCommands::masterStatus))
                    .then(Commands.literal("enabled")
                        .executes(context -> setMasterEnabled(context, PatchRuntime.isMasterPatchEnabled()))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> setMasterEnabled(context, BoolArgumentType.getBool(context, "value"))))))
                .then(Commands.literal("belt")
                    .then(Commands.literal("status")
                        .executes(ArcadiaPatchCommands::beltStatus))
                    .then(Commands.literal("enabled")
                        .executes(context -> setBeltEnabled(context, PatchRuntime.isBeltPatchConfiguredEnabled()))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> setBeltEnabled(context, BoolArgumentType.getBool(context, "value"))))))
                .then(Commands.literal("fluid")
                    .then(Commands.literal("status")
                        .executes(ArcadiaPatchCommands::fluidStatus))
                    .then(Commands.literal("enabled")
                        .executes(context -> setFluidEnabled(context, PatchRuntime.isFluidPatchConfiguredEnabled()))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> setFluidEnabled(context, BoolArgumentType.getBool(context, "value"))))))
                .then(Commands.literal("chute")
                    .then(Commands.literal("status")
                        .executes(ArcadiaPatchCommands::chuteStatus))
                    .then(Commands.literal("enabled")
                        .executes(context -> setChuteEnabled(context, PatchRuntime.isChutePatchConfiguredEnabled()))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> setChuteEnabled(context, BoolArgumentType.getBool(context, "value"))))))
                .then(Commands.literal("createDrops")
                    .then(Commands.literal("status")
                        .executes(ArcadiaPatchCommands::createDropsStatus))
                    .then(Commands.literal("enabled")
                        .executes(context -> setCreateDropsEnabled(context, PatchRuntime.isCreatePhysicalItemsFastDespawnConfiguredEnabled()))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> setCreateDropsEnabled(context, BoolArgumentType.getBool(context, "value")))))
                    .then(Commands.literal("seconds")
                        .then(Commands.argument("value", IntegerArgumentType.integer(5, 600))
                            .executes(context -> setCreateDropsSeconds(
                                context,
                                IntegerArgumentType.getInteger(context, "value")
                            )))))
                .then(Commands.literal("throttle")
                    .then(Commands.literal("status")
                        .executes(ArcadiaPatchCommands::throttleStatus))
                    .then(Commands.literal("mode")
                        .then(Commands.literal("off")
                            .executes(context -> setGlobalThrottleMode(context, ThrottleMode.OFF)))
                        .then(Commands.literal("adaptive")
                            .executes(context -> setGlobalThrottleMode(context, ThrottleMode.ADAPTIVE)))
                        .then(Commands.literal("static")
                            .then(Commands.argument("interval", IntegerArgumentType.integer(1, 5))
                                .executes(context -> setGlobalStaticInterval(
                                    context,
                                    IntegerArgumentType.getInteger(context, "interval")
                                )))))
                    .then(Commands.literal("simulateMspt")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 200.0D))
                            .executes(context -> setSimulatedMspt(
                                context,
                                DoubleArgumentType.getDouble(context, "value")
                            ))))
                    .then(Commands.literal("clearSimulatedMspt")
                        .executes(ArcadiaPatchCommands::clearSimulatedMspt)))
                .then(Commands.literal("factoryGauge")
                    .then(Commands.literal("status")
                        .executes(ArcadiaPatchCommands::factoryGaugeStatus))
                    .then(Commands.literal("enabled")
                        .executes(context -> setFactoryGaugeEnabled(context, PatchRuntime.isFactoryGaugeConfiguredEnabled()))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> setFactoryGaugeEnabled(context, BoolArgumentType.getBool(context, "value"))))))
                .then(Commands.literal("debug")
                    .then(Commands.literal("dump")
                        .executes(ArcadiaPatchCommands::debugDump)))
        );
    }

    private static int openPanel(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Only players can open the Arcadia Patch panel."));
            return 0;
        }

        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new ArcadiaPatchAdminMenu(containerId, inventory),
            Component.literal("Arcadia Patch Create")
        ));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double mspt = PatchRuntime.getCurrentMspt(source.getServer());
        int interval = PatchRuntime.resolveGlobalInterval(source.getServer());
        sendSuccess(
            source,
            "Arcadia Patch Create status | belt=" + PatchRuntime.isBeltPatchEnabled()
                + " master=" + PatchRuntime.isMasterPatchEnabled()
                + " skips=" + PatchRuntime.getBeltSkips()
                + " | fluid=" + PatchRuntime.isFluidPatchEnabled()
                + " skips=" + PatchRuntime.getFluidSkips()
                + " failures=" + PatchRuntime.getFluidInspectionFailures()
                + " | factoryGauge=" + PatchRuntime.isFactoryGaugeEnabled()
                + " skips=" + PatchRuntime.getFactoryGaugeSkips()
                + " forced=" + PatchRuntime.getFactoryGaugeForcedRuns()
                + " | chute=" + PatchRuntime.isChutePatchEnabled()
                + " probeSkips=" + PatchRuntime.getChuteProbeSkips()
                + " | createDrops=" + PatchRuntime.isCreatePhysicalItemsFastDespawnEnabled()
                + " despawn=" + (PatchRuntime.getCreatePhysicalItemsDespawnTicks() / 20) + "s"
                + " marked=" + PatchRuntime.getCreatePhysicalItemMarks()
                + " | throttle=" + PatchRuntime.describeGlobalThrottleMode()
                + " interval=" + interval
                + " skips=" + PatchRuntime.getFactoryGaugeSkips()
                + " forced=" + PatchRuntime.getFactoryGaugeForcedRuns()
                + " | mspt=" + String.format(java.util.Locale.ROOT, "%.2f", mspt)
        );
        return 1;
    }

    private static int factoryGaugeStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double mspt = PatchRuntime.getCurrentMspt(source.getServer());
        int interval = PatchRuntime.resolveGlobalInterval(source.getServer());
        sendSuccess(
            source,
            "Factory Gauge patch | configured=" + PatchRuntime.isFactoryGaugeConfiguredEnabled()
                + " effective=" + PatchRuntime.isFactoryGaugeEnabled()
                + " | globalThrottle=" + PatchRuntime.describeGlobalThrottleMode()
                + " interval=" + interval
                + " simulatedMspt=" + (PatchRuntime.getSimulatedMspt() == null ? "off" : PatchRuntime.getSimulatedMspt())
                + " currentMspt=" + String.format(java.util.Locale.ROOT, "%.2f", mspt)
                + " skips=" + PatchRuntime.getFactoryGaugeSkips()
                + " forced=" + PatchRuntime.getFactoryGaugeForcedRuns()
        );
        return 1;
    }

    private static int beltStatus(CommandContext<CommandSourceStack> context) {
        sendSuccess(
            context.getSource(),
            "Belt patch | enabled=" + PatchRuntime.isBeltPatchEnabled() + " skips=" + PatchRuntime.getBeltSkips()
        );
        return 1;
    }

    private static int fluidStatus(CommandContext<CommandSourceStack> context) {
        sendSuccess(
            context.getSource(),
            "Fluid patch | enabled=" + PatchRuntime.isFluidPatchEnabled()
                + " skips=" + PatchRuntime.getFluidSkips()
                + " failures=" + PatchRuntime.getFluidInspectionFailures()
        );
        return 1;
    }

    private static int masterStatus(CommandContext<CommandSourceStack> context) {
        sendSuccess(
            context.getSource(),
            "Master patch switch | enabled=" + PatchRuntime.isMasterPatchEnabled()
        );
        return 1;
    }

    private static int chuteStatus(CommandContext<CommandSourceStack> context) {
        sendSuccess(
            context.getSource(),
            "Chute patch | enabled=" + PatchRuntime.isChutePatchEnabled()
                + " probeSkips=" + PatchRuntime.getChuteProbeSkips()
        );
        return 1;
    }

    private static int throttleStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        sendSuccess(
            source,
            "Global throttle | mode=" + PatchRuntime.describeGlobalThrottleMode()
                + " interval=" + PatchRuntime.resolveGlobalInterval(source.getServer())
                + " simulatedMspt=" + (PatchRuntime.getSimulatedMspt() == null ? "off" : PatchRuntime.getSimulatedMspt())
                + " currentMspt=" + String.format(java.util.Locale.ROOT, "%.2f", PatchRuntime.getCurrentMspt(source.getServer()))
        );
        return 1;
    }

    private static int setFactoryGaugeEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        PatchRuntime.setFactoryGaugeEnabled(enabled);
        sendSuccess(context.getSource(), "Set Factory Gauge patch enabled=" + enabled);
        return 1;
    }

    private static int createDropsStatus(CommandContext<CommandSourceStack> context) {
        sendSuccess(
            context.getSource(),
            "Create physical drops | enabled=" + PatchRuntime.isCreatePhysicalItemsFastDespawnEnabled()
                + " despawn=" + (PatchRuntime.getCreatePhysicalItemsDespawnTicks() / 20) + "s"
                + " marked=" + PatchRuntime.getCreatePhysicalItemMarks()
        );
        return 1;
    }

    private static int debugDump(CommandContext<CommandSourceStack> context) {
        AdminDebugReporter.sendToSource(context.getSource());
        return 1;
    }

    private static int setMasterEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        PatchRuntime.setMasterPatchEnabled(enabled);
        sendSuccess(context.getSource(), "Set master patch switch enabled=" + enabled);
        return 1;
    }

    private static int setBeltEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        PatchRuntime.setBeltPatchEnabled(enabled);
        sendSuccess(context.getSource(), "Set belt patch enabled=" + enabled);
        return 1;
    }

    private static int setFluidEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        PatchRuntime.setFluidPatchEnabled(enabled);
        sendSuccess(context.getSource(), "Set fluid patch enabled=" + enabled);
        return 1;
    }

    private static int setChuteEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        PatchRuntime.setChutePatchEnabled(enabled);
        sendSuccess(context.getSource(), "Set chute patch enabled=" + enabled);
        return 1;
    }

    private static int setGlobalThrottleMode(CommandContext<CommandSourceStack> context, ThrottleMode mode) {
        PatchRuntime.setGlobalThrottleMode(mode);
        sendSuccess(context.getSource(), "Set global throttle mode=" + mode.name().toLowerCase(java.util.Locale.ROOT));
        return 1;
    }

    private static int setGlobalStaticInterval(CommandContext<CommandSourceStack> context, int interval) {
        PatchRuntime.setGlobalThrottleMode(ThrottleMode.STATIC);
        PatchRuntime.setGlobalStaticInterval(interval);
        sendSuccess(
            context.getSource(),
            "Set global throttle mode=static interval=" + PatchRuntime.getGlobalStaticInterval()
        );
        return 1;
    }

    private static int setCreateDropsEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        PatchRuntime.setCreatePhysicalItemsFastDespawnEnabled(enabled);
        sendSuccess(context.getSource(), "Set Create physical drop fast despawn enabled=" + enabled);
        return 1;
    }

    private static int setCreateDropsSeconds(CommandContext<CommandSourceStack> context, int seconds) {
        PatchRuntime.setCreatePhysicalItemsDespawnTicks(seconds * 20);
        PatchRuntime.setCreatePhysicalItemsFastDespawnEnabled(true);
        sendSuccess(context.getSource(), "Set Create physical drop despawn to " + seconds + "s");
        return 1;
    }

    private static int setSimulatedMspt(CommandContext<CommandSourceStack> context, double mspt) {
        PatchRuntime.setSimulatedMspt(mspt);
        sendSuccess(context.getSource(), "Set simulated mspt=" + String.format(java.util.Locale.ROOT, "%.2f", mspt));
        return 1;
    }

    private static int clearSimulatedMspt(CommandContext<CommandSourceStack> context) {
        PatchRuntime.clearSimulatedMspt();
        sendSuccess(context.getSource(), "Cleared simulated mspt override.");
        return 1;
    }

    private static void sendSuccess(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), true);
    }
}
