package fr.arcadia.arcadiapatchcreate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime.FactoryGaugeMode;
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
                .then(Commands.literal("belt")
                    .then(Commands.literal("status")
                        .executes(ArcadiaPatchCommands::beltStatus))
                    .then(Commands.literal("enabled")
                        .executes(context -> setBeltEnabled(context, PatchRuntime.isBeltPatchEnabled()))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> setBeltEnabled(context, BoolArgumentType.getBool(context, "value"))))))
                .then(Commands.literal("fluid")
                    .then(Commands.literal("status")
                        .executes(ArcadiaPatchCommands::fluidStatus))
                    .then(Commands.literal("enabled")
                        .executes(context -> setFluidEnabled(context, PatchRuntime.isFluidPatchEnabled()))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> setFluidEnabled(context, BoolArgumentType.getBool(context, "value"))))))
                .then(Commands.literal("factoryGauge")
                    .then(Commands.literal("status")
                        .executes(ArcadiaPatchCommands::factoryGaugeStatus))
                    .then(Commands.literal("mode")
                        .then(Commands.literal("off")
                            .executes(context -> setFactoryGaugeMode(context, FactoryGaugeMode.OFF)))
                        .then(Commands.literal("adaptive")
                            .executes(context -> setFactoryGaugeMode(context, FactoryGaugeMode.ADAPTIVE)))
                        .then(Commands.literal("static")
                            .then(Commands.argument("interval", IntegerArgumentType.integer(1, 5))
                                .executes(context -> setFactoryGaugeStaticInterval(
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
        int interval = PatchRuntime.resolveFactoryGaugeInterval(source.getServer());
        sendSuccess(
            source,
            "Arcadia Patch Create status | belt=" + PatchRuntime.isBeltPatchEnabled()
                + " skips=" + PatchRuntime.getBeltSkips()
                + " | fluid=" + PatchRuntime.isFluidPatchEnabled()
                + " skips=" + PatchRuntime.getFluidSkips()
                + " failures=" + PatchRuntime.getFluidInspectionFailures()
                + " | factoryGauge=" + PatchRuntime.describeFactoryGaugeMode()
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
        int interval = PatchRuntime.resolveFactoryGaugeInterval(source.getServer());
        sendSuccess(
            source,
            "Factory Gauge throttle | mode=" + PatchRuntime.describeFactoryGaugeMode()
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

    private static int setFactoryGaugeMode(CommandContext<CommandSourceStack> context, FactoryGaugeMode mode) {
        PatchRuntime.setFactoryGaugeMode(mode);
        sendSuccess(context.getSource(), "Set Factory Gauge throttle mode=" + mode.name().toLowerCase(java.util.Locale.ROOT));
        return 1;
    }

    private static int setFactoryGaugeStaticInterval(CommandContext<CommandSourceStack> context, int interval) {
        PatchRuntime.setFactoryGaugeMode(FactoryGaugeMode.STATIC);
        PatchRuntime.setFactoryGaugeStaticInterval(interval);
        sendSuccess(
            context.getSource(),
            "Set Factory Gauge throttle mode=static interval=" + PatchRuntime.getFactoryGaugeStaticInterval()
        );
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
