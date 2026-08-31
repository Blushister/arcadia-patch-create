package fr.arcadia.arcadiapatchcreate.diagnostic;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Turns a measurement into a verdict a moderator can act on.
 *
 * <p>The number alone says nothing: a block at 400 us uses under 1% of the tick budget. What matters
 * is whether this block is abnormal <em>for its kind</em> - a crafter at 118 us when its 542 peers
 * sit at 1 us has a reason - and how crowded its chunk is, which is what actually makes a server
 * struggle.
 */
public final class BlockDiagnostics {

    /** A tick is 50 ms; anything under this share of it is noise on its own. */
    private static final double TICK_BUDGET_MICROS = 50_000.0D;
    private static final int CROWDED_CHUNK = 1_000;
    private static final double ANOMALY_FACTOR = 10.0D;

    private BlockDiagnostics() {
    }

    public static void report(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        LevelChunk chunk = level.getChunkAt(pos);
        int chunkBlockEntities = chunk.getBlockEntities().size();

        line(player, "");
        header(player, "Analyse — " + blockId);
        field(player, "Position", pos.getX() + " " + pos.getY() + " " + pos.getZ()
            + "  (chunk " + (pos.getX() >> 4) + ", " + (pos.getZ() >> 4) + ")");

        if (blockEntity == null) {
            field(player, "Machine", "aucune — ce bloc ne tick pas");
            verdict(player, ChatFormatting.GREEN, "NE PAS REMONTER",
                "Un bloc sans machine ne consomme aucun temps serveur.");
            reportChunk(player, chunkBlockEntities);
            return;
        }

        boolean createBlock = CreateBlockInspector.isCreateBlockEntity(blockEntity);
        if (!createBlock) {
            field(player, "Machine", blockEntity.getType().builtInRegistryHolder().key().location().toString());
            field(player, "Mesure", "indisponible — ce bloc n'appartient pas à Create");
            verdict(player, ChatFormatting.YELLOW, "HORS PÉRIMÈTRE",
                "Ce mod n'analyse que les machines Create. Signale le chunk si besoin.");
            reportChunk(player, chunkBlockEntities);
            return;
        }

        double micros = DiagnosticProbe.averageMicros();
        int samples = DiagnosticProbe.samples();
        if (samples == 0 || micros < 0.0D) {
            field(player, "Mesure", "la machine n'a pas tické pendant l'analyse");
            verdict(player, ChatFormatting.GREEN, "NE PAS REMONTER",
                "Une machine qui ne tick pas ne coûte rien.");
            reportChunk(player, chunkBlockEntities);
            return;
        }

        double share = micros / TICK_BUDGET_MICROS * 100.0D;

        field(player, "Coût", format(micros) + " µs/tick   =   " + format(share) + " % du serveur");
        field(player, "Mesures", samples + " ticks");
        double peerMedian = DiagnosticProbe.peerMedianMicros();
        int peers = DiagnosticProbe.peerCount();
        if (peers > 0 && peerMedian >= 0.0D) {
            field(player, "Son type", peers + " autres mesurés, médiane " + format(peerMedian) + " µs");
        }
        CreateBlockInspector.describe(player, blockEntity);

        double ratio = peerMedian > 0.01D ? micros / peerMedian : 1.0D;
        boolean anomaly = peers >= 4 && peerMedian > 0.01D && ratio >= ANOMALY_FACTOR;
        boolean heavy = share >= 2.0D;

        if (anomaly) {
            field(player, "Écart", format(ratio) + "× la normale de son type");
            verdict(player, ChatFormatting.RED, "À REMONTER",
                "Cette machine coûte bien plus que ses semblables : il y a une cause.");
        } else if (heavy) {
            verdict(player, ChatFormatting.RED, "À REMONTER",
                "Cette machine dépasse 2 % du budget serveur à elle seule.");
        } else {
            verdict(player, ChatFormatting.GREEN, "NE PAS REMONTER",
                "Normale pour son type. Une machine qui travaille tick, c'est attendu.");
        }
        reportChunk(player, chunkBlockEntities);
    }

    private static void reportChunk(ServerPlayer player, int blockEntities) {
        if (blockEntities >= CROWDED_CHUNK) {
            field(player, "Ce chunk", blockEntities + " machines");
            verdict(player, ChatFormatting.RED, "CHUNK À SIGNALER",
                "La densité est le vrai facteur de lag, bien avant les pics isolés.");
        } else {
            field(player, "Ce chunk", blockEntities + " machines — densité normale");
        }
        line(player, "");
    }

    private static void header(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("── " + text + " ──")
            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
    }

    private static void field(ServerPlayer player, String label, String value) {
        player.sendSystemMessage(Component.literal("  " + label + " : ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE)));
    }

    private static void verdict(ServerPlayer player, ChatFormatting color, String title, String why) {
        player.sendSystemMessage(Component.literal("  ► " + title)
            .withStyle(color, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("    " + why).withStyle(ChatFormatting.GRAY));
    }

    private static void line(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
