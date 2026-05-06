package fr.faboit.blg.authme;

import fr.faboit.blg.BLGPlugin;
import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Thin wrapper around the AuthMe API.
 *
 * This plugin NEVER stores, verifies, or processes passwords.
 * It only queries AuthMe to detect whether a player is registered,
 * so the UI layer can decide whether to show the Login or Register dialog.
 *
 * Actual login / register is done by executing player commands
 * ({@code /login <pw>} and {@code /register <pw> <pw>}).
 */
public final class AuthMeWrapper {

    private final BLGPlugin plugin;

    public AuthMeWrapper(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Availability ──────────────────────────────────────────────────────────

    /** @return true if the AuthMe plugin is installed and enabled. */
    public boolean isAuthMePresent() {
        Plugin p = Bukkit.getPluginManager().getPlugin("AuthMe");
        return p != null && p.isEnabled();
    }

    /** @return the AuthMe API instance, or null if not available. */
    private AuthMeApi api() {
        if (!isAuthMePresent()) return null;
        try {
            return AuthMeApi.getInstance();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to obtain AuthMeApi instance", e);
            return null;
        }
    }

    // ── Registration check ────────────────────────────────────────────────────

    /**
     * Checks asynchronously whether {@code player} is registered in AuthMe,
     * then calls {@code callback} on the main thread with the result.
     *
     * Falls back to {@code true} (show Login dialog) if AuthMe is unavailable.
     */
    public void isRegistered(Player player, Consumer<Boolean> callback) {
        if (plugin.getConfigManager().asyncAuthmeCheck()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean result = queryRegistered(player);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
            });
        } else {
            callback.accept(queryRegistered(player));
        }
    }

    /** Blocking check – must NOT be called on the main thread if async is desired. */
    private boolean queryRegistered(Player player) {
        AuthMeApi authMeApi = api();
        if (authMeApi == null) {
            plugin.getLogger().warning(
                    "AuthMe API unavailable; defaulting to 'registered=true' for " + player.getName());
            return true;
        }

        if (plugin.getConfigManager().useAuthMeApi()) {
            try {
                return authMeApi.isRegistered(player.getName());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "AuthMe API error while checking registration for " + player.getName(), e);
                return true; // safe fallback
            }
        }

        // Command-based fallback (not async-safe, but left here as reference)
        plugin.getLogger().warning("Command-based AuthMe check is not supported in async mode. " +
                "Set authme.use-api: true in config.");
        return true;
    }

    // ── Command execution helpers ─────────────────────────────────────────────

    /**
     * Makes the player execute the login command (e.g. {@code /login <pw>}).
     * Passwords are NOT logged or stored by this plugin.
     */
    public void performLogin(Player player, String password) {
        String cmd = plugin.getConfigManager().loginCommand()
                .replace("{password}", password);
        player.performCommand(cmd);

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Debug] " + player.getName() + " executed login command.");
        }
    }

    /**
     * Makes the player execute the register command
     * (e.g. {@code /register <pw> <pw>}).
     */
    public void performRegister(Player player, String password, String confirm) {
        String cmd = plugin.getConfigManager().registerCommand()
                .replace("{password}", password)
                .replace("{confirm}", confirm);
        player.performCommand(cmd);

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Debug] " + player.getName() + " executed register command.");
        }
    }
}
