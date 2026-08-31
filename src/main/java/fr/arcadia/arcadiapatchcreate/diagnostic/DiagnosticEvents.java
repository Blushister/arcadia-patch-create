package fr.arcadia.arcadiapatchcreate.diagnostic;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Wires the diagnostic tool: command, interaction handling and the analysis countdown. */
public final class DiagnosticEvents {

    private DiagnosticEvents() {
    }

    public static void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(DiagnosticEvents::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(DiagnosticEvents::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(DiagnosticEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(DiagnosticPermissions::register);
        // Probed on server start, not during mod loading: LuckPerms is not ready before that.
        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStartedEvent event) -> LuckPermsLookup.probeAtStartup()
        );
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            // Separate from /arcadiapatchcreate on purpose: that tree is operator-only, this one
            // is open to whoever holds the LuckPerms node.
            //
            // No requires() filter here on purpose. Brigadier sends the command tree to the client
            // when it connects and evaluates requires() at that moment, so a player already online
            // when a permission is granted keeps the old tree and the command stays invisible to
            // them. Checking on execution instead means the grant takes effect immediately, and a
            // player without the node gets a clear refusal rather than a command that does not exist.
            Commands.literal("arcadiadiag")
                .executes(DiagnosticEvents::giveTool)
        );
    }

    private static int giveTool(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Commande réservée aux joueurs."));
            return 0;
        }
        if (!DiagnosticPermissions.canDiagnose(player)) {
            context.getSource().sendFailure(Component.literal(
                "Permission requise : " + String.join("  ou  ", DiagnosticPermissions.NODES)));
            return 0;
        }
        if (!player.getInventory().add(DiagnosticTool.create())) {
            player.sendSystemMessage(Component.literal("Ton inventaire est plein.")
                .withStyle(ChatFormatting.RED));
            return 0;
        }
        player.sendSystemMessage(Component.literal(
            "Outil de diagnostic reçu. Clic gauche sur une machine pour l'analyser.")
            .withStyle(ChatFormatting.AQUA));
        return 1;
    }

    /**
     * A left click normally breaks the block. The event is cancelled before anything else so the
     * tool can never destroy what it is asked to inspect.
     */
    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide
            || !(event.getEntity() instanceof ServerPlayer player)
            || !DiagnosticTool.isTool(event.getItemStack())) {
            return;
        }

        event.setCanceled(true);
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);

        if (!DiagnosticPermissions.canDiagnose(player)) {
            player.sendSystemMessage(Component.literal("Tu n'as pas la permission d'utiliser cet outil.")
                .withStyle(ChatFormatting.RED));
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        player.getCooldowns().addCooldown(event.getItemStack().getItem(), DiagnosticTool.cooldownTicks());
        DiagnosticTool.begin(player, level, event.getPos());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (DiagnosticTool.isBusy()) {
            DiagnosticTool.tick();
        }
    }
}
