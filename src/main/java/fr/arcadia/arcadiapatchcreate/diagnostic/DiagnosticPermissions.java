package fr.arcadia.arcadiapatchcreate.diagnostic;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Permission for the diagnostic tool, resolvable by LuckPerms.
 *
 * <p>The node defaults to operator level, so behaviour is unchanged where no permission mod is
 * installed. With LuckPerms, a moderator or helper group can be granted
 * {@code arcadia_patch_create.diagnose} without being an operator.
 *
 * <p>Deliberately scoped to the tool only: the admin panel and the module toggles stay operator
 * territory, so nobody can disable a production patch by accident while investigating a block.
 */
public final class DiagnosticPermissions {

    public static final PermissionNode<Boolean> DIAGNOSE = new PermissionNode<>(
        "arcadia_patch_create",
        "diagnose",
        PermissionTypes.BOOLEAN,
        (player, uuid, context) -> player != null && player.hasPermissions(2)
    );

    private DiagnosticPermissions() {
    }

    public static void register(PermissionGatherEvent.Nodes event) {
        event.addNodes(DIAGNOSE);
    }

    /**
     * Nodes accepted for the diagnostic tool, in order of preference.
     *
     * <p>The first one follows the convention already used across the suite - the admin panel
     * stores {@code arcadia.adminpanel.ban} and friends - so staff can grant it the same way as
     * everything else. The second is the string form of the NeoForge node, kept so a group
     * configured before this change keeps working.
     *
     * <p>Holding any of them is enough.
     */
    public static final String[] NODES = {
        "arcadia.patchcreate.diagnose",
        "arcadia_patch_create.diagnose"
    };

    /** The node to advertise in messages and logs. */
    public static final String NODE = NODES[0];

    /**
     * Resolution order: LuckPerms directly, then the NeoForge API in case another permission mod
     * handles it, then operator level - the behaviour on a server with no permission mod at all.
     */
    public static boolean canDiagnose(ServerPlayer player) {
        boolean deniedSomewhere = false;
        for (String node : NODES) {
            Boolean granted = LuckPermsLookup.check(player.getUUID(), node);
            if (Boolean.TRUE.equals(granted)) {
                return true;
            }
            if (Boolean.FALSE.equals(granted)) {
                deniedSomewhere = true;
            }
        }
        // An explicit deny on a node wins; only an entirely undefined permission falls through.
        if (deniedSomewhere) {
            return false;
        }
        try {
            if (PermissionAPI.getPermission(player, DIAGNOSE)) {
                return true;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // No permission handler registered; fall through to the operator check.
        }
        return player.hasPermissions(2);
    }
}
