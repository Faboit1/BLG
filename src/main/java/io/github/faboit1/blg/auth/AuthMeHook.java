package io.github.faboit1.blg.auth;

import fr.xephi.authme.api.v3.AuthMeApi;
import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Soft-dependency bridge to AuthMe.
 *
 * <p>BLG uses AuthMe <em>only</em> to determine whether a player already has
 * a registered account.  All password handling, hashing, and authentication
 * logic remain entirely inside AuthMe.
 *
 * <p>If AuthMe is not installed the hook gracefully degrades: all
 * {@link #isRegistered} calls return {@code false} (i.e. the plugin will
 * default to showing the register dialog via {@code /openauto}).
 */
public class AuthMeHook {

    /**
     * Commands that BLG needs to whitelist in AuthMe's allowed-commands list
     * so that unauthenticated players can use dialog buttons without getting
     * blocked by AuthMe's command restriction.
     */
    private static final List<String> BLG_COMMANDS = List.of(
            "/blg_login_submit", "/blg_register_submit",
            "/blg_login_choice", "/blg_register_choice",
            "/blg_auto_choice", "/blg_rules_accept",
            "/blg_rules_leave", "/blg_rules_page",
            "/blg_forgot_password",
            "/openlogin", "/openregister", "/openauto", "/openprelogin"
    );

    private final BLGPlugin plugin;

    /**
     * Live reference to the AuthMe API, or {@code null} if AuthMe is absent.
     */
    private AuthMeApi authMeApi;
    private boolean authenticatedMethodWarningLogged;
    private boolean forceLoginWarningLogged;

    public AuthMeHook(BLGPlugin plugin) {
        this.plugin = plugin;
        tryHook();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the raw {@link AuthMeApi} instance, or {@code null} if AuthMe
     * is not hooked.  Prefer calling the higher-level methods on this class
     * instead of using the API directly.
     */
    public AuthMeApi getAuthMeApi() {
        return authMeApi;
    }

    /**
     * Returns {@code true} if the given player has a registered account in
     * AuthMe.
     *
     * <p>Falls back to {@code false} when AuthMe is unavailable, so that
     * {@code /openauto} shows the register dialog rather than the login
     * dialog.
     *
     * @param player the player to check
     * @return {@code true} if registered in AuthMe, {@code false} otherwise
     */
    public boolean isRegistered(Player player) {
        if (authMeApi == null) {
            return false;
        }
        try {
            return authMeApi.isRegistered(player.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "AuthMe isRegistered() threw an exception for "
                    + player.getName() + ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Returns {@code true} if the given player is currently authenticated in
     * AuthMe.
     *
     * <p>The exact AuthMe API surface varies between builds, so this lookup is
     * performed reflectively against the hooked API instance.
     */
    public boolean isAuthenticated(Player player) {
        if (authMeApi == null) {
            return false;
        }

        try {
            Boolean result = invokeBoolean("isAuthenticated", Player.class, player);
            if (result != null) {
                return result;
            }

            result = invokeBoolean("isAuthenticated", String.class, player.getName());
            if (result != null) {
                return result;
            }

            logUnsupportedAuthenticatedMethod();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "AuthMe isAuthenticated() threw an exception for "
                            + player.getName() + ": " + e.getMessage(), e);
        }

        return false;
    }

    /**
     * Registers the given player in AuthMe with the supplied password.
     *
     * <p>Tries {@code forceRegister(Player, String)} first; falls back to
     * {@code forceRegister(String, String)} for older AuthMe builds.  Returns
     * {@code true} if the call succeeded, {@code false} otherwise.
     */
    public boolean forceRegister(Player player, String password) {
        if (authMeApi == null) {
            return false;
        }
        try {
            try {
                authMeApi.getClass()
                        .getMethod("forceRegister", Player.class, String.class)
                        .invoke(authMeApi, player, password);
                return true;
            } catch (NoSuchMethodException ignored) {
                // Fall through to name-based variant
            }
            try {
                authMeApi.getClass()
                        .getMethod("forceRegister", String.class, String.class)
                        .invoke(authMeApi, player.getName(), password);
                return true;
            } catch (NoSuchMethodException ignored) {
                // Fall through
            }

            plugin.getLogger().warning(
                    "AuthMe API does not expose a supported forceRegister method; "
                            + "auto-register-bedrock will not work.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "AuthMe forceRegister() threw an exception for "
                            + player.getName() + ": " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * Forces the given player into an authenticated state via AuthMe.
     *
     * <p>Tries {@code forceLogin(Player)} first; falls back to
     * {@code forceLogin(String)} with the player's name.  Returns
     * {@code true} if the call succeeded, {@code false} otherwise.
     */
    public boolean forceLogin(Player player) {
        if (authMeApi == null) {
            return false;
        }
        try {
            // Try forceLogin(Player)
            try {
                authMeApi.getClass()
                        .getMethod("forceLogin", Player.class)
                        .invoke(authMeApi, player);
                return true;
            } catch (NoSuchMethodException ignored) {
                // Fall through to name-based variant
            }
            // Try forceLogin(String) – some older AuthMe builds
            try {
                authMeApi.getClass()
                        .getMethod("forceLogin", String.class)
                        .invoke(authMeApi, player.getName());
                return true;
            } catch (NoSuchMethodException ignored) {
                // Fall through
            }

            if (!forceLoginWarningLogged) {
                forceLoginWarningLogged = true;
                plugin.getLogger().warning(
                        "AuthMe API does not expose a supported forceLogin method; "
                                + "auto-authenticate-bedrock will not work.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "AuthMe forceLogin() threw an exception for "
                            + player.getName() + ": " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * Returns {@code true} when the AuthMe plugin was found and hooked
     * successfully.
     */
    public boolean isHooked() {
        return authMeApi != null;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Attempts to obtain the AuthMe API handle via the Bukkit services manager.
     * Tries the registered service provider first (recommended), then falls
     * back to {@link AuthMeApi#getInstance()} for older AuthMe builds.
     */
    private void tryHook() {
        // Prefer the services-manager registration (AuthMe 5.6+)
        try {
            RegisteredServiceProvider<AuthMeApi> provider =
                    plugin.getServer().getServicesManager()
                            .getRegistration(AuthMeApi.class);
            if (provider != null) {
                authMeApi = provider.getProvider();
                plugin.getLogger().info("Hooked into AuthMe via ServicesManager.");
                whitelistCommands();
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE,
                    "ServicesManager AuthMe lookup failed – trying getInstance()", e);
        }

        // Fallback: static singleton (AuthMe < 5.6)
        try {
            authMeApi = AuthMeApi.getInstance();
            if (authMeApi != null) {
                plugin.getLogger().info("Hooked into AuthMe via AuthMeApi.getInstance().");
                whitelistCommands();
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE,
                    "AuthMeApi.getInstance() failed.", e);
        }

        plugin.getLogger().warning(
                "AuthMe not found or could not be hooked. " +
                "/openauto will default to the register dialog.");
    }

    private Boolean invokeBoolean(String methodName, Class<?> parameterType, Object argument) {
        try {
            Object result = authMeApi.getClass()
                    .getMethod(methodName, parameterType)
                    .invoke(authMeApi, argument);
            return result instanceof Boolean bool ? bool : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING,
                    "AuthMe reflective call failed for method '" + methodName
                            + "' with parameter " + parameterType.getSimpleName()
                            + ": " + e.getMessage(), e);
            return null;
        }
    }

    private void logUnsupportedAuthenticatedMethod() {
        if (authenticatedMethodWarningLogged) {
            return;
        }
        authenticatedMethodWarningLogged = true;
        plugin.getLogger().log(Level.WARNING,
                "AuthMe API does not expose a supported isAuthenticated method; "
                        + "autojoin login checks will treat players as unauthenticated.");
    }

    /**
     * Attempts to add BLG commands to AuthMe's allowed-commands list so that
     * unauthenticated players are not blocked from using dialog buttons.
     *
     * <p>This modifies AuthMe's in-memory config.  The change is best-effort:
     * if AuthMe caches the allowed-commands list at startup, the modification
     * may not take effect until AuthMe is reloaded.
     */
    private void whitelistCommands() {
        Plugin authMePlugin = plugin.getServer().getPluginManager().getPlugin("AuthMe");
        if (authMePlugin == null) {
            return;
        }
        try {
            FileConfiguration authMeConfig = authMePlugin.getConfig();
            // AuthMe uses "settings.restrictions.allowCommands" in most versions
            String path = "settings.restrictions.allowCommands";
            if (!authMeConfig.contains(path)) {
                // Some builds use kebab-case
                path = "settings.restrictions.allow-commands";
            }
            if (!authMeConfig.contains(path)) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info(
                            "[DEBUG] AuthMe config does not contain allowCommands path – "
                                    + "skipping automatic whitelisting.");
                }
                return;
            }
            List<String> allowed = new ArrayList<>(authMeConfig.getStringList(path));
            boolean changed = false;
            for (String cmd : BLG_COMMANDS) {
                if (!allowed.contains(cmd)) {
                    allowed.add(cmd);
                    changed = true;
                }
            }
            if (changed) {
                authMeConfig.set(path, allowed);
                plugin.getLogger().info("Added BLG commands to AuthMe's allowed-commands list.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not whitelist BLG commands in AuthMe config: " + e.getMessage(), e);
        }
    }
}
