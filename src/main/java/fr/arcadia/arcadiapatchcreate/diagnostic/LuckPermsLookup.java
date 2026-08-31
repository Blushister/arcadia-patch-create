package fr.arcadia.arcadiapatchcreate.diagnostic;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Asks LuckPerms directly whether a player holds a permission node.
 *
 * <p>NeoForge's {@code PermissionAPI} was not enough in practice: granting the node to a group had
 * no effect, even after a reconnect, which means the NeoForge node was never resolved by LuckPerms
 * on this server. Querying LuckPerms with a plain string node is what its own groups actually store.
 *
 * <p>Everything is reflective so the mod still starts, and still works for operators, when LuckPerms
 * is absent. The whole chain is resolved once and cached; a failure disables this path for the
 * session rather than retrying on every check.
 *
 * <p>Side benefit: every check makes LuckPerms see the node, so it starts offering it in the
 * suggestions of {@code /lp ... permission set} once the tool has been used at least once.
 */
public final class LuckPermsLookup {

    private static volatile boolean resolved;
    private static volatile boolean available;

    private static Method getApi;
    private static Method getUserManager;
    private static Method getUser;
    private static Method loadUser;
    private static Method join;
    private static Method defaultContextualOptions;
    private static Method getCachedData;
    private static Method getPermissionData;
    private static Method checkPermission;
    private static Method asBoolean;
    private static Method tristateName;

    private LuckPermsLookup() {
    }

    public static boolean isAvailable() {
        resolve();
        return available;
    }

    /**
     * Verifies at startup that LuckPerms really answers, instead of finding out lazily on the first
     * command. A lazy check logs nothing until someone succeeds, which makes a failure impossible to
     * diagnose - exactly what happened on this server.
     */
    public static void probeAtStartup() {
        resolve();
        if (!available) {
            ArcadiaPatchCreate.LOGGER.info(
                "[ArcadiaPatchCreate] LuckPerms not found. The diagnostic tool requires operator level."
            );
            return;
        }
        try {
            Object api = getApi.invoke(null);
            getUserManager.invoke(api);
            ArcadiaPatchCreate.LOGGER.info(
                "[ArcadiaPatchCreate] LuckPerms detected - grant '{}' to a group to open the diagnostic tool.",
                DiagnosticPermissions.NODE
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            available = false;
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] LuckPerms is present but did not answer. "
                    + "The diagnostic tool falls back to operator level.",
                exception
            );
        }
    }

    /**
     * @return {@code TRUE} or {@code FALSE} when LuckPerms has an explicit value for the node,
     *         {@code null} when it is undefined or LuckPerms is unavailable - in which case the
     *         caller falls back to the operator check.
     */
    public static Boolean check(UUID playerId, String node) {
        resolve();
        if (!available || playerId == null) {
            return null;
        }
        try {
            Object api = getApi.invoke(null);
            Object userManager = getUserManager.invoke(api);
            Object user = getUser.invoke(userManager, playerId);
            if (user == null) {
                // getUser only returns cached users. An offline or not-yet-loaded player comes back
                // null, which is what silently broke this before: the check gave up and fell back to
                // operator level. loadUser fetches them for real.
                user = join.invoke(loadUser.invoke(userManager, playerId));
            }
            if (user == null) {
                return null;
            }
            Object cached = getCachedData.invoke(user);
            Object queryOptions = defaultContextualOptions.invoke(null);
            Object permissionData = getPermissionData.invoke(cached, queryOptions);
            Object tristate = checkPermission.invoke(permissionData, node);
            if (tristate == null) {
                return null;
            }
            // Tristate has no isUndefined(); the constants are TRUE / FALSE / UNDEFINED and
            // asBoolean() collapses UNDEFINED to false. Distinguish it by name so an unset node
            // falls through to the operator check instead of being read as an explicit deny.
            if ("UNDEFINED".equals(tristateName.invoke(tristate))) {
                return null;
            }
            return (boolean) asBoolean.invoke(tristate);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            available = false;
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] LuckPerms lookup failed, falling back to operator level.",
                exception
            );
            return null;
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (LuckPermsLookup.class) {
            if (resolved) {
                return;
            }
            resolved = true;
            try {
                ClassLoader loader = LuckPermsLookup.class.getClassLoader();
                Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider", false, loader);
                Class<?> luckPerms = Class.forName("net.luckperms.api.LuckPerms", false, loader);
                Class<?> userManager = Class.forName("net.luckperms.api.model.user.UserManager", false, loader);
                Class<?> user = Class.forName("net.luckperms.api.model.user.User", false, loader);
                Class<?> cachedData = Class.forName("net.luckperms.api.cacheddata.CachedDataManager", false, loader);
                Class<?> permissionData =
                    Class.forName("net.luckperms.api.cacheddata.CachedPermissionData", false, loader);
                Class<?> tristate = Class.forName("net.luckperms.api.util.Tristate", false, loader);

                getApi = provider.getMethod("get");
                getUserManager = luckPerms.getMethod("getUserManager");
                getUser = userManager.getMethod("getUser", UUID.class);
                getCachedData = user.getMethod("getCachedData");
                Class<?> queryOptions = Class.forName("net.luckperms.api.query.QueryOptions", false, loader);
                loadUser = userManager.getMethod("loadUser", UUID.class);
                join = java.util.concurrent.CompletableFuture.class.getMethod("join");
                defaultContextualOptions = queryOptions.getMethod("defaultContextualOptions");
                getPermissionData = cachedData.getMethod("getPermissionData", queryOptions);
                checkPermission = permissionData.getMethod("checkPermission", String.class);
                asBoolean = tristate.getMethod("asBoolean");
                tristateName = tristate.getMethod("name");
                available = true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                available = false;
            }
        }
    }
}
